plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.familylink.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.familylink.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables { useSupportLibrary = true }
    }

    /**
     * The SAME key as the main app, on purpose.
     *
     * The two apps have to trust each other: the launcher reads which apps are currently locked
     * through a provider the main app guards with a signature-level permission, and Android only
     * grants that when both APKs carry the same signature. Sharing the key is what makes the
     * pair work without opening the provider to every app on the phone.
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
        debug { signingConfig = signingConfigs.getByName("familylink") }
        release {
            signingConfig = signingConfigs.getByName("familylink")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
