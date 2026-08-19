plugins {
    id("com.android.application")
}

val releaseVersionCode = providers.gradleProperty("releaseVersionCode")
    .map(String::toInt)
    .getOrElse(1)
val releaseVersionName = providers.gradleProperty("releaseVersionName")
    .getOrElse("1.0")

android {
    namespace = "de.strudel.notificationiconsoverlay"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.strudel.notificationiconsoverlay"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
    }

    signingConfigs {
        create("release") {
            providers.environmentVariable("ANDROID_SIGNING_KEYSTORE").orNull?.let {
                storeFile = file(it)
            }
            storePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    val shizukuVersion = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")
    testImplementation("junit:junit:4.13.2")
}
