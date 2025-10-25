package com.escudex.antivirus.samples.architecture

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.escudex.antivirus.FileScanner
import com.escudex.antivirus.ScanResult
import com.escudex.antivirus.SessionManager
import com.escudex.antivirus.PlanType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sealed class for one-time UI events (e.g., showing a Toast or Dialog).
 *
 * DECISION: Using a Channel/Flow for events (like a Toast) instead of
 * StateFlow prevents the event from being re-fired on configuration changes
 * (e.g., screen rotation).
 */
sealed class UiEvent {
    data class MalwareAlert(val fileName: String, val fileHash: String) : UiEvent()
    data class ToastEvent(val message: String) : UiEvent()
}

/**
 * Demonstrates a central ViewModel (SharedViewModel) managing the state
 * for multiple fragments and coordinating complex logic.
 *
 * SENIOR-LEVEL DECISIONS HIGHLIGHTED:
 * 1.  **StateFlow for UI State:** Uses `StateFlow` to hold observable,
 * replayable UI state (e.g., `isScanning`, `currentUserPlan`).
 * Fragments collect this flow to react to changes.
 * 2.  **Channel for UI Events:** Uses `Channel` (exposed as a Flow) to send
 * one-time "events" to the UI (like showing a Toast or Dialog).
 * 3.  **viewModelScope:** All coroutines are launched in `viewModelScope`,
 * ensuring they are automatically cancelled when the ViewModel is cleared.
 * 4.  **Coordination Hub:** This ViewModel acts as the "brain" for the UI,
 * coordinating the FileScanner, WorkManager, and SessionManager.
 */
class SharedViewModel(application: Application) : AndroidViewModel(application) {

    // (Dependencies would be injected via Hilt in the full project)
    private val fileScanner: FileScanner by lazy { (application as App).fileScanner }
    private val sessionManager: SessionManager = SessionManager(application)
    private val workManager: WorkManager = WorkManager.getInstance(application)

    // ---
    // DECISION 1: StateFlow for observable UI state.
    // A private MutableStateFlow is the "source of truth",
    // and a public StateFlow is exposed for Fragments to collect.
    // ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _currentUserPlan = MutableStateFlow<PlanType?>(null)
    val currentUserPlan: StateFlow<PlanType?> = _currentUserPlan.asStateFlow()

    // ---
    // DECISION 2: Channel for one-time UI events.
    // This flow does not "hold" a value and is perfect for showing alerts.
    // ---
    private val _uiEventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val uiEventFlow = _uiEventChannel.receiveAsFlow()


    init {
        // Load initial state when the ViewModel is first created
        loadInitialPlan()
    }

    private fun loadInitialPlan() {
        // Use viewModelScope to launch a coroutine safely
        viewModelScope.launch {
            _currentUserPlan.value = sessionManager.getUserPlan()
            // (In real app, would also fetch from API)
        }
    }

    /**
     * Orchestrates a manual file scan initiated by the user.
     * This function demonstrates state/event management and context switching.
     */
    fun analyzeFile(uriToScan: Uri, fileName: String) {
        if (_isScanning.value) return // Prevent multiple scans at once

        viewModelScope.launch {
            // 1. Update UI State (Show loading indicator)
            _isScanning.value = true
            
            try {
                // ---
                // DECISION: Dispatch I/O-bound work (hashing, DB query)
                // to the IO dispatcher. This keeps the main thread free.
                // ---
                when (val result = withContext(Dispatchers.IO) { fileScanner.checkFile(uriToScan) }) {
                    is ScanResult.Malware -> {
                        // 2. Send one-time UI Event (Show alert)
                        _uiEventChannel.send(UiEvent.MalwareAlert(fileName, result.hash))
                        // ... (logic to log malware to server) ...
                    }
                    is ScanResult.Safe -> {
                        _uiEventChannel.send(UiEvent.ToastEvent("'$fileName' appears safe."))
                    }
                    is ScanResult.Error -> {
                        _uiEventChannel.send(UiEvent.ToastEvent("Error scanning '$fileName'."))
                    }
                }
            } catch (e: Exception) {
                _uiEventChannel.send(UiEvent.ToastEvent("Error: ${e.message}"))
            } finally {
                // 3. Update UI State (Hide loading indicator)
                _isScanning.value = false
            }
        }
    }

    /**
     * Orchestrates the enqueueing of a full-system scan using WorkManager.
     */
    fun startManualFullScan() {
        val scanWorkRequest = OneTimeWorkRequestBuilder<FullScanWorker>()
            .addTag("MANUAL_FULL_SCAN_WORK_NAME")
            .build()
            
        workManager.enqueueUniqueWork(
            "MANUAL_FULL_SCAN_WORK_NAME",
            ExistingWorkPolicy.REPLACE,
            scanWorkRequest
        )

        // Send a feedback event to the user
        viewModelScope.launch {
            _uiEventChannel.send(UiEvent.ToastEvent("Full system scan started."))
        }
    }
    
    // ---
    // NOTE: The full ViewModel also manages permissions, billing logic,
    // network callbacks, and report generation. Those have been
    // omitted here to focus purely on the core MVVM architecture.
    // ---
}
