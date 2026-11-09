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
// 앱 조립(app), 기능이 함께 쓰는 기반(core), 화면 기능(feature). 내부 계층과 하위 기능은 Kotlin 패키지로 구분한다.
include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:navigation")
include(":core:analytics")
include(":core:network")
include(":core:auth")
include(":core:report")
include(":core:testing")
include(":feature:home")
include(":feature:chat")
include(":feature:studio")
include(":feature:story")
include(":feature:login")
include(":feature:legal")
include(":feature:my")
include(":feature:create")
include(":feature:notification")
include(":feature:notice")
include(":feature:onboarding")
