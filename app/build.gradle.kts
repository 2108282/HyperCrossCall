plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.xiaomi.hypercrosscall"
    compileSdk = 35 // Android 15/16 编译兼容
    
    defaultConfig {
        applicationId = "com.xiaomi.hypercrosscall"
        minSdk = 31 // Android 12 起
        targetSdk = 36 // Android 16 (Baklava)
        versionCode = 100
        versionName = "1.0.0-hyperos3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            abiFilters.addAll(setOf("arm64-v8a"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // YukiHookAPI (现代 LSPosed 专用响应式 Hook 框架)
    implementation("com.highcapable.yukihookapi:api:1.2.1")
    ksp("com.highcapable.yukihookapi:ksp-xposed:1.2.1")

    // DexKit (应对 HyperOS OTA 混淆类名变动的动态特征搜索引擎)
    implementation("org.luckypray:dexkit:2.0.3")

    // 基础支持库
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.annotation:annotation:1.8.0")
}
