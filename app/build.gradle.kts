plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.luna.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luna.music"
        minSdk = 26
        targetSdk = 36
        // CI supplies a monotonically increasing build number; local builds stay at 1.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.0"
    }

    // Signing secrets come from the environment (CI) or ~/.gradle/gradle.properties and are
    // never committed. Without them the release build is simply left unsigned.
    val keystorePath = System.getenv("LUNA_KEYSTORE_PATH")
        ?: providers.gradleProperty("lunaKeystorePath").orNull
    fun secret(env: String, prop: String) =
        System.getenv(env) ?: providers.gradleProperty(prop).orNull

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = secret("LUNA_KEYSTORE_PASSWORD", "lunaKeystorePassword")
                keyAlias = secret("LUNA_KEY_ALIAS", "lunaKeyAlias")
                keyPassword = secret("LUNA_KEY_PASSWORD", "lunaKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
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
    implementation("dev.chrisbanes.haze:haze:1.6.10")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

