package app.manyak.core.data.repository

import app.manyak.core.data.api.ChatApi
import app.manyak.core.data.api.UserApi
import app.manyak.core.data.api.apiCall
import app.manyak.core.data.api.dto.ChatCreateRequestDto
import app.manyak.core.data.api.dto.toDomain
import app.manyak.core.domain.chat.ChatDetail
import app.manyak.core.domain.chat.ChatRepository
import app.manyak.core.domain.chat.ChatSummary
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
        // 목록 경로는 본인 소유 자원이라 UserApi 에 있다. 내 스토리 목록과 같은 배치다.
        private val userApi: UserApi,
    ) : ChatRepository {
        override suspend fun createChat(
            storyId: String,
            startSettingId: String?,
        ): DomainResult<CreatedChat> =
            apiCall {
                chatApi.createChat(ChatCreateRequestDto(storyId = storyId, startSettingId = startSettingId))
            }.map { it.toDomain() }

        // 서버 응답 순서(최근 활동순)를 그대로 둔다 — 다시 정렬하면 방금 진행한 채팅이 맨 위로 오지 않는다.
        override suspend fun myChats(): DomainResult<List<ChatSummary>> =
            apiCall { userApi.myChats() }.map { chats -> chats.map { chat -> chat.toDomain() } }

        override suspend fun chatDetail(chatId: String): DomainResult<ChatDetail> =
            apiCall { chatApi.chatDetail(chatId) }.map { it.toDomain() }
    }
