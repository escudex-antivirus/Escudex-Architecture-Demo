package com.escudex.antivirus.samples.core_logic

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import com.escudex.antivirus.data.database.HashDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Sealed class to represent the result of a file scan.
 */
sealed class ScanResult {
    data class Safe(val hash: String) : ScanResult()
    data class Malware(val hash: String) : ScanResult()
    data class Error(val message: String) : ScanResult()
}

/**
 * Demonstrates the core "brain" of the antivirus scanner.
 *
 * SENIOR-LEVEL DECISIONS HIGHLIGHTED:
 * 1.  **Efficient Hashing (calculateSHA256):** Uses a buffered InputStream
 * (8192-byte buffer) to calculate the SHA-256 hash. This is memory-efficient
 * and can handle large files without an OutOfMemoryError.
 * 2.  **ContentResolver Mastery:** Correctly uses `context.contentResolver.openInputStream`
 * to handle *any* URI from the Android system (Media Store, file picker, etc.).
 * 3.  **Thread Safety (Coroutines):** All I/O operations are designed to be
 * called from a background thread (e.g., `withContext(Dispatchers.IO)`),
 * as shown in `checkFile`.
 */
class FileScanner(
    private val hashDao: HashDao, // Injected via Hilt in the real app
    private val context: Context
) {

    companion object {
        private const val TAG = "FileScanner"
    }

    /**
     * Checks a file URI against the malware database.
     * This is the main entry point for scanning.
     */
    @WorkerThread
    suspend fun checkFile(fileUri: Uri): ScanResult {
        // Step 1: Efficiently calculate the file's hash
        // This function is I/O-bound and must be called from a background thread.
        val sha256Hash = calculateSHA256(fileUri)
            ?: return ScanResult.Error("Failed to calculate hash for $fileUri")

        // Step 2: Check the hash against the local Room database
        return try {
            // This is a suspend function from Room, also non-blocking.
            if (hashDao.hashExists(sha256Hash) > 0) {
                Log.w(TAG, "Malware DETECTED! Hash: $sha256Hash (URI: $fileUri)")
                ScanResult.Malware(sha256Hash)
            } else {
                ScanResult.Safe(sha256Hash)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying database for $fileUri", e)
            ScanResult.Error("Database error: ${e.message}")
        }
    }

    /**
     * Calculates the SHA-256 hash of a file given its URI.
     * This method is the core of the scanner's detection logic.
     */
    @WorkerThread
    fun calculateSHA256(fileUri: Uri): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")

            // ---
            // DECISION: Use contentResolver.openInputStream to handle *any* URI
            // (from file picker, media store, etc.) safely and without
            // needing legacy file path permissions.
            // ---
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                // ---
                // DECISION: Use a buffer. Reading byte-by-byte is extremely slow.
                // Reading the entire file into memory causes OutOfMemoryError.
                // An 8KB buffer is a standard, efficient compromise for I/O.
                // ---
                val buffer = ByteArray(8192)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            } ?: run {
                Log.e(TAG, "Could not open InputStream for URI: $fileUri")
                return null
            }
            
            // Format the resulting hash bytes as a hex string
            md.digest().joinToString("") { "%02x".format(it) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating SHA-256 for URI '$fileUri': ${e.message}")
            null
        }
    }
    
    // ---
    // NOTE: Other utility functions like getRealPathFromUri() and
    // getFileNameFromUri() are part of the full class but omitted
    // here for brevity. This sample focuses on the core scanning logic.
    // ---
}
