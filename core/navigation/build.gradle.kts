plugins {
    id("manyak.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.manyak.navigation"
}

dependencies {
    testImplementation(libs.junit)
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.serialization.json)
}
