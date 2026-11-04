plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "vn.bubbledict"
    compileSdk = 35

    // SDK chỉ có sẵn 35.0.0 (máy này không cho ghi vào SDK dir); AGP 8.7.3 mặc định đòi 34.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "vn.bubbledict"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        // F12: banner cập nhật đọc manifest JSON. Đổi URL ở đây khi chuyển host (LAN -> Cloudflare).
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"http://192.168.100.106:8770/update.json\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // BE-18: bật R8 + shrink resources để đo đúng mục tiêu APK ≤ 25MB.
            isMinifyEnabled = true
            isShrinkResources = true
            // MVP phân phối APK sideload cho máy thật arm64; emulator x86_64 chỉ dùng debug.
            ndk { abiFilters += "arm64-v8a" }
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Migration test cần schema JSON của các version trong androidTest assets.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // UI (FE)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    // Lưu trữ + mạng + offline (BE)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.text.recognition)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
}