package app.manyak.core.data.repository

import app.manyak.core.data.api.ChatApi
import app.manyak.core.data.api.apiCall
import app.manyak.core.data.api.dto.ChatCreateRequestDto
import app.manyak.core.data.api.dto.toDomain
import app.manyak.core.domain.chat.ChatDetail
import app.manyak.core.domain.chat.ChatRepository
import app.manyak.core.domain.chat.CreatedChat
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.error.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl
    @Inject
    constructor(
        private val chatApi: ChatApi,
    ) : ChatRepository {
        override suspend fun createChat(storyId: String): DomainResult<CreatedChat> =
            apiCall { chatApi.createChat(ChatCreateRequestDto(storyId = storyId)) }.map { it.toDomain() }

        override suspend fun chatDetail(chatId: String): DomainResult<ChatDetail> =
            apiCall { chatApi.chatDetail(chatId) }.map { it.toDomain() }
    }
