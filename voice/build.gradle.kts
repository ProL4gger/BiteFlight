plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.biteflight.voice"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
}
