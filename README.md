# Escudex - Android Security Architecture Showcase

### Executive Summary
Escudex is a high-performance Android security suite, providing antivirus, anti-theft, and privacy-protection features. This repository is a public showcase demonstrating the core architecture, advanced technical solutions, and code quality of the project.

It is designed to highlight senior-level expertise in modern Android development, security principles, and scalable system design.

### Core Architecture
This project is built on a modern, decoupled Android stack, emphasizing testability, scalability, and maintainability.

* **Architecture:** MVVM (Model-View-ViewModel) with Clean Architecture principles.
* **Decoupling:** Separation of concerns using Repositories, UseCases (Interactors), and ViewModels.
* **Asynchronous:** 100% Kotlin Coroutines & Flows (StateFlow, SharedFlow) for managing UI state and background tasks.
* **Dependency Injection:** Hilt for managing dependencies and component lifecycles.

---

### Anti-Theft Architecture Diagram

<img width="3308" height="2338" alt="Escudex - System Architecture" src="https://github.com/user-attachments/assets/9b9f1fd5-680d-4c88-ac14-0e85a79bb4e8" />


---

### Technology Stack

| Category | Technology |
| :--- | :--- |
| **Language** | Kotlin (100%) |
| **UI** | Android XML (Jetpack) |
| **Architecture** | MVVM / Clean Architecture |
| **Async** | Kotlin Coroutines & Flows |
| **DI** | Hilt |
| **Background Tasks** | WorkManager (for persistent, efficient tasks) |
| **Services** | Foreground Services (for 24/7 monitoring) |
| **Security APIs** | Device Administration API, UsageStatsManager, Crypto |
| **Networking** | Retrofit, OkHttp (with secure interceptors) |
| **Database** | Room (for local virus definitions & logs) |
| **Backend** | AWS Lambda, DynamoDB, AWS Secrets Manager, AWS Cognito, Google Firebase (FCM) |

---

### Featured Code Slices

The `/samples` directory contains curated code slices demonstrating solutions to complex Android security challenges. These are non-compilable, sanitized examples, **annotated with comments explaining senior-level architectural decisions.**

* **`/samples/background_sync`**:
    * **File:** `UpdateHashesWorker.kt`
    * **Demonstrates:** An efficient, differential database update strategy using `WorkManager`. Instead of downloading the entire database, it fetches only the "diff," saving data and battery.

* **`/samples/services_api`**:
    * **File:** `AppLaunchMonitorService.kt`
    * **Demonstrates:** A high-performance `ForegroundService` that uses the restricted `UsageStatsManager` API. It shows how to monitor app launches in real-time for the App Lock feature without draining the battery.

* **`/samples/core_logic`**:
    * **File:** `FileScanner.kt`
    * **Demonstrates:** The "brain" of the scanner. This slice shows efficient byte-stream hashing (SHA-256) and robust handling of Android's complex `ContentResolver` and URI systems (a common developer pain point).

* **`/samples/architecture`**:
    * **File:** `SharedViewModel.kt`
    * **Demonstrates:** A central `ViewModel` managing complex, global UI state (`StateFlow`) and one-time UI events (`Channel`), following modern MVVM and Clean Architecture principles.
---

### Intellectual Property Notice

**Please Note:** This is a public showcase repository. It demonstrates the architecture, core concepts, and code quality of the Escudex project. For security and intellectual property reasons, the full source code, API endpoints, and sensitive business logic are not included.
