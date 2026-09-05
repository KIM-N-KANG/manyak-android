plugins {
    id("manyak.android.library")
    id("manyak.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.manyak.network"
}

dependencies {
    implementation(projects.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.logging.interceptor)

    testImplementation(libs.junit)
}
