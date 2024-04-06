
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.datadoghq.dd-sdk-android-gradle-plugin") version "1.12.0"
}


android {
    namespace = "com.cliptracer.ClipTracer"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.cliptracer.ClipTracer"
        minSdk = 31
        targetSdk = 33
        versionCode = 1
        versionName = "ClipTracer_1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.media:media:1.1.0")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.17.1") // for UI components like PlayerView
    implementation("com.google.android.exoplayer:exoplayer-core:2.17.1")
    implementation("io.coil-kt:coil-compose:1.4.0") // Check for the latest version
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("com.arthenica:ffmpeg-kit-full-gpl:5.1")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation(platform("androidx.compose:compose-bom:2023.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.datadoghq:dd-sdk-android-rum:2.2.0")
    implementation("com.datadoghq:dd-sdk-android-logs:2.2.0")
    implementation("io.ktor:ktor-client-core:2.2.3")
    implementation("io.ktor:ktor-client-cio:2.2.3")
    implementation("com.rabbitmq:amqp-client:5.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
//    implementation("org.opencv:opencv:4.9.0")
//    implementation(project(":opencvsdk490"))
    testImplementation ("io.mockk:mockk:1.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation ("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation ("org.robolectric:robolectric:4.6.1") // For Android class mocking
    testImplementation ("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.0") // For coroutine testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

}