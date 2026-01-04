import org.jetbrains.kotlin.gradle.tasks.KotlinCompile  // 如果没有这行，放到文件最上面

android {
    // ...
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.cgmdemo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cgmdemo"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // ✅ Java 编译目标统一成 1.8
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }


    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.jjoe64:graphview:4.2.2")


    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // MPAndroidChart，用来画折线图
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}
