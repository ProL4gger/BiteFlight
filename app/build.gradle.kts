import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.biteflight"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.biteflight"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AWS_ACCESS_KEY", "\"${localProperties.getProperty("AWS_ACCESS_KEY_ID", "")}\"")
        buildConfigField("String", "AWS_SECRET_KEY", "\"${localProperties.getProperty("AWS_SECRET_ACCESS_KEY", "")}\"")
        buildConfigField("String", "AWS_BUCKET_NAME", "\"${localProperties.getProperty("AWS_BUCKET_NAME", "biteflight-s3-bucket")}\"")
        buildConfigField("String", "AWS_REGION", "\"${localProperties.getProperty("AWS_REGION", "eu-north-1")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}


dependencies {
    implementation(project(":voice"))
    // Core AndroidX & UI
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")

    // Firebase (BoM manages compatible versions automatically)
    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Google Maps & Location Services
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // OSMDroid Maps
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Coil Image Loading Library
    implementation("io.coil-kt:coil:2.7.0")

    // AWS SDK for Android (S3 Storage)
    implementation("com.amazonaws:aws-android-sdk-core:2.77.0")
    implementation("com.amazonaws:aws-android-sdk-s3:2.77.0")


    implementation("org.maplibre.gl:android-sdk:11.11.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
