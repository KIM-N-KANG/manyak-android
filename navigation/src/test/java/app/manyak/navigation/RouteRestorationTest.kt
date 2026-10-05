package app.manyak.navigation

import androidx.navigation3.runtime.NavKey
import app.manyak.core.navigation.ChatRoomRoute
import app.manyak.core.navigation.CreateAdditionalInfoRoute
import app.manyak.core.navigation.CreateMethodRoute
import app.manyak.core.navigation.GeneralCreateRoute
import app.manyak.core.navigation.GeneralSubmissionRoute
import app.manyak.core.navigation.LegalDocument
import app.manyak.core.navigation.LegalRoute
import app.manyak.core.navigation.MainTabsRoute
import app.manyak.core.navigation.StoryDetailRoute
import app.manyak.core.navigation.StoryEditRoute
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteRestorationTest {
    private val json =
        Json {
            serializersModule =
                SerializersModule {
                    polymorphic(NavKey::class) {
                        subclass(MainTabsRoute::class)
                        subclass(CreateMethodRoute::class)
                        subclass(GeneralCreateRoute::class)
                        subclass(GeneralSubmissionRoute::class)
                        subclass(StoryEditRoute::class)
                        subclass(StoryDetailRoute::class)
                        subclass(ChatRoomRoute::class)
                        subclass(CreateAdditionalInfoRoute::class)
                        subclass(LegalRoute::class)
                    }
                }
        }
    private val serializer = ListSerializer(PolymorphicSerializer(NavKey::class))

    @Test
    fun `일반 제작 경로는 초안과 제출본 및 스토리 식별자로 복원된다`() {
        val routes =
            listOf(
                MainTabsRoute,
                CreateMethodRoute,
                GeneralCreateRoute("draft"),
                GeneralSubmissionRoute("submission"),
                StoryEditRoute("story"),
            )
        assertEquals(routes, json.decodeFromString(serializer, json.encodeToString(serializer, routes)))
    }

    @Test
    fun `stored route names and identifiers survive module movement`() {
        val fixture = requireNotNull(javaClass.getResource("/route-backstack-v1.json")).readText()
        val expected =
            listOf(
                MainTabsRoute,
                StoryDetailRoute("story-fixture"),
                ChatRoomRoute("chat-fixture"),
                CreateAdditionalInfoRoute("draft-fixture", 2),
                LegalRoute(LegalDocument.PRIVACY),
            )

        assertEquals(expected, json.decodeFromString(serializer, fixture))
        assertEquals(
            json.parseToJsonElement(fixture),
            json.parseToJsonElement(json.encodeToString(serializer, expected)),
        )
    }
}
