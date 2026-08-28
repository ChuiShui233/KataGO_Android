plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.chuishui.katago"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chuishui.katago"
        minSdk = 29
        targetSdk = 35
        versionCode = 20260820
        versionName = "1.0.3"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        create("release") {
            val ksPass = providers.gradleProperty("KATA_ANDROID_KEYSTORE_PASSWORD").getOrElse("")
            val kPass = providers.gradleProperty("KATA_ANDROID_KEY_PASSWORD").getOrElse("")
            storeFile = rootProject.file(providers.gradleProperty("KATA_ANDROID_KEYSTORE").getOrElse("").ifEmpty { return@create })
            storePassword = providers.environmentVariable("KATA_ANDROID_KEYSTORE_PASSWORD").getOrElse(ksPass)
            keyAlias = providers.gradleProperty("KATA_ANDROID_KEY_ALIAS").getOrElse("")
            keyPassword = providers.environmentVariable("KATA_ANDROID_KEY_PASSWORD").getOrElse(kPass)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.kotlinx.coroutines)
    implementation(libs.fluent.ui)
    implementation(libs.fluent.icons.extended)
    implementation(libs.cropify)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}