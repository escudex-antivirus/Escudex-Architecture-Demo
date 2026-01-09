/*
 * SAMPLE 4: High-Performance Malware Analysis & Scoped Storage Compliance
 *
 * * ARCHITECT'S STRATEGY: 
 *
 * Malware scanning on Android requires a balance between deep file analysis 
 * and system stability. I designed this component to operate with a 
 * "Constant Memory Footprint" strategy. 
 *
 * * AI ORCHESTRATION NOTE: 
 *
 * I orchestrated the AI agents to implement a "Buffered Stream" approach instead 
 * of the naive 'readBytes()' method. This ensures the app can hash multi-GB files 
 * without triggering OutOfMemory (OOM) exceptions—a common pitfall in AI-generated I/O code.
 *
 */

class FileScanner(
    private val hashDao: HashDao,
    private val context: Context
) {

    companion object {
        private const val TAG = "FileScanner"
      
        // OPTIMIZATION: 8KB buffer size is aligned with standard flash storage 
        // page sizes, maximizing I/O throughput while keeping the stack footprint low.
      
        private const val BUFFER_SIZE = 8192 
    }

    /**
     * Calculates the SHA-256 hash using the ContentResolver system.
     * * DECISION: Utilizing 'openInputStream' via Uri is the only future-proof way 
     * to comply with Scoped Storage (API 29+). I enforced this to ensure 
     * cross-version compatibility without requiring 'LegacyExternalStorage' flags.
     */
     
    @WorkerThread
    fun calculateSHA256(fileUri: Uri): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            
            // ARCHITECTURAL ENFORCEMENT: The 'use' block (Kotlin's Try-with-resources) 
            // ensures the stream is closed automatically, preventing file descriptor leaks 
            // that could lead to system-wide instability.
            
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                
                // PERFORMANCE: Iterative update loop.
                // This allows the MessageDigest to process data in chunks, 
                // maintaining a stable 8KB memory usage regardless of file size.
              
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            } ?: return null

            // IDIOMATIC KOTLIN: Converting the byte array to a Hex string 
            // using an optimized joinToString transform.
          
            md.digest().joinToString("") { "%02x".format(it) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Security Scan I/O Failure for: $fileUri", e)
            null
        }
    }

    /**
     * Executes the malware lookup on a background dispatcher.
     * * STRATEGY: By explicitly using Dispatchers.IO, I orchestrated the 
     * separation of 'I/O Bound' tasks from the main thread, guaranteeing 
     * 60fps UI fluidness even during high-intensity disk operations.
     */
     
    suspend fun scanFile(fileUri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val hash = calculateSHA256(fileUri) ?: return@withContext ScanResult.Error("I/O Access Denied")

        // DECISION: Local hash-lookup for privacy and offline resilience.
        // I directed the AI to prioritize local DB hits to reduce network dependency.
        
        val isMalware = hashDao.isHashMalicious(hash)

        if (isMalware) {
            ScanResult.Malware(hash)
        } else {
            ScanResult.Safe(hash)
        }
    }
}
