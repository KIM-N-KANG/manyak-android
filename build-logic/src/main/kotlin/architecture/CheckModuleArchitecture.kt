package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

abstract class CheckModuleArchitecture
    @Inject
    constructor(
        private val workers: WorkerExecutor,
    ) : DefaultTask() {
        @get:Classpath
        abstract val compilerClasspath: ConfigurableFileCollection

        @get:Input
        abstract val dependencyGraph: MapProperty<String, Set<String>>

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val sources: ConfigurableFileCollection

        @get:Internal
        abstract val sourceRoot: DirectoryProperty

        @TaskAction
        fun check() {
            val root = sourceRoot.get().asFile
            val texts = sources.files.associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
            workers
                .processIsolation {
                    classpath.from(compilerClasspath)
                    forkOptions.maxHeapSize = "512m"
                }.submit(ArchitectureWork::class.java) {
                    graph.set(dependencyGraph)
                    sourceText.set(texts)
                }
        }
    }

interface ArchitectureParameters : WorkParameters {
    val graph: MapProperty<String, Set<String>>
    val sourceText: MapProperty<String, String>
}

abstract class ArchitectureWork : WorkAction<ArchitectureParameters> {
    override fun execute() {
        val graph = parameters.graph.get()
        val sources = parameters.sourceText.get()
        val errors = ModuleArchitecture().use { it.check(graph, sources) }
        if (errors.isNotEmpty()) {
            throw GradleException(
                errors.joinToString("\n", prefix = "Module architecture violations:\n"),
            )
        }
        Logging
            .getLogger(ArchitectureWork::class.java)
            .lifecycle("Module architecture: ${graph.size} modules, ${sources.size} Kotlin files checked")
    }
}
