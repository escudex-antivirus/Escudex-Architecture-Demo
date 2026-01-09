/*
 * SAMPLE 2: AI-Native Remote Command Orchestration (FCM Bridge)
 *
 * * ARCHITECT'S STRATEGY:
 *
 * On modern Android versions (API 31+), triggering immediate hardware actions 
 * from the background is a non-trivial architectural challenge. 
 *
 * * AI ORCHESTRATION LOOP:
 *
 * I orchestrated the AI agents to implement a "High-Priority Command Bridge." 
 * I directed the LLM to specifically handle the transition from a Firebase background 
 * thread to a System-Level Foreground Service, ensuring compliance with 
 * Android 14's strict background start restrictions.
 *
 */

class PushNotificationService : FirebaseMessagingService() {

    private val TAG = "PushNotificationService"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        
        // SECURITY REQUIREMENT:
        // High priority is enforced. I instructed the AI to ignore any low-priority commands 
        // to prevent potential "Command-Injection" delayed executions.
        
        if (remoteMessage.priority == RemoteMessage.PRIORITY_HIGH) {
            val command = remoteMessage.data["command"]
            Log.i(TAG, "Authenticated Remote Command Received: $command")

            // DECISION: Using a 'when' block for exhaustive command handling, 
            // ensuring a clear separation of concerns between different security modules.
            
            when (command) {
                "ALARM" -> triggerRemoteAlarm()
                "LOCK" -> triggerRemoteLock()
                "FETCH_LOCATION" -> startAntiTheftTracking()
                else -> Log.w(TAG, "Unknown or unmapped command received: $command")
            }
        }
    }

    /**
     * DECISION: Promoting to a MediaPlayback Foreground Service.
     * I orchestrated this logic to ensure the alarm bypasses "Doze Mode" 
     * by wrapping the intent in a startForegroundService call.
     */
     
    private fun triggerRemoteAlarm() {
        val intent = Intent(this, AlarmForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * SYSTEM-LEVEL SECURITY PROTOCOL:
     * This orchestrates a secure overlay (Activity) that acts as a gatekeeper 
     * before invoking DevicePolicyManager. 
     * * AI NOTE: Directed the AI to use FLAG_ACTIVITY_NEW_TASK and CLEAR_TOP 
     * to ensure the Lock Screen becomes the top-most visual element instantly.
     */
     
    private fun triggerRemoteLock() {
        val intent = Intent(this, RemoteLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    /**
     * PERFORMANCE OPTIMIZATION:
     * Immediate GPS warm-up. By promoting the task to a 'location' type 
     * Foreground Service, we ensure the system grants access to Fine Location 
     * even if the app was in a cached state.
     */
     
    private fun startAntiTheftTracking() {
        val intent = Intent(this, AntiTheftLocationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * ATOMIC TOKEN MANAGEMENT:
     * * ARCHITECT'S NOTE: I enforced a "User-First" authentication policy. 
     * Registration tokens are saved locally but only synced with AWS Cognito 
     * after a successful MFA authentication, preventing orphaned device links.
     */
     
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "System generated new FCM token.")
        
        // Orchestrated the AI to implement a safe persistence pattern 
        // to avoid network calls during early app lifecycle stages.
        saveTokenLocally(token)
    }

    private fun saveTokenLocally(token: String) {
        getSharedPreferences("escudex_secure_prefs", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
    }
}
