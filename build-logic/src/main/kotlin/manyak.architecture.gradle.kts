import architecture.CheckModuleArchitecture
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
    val modules = rootProject.subprojects
    val graph =
        modules.associate { module ->
            module.name to
                module.configurations
                    .flatMap { configuration ->
                        configuration.dependencies
                            .withType<ProjectDependency>()
                            .filter { it.path != module.path }
                            .map { it.path.removePrefix(":") }
                    }.toSet()
        }
    architectureCheck.configure {
        dependencyGraph.set(graph)
        sources.from(modules.map { it.fileTree("src/main") { include("**/*.kt") } })
    }
}
