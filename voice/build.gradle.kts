import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

val voiceProperties = Properties()
rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(voiceProperties::load)
val turnEndpoint = voiceProperties.getProperty("biteflight.turnEndpoint", "")
require(turnEndpoint.isEmpty() || Regex("https://[A-Za-z0-9.-]+(?:/[A-Za-z0-9/_-]*)?").matches(turnEndpoint)) {
    "biteflight.turnEndpoint must be an HTTPS URL"
}

android {
    namespace = "com.example.biteflight.voice"
    compileSdk = 35
    buildFeatures.buildConfig = true
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TURN_ENDPOINT", "\"$turnEndpoint\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-tasks:18.2.0")
    implementation("io.github.webrtc-sdk:android:150.7871.08")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:runner:1.5.2")
}
