plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.englishread.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.englishread.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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

    // samples/ 里的 TXT/EPUB 样本给仪器测试用，不打包进 APK
    sourceSets {
        getByName("androidTest") {
            assets.srcDir(rootProject.file("samples"))
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
