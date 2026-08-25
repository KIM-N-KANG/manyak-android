package app.manyak.feature.create

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CreationProgress
import app.manyak.core.domain.story.CreationRequestSnapshot
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.PendingStoryCreationStore
import app.manyak.core.domain.story.StoryCharacterInput
import app.manyak.core.domain.story.StoryCompletionCommand
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.StorylineGeneration
import app.manyak.core.domain.story.StorylineGenerationCommand
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

/** 키워드 화면이 조립해 넘기는 생성 입력. 요청 ID·재생성 체인은 [StorylineGenerationStore] 가 관리한다. */
data class StorylineGenerationInput(
    val genreTagIds: List<Long>,
    val customGenreTags: List<String>,
    val protagonist: StoryCharacterInput,
    val supportingCharacters: List<StoryCharacterInput>,
)

sealed interface StorylineGenerationState {
    /** 아직 생성을 시작하지 않았거나 프로세스 재시작으로 결과가 사라진 상태. */
    data object Idle : StorylineGenerationState

    data object Generating : StorylineGenerationState

    data class Generated(
        val result: StorylineGeneration,
    ) : StorylineGenerationState

    /** 실패. 재생성 실패면 직전 성공 결과를 계속 보여줄 수 있게 함께 담는다. */
    data class Failed(
        val previousResult: StorylineGeneration?,
    ) : StorylineGenerationState
}

/** 화면에 보여줄 수 있는 생성 결과. 실패 상태에서도 직전 성공 결과는 남는다. */
fun StorylineGenerationState.resultOrNull(): StorylineGeneration? =
    when (this) {
        is StorylineGenerationState.Generated -> result
        is StorylineGenerationState.Failed -> previousResult
        else -> null
    }

/**
 * 퍼널 단계 화면들이 공유하는 스토리라인 생성 상태.
 *
 * 단계마다 목적지가 나뉘어 ViewModel 이 각각 생기므로, 키워드 화면이 시작한 생성을 스토리라인
 * 화면이 관찰하고 추가 정보 화면이 결과를 읽는 자리가 필요하다. 라우트에는 식별자만 싣는 규칙에
 * 따라 결과 본문은 여기에 둔다. 생성 실행은 퍼널 수명 스코프([FunnelScope])가 담는다 —
 * 스토리라인 단계가 키워드 목적지를 대체해, 요청을 시작한 화면의 ViewModel 은 응답 전에 죽는다.
 *
 * 생성·완성 요청은 시작 전에 진행 레코드로 영속되어, 응답을 못 받은 채 퍼널을 떠나거나 프로세스가
 * 재시작해도 복구 조회로 결과를 되찾는다. 이탈 시에는 진행 스냅숏을 임시 저장한다.
 *
 * 생성 실행·요청 영속·복구 폴링·진행 미러가 한 수명(퍼널)을 공유하는 조정자라 함수 수 상한을
 * 넘는다. 나누면 상태 소유가 흩어져 더 위험하므로 이 클래스만 예외로 둔다.
 */
@Suppress("TooManyFunctions")
@ActivityRetainedScoped
class StorylineGenerationStore
    @Inject
    constructor(
        private val storyCreationRepository: StoryCreationRepository,
        private val pendingCreationStore: PendingStoryCreationStore,
        @param:FunnelScope private val funnelScope: CoroutineScope,
    ) {
        private val mutableState = MutableStateFlow<StorylineGenerationState>(StorylineGenerationState.Idle)
        val state: StateFlow<StorylineGenerationState> = mutableState.asStateFlow()

        private var lastCommand: StorylineGenerationCommand? = null
        private var lastResult: StorylineGeneration? = null

        /** 마지막 완성 명령. 같은 페이로드 재시도의 requestId 재사용과 임시 저장 승계에 쓴다. */
        var lastCompletionCommand: StoryCompletionCommand? = null
            private set

        /** 임시 저장·복원 재료. 단계 ViewModel 이 입력 변화를 미러링한다. */
        var progress: CreationProgress = CreationProgress()
            private set

        /**
         * 퍼널을 떠난 뒤 도착한 원 응답이 상태·레코드를 건드리지 않게 하는 플래그.
         * 화면에 반영할 곳이 없는데 레코드만 지우면 재진입 복구 경로를 잃는다(3-1 정리 규칙).
         */
        private var leftFunnel = false

        private val restoreMutex = Mutex()
        private var restoreAttempted = false

        private var runJob: Job? = null

        /** 스토리라인 단계 복구 폴링 대상. 스토리라인 화면이 STARTED 동안 [runStorylineRecovery] 로 소비한다. */
        private val storylineRecoveryTarget = MutableStateFlow<StorylineGenerationCommand?>(null)

        private val mutableCompletionRecoveryTarget = MutableStateFlow<StoryCompletionCommand?>(null)

        /** 완성 단계 복구 폴링 대상. 추가 정보 화면의 ViewModel 이 STARTED 동안 소비한다. */
        val completionRecoveryTarget: StateFlow<StoryCompletionCommand?> =
            mutableCompletionRecoveryTarget.asStateFlow()

        /** 키워드 입력으로 새 생성을 시작한다. 이전 퍼널의 결과·진행 레코드는 덮인다. */
        fun generate(input: StorylineGenerationInput) {
            if (mutableState.value is StorylineGenerationState.Generating) return
            leftFunnel = false
            lastResult = null
            lastCompletionCommand = null
            progress = CreationProgress()
            startRun(
                StorylineGenerationCommand(
                    requestId = newRequestId(),
                    genreTagIds = input.genreTagIds,
                    customGenreTags = input.customGenreTags,
                    protagonist = input.protagonist,
                    supportingCharacters = input.supportingCharacters,
                    // 최초 생성도 null 을 명시한다 — 재생성이 직전 명령을 복사하는 구조라 비워 두면
                    // 이전 값이 딸려와 체인이 부모가 아닌 조부모를 가리킨다.
                    parentCreationId = null,
                    isRegenerated = false,
                ),
            )
        }

        /**
         * 직전 입력 그대로 다시 생성한다. 성공 결과의 재생성은 새 요청 ID 에 직전 요청 ID 를
         * 부모 체인으로 싣고, 실패 재시도는 같은 요청 ID 를 재사용한다 — 서버가 실패한 요청은
         * 재실행하고 완료된 요청은 저장된 결과를 돌려주는 멱등 계약이 있다.
         */
        fun regenerate() {
            if (mutableState.value is StorylineGenerationState.Generating) return
            val previous = lastCommand ?: return
            val command =
                when (mutableState.value) {
                    is StorylineGenerationState.Generated ->
                        previous.copy(
                            requestId = newRequestId(),
                            parentCreationId = previous.requestId,
                            isRegenerated = true,
                        )

                    else -> previous
                }
            startRun(command)
        }

        /** 상태 전이는 호출 지점에서 동기로 일어나 중복 시작을 막고, 실행은 퍼널 스코프로 넘긴다. */
        private fun startRun(command: StorylineGenerationCommand) {
            lastCommand = command
            mutableState.value = StorylineGenerationState.Generating
            runJob = funnelScope.launch { run(command) }
        }

        private suspend fun run(command: StorylineGenerationCommand) {
            // 요청 전에 영속한다 — 응답을 못 받아도 재진입 복구 조회가 이 requestId 를 쓴다.
            pendingCreationStore.write(PendingStoryCreation.GeneratingStorylines(command))
            val result =
                try {
                    storyCreationRepository.generateStorylines(command)
                } catch (cancellation: CancellationException) {
                    // 퍼널 이탈로 취소된 요청이 Generating 을 남기면 다음 생성 시작이 잠긴다.
                    // 이탈 처리로 이미 Idle 이 된 스토어는 건드리지 않는다. 레코드는 복구 대상으로 남는다.
                    if (mutableState.value is StorylineGenerationState.Generating && !leftFunnel) {
                        mutableState.value = StorylineGenerationState.Failed(lastResult)
                    }
                    throw cancellation
                }
            if (leftFunnel) return
            when (result) {
                is DomainResult.Success -> {
                    lastResult = result.value
                    progress = CreationProgress()
                    mutableState.value = StorylineGenerationState.Generated(result.value)
                    pendingCreationStore.clear()
                }

                is DomainResult.Failure ->
                    when {
                        // 같은 requestId 의 재시도가 서버 PENDING 과 겹쳤다(409). 실패가 아니라
                        // 진행 중이라는 뜻이므로 복구 폴링으로 결과를 되찾는다.
                        result.error.isConflict() -> storylineRecoveryTarget.value = command

                        // 상태 코드로 응답한 실패는 복구 대상이 아니다. 응답을 못 받은 네트워크
                        // 오류만 레코드를 보존한다(3-1 정리 규칙).
                        result.error is DomainError.Network ->
                            mutableState.value = StorylineGenerationState.Failed(lastResult)

                        else -> {
                            mutableState.value = StorylineGenerationState.Failed(lastResult)
                            pendingCreationStore.clear()
                        }
                    }
            }
        }

        /**
         * 프로세스 재시작·재개 진입으로 비어 있는 스토어를 진행 레코드에서 복원한다.
         * 단계 ViewModel 초기화가 호출하며, 스토어에 상태가 살아 있으면 아무것도 하지 않는다.
         */
        suspend fun ensureRestored() {
            restoreMutex.withLock {
                if (restoreAttempted || mutableState.value !is StorylineGenerationState.Idle) return
                restoreAttempted = true
                leftFunnel = false
                when (val record = pendingCreationStore.read()) {
                    null -> Unit

                    // 키워드 입력은 키워드 화면이 직접 복원하고 소비한다.
                    is PendingStoryCreation.KeywordDraft -> Unit

                    is PendingStoryCreation.GeneratingStorylines -> {
                        lastCommand = record.command
                        mutableState.value = StorylineGenerationState.Generating
                        storylineRecoveryTarget.value = record.command
                    }

                    is PendingStoryCreation.CompletingStory -> {
                        lastCommand = record.generationCommand
                        lastResult = record.generation
                        lastCompletionCommand = record.command
                        progress = record.progress
                        mutableState.value = StorylineGenerationState.Generated(record.generation)
                        mutableCompletionRecoveryTarget.value = record.command
                    }

                    is PendingStoryCreation.Draft -> {
                        lastCommand = record.generationCommand
                        lastResult = record.generation
                        lastCompletionCommand = record.lastCompletionCommand
                        progress = record.progress
                        mutableState.value = StorylineGenerationState.Generated(record.generation)
                        // 복원은 레코드를 소비한다 — 재개 후 다시 이탈하면 그 시점 상태로 새로 저장된다.
                        pendingCreationStore.clear()
                    }
                }
            }
        }

        /**
         * 스토리라인 단계 복구 폴링. 화면이 STARTED 동안만 수집해 백그라운드에서 멈추고 복귀 시
         * 재개된다. 완료·실패를 상태에 반영한 뒤 레코드를 정리한다.
         */
        suspend fun runStorylineRecovery() {
            // collectLatest 를 쓰면 블록 안에서 target 을 비우는 순간 진행 중 정리가 취소된다.
            storylineRecoveryTarget.collect { command ->
                if (command != null) pollStoryline(command)
            }
        }

        private suspend fun pollStoryline(command: StorylineGenerationCommand) {
            while (true) {
                when (val result = storyCreationRepository.creationRequest(command.requestId)) {
                    is DomainResult.Success ->
                        when (val snapshot = result.value) {
                            CreationRequestSnapshot.Pending -> Unit

                            is CreationRequestSnapshot.StorylinesReady -> {
                                lastResult = snapshot.generation
                                progress = CreationProgress()
                                mutableState.value = StorylineGenerationState.Generated(snapshot.generation)
                                pendingCreationStore.clear()
                                storylineRecoveryTarget.value = null
                                return
                            }

                            // 단계가 어긋난 결과는 계약 위반이다. 실패 화면으로 합류한다.
                            is CreationRequestSnapshot.StoryReady -> {
                                finishStorylineRecoveryAsFailure()
                                return
                            }

                            CreationRequestSnapshot.Failed -> {
                                finishStorylineRecoveryAsFailure()
                                return
                            }
                        }

                    is DomainResult.Failure ->
                        // 폴링은 읽기라 네트워크 단절은 다음 주기로 넘기고, 404 를 포함한
                        // 서버 응답 실패는 기존 실패 처리로 합류한다.
                        if (result.error !is DomainError.Network) {
                            finishStorylineRecoveryAsFailure()
                            return
                        }
                }
                delay(RECOVERY_POLL_INTERVAL_MS)
            }
        }

        private suspend fun finishStorylineRecoveryAsFailure() {
            mutableState.value = StorylineGenerationState.Failed(lastResult)
            pendingCreationStore.clear()
            storylineRecoveryTarget.value = null
        }

        /** 완성 요청 시작. 레코드를 영속하고 재시도 승계용 마지막 명령을 기억한다. */
        suspend fun beginCompletion(command: StoryCompletionCommand) {
            lastCompletionCommand = command
            val generation = lastResult ?: return
            pendingCreationStore.write(
                PendingStoryCreation.CompletingStory(
                    generationCommand = lastCommand,
                    generation = generation,
                    command = command,
                    progress = progress,
                ),
            )
        }

        /** 완성 재시도가 409 로 거절됐다 — 서버가 진행 중이므로 복구 폴링으로 전환한다. */
        fun requestCompletionRecovery(command: StoryCompletionCommand) {
            mutableCompletionRecoveryTarget.value = command
        }

        fun clearCompletionRecovery() {
            mutableCompletionRecoveryTarget.value = null
        }

        /** 스토리라인 선택 화면의 활성 탭 미러. */
        fun updateActiveStoryline(index: Int) {
            progress = progress.copy(activeStorylineIndex = index)
        }

        /** "선택하기"로 추가 정보 단계에 넘긴 스토리라인 순번 미러. */
        fun markStorylineSelected(index: Int) {
            progress = progress.copy(selectedStorylineIndex = index)
        }

        /** 추가 정보 화면의 입력·추천 선택 미러. */
        fun updateAdditionalInfoProgress(
            inputs: List<String>,
            recommendations: List<String>,
        ) {
            progress = progress.copy(additionalInfoInputs = inputs, selectedRecommendations = recommendations)
        }

        /**
         * "다시 선택하기"로 스토리라인 단계에 되돌아간다. 입력·추천 선택과 함께 선택 순번도
         * 지운다 — 순번이 남으면 이후 이탈이 추가 정보 단계로 재개되어 이미 버린 입력 화면으로 돌아간다.
         */
        fun clearAdditionalInfoProgress() {
            progress =
                progress.copy(
                    selectedStorylineIndex = null,
                    additionalInfoInputs = emptyList(),
                    selectedRecommendations = emptyList(),
                )
        }

        /**
         * 이탈 시 보존할 내용이 있는지 — 진행 중 레코드 또는 생성 결과. 없으면 호출부가
         * 소실 경고 다이얼로그(3-1)를 띄운 뒤에야 [leaveFunnel] 을 부른다.
         */
        suspend fun hasContentToPreserve(): Boolean = lastResult != null || pendingCreationStore.read().isInFlight()

        /** 완성 성공으로 퍼널이 닫혔다. 남은 결과가 다음 이탈에서 임시 저장으로 둔갑하지 않게 비운다. */
        fun resetAfterCompletion() {
            leftFunnel = true
            resetInMemory()
        }

        /**
         * 퍼널 이탈 처리. 진행 중 레코드는 유지하고, 없으면 생성 결과를 임시 저장한다.
         * 내용이 남았으면 true 를 돌려주고 호출부가 토스트를 띄운다. 스토어는 초기화된다.
         */
        suspend fun leaveFunnel(): Boolean {
            leftFunnel = true
            // 서버는 끝까지 진행하므로 클라이언트 대기만 끊는다. 레코드가 복구 대상으로 남는다.
            runJob?.cancel()
            val result = lastResult
            val preserved =
                when {
                    pendingCreationStore.read().isInFlight() -> true

                    result != null -> {
                        pendingCreationStore.write(
                            PendingStoryCreation.Draft(
                                generationCommand = lastCommand,
                                generation = result,
                                progress = progress,
                                lastCompletionCommand = lastCompletionCommand,
                            ),
                        )
                        true
                    }

                    else -> false
                }
            resetInMemory()
            return preserved
        }

        private fun resetInMemory() {
            mutableState.value = StorylineGenerationState.Idle
            lastCommand = null
            lastResult = null
            lastCompletionCommand = null
            progress = CreationProgress()
            storylineRecoveryTarget.value = null
            mutableCompletionRecoveryTarget.value = null
            restoreAttempted = false
        }

        private fun newRequestId(): String = UUID.randomUUID().toString()

        companion object {
            const val RECOVERY_POLL_INTERVAL_MS: Long = 3_000
        }
    }

/** requestId 재사용이 서버 `PENDING` 과 겹친 409 판정. */
internal fun DomainError.isConflict(): Boolean = this is DomainError.Server && status == HTTP_CONFLICT

/** 서버에서 실제로 생성이 돌고 있(었)을 수 있는 복구 대상 레코드인지. 임시 저장본은 아니다. */
private fun PendingStoryCreation?.isInFlight(): Boolean =
    this is PendingStoryCreation.GeneratingStorylines || this is PendingStoryCreation.CompletingStory

private const val HTTP_CONFLICT = 409
