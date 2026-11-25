package app.manyak.notification.inbox.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationInboxApi {
    @GET("users/me/notifications")
    suspend fun list(
        @Query("category") category: String,
        @Query("cursor") cursor: String?,
    ): Response<NotificationPageDto>

    @GET("users/me/notifications/unread-count")
    suspend fun unreadCount(): Response<NotificationUnreadCountDto>

    @POST("users/me/notifications/{notificationId}/read")
    suspend fun read(
        @Path("notificationId") id: String,
    ): Response<Unit>

    @POST("users/me/notifications/read-all")
    suspend fun readAll(): Response<Unit>
}

@Serializable
data class NotificationPageDto(
    val items: List<NotificationItemDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class NotificationItemDto(
    val notificationId: String,
    val category: String,
    val type: String,
    val title: String,
    val body: String = "",
    val deepLink: String? = null,
    val storyId: String? = null,
    val noticeId: String? = null,
    val noticeTag: String? = null,
    val createdAt: String? = null,
    val readAt: String? = null,
)

@Serializable
data class NotificationUnreadCountDto(
    val total: Long = 0,
    val news: Long = 0,
    val activity: Long = 0,
)
