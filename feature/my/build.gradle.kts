plugins {
    id("manyak.android.compose")
    id("manyak.hilt")
}

android {
    namespace = "app.manyak.feature.my"
}

dependencies {
    // 구현이 있는 :core:data 는 의존하지 않는다. ViewModel 은 Repository 인터페이스만 안다.
    implementation(projects.common)
    implementation(projects.core.ui)
    implementation(projects.core.analytics)
    implementation(projects.core.navigation)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coil.compose)

    // 오픈소스 고지 데이터. UI 는 이 모듈이 직접 그리므로 로더·모델만 쓴다.
    implementation(libs.aboutlibraries.compose.core)

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
