plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.eduarddragu.anotherreminderapp"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.eduarddragu.anotherreminderapp"
        minSdk = 34
        targetSdk = 37
        versionCode = 27
        versionName = "1.0.0"
    }

    // The same personal key as the habit tracker (aht.* in ~/.gradle/gradle.properties). Without it,
    // builds fall back to the default debug key, so a fresh clone still compiles.
    val signingProps = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .associateWith { providers.gradleProperty("aht.$it").orNull }
    val personalKey = if (signingProps.values.all { it != null }) {
        signingConfigs.create("personal") {
            storeFile = file(signingProps.getValue("storeFile")!!)
            storePassword = signingProps.getValue("storePassword")
            keyAlias = signingProps.getValue("keyAlias")
            keyPassword = signingProps.getValue("keyPassword")
        }
    } else null

    buildTypes {
        debug {
            personalKey?.let { signingConfig = it }
        }
        release {
            personalKey?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
    }
    lint {
      // Play services pulls an old androidx.fragment, but no Fragment or FragmentActivity is used here.
      disable += "InvalidFragmentVersionForActivityResult"
    }
    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        excludes += "DebugProbesKt.bin"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.kotlinx.serialization.json)
  // Geofencing and the current location. Neither needs the network (CLAUDE.md lists what does).
  implementation(libs.play.services.location)
  testImplementation(libs.junit)
}
