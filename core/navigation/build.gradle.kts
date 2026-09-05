plugins {
    id("manyak.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.manyak.core.navigation"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.serialization.json)
}
