plugins {
    id("com.android.application")
}

android {
    namespace = "com.largebatata.nettoggle"
    compileSdk { version = release(37) }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.largebatata.nettoggle"
        minSdk = 33 // read-back command is available on Android 13+
        targetSdk = 37
        versionCode = 8
        versionName = "0.9.0-beta.1"
    }

    buildFeatures { aidl = true }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}
