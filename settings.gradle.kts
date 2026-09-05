pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 카카오 SDK 는 Maven Central 에 없다. 다른 의존성 조회를 가로채지 않도록 그룹을 한정한다.
        maven("https://devrepo.kakao.com/nexus/content/groups/public/") {
            content { includeGroup("com.kakao.sdk") }
        }
    }
}

rootProject.name = "manyak-android"
include(":app")

// 순수 Kotlin. 도메인 모델·Repository 계약·오류 타입을 소유한다.
include(":common")

// Repository 구현 · Retrofit · DataStore · 인터셉터 · 제공자 SDK 어댑터.

// 디자인 시스템 · 공용 컴포저블 · MviViewModel · 문자열 리소스 전량.
include(":core:ui")

// 타입 안전 라우트의 단일 등록처. feature 끼리 직접 참조하지 않고 여기를 거친다.
include(":navigation")

// Amplitude 배선 · 이벤트 카탈로그 · 노출 추적 헬퍼. 화면은 이 모듈의 Analytics 만 안다.
include(":analytics")

// 화면 단위 모듈. 모듈 이름이 화면·탭 이름과 1:1로 맞고, 화면은 모듈 루트 패키지에 둔다.
include(":login")
include(":legal")
include(":home")
include(":chat")
include(":studio")
include(":my")
include(":create")
include(":story")

include(":designsystem")

include(":network")

include(":auth")

include(":report")
