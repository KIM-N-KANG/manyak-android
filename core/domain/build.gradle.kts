plugins {
    alias(libs.plugins.kotlin.jvm)

    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// 안드로이드 플러그인을 적용하지 않는다. Context·Uri 같은 프레임워크 참조가 컴파일 에러가 되어
// "도메인은 안드로이드를 모른다"를 컴파일러가 강제한다.

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
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

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
