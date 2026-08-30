import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 读取本地签名配置（keystore.properties 已在 .gitignore 排除，不上传 GitHub）
// 文件不存在或字段不全时跳过，保证 assembleDebug / fork 环境仍可正常构建。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigning = signingKeys.all { !keystoreProps.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.example.netconnect_tool"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.netconnect_tool"
        minSdk = 24
        targetSdk = 36
        versionCode = 14
        versionName = "1.0.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile")!!)
                storePassword = keystoreProps.getProperty("storePassword")!!
                keyAlias = keystoreProps.getProperty("keyAlias")!!
                keyPassword = keystoreProps.getProperty("keyPassword")!!
            }
        }
    }

    buildTypes {
        release {
            // 用 release 密钥签名，保证已安装用户可覆盖升级
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // v1.0.13 起开启 R8 混淆 + 资源收缩，缩小体积并去除无用代码。
            // 反射点已排查（详见 proguard-rules.pro），依赖库均自带 consumer 规则。
            // （optimization.enable 是 AGP9 实验性 gradual R8，需额外 flag，暂不采用）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // JVM 单测中 android.util.Log 无实现，返回默认值避免 "not mocked" 异常
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.vico.compose.m3)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}