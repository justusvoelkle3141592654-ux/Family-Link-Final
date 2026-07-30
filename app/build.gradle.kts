plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.familylink.ios"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.familylink.ios"
        minSdk = 26
        targetSdk = 34
        versionCode = 22
        versionName = "3.1"

        vectorDrawables { useSupportLibrary = true }
    }

    /**
     * A FIXED signing key, checked into the repository on purpose.
     *
     * Android refuses to install a build over an existing app when the two are signed with
     * different keys. Without a key of our own, every CI run generated a fresh random debug
     * keystore, so each new APK could only be installed after uninstalling the old one — which
     * wiped the setup, the PIN and the pairing every single time.
     *
     * With this key every future build installs straight over the previous one as an update.
     * The key protects nothing secret: the app is distributed by hand to this family's own
     * phones, not through a store, so there is no signature anyone could usefully forge.
     */
    signingConfigs {
        create("familylink") {
            storeFile = rootProject.file("familylink-release.jks")
            storePassword = "familylink"
            keyAlias = "familylink"
            keyPassword = "familylink"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("familylink")
        }
        release {
            signingConfig = signingConfigs.getByName("familylink")
            // Kept off so a plain `assembleRelease` produces an installable, un-signed-shrink-free APK.
            // Enable for a smaller production build once you have a signing config.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        // Keep the bedtime ambient audio uncompressed so it streams/loops smoothly.
        noCompress += "wav"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    // Plain JVM tests for the pure logic (StreakLogic) — no Android framework involved.
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-service:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Fingerprint / face unlock for the parent app (BiometricPrompt needs FragmentActivity)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.3")

    // Overlay lock screen renders Compose inside a WindowManager view; needs savedstate glue.
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
}
