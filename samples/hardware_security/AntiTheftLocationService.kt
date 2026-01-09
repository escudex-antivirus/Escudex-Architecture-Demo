/*
 * SAMPLE 3: High-Priority Hardware Orchestration (Anti-Theft GPS Protocol)
 *
 * * ARCHITECT'S STRATEGY:
 *
 * Standard background solutions like WorkManager are insufficient for real-time recovery. 
 * I orchestrated this "Gold Standard" service to bypass system-imposed latency. 
 *
 * * AI ORCHESTRATION NOTE: 
 *
 * I guided the AI agents to select the FusedLocationProviderClient over the legacy 
 * LocationManager, specifically enforcing the use of coroutine-based '.await()' calls 
 * to maintain a clean, non-blocking asynchronous flow for hardware-bound tasks.
 *
 */

class AntiTheftLocationService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "AntiTheftService"
        private const val WAKELOCK_TAG = "Escudex:AntiTheftTracking"
        
        // SAFETY TIMEOUT: Hardcoded 30s limit to protect user's battery health 
        // in case of satellite signal obstruction (Deep Indoor/Tunnels).
        
        private const val GPS_TIMEOUT_MS = 30000L 
        private const val NOTIFICATION_ID = 999
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        
        // DECISION: Immediate WakeLock acquisition. 
        // I directed the AI to acquire the CPU lock BEFORE starting the foreground protocol 
        // to ensure the process isn't suspended during the initial notification handshake.
        
        acquireWakeLock()
        
        // COMPLIANCE: Adhering to Android 14+ Foreground Service types.
        // Explicitly declaring 'FOREGROUND_SERVICE_TYPE_LOCATION' is mandatory for system trust.
        
        startForeground(
            NOTIFICATION_ID, 
            createNotification(), 
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        
        serviceScope.launch {
            executeTrackingProtocol()
        }
        
        // STRATEGY: START_NOT_STICKY prevents the system from recreating the service 
        // with a null intent if killed—forcing a clean, orchestrated restart from FCM instead.
        
        return START_NOT_STICKY
    }

    private suspend fun executeTrackingProtocol() {
        try {
            
            // STRATEGY: High-Precision Burst.
            // I instructed the AI to bypass the location cache (MaxUpdateAge = 0) 
            // to ensure the recovery coordinates are real-time, not historical.
            
            val locationRequest = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0) 
                .build()

            val location = LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(locationRequest, null)
                .await() // Suspending until hardware provides a fix

            location?.let {
                
                // ORCHESTRATION: Direct cloud sync via our authenticated API layer.
                
                sendLocationToApi(it.latitude, it.longitude)
            }
        } finally {
            
            // CRITICAL RESOURCE STEWARDSHIP: 
            // Enforced a mandatory cleanup loop. The hardware MUST release 
            // CPU and GPS resources even if the network or API call fails.
            
            releaseWakeLock()
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        
        // DECISION: Using PARTIAL_WAKE_LOCK to keep the CPU active while allowing 
        // the screen to remain off, optimizing battery during a silent tracking session.
        
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            acquire(GPS_TIMEOUT_MS + 5000) // Safety margin for network latency
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock release failed: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
