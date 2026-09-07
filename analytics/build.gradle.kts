plugins {
    id("manyak.android.compose")
    id("manyak.hilt")
}

android {
    namespace = "app.manyak.analytics"
}

dependencies {
    implementation(projects.common)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.amplitude.analytics.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
}
