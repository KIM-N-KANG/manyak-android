plugins {
    id("manyak.android.compose")
    id("manyak.hilt")
}

android {
    namespace = "app.manyak.feature.story"
}

dependencies {
    implementation(projects.designsystem)
    // 구현이 있는 :core:data 는 의존하지 않는다. ViewModel 은 Repository 인터페이스만 안다.
    implementation(projects.common)
    implementation(projects.core.ui)
    implementation(projects.analytics)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    // 썸네일 이미지 뷰어의 시스템 뒤로가기 인터셉트(BackHandler — 뷰어만 닫는다).
    implementation(libs.androidx.activity.compose)
    // 전체 화면 뷰어는 목록 카드 표지와 다른 방식으로 그린다(잘라내기 대신 전체 맞춤).
    // 네트워크 fetcher 는 :core:ui 와 같이 composition root 인 :app 이 갖는다.
    implementation(libs.coil.compose)

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
