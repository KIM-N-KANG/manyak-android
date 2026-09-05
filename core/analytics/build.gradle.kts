plugins {
    id("manyak.android.compose")
    id("manyak.hilt")
}

android {
    namespace = "app.manyak.core.analytics"
}

dependencies {
    // 이벤트 프로퍼티가 도메인 enum 을 그대로 쓴다. 화면이 값을 문자열로 옮겨 적지 않게 한다.
    api(projects.core.domain)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.amplitude.analytics.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
}
