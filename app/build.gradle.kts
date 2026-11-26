import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keyProps = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.droidtorrent"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.droidtorrent.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        if (keyProps.containsKey("storeFile")) {
            create("release") {
                keyAlias = keyProps["keyAlias"] as String
                keyPassword = keyProps["keyPassword"] as String
                storeFile = file(keyProps["storeFile"] as String)
                storePassword = keyProps["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation("com.frostwire:jlibtorrent-android:2.1.2.1")

    implementation("androidx.compose.material3:material3:1.6.0-alpha01")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui:1.14.0-alpha01")
    implementation("androidx.activity:activity-compose:1.14.0-alpha03")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.12.0-alpha06")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.12.0-alpha06")
    implementation("androidx.core:core-ktx:1.20.0-alpha01")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
