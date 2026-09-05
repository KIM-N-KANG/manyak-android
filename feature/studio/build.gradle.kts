plugins {
    id("manyak.android.compose")
    id("manyak.hilt")
}

android {
    namespace = "app.manyak.feature.studio"
}

dependencies {
    implementation(projects.designsystem)
    // 구현이 있는 :core:data 는 의존하지 않는다. ViewModel 은 Repository 인터페이스만 안다.
    implementation(projects.common)
    implementation(projects.core.ui)
    implementation(projects.analytics)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
