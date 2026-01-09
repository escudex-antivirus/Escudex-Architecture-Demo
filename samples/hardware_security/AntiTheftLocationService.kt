// SAMPLE: Anti-Theft Hardware Orchestration (GPS & Persistence)
// 
// ARCHITECT'S NOTE: 
// Unlike standard background tasks, Anti-Theft tracking requires "Gold Standard" persistence.
// I orchestrated this service to use a PARTIAL_WAKE_LOCK to prevent CPU sleep during 
// GPS warm-up and data upload. It bypasses WorkManager for this specific use case 
// to ensure zero-latency execution, which is critical when a device is being tracked in real-time.

class AntiTheftLocationService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "AntiTheftService"
        private const val WAKELOCK_TAG = "Escudex:AntiTheftTracking"
        private const val GPS_TIMEOUT_MS = 30000L // 30s safety timeout to preserve battery
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      
        // DECISION: Acquiring WakeLock immediately to ensure the CPU doesn't 
        // enter deep sleep before the GPS can acquire a satellite fix.
      
        acquireWakeLock()
        
        startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        
        serviceScope.launch {
            executeTrackingProtocol()
        }
        
        return START_NOT_STICKY
    }

    private suspend fun executeTrackingProtocol() {
        try {
          
            // STRATEGY: High-accuracy request. We don't use cached locations here 
            // because accuracy is paramount for recovering a stolen device.
          
            val locationRequest = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0) // Force fresh location
                .build()

            val location = LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(locationRequest, null)
                .await()

            location?.let {
              
                // INTEGRATION: Securely uploading to AWS via the ApiManager orchestration.
              
                sendLocationToApi(it.latitude, it.longitude)
            }
        } finally {
          
            // CRITICAL: Always release hardware resources in the finally block 
            // to prevent permanent battery drain in case of network failure.
          
            releaseWakeLock()
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
          
            // Safety timeout: Even if everything fails, the hardware MUST release in 35s.
          
            acquire(GPS_TIMEOUT_MS + 5000)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
