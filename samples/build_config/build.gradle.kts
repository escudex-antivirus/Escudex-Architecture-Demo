/*
 *
 * SAMPLE 1: AI-Native Platform Engineering & SDLC 2.0 Infrastructure
 *
 * * ARCHITECT'S STRATEGY: 
 *
 * This module serves as the automated blueprint for the Escudex ecosystem. 
 * Challenge: Aligning the cutting-edge Kotlin 2.0.0 toolchain with heavyweight 
 * enterprise SDKs (AWS Amplify, Firebase, Google Billing) while targeting SDK 36.
 *
 * * AI ORCHESTRATION NOTE: 
 *
 * I orchestrated a multi-agent feedback loop to resolve the 'Smithy' dependency hell 
 * that typically causes AI agents to hallucinate incompatible build scripts. 
 * By enforcing a centralized Version Catalog and a custom Resolution Strategy, 
 * I achieved a zero-technical-debt infrastructure.
 *
 */

plugins {

    // DECISION: Enforcing 'Version Catalog' (libs.versions.toml) to prevent 
    // version drift—a common failure point in large-scale AI coding projects.

    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    
    // EVOLUTION: Mandatory shift to the new 'Compose Compiler' plugin for Kotlin 2.0.
    // This removes the need for manual compiler-version mapping, 
    // streamlining the AI's ability to generate stable UI code.

    alias(libs.plugins.compose.compiler)
    
    id("com.google.gms.google-services")
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.firebase.crashlytics)
}

android {
    namespace = "com.escudex.antivirus"
    
    // COMPLIANCE & SECURITY: Targeting SDK 36 (latest Android preview/standard) 
    // to leverage advanced runtime permissions and foreground service restrictions.

    compileSdk = 36

    defaultConfig {
        applicationId = "com.escudex.antivirus"
        minSdk = 29 // Decision: Baseline for Scoped Storage & modern Biometrics.
        targetSdk = 36 
        
        versionCode = 11
        versionName = "2.0-AI-Enhanced"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
    }
    
    // SECURITY OBFUSCATION (R8/ProGuard):
    // Orchestrated AI to configure aggressive minification and resource shrinking, 
    // ensuring the production binary is resilient against reverse engineering.

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), 
                "proguard-rules.pro"
            )
        }
    }

    // MODERN TOOLCHAIN: Enforcing Java 17 for enhanced build performance 
    // and compatibility with the latest Android Gradle Plugin (AGP 8.9.1).

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

/**
 * THE "ORCHESTRATION MASTERPIECE":
 * AI agents initially failed to reconcile 'aws.smithy.kotlin' versioning 
 * across the Amplify Auth and Storage modules. 
 *
 * * My Fix: Implementation of a global resolutionStrategy to force module 
 * alignment, preventing 'ClassNotFound' or 'MethodNotFound' runtime crashes.
 *
 */

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "aws.smithy.kotlin") {
            useVersion("1.3.23")
            because("Critical fix: Aligns Smithy modules required by AWS Amplify Auth.")
        }
    }
}

dependencies {

    // INFRASTRUCTURE BUNDLES (From libs.versions.toml)
    // Using bundles minimizes 'dependency bloat' and improves AI context window efficiency.

    implementation(libs.bundles.amplify)
    implementation(libs.bundles.room)
    
    // REMOTE SECURITY ORCHESTRATION

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging) // Cloud-to-Device Command Bridge
    
    // HARDWARE-LEVEL SECURITY APIs

    implementation(libs.google.play.services.location) // High-precision anti-theft
    implementation(libs.androidx.biometric) // Bio-metric Vault Access
    
    // QUALITY ASSURANCE (SDLC 2.0)

    testImplementation(libs.bundles.testing.unit)
    androidTestImplementation(libs.bundles.testing.instrumentation)
}
