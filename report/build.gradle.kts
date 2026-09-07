plugins {
    id("manyak.android.compose")
    id("manyak.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.manyak.report"
}

dependencies {
    implementation(projects.common)
    implementation(projects.network)
    implementation(projects.designsystem)
    implementation(projects.analytics)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.logging.interceptor)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
