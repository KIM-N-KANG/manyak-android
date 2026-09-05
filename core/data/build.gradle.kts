plugins {
    id("manyak.android.library")
    alias(libs.plugins.kotlin.serialization)
    id("manyak.hilt")
    alias(libs.plugins.room)
}

android {
    namespace = "app.manyak.core.data"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

// 스키마를 파일로 남겨 컬럼 변경이 리뷰 diff 에 드러나게 한다. 진행 레코드는 재생성 가능한
// 스냅숏이라 마이그레이션 대신 파괴적 폴백을 쓰지만, 무엇이 바뀌었는지는 보여야 한다.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(projects.core.domain)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.okhttp.sse)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kakao.user)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
