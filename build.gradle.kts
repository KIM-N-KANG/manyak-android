// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // 루트의 *.gradle.kts 도 검사 대상에 포함시키기 위해 루트에도 적용한다.
    alias(libs.plugins.ktlint)

    alias(libs.plugins.detekt) apply false
}

ktlint {
    ignoreFailures = false
}
