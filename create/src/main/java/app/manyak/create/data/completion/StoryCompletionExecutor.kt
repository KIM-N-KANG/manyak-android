package app.manyak.create.data.completion

import app.manyak.auth.domain.AuthWork
import app.manyak.auth.domain.SessionGate
import app.manyak.common.data.di.ApplicationScope
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.CreationProgressAccess
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.common.entity.story.CreationProgressSummary
import app.manyak.create.domain.PendingStoryCreationStore
import app.manyak.create.domain.StoryCompletionRequestStore
import app.manyak.create.domain.StoryCompletionSubmitter
import app.manyak.create.domain.StoryCreationRepository
import app.manyak.create.domain.toProgressSummary
import app.manyak.create.domain.toSummary
import app.manyak.create.entity.CompletionOutcome
import app.manyak.create.entity.CreationRequestSnapshot
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.StoryCompletionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 완성 요청의 전송·복구 실행자. 화면 ViewModel 이 아니라 앱 스코프에서 돌아 퍼널을 떠나도 요청이
 * 이어지며, 결과 반영은 [SessionGate.commit] 안에서만 해 로그아웃 뒤의 늦은 응답이 다음 계정의
 * 저장소에 닿지 않는다.
 *
 * 같은 requestId 의 전송만 합류시키고 요청끼리는 직렬화하지 않는다 — 완성 POST 는 오래 걸리므로
 * 하나가 도는 동안에도 다른 요청을 낼 수 있어야 한다. 결과는 requestId 행만 갱신하고 편집 슬롯은
 * 건드리지 않는다.
 */
@Singleton
class StoryCompletionExecutor
    @Inject
    constructor(
        private val requestStore: StoryCompletionRequestStore,
        private val draftStore: PendingStoryCreationStore,
        private val repository: StoryCreationRepository,
        private val gate: SessionGate,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : StoryCompletionSubmitter,
        CreationProgressAccess {
        private val inFlightLock = Any()
        private val inFlight = mutableMapOf<String, Job>()
        private val refreshMutex = Mutex()
        private var refreshJob: Job? = null

        override val progress: Flow<CreationProgressSummary?> = draftStore.record.map { it?.toProgressSummary() }

        override val completionRequests: Flow<List<CompletionRequestSummary>> =
            requestStore.requests.map { requests -> requests.map(StoryCompletionRequest::toSummary) }

        override suspend fun discard(): Boolean = draftStore.clear()

        override suspend fun submit(request: StoryCompletionRequest): Boolean {
            if (!requestStore.submit(request)) return false
            send(request.command)
            return true
        }

        /** 이미 도는 새로고침이 있으면 합류한다. 앱 스코프에서 돌아 호출한 화면이 사라져도 결과 반영은 끝난다. */
        override suspend fun refreshCompletionRequests() {
            val job =
                refreshMutex.withLock {
                    refreshJob?.takeIf { it.isActive }
                        ?: applicationScope.launch { refreshAll() }.also { refreshJob = it }
                }
            job.join()
        }

        override suspend fun retryCompletionRequest(requestId: String) {
            val request = requestStore.readAll().firstOrNull { it.requestId == requestId } ?: return
            if (request.outcome != CompletionOutcome.Failed) return
            if (requestStore.markPending(requestId)) send(request.command)
        }

        override suspend fun deleteCompletionRequest(requestId: String): Boolean = requestStore.delete(requestId)

        private fun send(command: StoryCompletionCommand) {
            synchronized(inFlightLock) {
                if (inFlight[command.requestId]?.isActive == true) return
                val job = applicationScope.launch { post(command) }
                inFlight[command.requestId] = job
                job.invokeOnCompletion {
                    synchronized(inFlightLock) {
                        if (inFlight[command.requestId] ===
                            job
                        ) {
                            inFlight.remove(command.requestId)
                        }
                    }
                }
            }
        }

        private fun isInFlight(requestId: String): Boolean =
            synchronized(inFlightLock) {
                inFlight[requestId]?.isActive ==
                    true
            }

        private suspend fun post(command: StoryCompletionCommand) =
            gate.withAuthWork(onBlocked = {}) { work ->
                when (val result = repository.completeStory(command)) {
                    is DomainResult.Success ->
                        gate.commit(
                            work,
                        ) { requestStore.markCompleted(command.requestId, result.value) }

                    is DomainResult.Failure ->
                        when (result.error) {
                            // 응답을 못 받은 것은 미확정이다. 다음 새로고침이 복구 조회로 판정한다.
                            DomainError.Network -> Unit

                            // 세션 종료 경로가 따로 처리한다. 요청은 미확정으로 남긴다.
                            DomainError.Unauthorized, DomainError.AccountSuspended -> Unit

                            // 409 를 포함한 상태 코드 응답은 서버가 이 requestId 를 어떻게 기록했는지에 따라
                            // 갈린다. 거절만 보고 실패로 낮추지 않고 복구 조회로 판정한다.
                            else -> resolve(command, work, notReceivedIsFailure = true)
                        }
                }
            }

        private suspend fun refreshAll() =
            gate.withAuthWork(onBlocked = {}) { work ->
                // 제작 탭 새로고침이 로그인 뒤 첫 접점이다 — 소유자 없는 이전 버전 행을 지금 회원이 넘겨받는다.
                draftStore.claimUnowned()
                requestStore.claimUnowned()
                val pending =
                    requestStore.readAll().filter { request ->
                        request.outcome == CompletionOutcome.Pending && !isInFlight(request.requestId)
                    }
                // 요청별로 따로 조회한다 — 한 건의 실패가 다른 건의 판정을 막지 않는다.
                coroutineScope {
                    pending.forEach { request ->
                        launch { resolve(request.command, work, notReceivedIsFailure = false) }
                    }
                }
            }

        /**
         * 서버 기록으로 요청을 판정한다. 404 는 서버가 이 requestId 를 받은 적이 없다는 뜻이다 —
         * 전송 실패 직후라면 확정 거절이고, 새로고침에서 만났다면 접수 전에 끊긴 요청이라 같은 명령으로
         * 다시 보낸다. 읽기 실패는 미확정으로 남긴다.
         */
        private suspend fun resolve(
            command: StoryCompletionCommand,
            work: AuthWork,
            notReceivedIsFailure: Boolean,
        ) {
            val requestId = command.requestId
            when (val result = repository.creationRequest(requestId)) {
                is DomainResult.Success ->
                    when (val snapshot = result.value) {
                        CreationRequestSnapshot.Pending -> Unit

                        is CreationRequestSnapshot.StoryReady ->
                            gate.commit(work) { requestStore.markCompleted(requestId, snapshot.story) }

                        // 단계가 어긋난 결과는 계약 위반이라 실패로 합류한다.
                        is CreationRequestSnapshot.StorylinesReady,
                        CreationRequestSnapshot.Failed,
                        -> gate.commit(work) { requestStore.markFailed(requestId) }
                    }

                is DomainResult.Failure ->
                    if (result.error.isNotFound()) {
                        if (notReceivedIsFailure) {
                            gate.commit(work) { requestStore.markFailed(requestId) }
                        } else {
                            send(command)
                        }
                    }
            }
        }
    }

private fun DomainError.isNotFound(): Boolean = this is DomainError.Server && status == HTTP_NOT_FOUND

private const val HTTP_NOT_FOUND = 404
