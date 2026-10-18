package app.manyak.create.data.draft

import app.manyak.common.data.time.toEpochMillisOrNull
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.create.data.api.PutStoryDraftDto
import app.manyak.create.data.api.StoryDraftApi
import app.manyak.create.data.api.StoryDraftCardDto
import app.manyak.create.data.api.StoryDraftDetailDto
import app.manyak.create.data.api.StoryDraftErrorDto
import app.manyak.create.entity.StoryDraftSave
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 간편 제작과 일반 제작이 함께 쓰는 서버 초안 API. 목록에는 두 방식의 초안이 함께 오므로 계정별로 마지막에 받은 것을 한곳에
 * 두고, 다시 받다 실패해도 카드가 사라지지 않게 한다. 저장은 오류 본문을 저장 결과로 읽는다.
 */
@Singleton
class StoryDraftRemote
    @Inject
    constructor(
        private val api: StoryDraftApi,
        private val json: Json,
    ) {
        private val listed = MutableStateFlow<Pair<String, List<StoryDraftCardDto>>?>(null)

        /** [ownerId]의 초안 카드. 목록에 섞여 오는 생성 요청 항목은 뺀다. 다른 계정의 목록은 보이지 않는다. */
        fun cards(ownerId: String): Flow<List<StoryDraftCardDto>> =
            listed.map { server -> server?.takeIf { it.first == ownerId }?.second.orEmpty() }

        suspend fun refresh(ownerId: String): Boolean =
            when (val result = apiCall { api.list() }) {
                is DomainResult.Success -> {
                    listed.value = ownerId to result.value.filter { it.kind == "DRAFT" && it.draftId != null }
                    true
                }

                is DomainResult.Failure -> {
                    // 다른 계정의 목록이 남아 이 계정의 카드로 보이지 않게 한다.
                    listed.update { it?.takeIf { server -> server.first == ownerId } }
                    false
                }
            }

        suspend fun detail(draftId: String): DomainResult<StoryDraftDetailDto> = apiCall { api.detail(draftId) }

        /** 열어 둔 초안이 다른 곳에서 지워지면 서버는 기준 버전이 있는 저장을 거절한다. 그때는 같은 ID로 새로 만들어 입력을 남긴다. */
        suspend fun save(
            draftId: String,
            baseVersion: Int?,
            request: (baseVersion: Int?) -> PutStoryDraftDto,
        ): StoryDraftSave {
            val result = put(draftId, request(baseVersion))
            val gone =
                result == StoryDraftSave.Failed &&
                    baseVersion != null &&
                    (detail(draftId) as? DomainResult.Failure)?.error?.isNotFound() == true
            return if (gone) put(draftId, request(null)) else result
        }

        suspend fun delete(draftId: String): Boolean {
            if (emptyBodyApiCall { api.delete(draftId) } is DomainResult.Failure) return false
            listed.update { server ->
                server?.let { it.first to it.second.filterNot { card -> card.draftId == draftId } }
            }
            return true
        }

        private suspend fun put(
            draftId: String,
            request: PutStoryDraftDto,
        ): StoryDraftSave {
            val response =
                try {
                    api.put(draftId, request)
                } catch (_: IOException) {
                    return StoryDraftSave.Failed
                } catch (_: SerializationException) {
                    return StoryDraftSave.Failed
                }
            response.body()?.takeIf { response.isSuccessful }?.let {
                return StoryDraftSave.Saved(it.version, it.createdAt?.toEpochMillisOrNull())
            }
            val error =
                try {
                    response.errorBody()?.string()?.let { json.decodeFromString<StoryDraftErrorDto>(it) }
                } catch (_: IOException) {
                    null
                } catch (_: SerializationException) {
                    null
                }
            return when (error?.code) {
                "DRAFT_VERSION_CONFLICT" ->
                    error.details
                        .firstOrNull { it.field == "version" }
                        ?.message
                        ?.toIntOrNull()
                        ?.let(StoryDraftSave::Conflict)
                        ?: StoryDraftSave.Failed
                "DRAFT_LIMIT_EXCEEDED" -> StoryDraftSave.LimitExceeded
                "DRAFT_PAYLOAD_TOO_LARGE" -> StoryDraftSave.TooLarge
                else -> StoryDraftSave.Failed
            }
        }
    }

internal fun DomainError.isNotFound(): Boolean = (this as? DomainError.Server)?.status == HTTP_NOT_FOUND

private const val HTTP_NOT_FOUND = 404
