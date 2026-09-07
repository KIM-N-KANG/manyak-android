plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
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
        exclude { it.file.invariantSeparatorsPath.contains("/build/") }
    }
}

// Kotlin DSL attaches generated roots after plugin application; replace the final task inputs after that setup.
afterEvaluate {
    listOf("runKtlintCheckOverMainSourceSet", "runKtlintFormatOverMainSourceSet").forEach { taskName ->
        tasks.named<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>(taskName) {
            setSource(fileTree("src/main/kotlin"))
            setIncludes(mutableListOf("**/*.kts", "**/*.kt"))
            exclude { it.file.invariantSeparatorsPath.contains("/build/") }
        }
    }
}
