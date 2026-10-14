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
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    // AWS S3 SDK
    implementation("com.amazonaws:aws-android-sdk-s3:2.73.0")
    implementation("com.amazonaws:aws-android-sdk-core:2.73.0")

    // Image loading library
    implementation("io.coil-kt:coil:2.6.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
