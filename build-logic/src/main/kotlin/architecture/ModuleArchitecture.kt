package architecture

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtUserType

/** Checks declared dependencies and Kotlin syntax, including qualified names and type aliases. */
class ModuleArchitecture : AutoCloseable {
    private val disposable = Disposer.newDisposable()

    @OptIn(org.jetbrains.kotlin.K1Deprecation::class)
    private val environment =
        KotlinCoreEnvironment.createForProduction(
            disposable,
            CompilerConfiguration(),
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
    private val factory = KtPsiFactory(environment.project)

    fun check(
        graph: Map<String, Set<String>>,
        sources: Map<String, String>,
    ): List<String> {
        val errors = mutableListOf<String>()
        graph.forEach { (module, dependencies) ->
            val allowed = allowedDependencies[module]
            if (allowed == null) errors += "Unknown module: $module"
            dependencies.filter { it !in allowed.orEmpty() }.forEach {
                errors += "$module must not depend on $it"
            }
        }
        val files = sources.mapValues { (path, source) -> factory.createFile(path.substringAfterLast('/'), source) }
        val aliases =
            files.values
                .flatMap { file ->
                    file.declarations.filterIsInstance<KtTypeAlias>().mapNotNull { alias ->
                        val name = alias.name ?: return@mapNotNull null
                        val target = alias.getTypeReference()?.text ?: return@mapNotNull null
                        "${file.packageFqName.asString()}.$name" to resolve(target, file)
                    }
                }.toMap()
        files.forEach { (path, file) ->
            val module = path.substringBefore('/')
            val pkg = file.packageFqName.asString()
            val expectedPackage =
                when (module) {
                    "app" -> "app.manyak"
                    "navigation" -> "app.manyak.core.navigation"
                    else -> "app.manyak.$module"
                }
            if (pkg != expectedPackage && !pkg.startsWith("$expectedPackage.")) {
                errors += "$path: package $pkg does not belong to $module"
            }
            val layer = layerOf(pkg)
            if (module !in setOf("app", "navigation", "designsystem") && layer == null) {
                errors += "$path: production code needs an entity/domain/data/presentation package"
            }
            references(file).forEach { reference ->
                checkReference(path, module, layer, reference, graph, errors)
                var target = reference
                val visited = mutableSetOf<String>()
                while (visited.add(target)) {
                    target = aliases[target] ?: break
                    checkReference(path, module, layer, target, graph, errors)
                }
            }
        }
        return errors.distinct().sorted()
    }

    private fun references(file: KtFile): Set<String> =
        buildSet {
            file.importDirectives.forEach { directive -> directive.importedFqName?.asString()?.let(::add) }
            PsiTreeUtil.collectElementsOfType(file, KtUserType::class.java).forEach { type ->
                if (type.parent is KtUserType) return@forEach
                val name = type.text.substringBefore('<').removeSuffix("?")
                add(resolve(name, file))
            }
            PsiTreeUtil.collectElementsOfType(file, KtDotQualifiedExpression::class.java).forEach { expression ->
                if (expression.parent is KtDotQualifiedExpression ||
                    PsiTreeUtil.getParentOfType(expression, KtPackageDirective::class.java) != null ||
                    PsiTreeUtil.getParentOfType(expression, KtImportDirective::class.java) != null
                ) {
                    return@forEach
                }
                val name = expression.text.takeWhile { it.isLetterOrDigit() || it in "._" }
                add(resolve(name, file))
            }
        }

    private fun resolve(
        name: String,
        file: KtFile,
    ): String {
        val head = name.substringBefore('.')
        val imported =
            file.importDirectives
                .firstOrNull {
                    (it.aliasName ?: it.importedName?.asString()) == head && !it.isAllUnder
                }?.importedFqName
                ?.asString()
        if (imported != null) return imported + name.removePrefix(head)
        if (name.startsWith("app.manyak.") || externalPrefixes.any { name.startsWith(it) }) return name
        return "${file.packageFqName.asString()}.$name"
    }

    private fun checkReference(
        path: String,
        module: String,
        layer: String?,
        reference: String,
        graph: Map<String, Set<String>>,
        errors: MutableList<String>,
    ) {
        val target = ownerOf(reference)
        if (target != null && target != module && target !in graph[module].orEmpty()) {
            errors += "$path: $reference needs an explicit, allowed module dependency"
        }
        if (module == "app" || module == "designsystem" || module == "navigation") return
        if (target == "network" && module != "network" && layer != "data") {
            errors += "$path: only data may use network: $reference"
        }
        val targetLayer = layerOf(reference)
        when (layer) {
            "entity", "domain" -> {
                if (externalPrefixes.any { reference.startsWith(it) } || targetLayer in setOf("data", "presentation")) {
                    errors += "$path: $layer cannot use $reference"
                }
                if (layer == "entity" &&
                    targetLayer == "domain" &&
                    reference != "app.manyak.common.domain.error.DomainError" &&
                    !reference.startsWith("app.manyak.common.domain.error.DomainError.")
                ) {
                    errors += "$path: entity cannot use domain: $reference"
                }
            }
            "presentation" -> {
                if (targetLayer == "data" || reference.startsWith("okhttp3.") || reference.startsWith("retrofit2.")) {
                    errors += "$path: presentation cannot use $reference"
                }
            }
            "data" -> if (targetLayer == "presentation") errors += "$path: data cannot use $reference"
        }
    }

    override fun close() = Disposer.dispose(disposable)

    companion object {
        private val externalPrefixes = setOf("android.", "androidx.", "okhttp3.", "retrofit2.")
        private val features = setOf("home", "chat", "studio", "story", "login", "legal", "my", "create")
        private val infrastructure = setOf("common", "designsystem", "navigation", "analytics", "network", "auth")
        val allowedDependencies: Map<String, Set<String>> =
            buildMap {
                put("app", infrastructure + features + "report")
                put("common", emptySet())
                put("designsystem", emptySet())
                put("navigation", emptySet())
                put("network", setOf("common"))
                put("analytics", setOf("common"))
                put("auth", setOf("common", "network"))
                put("report", setOf("common", "network", "designsystem", "analytics"))
                features.forEach { feature ->
                    put(
                        feature,
                        infrastructure +
                            if (feature in setOf("chat", "studio", "story")) setOf("report") else emptySet(),
                    )
                }
            }

        private fun layerOf(name: String): String? =
            name.split('.').firstOrNull { it in setOf("entity", "domain", "data", "presentation") }

        private fun ownerOf(name: String): String? =
            when {
                name == "app.manyak.core.navigation" || name.startsWith("app.manyak.core.navigation.") -> "navigation"
                name.startsWith("app.manyak.core.") || name.startsWith("app.manyak.feature.") -> "removed-module"
                name.startsWith("app.manyak.") ->
                    name
                        .removePrefix("app.manyak.")
                        .substringBefore('.')
                        .let { if (it in allowedDependencies) it else "app" }
                else -> null
            }
    }
}
