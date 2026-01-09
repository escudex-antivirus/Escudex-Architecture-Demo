// SAMPLE: High-Performance File I/O & Malware Analysis
// 
// ARCHITECT'S NOTE: 
// Scanning the entire file system for malware requires a balance between speed and 
// system stability. I orchestrated this component to use a "Buffered Stream" 
// approach. By processing files in 8KB chunks, the app can hash files of any size 
// (even multi-GB videos) with a constant memory footprint, preventing OOM crashes.

class FileScanner(
    private val hashDao: HashDao,
    private val context: Context
) {

    companion object {
        private const val TAG = "FileScanner"
        private const val BUFFER_SIZE = 8192 // 8KB: Optimized for most flash storage page sizes
    }

    /**
     * Calculates the SHA-256 hash of a file using the ContentResolver system.
     * * DECISION: Utilizing 'openInputStream' via Uri is the only future-proof way 
     * to comply with Android's Scoped Storage (API 29+). This ensures the app 
     * remains functional as Google further restricts direct file path access.
     */
    @WorkerThread
    fun calculateSHA256(fileUri: Uri): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            
            // ARCHITECTURAL ENFORCEMENT: The 'use' block ensures the stream 
            // is closed automatically, preventing file descriptor leaks.
            
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                
                // PERFORMANCE: We update the digest iteratively.
                // This keeps memory usage low regardless of file size.
                
                while (inputStream.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            } ?: return null

            // Convert the byte array to a readable Hex string.
            
            md.digest().joinToString("") { "%02x".format(it) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate hash for: $fileUri", e)
            null
        }
    }

    /**
     * Checks the calculated hash against the local malware database.
     * * STRATEGY: By performing the lookup on the IO Dispatcher, we ensure 
     * that the DB query doesn't compete with UI rendering resources.
     */
     
    suspend fun scanFile(fileUri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val hash = calculateSHA256(fileUri) ?: return@withContext ScanResult.Error("I/O Error")

        // DECISION: Local database lookup provides instant offline protection, 
        // a core requirement for a resilient security suite.
        
        val isMalware = hashDao.isHashMalicious(hash)

        if (isMalware) {
            ScanResult.Malware(hash)
        } else {
            ScanResult.Safe(hash)
        }
    }
}
