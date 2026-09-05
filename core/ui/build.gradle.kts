plugins {
    id("manyak.android.compose")
}

android {
    namespace = "app.manyak.core.ui"
}

dependencies {
    implementation(projects.designsystem)
    // 화면 모듈이 그대로 쓰는 것들은 api 로 노출한다. feature 마다 같은 줄을 복제하지 않기 위해서다.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(projects.common)
    // 신고 시트 컨트롤러가 이벤트를 보낸다. 네 화면이 공유하는 절차라 발화도 한 곳에 둔다.
    implementation(projects.analytics)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    // 네트워크 fetcher(coil-network-okhttp)는 런타임 배선이라 composition root 인 :app 이 갖는다.
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
