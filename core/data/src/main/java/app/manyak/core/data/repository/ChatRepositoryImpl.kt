package app.manyak.core.data.repository

import app.manyak.common.domain.chat.ChatRepository
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.common.entity.chat.ChatDetail
import app.manyak.common.entity.chat.ChatStreamEvent
import app.manyak.common.entity.chat.ChatSummary
import app.manyak.common.entity.chat.CreatedChat
import app.manyak.common.entity.chat.UserSource
import app.manyak.core.data.api.ChatApi
import app.manyak.core.data.api.UserApi
import app.manyak.core.data.api.dto.ChatCreateRequestDto
import app.manyak.core.data.api.dto.ChatRegenerateRequestDto
import app.manyak.core.data.api.dto.ChatTurnStreamRequestDto
import app.manyak.core.data.api.dto.toDomain
import app.manyak.core.data.sse.ChatSseSource
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl
    @Inject
    constructor(
        private val chatApi: ChatApi,
        // 목록 경로는 본인 소유 자원이라 UserApi 에 있다. 내 스토리 목록과 같은 배치다.
        private val userApi: UserApi,
        private val sseSource: ChatSseSource,
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

        override fun streamTurn(
            chatId: String,
            userInput: String,
            userSource: UserSource,
            sourceTurnId: Long?,
            choiceOrder: Int?,
        ): Flow<ChatStreamEvent> =
            sseSource.streamTurn(
                chatId = chatId,
                request =
                    ChatTurnStreamRequestDto(
                        userInput = userInput,
                        userSource = userSource.wireValue,
                        // 원본 턴이 없으면 순번도 뜻이 없다. 한쪽만 실어 보내지 않는다.
                        sourceTurnId = sourceTurnId,
                        choiceOrder = choiceOrder.takeIf { sourceTurnId != null },
                    ),
            )

        override fun regenerateTurn(
            chatId: String,
            turnId: Long,
        ): Flow<ChatStreamEvent> = sseSource.regenerateTurn(chatId, ChatRegenerateRequestDto(turnId = turnId))

        override suspend fun generateChoices(
            chatId: String,
            turnId: Long,
        ): DomainResult<Unit> = apiCall { chatApi.generateChoices(chatId, turnId) }.map { }

        /** 없는 채팅을 지우려 한 것은 사용자가 할 일이 없는 상태라 성공으로 접는다. */
        override suspend fun deleteChat(chatId: String): DomainResult<Unit> {
            val result = emptyBodyApiCall { chatApi.deleteChat(chatId) }
            val status = (result as? DomainResult.Failure)?.let { (it.error as? DomainError.Server)?.status }
            return if (status == HTTP_NOT_FOUND) DomainResult.Success(Unit) else result
        }

        private companion object {
            const val HTTP_NOT_FOUND = 404
        }
    }
