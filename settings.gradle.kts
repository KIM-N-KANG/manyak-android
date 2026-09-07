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
// 앱 조립, 독립 기반, 기능 모듈. 내부 계층·하위 기능은 Kotlin 패키지로 구분한다.
include(":app")
include(":common")
include(":designsystem")
include(":navigation")
include(":analytics")
include(":network")
include(":auth")
include(":report")
include(":home")
include(":chat")
include(":studio")
include(":story")
include(":login")
include(":legal")
include(":my")
include(":create")
