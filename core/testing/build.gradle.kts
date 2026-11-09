plugins {
    id("manyak.android.library")
}

android {
    namespace = "app.manyak.testing"
}

// 테스트 소스 세트에서만 쓴다. 프로덕션 의존은 checkModuleArchitecture 가 막는다.
dependencies {
    implementation(projects.core.common)

    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
