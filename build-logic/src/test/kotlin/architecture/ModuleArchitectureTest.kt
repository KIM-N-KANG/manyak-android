package architecture

import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleArchitectureTest {
    @Test
    fun `feature dependencies and common reverse dependencies fail`() {
        assertViolation(mapOf("feature:home" to setOf("feature:chat")), emptyMap(), "home must not depend on chat")
        assertViolation(mapOf("core:common" to setOf("core:auth")), emptyMap(), "common must not depend on auth")
    }

    @Test
    fun `modules must be registered under their group`() {
        assertViolation(mapOf("home" to emptySet()), emptyMap(), "home must be registered as :feature:home")
        assertViolation(mapOf("feature:common" to emptySet()), emptyMap(), "common must be registered as :core:common")
        assertViolation(mapOf("feature:unknown" to emptySet()), emptyMap(), "Unknown module: feature:unknown")
        assertViolation(
            mapOf("feature:home" to emptySet()),
            mapOf("home/Screen.kt" to "package app.manyak.home.presentation\nclass Screen"),
            "home/Screen.kt: not inside a registered module",
        )
    }

    @Test
    fun `testing is only for test source sets`() {
        assertViolation(
            mapOf("feature:home" to setOf("core:testing"), "core:testing" to setOf("core:common")),
            emptyMap(),
            "home must not depend on testing",
        )
        val errors =
            check(
                mapOf("feature:home" to setOf("core:common"), "core:testing" to setOf("core:common")),
                emptyMap(),
                testGraph = mapOf("feature:home" to setOf("core:testing")),
            )
        assertTrue(errors.toString(), errors.isEmpty())
        assertViolation(
            mapOf("feature:home" to emptySet()),
            emptyMap(),
            "home tests must not depend on chat",
            testGraph = mapOf("feature:home" to setOf("feature:chat")),
        )
        assertViolation(
            mapOf("feature:home" to emptySet()),
            mapOf(
                "feature/home/Screen.kt" to
                    "package app.manyak.home.presentation\nimport app.manyak.testing.FakeTrialsRepository",
            ),
            "needs an explicit, allowed module dependency",
            testGraph = mapOf("feature:home" to setOf("core:testing")),
        )
    }

    @Test
    fun `presentation cannot import data with an alias`() {
        assertViolation(
            mapOf("feature:home" to emptySet()),
            mapOf(
                "feature/home/Screen.kt" to
                    """
                    package app.manyak.home.presentation
                    import app.manyak.home.data.RepositoryImpl as Repo
                    val repository: Repo? = null
                    """.trimIndent(),
            ),
            "presentation cannot use app.manyak.home.data.RepositoryImpl",
        )
    }

    @Test
    fun `qualified calls without imports cannot bypass the layer boundary`() {
        assertViolation(
            mapOf("feature:home" to emptySet()),
            mapOf(
                "feature/home/Screen.kt" to
                    """
                    package app.manyak.home.presentation
                    fun load() = app.manyak.home.data.RepositoryImpl().load()
                    """.trimIndent(),
            ),
            "presentation cannot use app.manyak.home.data.RepositoryImpl",
        )
    }

    @Test
    fun `domain rejects qualified Android types and entity rejects domain types`() {
        assertViolation(
            mapOf("feature:home" to emptySet()),
            mapOf(
                "feature/home/Repository.kt" to
                    """
                    package app.manyak.home.domain
                    interface Repository { fun load(context: android.content.Context) }
                    """.trimIndent(),
            ),
            "domain cannot use android.content.Context",
        )
        assertViolation(
            mapOf("feature:home" to emptySet()),
            mapOf(
                "feature/home/Entity.kt" to
                    """
                    package app.manyak.home.entity
                    data class Entity(val repository: app.manyak.home.domain.Repository)
                    """.trimIndent(),
            ),
            "entity cannot use domain",
        )
    }

    @Test
    fun `type aliases resolve through chains and imported aliases`() {
        assertViolation(
            mapOf("feature:home" to emptySet()),
            mapOf(
                "feature/home/AndroidAlias.kt" to
                    """
                    package app.manyak.home.presentation
                    typealias AndroidAlias = android.content.Context
                    typealias ContextAlias = AndroidAlias
                    """.trimIndent(),
                "feature/home/Repository.kt" to
                    """
                    package app.manyak.home.domain
                    import app.manyak.home.presentation.ContextAlias as HiddenContext
                    interface Repository { fun load(context: HiddenContext) }
                    """.trimIndent(),
            ),
            "Repository.kt: domain cannot use android.content.Context",
        )
    }

    @Test
    fun `comments and strings are not code references`() {
        val errors =
            check(
                mapOf("feature:home" to emptySet()),
                mapOf(
                    "feature/home/Entity.kt" to
                        """
                        package app.manyak.home.entity
                        // android.content.Context
                        val example = "app.manyak.home.data.RepositoryImpl()"
                        """.trimIndent(),
                ),
            )
        assertTrue(errors.toString(), errors.isEmpty())
    }

    @Test
    fun `allowed shared ports and navigation serialization package pass`() {
        val errors =
            check(
                mapOf(
                    "feature:chat" to setOf("core:common"),
                    "core:common" to emptySet(),
                    "core:navigation" to emptySet(),
                ),
                mapOf(
                    "feature/chat/Repository.kt" to
                        """
                        package app.manyak.chat.domain
                        import app.manyak.common.domain.chat.ChatStarter
                        interface Repository : ChatStarter
                        """.trimIndent(),
                    "feature/chat/Event.kt" to "package app.manyak.chat.entity\n" +
                        "data class Failed(val error: app.manyak.common.domain.error.DomainError)",
                    "core/navigation/Routes.kt" to "package app.manyak.core.navigation\nclass ChatRoomRoute",
                ),
            )
        assertTrue(errors.toString(), errors.isEmpty())
    }

    @Test
    fun `undeclared project use and old module packages fail`() {
        assertViolation(
            mapOf("feature:home" to emptySet()),
            mapOf(
                "feature/home/Repository.kt" to
                    """
                    package app.manyak.home.domain
                    import app.manyak.common.domain.error.DomainResult
                    """.trimIndent(),
            ),
            "needs an explicit, allowed module dependency",
        )
        assertViolation(
            mapOf("feature:home" to emptySet()),
            mapOf("feature/home/Screen.kt" to "package app.manyak.feature.home\nclass Screen"),
            "does not belong to home",
        )
    }

    @Test
    fun `network dependency is restricted to feature data`() {
        assertViolation(
            mapOf("feature:chat" to setOf("core:network")),
            mapOf(
                "feature/chat/Screen.kt" to "package app.manyak.chat.presentation\n" +
                    "import app.manyak.network.domain.SessionTokenAccess",
            ),
            "only data may use network",
        )
    }

    private fun check(
        graph: Map<String, Set<String>>,
        sources: Map<String, String>,
        testGraph: Map<String, Set<String>> = emptyMap(),
    ) = ModuleArchitecture().use { it.check(graph, sources, testGraph) }

    private fun assertViolation(
        graph: Map<String, Set<String>>,
        sources: Map<String, String>,
        expected: String,
        testGraph: Map<String, Set<String>> = emptyMap(),
    ) {
        val errors = check(graph, sources, testGraph)
        assertTrue("Expected $expected, got $errors", errors.any { expected in it })
    }
}
