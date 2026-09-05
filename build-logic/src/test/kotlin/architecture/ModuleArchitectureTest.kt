package architecture

import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleArchitectureTest {
    @Test
    fun `feature dependencies and common reverse dependencies fail`() {
        assertViolation(mapOf("home" to setOf("chat")), emptyMap(), "home must not depend on chat")
        assertViolation(mapOf("common" to setOf("auth")), emptyMap(), "common must not depend on auth")
    }

    @Test
    fun `presentation cannot import data with an alias`() {
        assertViolation(
            mapOf("home" to emptySet()),
            mapOf(
                "home/Screen.kt" to
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
            mapOf("home" to emptySet()),
            mapOf(
                "home/Screen.kt" to
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
            mapOf("home" to emptySet()),
            mapOf(
                "home/Repository.kt" to
                    """
                    package app.manyak.home.domain
                    interface Repository { fun load(context: android.content.Context) }
                    """.trimIndent(),
            ),
            "domain cannot use android.content.Context",
        )
        assertViolation(
            mapOf("home" to emptySet()),
            mapOf(
                "home/Entity.kt" to
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
            mapOf("home" to emptySet()),
            mapOf(
                "home/AndroidAlias.kt" to
                    """
                    package app.manyak.home.presentation
                    typealias AndroidAlias = android.content.Context
                    typealias ContextAlias = AndroidAlias
                    """.trimIndent(),
                "home/Repository.kt" to
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
                mapOf("home" to emptySet()),
                mapOf(
                    "home/Entity.kt" to
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
                mapOf("chat" to setOf("common"), "common" to emptySet(), "navigation" to emptySet()),
                mapOf(
                    "chat/Repository.kt" to
                        """
                        package app.manyak.chat.domain
                        import app.manyak.common.domain.chat.ChatStarter
                        interface Repository : ChatStarter
                        """.trimIndent(),
                    "chat/Event.kt" to "package app.manyak.chat.entity\n" +
                        "data class Failed(val error: app.manyak.common.domain.error.DomainError)",
                    "navigation/Routes.kt" to "package app.manyak.core.navigation\nclass ChatRoomRoute",
                ),
            )
        assertTrue(errors.toString(), errors.isEmpty())
    }

    @Test
    fun `undeclared project use and old module packages fail`() {
        assertViolation(
            mapOf("home" to emptySet()),
            mapOf(
                "home/Repository.kt" to
                    """
                    package app.manyak.home.domain
                    import app.manyak.common.domain.error.DomainResult
                    """.trimIndent(),
            ),
            "needs an explicit, allowed module dependency",
        )
        assertViolation(
            mapOf("home" to emptySet()),
            mapOf("home/Screen.kt" to "package app.manyak.feature.home\nclass Screen"),
            "does not belong to home",
        )
    }

    @Test
    fun `network dependency is restricted to feature data`() {
        assertViolation(
            mapOf("chat" to setOf("network")),
            mapOf(
                "chat/Screen.kt" to "package app.manyak.chat.presentation\n" +
                    "import app.manyak.network.domain.SessionTokenAccess",
            ),
            "only data may use network",
        )
    }

    private fun check(
        graph: Map<String, Set<String>>,
        sources: Map<String, String>,
    ) = ModuleArchitecture().use { it.check(graph, sources) }

    private fun assertViolation(
        graph: Map<String, Set<String>>,
        sources: Map<String, String>,
        expected: String,
    ) {
        val errors = check(graph, sources)
        assertTrue("Expected $expected, got $errors", errors.any { expected in it })
    }
}
