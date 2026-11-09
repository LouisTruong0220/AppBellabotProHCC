plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.roboworld.hcc"
    compileSdk = 34

    defaultConfig {
        /*
         * ⚠ applicationId PHẢI bắt đầu bằng com.pudutech.business.
         *   Tài liệu hãng (os-sdk/002-Development Environment) nói rõ: chỉ khi mang tiền tố này
         *   app mới HIỆN RA trong danh sách "Auto-start Module Selection" của robot.
         *   Đổi tên gói là app biến mất khỏi danh sách, không cách nào đặt làm màn chính.
         *
         *   namespace giữ nguyên vn.roboworld.hcc — đó là tên gói mã nguồn, không liên quan.
         */
        applicationId = "com.pudutech.business.hcc"
        minSdk = 27          // PdCoreSDK yêu cầu 27, KHÔNG phải 26 như tài liệu web ghi
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    buildTypes { release { isMinifyEnabled = false } }

    // index.html đã 1,6 MB — để nguyên cho WebView đọc thẳng từ assets, đừng nén
    androidResources { noCompress += listOf("html") }

    // Bắt buộc với PdCoreSDK, thiếu là lỗi trùng META-INF lúc đóng gói
    packaging { resources.pickFirsts += "META-INF/pudusdk_release.kotlin_module" }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.google.code.gson:gson:2.8.7")
    implementation("com.alibaba:fastjson:1.2.58")
    implementation("com.google.protobuf:protobuf-java:3.11.4")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.24")
}
