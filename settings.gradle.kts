pluginManagement {
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
include(":core:domain")

// Repository 구현 · Retrofit · DataStore · 인터셉터 · 제공자 SDK 어댑터.
include(":core:data")
