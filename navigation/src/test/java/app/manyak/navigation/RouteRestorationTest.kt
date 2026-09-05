package app.manyak.navigation

import androidx.navigation3.runtime.NavKey
import app.manyak.core.navigation.ChatRoomRoute
import app.manyak.core.navigation.CreateAdditionalInfoRoute
import app.manyak.core.navigation.LegalDocument
import app.manyak.core.navigation.LegalRoute
import app.manyak.core.navigation.MainTabsRoute
import app.manyak.core.navigation.StoryDetailRoute
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
                        subclass(StoryDetailRoute::class)
                        subclass(ChatRoomRoute::class)
                        subclass(CreateAdditionalInfoRoute::class)
                        subclass(LegalRoute::class)
                    }
                }
        }
    private val serializer = ListSerializer(PolymorphicSerializer(NavKey::class))

    @Test
    fun `stored route names and identifiers survive module movement`() {
        val fixture = requireNotNull(javaClass.getResource("/route-backstack-v1.json")).readText()
        val expected =
            listOf(
                MainTabsRoute,
                StoryDetailRoute("story-fixture"),
                ChatRoomRoute("chat-fixture"),
                CreateAdditionalInfoRoute(2),
                LegalRoute(LegalDocument.PRIVACY),
            )

        assertEquals(expected, json.decodeFromString(serializer, fixture))
        assertEquals(
            json.parseToJsonElement(fixture),
            json.parseToJsonElement(json.encodeToString(serializer, expected)),
        )
    }
}
