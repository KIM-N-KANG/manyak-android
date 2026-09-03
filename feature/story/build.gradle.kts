plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)

    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

ktlint {
    ignoreFailures = false
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    parallel = true
    ignoreFailures = false
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    reports {
        html.required = true
        checkstyle.required = true
        sarif.required = false
        markdown.required = false
    }
}

android {
    namespace = "app.manyak.feature.story"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 구현이 있는 :core:data 는 의존하지 않는다. ViewModel 은 Repository 인터페이스만 안다.
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(projects.core.analytics)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    // 썸네일 이미지 뷰어의 시스템 뒤로가기 인터셉트(BackHandler — 뷰어만 닫는다).
    implementation(libs.androidx.activity.compose)
    // 전체 화면 뷰어는 목록 카드 표지와 다른 방식으로 그린다(잘라내기 대신 전체 맞춤).
    // 네트워크 fetcher 는 :core:ui 와 같이 composition root 인 :app 이 갖는다.
    implementation(libs.coil.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
