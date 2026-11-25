package app.manyak.notification.inbox.data

import app.manyak.common.domain.error.DomainResult
import app.manyak.notification.inbox.entity.NotificationCategory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class NotificationInboxRepositoryTest {
    @Test fun `서버 응답과 204 읽음 성공을 변환한다`() =
        runTest {
            val dto =
                Json.decodeFromString<NotificationPageDto>(
                    """
                    {"items":[{"notificationId":"n","category":"NEWS","type":"NOTICE",
                    "title":"제목","noticeTag":"UPDATE","createdAt":"2026-10-24T10:00:00Z",
                    "readAt":null}],"nextCursor":"next"}
                    """.trimIndent(),
                )
            val repository =
                NotificationInboxRepositoryImpl(
                    object : NotificationInboxApi {
                        override suspend fun list(
                            category: String,
                            cursor: String?,
                        ) = Response.success(dto)

                        override suspend fun unreadCount() = Response.success(NotificationUnreadCountDto(3, 2, 1))

                        override suspend fun read(id: String): Response<Unit> = Response.success<Unit>(204, null)

                        override suspend fun readAll(): Response<Unit> = Response.success<Unit>(204, null)
                    },
                )
            val page = (repository.list(NotificationCategory.NEWS, null) as DomainResult.Success).value
            assertEquals("next", page.nextCursor)
            assertEquals("UPDATE", page.items.single().noticeTag)
            assertEquals(false, page.items.single().isRead)
            assertTrue(page.items.single().createdAtMillis != null)
            assertEquals(DomainResult.Success(Unit), repository.read("n"))
            assertEquals(DomainResult.Success(Unit), repository.readAll())
        }
}
