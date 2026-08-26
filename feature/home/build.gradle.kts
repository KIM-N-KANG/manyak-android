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
    namespace = "app.manyak.feature.home"
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // 네트워크 fetcher(coil-network-okhttp)는 런타임 배선이라 composition root 인 :app 이 갖는다.
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
