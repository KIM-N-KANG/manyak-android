import java.util.Properties

plugins {
    id("manyak.quality")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("manyak.hilt")
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

ktlint {
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}

// 오픈소스 고지는 손으로 적지 않는다. 실제 의존성 그래프에서 모아야 라이선스가 실물과 어긋나지 않는다.
aboutLibraries {
    collect {
        // Gradle 의존성이 아닌 것(번들 폰트)은 여기 둔 JSON 으로 합친다.
        configPath = file("aboutlibraries")
    }
    export {
        // 화면이 쓰지 않는 필드는 실어 보내지 않는다.
        excludeFields.addAll("developers", "funding", "organization", "scm", "description")
    }
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

// 제공자 키는 local.properties(추적 제외)에서 빌드 타입별로 읽는다. 값이 앱에 실려 나가긴 하지만
// 개발·운영 키가 섞이면 발급한 ID 토큰이 상대 환경 허용 목록에서 거부되므로 주입 지점을 하나로 둔다.
// 값이 없으면 빈 문자열이 들어가고, 로그인 시작 단계가 빈 키를 명시적 실패로 처리한다.
fun authProperty(
    name: String,
    buildType: String,
): String {
    val key = "${name}_${buildType.uppercase()}"
    return localProperties.getProperty(key) ?: System.getenv(key) ?: ""
}

// 업로드 키는 저장소에 두지 않는다. local.properties(추적 제외)나 CI 환경변수에서 읽고,
// 하나라도 비면 signingConfig 를 붙이지 않는다 — 반쯤 채워진 값으로 서명이 조용히 어긋나는 것보다
// 서명되지 않은 산출물이 업로드 단계에서 바로 거부되는 편이 낫다.
fun signingProperty(name: String): String? =
    (localProperties.getProperty(name) ?: System.getenv(name))?.takeIf { it.isNotBlank() }

val uploadKeystore = signingProperty("RELEASE_STORE_FILE")?.let { rootProject.file(it) }
val uploadStorePassword = signingProperty("RELEASE_STORE_PASSWORD")
val uploadKeyAlias = signingProperty("RELEASE_KEY_ALIAS")
val uploadKeyPassword = signingProperty("RELEASE_KEY_PASSWORD")
val hasUploadKey =
    uploadKeystore?.exists() == true &&
        uploadStorePassword != null &&
        uploadKeyAlias != null &&
        uploadKeyPassword != null

android {
    namespace = "app.manyak"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.manyak"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 약관·개인정보처리방침은 웹 페이지를 그대로 연다(docs/plans/login.md 결정 1).
        // 본문 정본이 웹 하나뿐이라 환경과 무관하게 운영 웹을 가리킨다.
        val webBaseUrl = localProperties.getProperty("WEB_BASE_URL") ?: "https://manyak.app"
        buildConfigField("String", "WEB_BASE_URL", "\"$webBaseUrl\"")
    }

    signingConfigs {
        if (hasUploadKey) {
            create("release") {
                storeFile = uploadKeystore
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // 로컬 서버에 붙일 때는 local.properties 에 BASE_URL 을 넣어 덮어쓴다.
            val debugBaseUrl = localProperties.getProperty("BASE_URL") ?: "https://dev-api.manyak.app/api/v1/"
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
            buildConfigField(
                "String",
                "GOOGLE_SERVER_CLIENT_ID",
                "\"${authProperty("GOOGLE_SERVER_CLIENT_ID", "debug")}\"",
            )
            buildConfigField(
                "String",
                "KAKAO_NATIVE_APP_KEY",
                "\"${authProperty("KAKAO_NATIVE_APP_KEY", "debug")}\"",
            )
            manifestPlaceholders["kakaoNativeAppKey"] = authProperty("KAKAO_NATIVE_APP_KEY", "debug")
            buildConfigField("String", "AMPLITUDE_API_KEY", "\"${authProperty("AMPLITUDE_API_KEY", "debug")}\"")
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "BASE_URL", "\"https://api.manyak.app/api/v1/\"")
            buildConfigField(
                "String",
                "GOOGLE_SERVER_CLIENT_ID",
                "\"${authProperty("GOOGLE_SERVER_CLIENT_ID", "release")}\"",
            )
            buildConfigField(
                "String",
                "KAKAO_NATIVE_APP_KEY",
                "\"${authProperty("KAKAO_NATIVE_APP_KEY", "release")}\"",
            )
            manifestPlaceholders["kakaoNativeAppKey"] = authProperty("KAKAO_NATIVE_APP_KEY", "release")
            buildConfigField("String", "AMPLITUDE_API_KEY", "\"${authProperty("AMPLITUDE_API_KEY", "release")}\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(projects.auth)
    implementation(projects.network)
    implementation(projects.designsystem)
    implementation(projects.common)
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(projects.navigation)
    implementation(projects.analytics)
    implementation(projects.feature.login)
    implementation(projects.feature.legal)
    implementation(projects.feature.home)
    implementation(projects.feature.chat)
    implementation(projects.feature.studio)
    implementation(projects.feature.my)
    implementation(projects.feature.create)
    implementation(projects.feature.story)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.kotlinx.coroutines.test)
}
