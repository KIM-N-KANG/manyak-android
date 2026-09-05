plugins {
    id("manyak.android.library")
    id("manyak.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.manyak.auth"
}

dependencies {
    implementation(projects.common)
    implementation(projects.network)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kakao.user)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
