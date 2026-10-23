import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk = 36

  val vCode = (System.getenv("APP_VERSION_CODE") ?: project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 29
  val vName = System.getenv("APP_VERSION_NAME") ?: (project.findProperty("versionName") as? String) ?: "1.1.3"
  val backendUrl = (System.getenv("BACKEND_URL")?.takeIf { it.isNotBlank() } ?: project.findProperty("backendUrl") as? String)?.takeIf { it.isNotBlank() } ?: "https://yosan-xgsd.onrender.com/"

  defaultConfig {
    applicationId = "yosan.finance"
    minSdk = 24
    targetSdk = 35
    versionCode = vCode
    versionName = vName

    buildConfigField("String", "DEFAULT_BACKEND_URL", "\"$backendUrl\"")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystoreFile = sequenceOf(
        file("${rootDir}/app/debug.keystore"),
        file("${rootDir}/debug.keystore"),
        file("${rootDir}/app/release-key.jks"),
        file("${rootDir}/release-key.jks"),
        file("${rootDir}/my-upload-key.jks")
      ).firstOrNull { it.exists() }

      if (keystoreFile != null) {
        storeFile = keystoreFile
        storePassword = if (keystoreFile.name.contains("debug")) "android" else "yosanpassword123"
        keyAlias = if (keystoreFile.name.contains("debug")) "androiddebugkey" else if (keystoreFile.name.contains("release-key")) "yosan" else "upload"
        keyPassword = if (keystoreFile.name.contains("debug")) "android" else "yosanpassword123"
        enableV1Signing = true
        enableV2Signing = true
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseConfig = signingConfigs.getByName("release")
      if (releaseConfig.storeFile != null && releaseConfig.storeFile!!.exists()) {
        signingConfig = releaseConfig
      } else {
        signingConfig = signingConfigs.getByName("debug")
      }
    }
    debug {
      val releaseConfig = signingConfigs.getByName("release")
      if (releaseConfig.storeFile != null && releaseConfig.storeFile!!.exists()) {
        signingConfig = releaseConfig
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("SMTP_.*")
  ignoreList.add("JWT_.*")
  ignoreList.add("DB_.*")
  ignoreList.add("PORT")
  ignoreList.add("NODE_ENV")
  ignoreList.add("GOOGLE_CLIENT_SECRET")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.play.services.auth)
  implementation(libs.security.crypto)
  implementation(libs.androidx.biometric)
  implementation("com.tom-roush:pdfbox-android:2.0.27.0")

  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
