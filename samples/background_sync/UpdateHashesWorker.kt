// "Demonstrates a robust WorkManager CoroutineWorker for differential
// updates to the local malware definition database."

package com.escudex.antivirus.samples.background_sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.escudex.antivirus.data.database.HashDao
import com.escudex.antivirus.data.database.MalwareHash
import com.escudex.antivirus.data.network.HashDiffResponse
// (In a real app, these would be injected via Hilt)
import com.escudex.antivirus.ApiManager
import com.escudex.antivirus.App
import com.escudex.antivirus.SessionManager

/**
 * Demonstrates a WorkManager CoroutineWorker responsible for updating the local
 * malware definition database.
 *
 * SENIOR-LEVEL DECISIONS HIGHLIGHTED:
 * 1.  **Diff-Based Updates:** Instead of downloading the entire hash database (which could be
 * megabytes), this worker fetches only the *difference* (diff) since the last
 * local version. This is critical for data efficiency and performance.
 * 2.  **Strategic Failure Handling:** The worker distinguishes between a "failure" (e.g.,
 * network error) and a "valid stop" (e.g., user is logged out).
 */
class UpdateHashesWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "UpdateHashesWorker"
    }

    // Dependencies are retrieved from the Application context for this sample.
    // In the full project, they are provided by Hilt.
    private val sessionManager: SessionManager = SessionManager(applicationContext)
    private val apiManager: ApiManager = ApiManager()
    private val hashDao: HashDao = (applicationContext as App).database.hashDao()


    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting hash database update check...")

        // ---
        // DECISION: Check for a valid user session *before* any network call.
        // If the user is logged out, there's no need to update. We return success()
        // to tell WorkManager the task is complete, not failed. Using retry() here
        // would create a pointless loop.
        // ---
        if (!sessionManager.isLoggedIn()) {
            Log.i(TAG, "User is not logged in. Skipping hash update.")
            return Result.success()
        }

        try {
            val localVersion = sessionManager.getDatabaseVersion()
            Log.d(TAG, "Local DB version: $localVersion")

            // Step 1: Check the latest version from the remote server
            val remoteVersion = apiManager.getLatestHashesVersion()
            Log.d(TAG, "Remote DB version: $remoteVersion")

            if (remoteVersion > localVersion) {
                Log.i(TAG, "New version found! Fetching diffs from $localVersion...")

                // Step 2: Fetch *only* the changes (the "diff")
                val diffResponse = apiManager.getHashesDiff(localVersion)

                // Step 3: Apply changes to the local Room database
                // (This operation would be wrapped in a @Transaction in a real repo)
                applyUpdates(hashDao, diffResponse)

                // Step 4: Only after a successful update, save the new version number
                sessionManager.saveDatabaseVersion(diffResponse.toVersion)
                Log.i(TAG, "Database updated to version ${diffResponse.toVersion}")

            } else {
                Log.i(TAG, "Hash database is already up-to-date.")
            }

            // ---
            // DECISION: Return success() as the work is finished, whether an
            // update was needed or not.
            // ---
            return Result.success()

        } catch (e: Exception) {
            // Catches any error (e.g., Network, API, Database)
            Log.e(TAG, "Failed to update hash database.", e)
            
            // ---
            // DECISION: Return retry() because this is a transient failure
            // (e.g., no internet connection). WorkManager will reschedule
            // this task according to its backoff policy.
            // ---
            return Result.retry()
        }
    }

    /**
     * Applies the downloaded diff to the local Room database.
     */
    private suspend fun applyUpdates(hashDao: HashDao, diff: HashDiffResponse) {
        if (diff.add.isNotEmpty()) {
            Log.d(TAG, "Adding ${diff.add.size} new hashes.")
            val newHashes = diff.add.map { MalwareHash(it) }
            hashDao.insertHashes(newHashes)
        }

        if (diff.remove.isNotEmpty()) {
            Log.d(TAG, "Removing ${diff.remove.size} hashes.")
            // In a full implementation, this would call hashDao.deleteHashes(diff.remove)
        }
    }
}
