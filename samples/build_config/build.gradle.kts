// SAMPLE: Cross-Stack Dependency Orchestration & SDLC 2.0 Infrastructure
// 
// ARCHITECT'S NOTE: 
// This configuration is the result of a high-complexity orchestration loop. 
// The challenge was to align the cutting-edge Kotlin 2.0.0 toolchain with 
// conflicting enterprise libraries (AWS Amplify, Firebase, and Google Billing).
// I supervised the AI to implement a centralized "Version Catalog" strategy, 
// resolving version collisions that typically break AI-generated builds.

plugins {
    // DECISION: Using the modern Version Catalog (libs.versions.toml) for centralized management.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    
    // ARCHITECTURAL SHIFT: Replaced the obsolete 'kotlin-compose' with the new 
    // 'compose-compiler' plugin. This is mandatory for Kotlin 2.0.0, 
    // integrating the Compose compiler directly into the Kotlin toolchain.
    alias(libs.plugins.compose.compiler)
    
    id("com.google.gms.google-services")
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.firebase.crashlytics)
}

android {
    namespace = "com.escudex.antivirus"
    
    // COMPLIANCE DECISION: Updated to SDK 36.
    // Necessary for AGP 8.9.1 compatibility and ensuring the app meets 
    // upcoming Google Play requirements while accessing the latest Security APIs.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.escudex.antivirus"
        minSdk = 29 // Required for Scoped Storage and modern Biometric prompts.
        targetSdk = 36 
        
        versionCode = 11
        versionName = "2.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
    }
    
    // OPTIMIZATION: R8/ProGuard configuration aligned with Kotlin 2.0 metadata requirements.
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

// THE "CONFLICT RESOLUTION" MASTERPIECE:
// AI agents initially failed to resolve the 'Smithy' mismatch between AWS Amplify 
// and the core Kotlin libraries. I orchestrated this resolution strategy 
// to enforce version parity and prevent runtime ClassNotFoundExceptions.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "aws.smithy.kotlin") {
            useVersion("1.3.23")
            because("Enforces alignment across all Smithy modules required by Amplify Auth.")
        }
    }
}

dependencies {
    // SECURE NETWORKING & CLOUD
    implementation(libs.okhttp)
    implementation(libs.bundles.amplify) // Grouped via Version Catalog for cleaner architecture
    
    // REAL-TIME REMOTE COMMANDS
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    
    // HARDWARE & SECURITY
    implementation(libs.google.play.services.location) // High-precision tracking
    implementation(libs.androidx.biometric) // Secure local authentication
    
    // DATA PERSISTENCE
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
}
