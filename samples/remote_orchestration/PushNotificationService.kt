// SAMPLE: Remote Command Bridge (FCM Orchestration)
// 
// ARCHITECT'S NOTE: 
// In modern Android (API 31+), starting background tasks from a broadcast/push is highly restricted.
// I orchestrated this service to utilize "High-Priority FCM" messages as a trigger to bypass 
// background execution limits, immediately promoting the command to a Foreground Service 
// or a System-Level Activity. This ensures near-zero latency for critical security actions.

class PushNotificationService : FirebaseMessagingService() {

    private val TAG = "PushNotificationService"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
      
        // DECISION: High priority is mandatory to start Foreground Services from the background 
        // on Android 12+. I enforced this policy to ensure the Anti-Theft features trigger instantly.
      
        if (remoteMessage.priority == RemoteMessage.PRIORITY_HIGH) {
            val command = remoteMessage.data["command"]
            Log.i(TAG, "High Priority Command Received: $command")

            when (command) {
                "ALARM" -> triggerRemoteAlarm()
                "LOCK" -> triggerRemoteLock()
                "FETCH_LOCATION" -> startAntiTheftTracking()
            }
        }
    }

    private fun triggerRemoteAlarm() {
      
        // ARCHITECTURAL CHOICE: 
        // We use a dedicated Foreground Service (AlarmForegroundService) to handle 
        // continuous audio playback even if the app process is under memory pressure.
      
        val intent = Intent(this, AlarmForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun triggerRemoteLock() {
      
        // SECURITY PROTOCOL:
        // Remote lock is a two-stage process. We launch a 'RemoteLockActivity' 
        // that acts as a secure overlay, which then invokes 'DevicePolicyManager.lockNow()'.
        // This ensures the user is visually notified before the system lock occurs.
      
        val intent = Intent(this, RemoteLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun startAntiTheftTracking() {
      
        // PERFORMANCE OPTIMIZATION:
        // Directly invoking the Gold-Standard Location Service.
        // This ensures the GPS hardware is warmed up and a WakeLock is acquired instantly.
      
        val intent = Intent(this, AntiTheftLocationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        
        // DECISION: Token registration is now an explicit user-driven action via UI,
        // preventing unnecessary network calls until the user is authenticated with AWS Cognito.
        
        Log.d(TAG, "New FCM Token generated: $token")
        saveTokenLocally(token)
    }
}
