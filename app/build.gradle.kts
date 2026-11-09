import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "io.github.timmylich.wellness"
  compileSdk = 36

  defaultConfig {
    applicationId = "io.github.timmylich.wellness"
    minSdk = 26
    targetSdk = 36
    // Continues the React Native app's numbering, so it installs as an update.
    versionCode = 10
    versionName = "0.6.0"
    // F-Droid builds pass -PupdateCheck=false (F-Droid updates the app itself).
    buildConfigField("boolean", "UPDATE_CHECK", (project.findProperty("updateCheck") ?: "true").toString())
  }

  // Optional local signing: keystore.properties (gitignored) with storeFile, storePassword,
  // keyAlias, keyPassword. Without it the release APK is unsigned and is signed afterwards
  // (scripts/install-signed.ps1 locally, the release workflow on CI).
  val keystore = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }
  if (keystore != null) signingConfigs.create("release") {
    storeFile = rootProject.file(keystore.getProperty("storeFile"))
    storePassword = keystore.getProperty("storePassword")
    keyAlias = keystore.getProperty("keyAlias")
    keyPassword = keystore.getProperty("keyPassword")
  }

  buildTypes {
    // Installs next to the release app, so testing never touches real data.
    debug { applicationIdSuffix = ".debug" }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (keystore != null) signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
  testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
  val bom = platform("androidx.compose:compose-bom:2025.06.01")
  implementation(bom)
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.activity:activity-compose:1.11.0")
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
  // Reads steps other apps wrote to Health Connect (no Play services, works on F-Droid builds).
  implementation("androidx.health.connect:connect-client:1.1.0")
  debugImplementation("androidx.compose.ui:ui-tooling")
  testImplementation("junit:junit:4.13.2")
}
