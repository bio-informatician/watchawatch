plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val uploadStoreFile = providers.environmentVariable("WATCHAWATCH_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.environmentVariable("WATCHAWATCH_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("WATCHAWATCH_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("WATCHAWATCH_UPLOAD_KEY_PASSWORD").orNull
val uploadSigningValues = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
val hasAnyUploadSigning = uploadSigningValues.any { !it.isNullOrBlank() }
val hasUploadSigning = uploadSigningValues.all { !it.isNullOrBlank() }
check(!hasAnyUploadSigning || hasUploadSigning) {
    "Set all four WATCHAWATCH_UPLOAD_* signing environment variables or leave all unset."
}

android {
    namespace = "com.watchawatch.app"
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.watchawatch.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 140
        versionName = "1.4.0"
    }

    if (hasUploadSigning) {
        signingConfigs {
            create("playUpload") {
                storeFile = file(requireNotNull(uploadStoreFile))
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasUploadSigning) signingConfig = signingConfigs.getByName("playUpload")
        }
    }

    buildFeatures {
        compose = true
    }
}

tasks.configureEach {
    if (name == "signReleaseBundle" || name == "bundleRelease") {
        doFirst {
            check(hasUploadSigning) {
                "A signed Play bundle requires WATCHAWATCH_UPLOAD_STORE_FILE, WATCHAWATCH_UPLOAD_STORE_PASSWORD, WATCHAWATCH_UPLOAD_KEY_ALIAS, and WATCHAWATCH_UPLOAD_KEY_PASSWORD."
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    testImplementation("junit:junit:4.13.2")
}
