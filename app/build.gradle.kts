plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.luna.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lunevra.luna"
        minSdk = 26
        targetSdk = 36
        // Release CI passes the tag (v0.2.0 -> 0.2.0) and a rising build number so updates install over old builds.
        versionCode = System.getenv("LUNA_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("LUNA_VERSION_NAME") ?: "0.1.0"
    }

    // One permanent key, supplied by CI secrets (or the same env vars locally). Without it the
    // release build stays unsigned. A stable key is what lets updates install over old versions
    // and stops every build looking like a brand-new developer to Play Protect.
    val keystorePath: String? = System.getenv("LUNA_KEYSTORE_PATH")
    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("LUNA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LUNA_KEY_ALIAS")
                keyPassword = System.getenv("LUNA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-common:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("io.github.kyant0:backdrop:1.0.1")

    // Haze is deliberately isolated behind LunaGlassSurface so it can be
    // replaced or disabled without changing screen architecture.
    implementation("dev.chrisbanes.haze:haze:2.0.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

