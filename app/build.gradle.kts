plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.krank56.webmote"
    compileSdk = 37

    defaultConfig {
        // Permanent once on Google Play: Canapé Studio's domain (canapestudio.app). The Kotlin
        // packages keep the original io.github.krank56.webmote namespace.
        applicationId = "app.canapestudio.webmote"
        minSdk = 26
        // Google Play requires API 36 for new apps and updates from 31 August 2026.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // The Play upload key lives outside the repository: its path and passwords come from the
    // WEBMOTE_UPLOAD_KEY* environment variables. Without them the release build is left unsigned.
    val uploadKeystore = providers.environmentVariable("WEBMOTE_UPLOAD_KEYSTORE").orNull
    signingConfigs {
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = providers.environmentVariable("WEBMOTE_UPLOAD_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("WEBMOTE_UPLOAD_KEY_ALIAS").getOrElse("upload")
                keyPassword = providers.environmentVariable("WEBMOTE_UPLOAD_KEY_PASSWORD")
                    .orElse(providers.environmentVariable("WEBMOTE_UPLOAD_KEYSTORE_PASSWORD")).get()
            }
        }
    }

    buildTypes {
        release {
            if (uploadKeystore != null) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        localeFilters += listOf("en", "fr")
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.media)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Plain JVM tests for the few adapter pieces with logic of their own.
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
