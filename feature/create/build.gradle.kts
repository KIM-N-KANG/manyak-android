plugins {
    id("manyak.android.compose")
    id("manyak.hilt")
}

android {
    namespace = "app.manyak.feature.create"
}

dependencies {
    implementation(projects.designsystem)
    // 구현이 있는 :core:data 는 의존하지 않는다. ViewModel 은 Repository 인터페이스만 안다.
    implementation(projects.common)
    implementation(projects.core.ui)
    implementation(projects.analytics)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    // 키워드 단계의 시스템 뒤로가기 인터셉트(BackHandler — 퍼널 이탈 처리).
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
