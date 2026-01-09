// SAMPLE: Efficient Data Synchronization (WorkManager Orchestration)
// 
// ARCHITECT'S NOTE: 
// Background synchronization for malware definitions is a battery-intensive task.
// I orchestrated this Worker to implement a "Differential Update Strategy." 
// Instead of a full database download, the AI was directed to fetch only the "diffs" 
// since the last local version. This optimizes data usage and preserves battery, 
// a hallmark of senior-level mobile architecture.

class UpdateHashesWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "UpdateHashesWorker"
    }

    // Dependencies provided by the AI-orchestrated Hilt/DI container in the full project.
    
    private val sessionManager: SessionManager = SessionManager(applicationContext)
    private val apiManager: ApiManager = ApiManager()
    private val hashDao: HashDao = (applicationContext as App).database.hashDao()

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting hash database update check...")

        // DECISION: Prevent unnecessary network overhead.
        // I instructed the AI to implement a pre-check: if the session is invalid, 
        // the task terminates immediately with success() to avoid infinite retry loops.
        
        if (!sessionManager.isLoggedIn()) {
            Log.i(TAG, "User is not logged in. Skipping hash update.")
            return Result.success()
        }

        return try {
            val localVersion = sessionManager.getDatabaseVersion()
            
            // STRATEGY: Remote-first version check.
            
            val remoteVersion = apiManager.getLatestHashesVersion()

            if (remoteVersion > localVersion) {
                
                // ORCHESTRATION: The AI correctly implemented the 'diff' fetching logic 
                // under my supervision to minimize payload size.
                
                val diffResponse = apiManager.getHashesDiff(localVersion)

                applyUpdates(hashDao, diffResponse)

                sessionManager.saveDatabaseVersion(diffResponse.toVersion)
                Log.i(TAG, "Database updated to version ${diffResponse.toVersion}")
                Result.success()
            } else {
                Log.i(TAG, "Hash database is already up-to-date.")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transient failure during sync.", e)
            
            // DECISION: Utilize WorkManager's exponential backoff.
            // By returning retry(), we leverage the system's ability to reschedule 
            // when conditions (like internet connectivity) are favorable.
            
            Result.retry()
        }
    }

    private suspend fun applyUpdates(hashDao: HashDao, diff: HashDiffResponse) {
        
        // DATA INTEGRITY: AI-generated mapping to MalwareHash entities 
        // ensuring type safety before database insertion.
        
        if (diff.add.isNotEmpty()) {
            val newHashes = diff.add.map { MalwareHash(it) }
            hashDao.insertHashes(newHashes)
        }
    }
}
