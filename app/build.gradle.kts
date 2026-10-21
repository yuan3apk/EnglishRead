import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 发布签名从 keystore.properties 读（该文件不进版本库）。
// 没有这个文件也能 build：release 退化成未签名包，开源仓库的构建不会因此失败。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

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

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 字典是 assets，不参与压缩；能瘦的是代码与未引用资源
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // SettingsActivity 用 BuildConfig.DEBUG 判断要不要放行本机明文接口
    // （给 tools/fake_llm.py 做端到端验收）。AGP 8 默认不生成这个类。
    buildFeatures {
        buildConfig = true
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
    implementation(libs.drawerlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
