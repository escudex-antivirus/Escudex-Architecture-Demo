package com.escudex.antivirus.samples.services_api

import android.app.AppOpsManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.annotation.WorkerThread
// (Assume other imports like SessionManager, NotificationHelper, etc. exist)
import com.escudex.antivirus.AppLockScreenActivity
import com.escudex.antivirus.NotificationHelper
import com.escudex.antivirus.PlanType
import com.escudex.antivirus.SessionManager
import com.escudex.antivirus.App

/**
 * Demonstrates a ForegroundService for the "App Lock" feature.
 *
 * SENIOR-LEVEL DECISIONS HIGHLIGHTED:
 * 1.  **Foreground Service:** Correctly promotes the service to the foreground
 * to ensure it is not killed by the OS, which is critical for a
 * security feature.
 * 2.  **Dedicated HandlerThread:** Polling for app usage is moved off the main
 * thread onto a *dedicated background thread* (HandlerThread) to prevent
 * *any* UI lag or ANRs.
 * 3.  **UsageStatsManager:** Uses a restricted, high-privacy API to efficiently
 * query the current foreground app.
 * 4.  **Performance Tuning:** Polling interval is carefully chosen (300ms) to be
 * responsive without draining the battery.
 */
class AppLaunchMonitorService : Service() {

    companion object {
        private const val TAG = "AppLockMonitorService"
        // A polling interval tuned for responsiveness without excessive battery use.
        private const val POLLING_INTERVAL_MS = 300L

    }

    // ---
    // DECISION: A HandlerThread is chosen over other async methods (like
    // Coroutines with a delay) because it provides a dedicated, serial
    // execution queue. This ensures that one check finishes before the
    // next one starts, preventing race conditions.
    // ---
    private lateinit var handlerThread: HandlerThread
    private lateinit var serviceHandler: Handler
    
    private lateinit var sessionManager: SessionManager
    private lateinit var usageStatsManager: UsageStatsManager

    @Volatile
    private var isMonitoring = false
    private var lastForegroundApp: String? = null

    private val monitorRunnable = object : Runnable {
        override fun run() {
            // This check runs on the HandlerThread
            checkForegroundApp()
            
            if (isMonitoring && serviceHandler.looper.thread.isAlive) {
                // Reschedule itself on the same HandlerThread
                serviceHandler.postDelayed(this, POLLING_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")

        // (Dependencies would be injected in the real app)
        sessionManager = SessionManager(applicationContext)
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager

        // Start the dedicated background thread for polling
        handlerThread = HandlerThread("AppLockMonitorThread", Process.THREAD_PRIORITY_BACKGROUND)
        handlerThread.start()
        serviceHandler = Handler(handlerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand received.")

        // Check for permissions and user plan before starting
        if (sessionManager.getUserPlan() != PlanType.PREMIUM || !hasUsageStatsPermission()) {
            Log.w(TAG, "User not premium or permission denied. Stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        // ---
        // DECISION: Promote to Foreground Service immediately with a notification.
        // This is a requirement for long-running services and guarantees
        // the OS will not kill this critical security monitor.
        // ---
        val notification = (applicationContext as App).notificationHelper.getAppLockServiceNotification()
        startForeground(NotificationHelper.APP_LOCK_FOREGROUND_SERVICE_ID, notification)
        Log.i(TAG, "Service promoted to Foreground.")

        if (!isMonitoring) {
            Log.i(TAG, "Starting monitoring...")
            isMonitoring = true
            // Post the runnable to the HandlerThread, NOT the main thread.
            serviceHandler.post(monitorRunnable)
        }

        // Ensures the service restarts if the OS kills it
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service onDestroy.")
        isMonitoring = false
        serviceHandler.removeCallbacks(monitorRunnable) // Stop the loop
        handlerThread.quitSafely() // Stop the background thread
        super.onDestroy()
    }

    @WorkerThread // This annotation confirms it runs on a background thread
    private fun checkForegroundApp() {
        if (!isMonitoring) return

        val currentForegroundApp = getForegroundAppPackage()

        if (currentForegroundApp == null || currentForegroundApp == lastForegroundApp) return

        // ---
        // DECISION: Ignore self, launchers, and system UI to prevent self-locking
        // or locking the user out of their home screen. This is a critical
        // stability check.
        // ---
        val ignorePackages = setOf(packageName, "com.android.launcher", "com.google.android.apps.nexuslauncher", "com.android.systemui")
        if (ignorePackages.contains(currentForegroundApp)) return

        Log.d(TAG, "App in foreground changed to: $currentForegroundApp")
        lastForegroundApp = currentForegroundApp

        val lockedApps = sessionManager.getLockedApps()

        if (lockedApps.contains(currentForegroundApp)) {
            // (The 'unlockedAppTracker' provides a "grace period" logic
            //  so a freshly-unlocked app isn't immediately re-locked)
            val tracker = (applicationContext as App).unlockedAppTracker
            if (!tracker.isWithinGracePeriod(currentForegroundApp)) {
                Log.i(TAG, "!!! Locked app ($currentForegroundApp) detected. Launching lock screen.")

                val lockIntent = Intent(this, AppLockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(AppLockScreenActivity.EXTRA_LOCKED_APP_PACKAGE_NAME, currentForegroundApp)
                }
                try {
                    startActivity(lockIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching AppLockScreenActivity", e)
                }
            }
        }
    }

    /**
     * Queries the UsageStatsManager to find the current foreground app.
     * This runs on the HandlerThread.
     */
    @WorkerThread
    private fun getForegroundAppPackage(): String? {
        var foregroundAppPackage: String? = null
        val endTime = System.currentTimeMillis()
        // Query a small window of time
        val startTime = endTime - (POLLING_INTERVAL_MS * 4) 

        try {
            val usageEvents: UsageEvents? = usageStatsManager.queryEvents(startTime, endTime)
            usageEvents?.let { events ->
                val event = UsageEvents.Event()
                var lastEventTime = 0L
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    // ---
                    // DECISION: Look for the *latest* ACTIVITY_RESUMED (or MOVE_TO_FOREGROUND)
                    // event, as this is the most reliable indicator of the
                    // current foreground app.
                    // ---
                    if ((event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) 
                        && event.timeStamp > lastEventTime) {
                        lastEventTime = event.timeStamp
                        foregroundAppPackage = event.packageName
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying UsageStatsManager", e)
        }
        return foregroundAppPackage
    }

    /**
     * Checks if the app has the necessary USAGE_STATS permission.
     */
    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode =
            appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    override fun onBind(intent: Intent): IBinder? = null
}
