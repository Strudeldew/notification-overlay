import java.util.Properties

plugins {
  id("com.android.application")
}

val signingProperties = Properties().apply {
  val propertiesFile = rootProject.file("keystore.properties")
  if (propertiesFile.isFile) {
    propertiesFile.inputStream().use(::load)
  }
}

val releaseVersionCode = providers.gradleProperty("releaseVersionCode")
  .map(String::toInt)
  .getOrElse(1)
val releaseVersionName = providers.gradleProperty("releaseVersionName")
  .getOrElse("1.0")

android()
{
  namespace = "de.strudel.notificationiconsoverlay"
  compileSdk = 36

  defaultConfig()
  {
    applicationId = "de.strudel.notificationiconsoverlay"
    minSdk = 26
    targetSdk = 36
    versionCode = releaseVersionCode
    versionName = releaseVersionName
  }

  compileOptions()
  {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures()
  {
    aidl = true
  }

  signingConfigs()
  {
    create("release")
    {
      val keystorePath = providers.environmentVariable("ANDROID_SIGNING_KEYSTORE").orNull
        ?: signingProperties.getProperty("storeFile")
      keystorePath?.let { storeFile = rootProject.file(it) }
      storePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
        ?: signingProperties.getProperty("storePassword")
      keyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
        ?: signingProperties.getProperty("keyAlias")
      keyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
        ?: signingProperties.getProperty("keyPassword")
    }
  }

  buildTypes()
  {
    release()
    {
      signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
}

dependencies()
{
  val shizukuVersion = "13.1.5"
  implementation("dev.rikka.shizuku:api:$shizukuVersion")
  implementation("dev.rikka.shizuku:provider:$shizukuVersion")
  testImplementation("junit:junit:4.13.2")
}
