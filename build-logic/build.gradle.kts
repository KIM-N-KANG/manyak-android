plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

dependencies {
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
    implementation("com.google.dagger:hilt-android-gradle-plugin:${libs.versions.hilt.get()}")
    implementation(
        "org.jlleitschuh.gradle.ktlint:org.jlleitschuh.gradle.ktlint.gradle.plugin:${libs.versions.ktlint.get()}",
    )
    implementation("dev.detekt:detekt-gradle-plugin:${libs.versions.detekt.get()}")
}

ktlint {
    ignoreFailures = false
    filter {
        exclude("**/build/**")
    }
}

// Precompiled plugin source sets also contain generated accessors; inspect authored sources only.
tasks.named<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>("runKtlintCheckOverMainSourceSet") {
    setSource(fileTree("src/main/kotlin"))
    include("**/*.kts")
}

tasks.named<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>("runKtlintFormatOverMainSourceSet") {
    setSource(fileTree("src/main/kotlin"))
    include("**/*.kts")
}
