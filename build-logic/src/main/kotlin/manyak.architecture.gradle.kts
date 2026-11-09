import architecture.CheckModuleArchitecture
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency

val architectureCompiler =
    configurations.create("architectureCompiler") {
        isCanBeConsumed = false
    }
val kotlinVersion =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("kotlin")
        .get()
dependencies.add(architectureCompiler.name, "org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")

val architectureCheck =
    tasks.register<CheckModuleArchitecture>("checkModuleArchitecture") {
        group = "verification"
        description = "Checks module dependencies and Kotlin layer boundaries, including aliases and qualified names"
        sourceRoot.set(layout.projectDirectory)
        compilerClasspath.from(architectureCompiler)
    }

// Capture immutable inputs after every module has declared its dependencies; the task action uses no Project API.
gradle.projectsEvaluated {
    // Grouping projects such as :core and :feature have no build file and own no code.
    val modules = rootProject.subprojects.filter { it.buildFile.exists() }

    fun graph(test: Boolean) =
        modules.associate { module ->
            module.path.removePrefix(":") to
                module.configurations
                    .filter { it.isTestConfiguration() == test }
                    .flatMap { configuration ->
                        configuration.dependencies
                            .withType<ProjectDependency>()
                            .filter { it.path != module.path }
                            .map { it.path.removePrefix(":") }
                    }.toSet()
        }
    architectureCheck.configure {
        dependencyGraph.set(graph(test = false))
        testDependencyGraph.set(graph(test = true))
        sources.from(modules.map { it.fileTree("src/main") { include("**/*.kt") } })
    }
}

// Configurations used only by test source sets, such as testImplementation, androidTestImplementation and kspTest.
fun Configuration.isTestConfiguration() = name.startsWith("test") || name.startsWith("androidTest") || "Test" in name
