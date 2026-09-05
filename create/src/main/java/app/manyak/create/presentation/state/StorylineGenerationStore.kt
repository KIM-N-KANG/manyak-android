package app.manyak.create.presentation.state

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.create.domain.PendingStoryCreationStore
import app.manyak.create.domain.StoryCreationRepository
import app.manyak.create.entity.CreationProgress
import app.manyak.create.entity.CreationRequestSnapshot
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.StoryCharacterInput
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.StoryTag
import app.manyak.create.entity.StorylineGeneration
import app.manyak.create.entity.StorylineGenerationCommand
import app.manyak.create.presentation.di.FunnelScope
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
 * 재시작해도 복구 조회로 결과를 되찾는다. 생성 성공도 곧바로 영속한다 — AI 결과를 메모리에만
 * 두는 시간을 없앤다. 그 뒤의 편집(활성 탭·선택 스토리라인·추가 정보)은 [progress] 에 모아 두었다가
 * 사용자가 임시 저장을 누르거나 앱이 백그라운드로 갈 때 한 번에 [saveDraft] 로 내보낸다.
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

        private val mutableDraftSave = MutableStateFlow(DraftSaveUiState())

        /** 퍼널 헤더의 임시 저장 버튼이 그리는 상태. */
        val draftSave: StateFlow<DraftSaveUiState> = mutableDraftSave.asStateFlow()

        private var lastCommand: StorylineGenerationCommand? = null
        private var lastResult: StorylineGeneration? = null

        /** 마지막 생성에 실린 키워드. 스토리라인 단계의 "선택한 키워드 보기"가 읽는다. */
        val generationCommand: StorylineGenerationCommand? get() = lastCommand

        private var cachedTags: List<StoryTag>? = null
        private val tagsMutex = Mutex()

        /** 마지막 완성 명령. 같은 페이로드 재시도의 requestId 재사용과 임시 저장 승계에 쓴다. */
        var lastCompletionCommand: StoryCompletionCommand? = null
            private set

        /** 임시 저장·복원 재료. 단계 ViewModel 이 입력 변화를 미러링하고 저장 시점에 통째로 나간다. */
        var progress: CreationProgress = CreationProgress()
            private set

        /** 디스크에 마지막으로 반영된 진행. [progress] 와 갈리면 저장하지 않은 변경이 있다는 뜻이다. */
        private var savedProgress: CreationProgress = CreationProgress()

        /**
         * 지금 초안이 그대로 디스크에 있는지. [savedProgress] 비교와 달리 활성 탭까지 따지고
         * 쓰기 실패도 반영해, 다시 쓸 필요가 정말 없을 때만 true 다.
         */
        private var draftPersisted = false

        /**
         * 퍼널을 떠난 뒤 도착한 원 응답이 상태·레코드를 건드리지 않게 하는 플래그.
         * 화면에 반영할 곳이 없는데 레코드만 지우면 재진입 복구 경로를 잃는다(3-1 정리 규칙).
         */
        private var leftFunnel = false

        private val restoreMutex = Mutex()
        private var restoreAttempted = false

        /** 임시 저장과 요청 단계 저장이 단일 슬롯을 덮는 순서를 직렬화한다. */
        private val persistenceMutex = Mutex()
        private var draftSaveJob: Job? = null
        private var savedDisplayJob: Job? = null

        /** 초안을 저장해도 되는 구간인지. 진행 중 요청 레코드가 슬롯을 쥐고 있으면 false 다. */
        private var draftSaveEnabled = false

        /**
         * 슬롯을 쥐고 있는 완성 레코드의 명령. 이 동안의 저장은 초안이 아니라 이 레코드의 진행만
         * 갈아 끼운다 — 초안으로 덮으면 복구 조회에 쓸 requestId 를 잃는다.
         */
        private var slotCompletionCommand: StoryCompletionCommand? = null

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
            disableDraftSave()
            lastCommand = command
            mutableState.value = StorylineGenerationState.Generating
            runJob = funnelScope.launch { run(command) }
        }

        private suspend fun run(command: StorylineGenerationCommand) {
            // 요청 전에 영속한다 — 응답을 못 받아도 재진입 복구 조회가 이 requestId 를 쓴다.
            persistStage(PendingStoryCreation.GeneratingStorylines(command))
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
                    // 생성 결과는 사용자가 만든 편집이 아니라 다시 얻기 비싼 재료다. 임시 저장을
                    // 기다리지 않고 바로 슬롯에 넣는다.
                    enableDraftSave()
                    persistDraft()
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
                            if (lastResult == null) {
                                clearPendingRecord()
                            } else {
                                enableDraftSave()
                                persistDraft()
                            }
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
                        slotCompletionCommand = record.command
                        restoreProgress(record.progress)
                        mutableState.value = StorylineGenerationState.Generated(record.generation)
                        mutableCompletionRecoveryTarget.value = record.command
                    }

                    is PendingStoryCreation.Draft -> {
                        lastCommand = record.generationCommand
                        lastResult = record.generation
                        lastCompletionCommand = record.lastCompletionCommand
                        restoreProgress(record.progress)
                        mutableState.value = StorylineGenerationState.Generated(record.generation)
                        enableDraftSave()
                    }
                }
                refreshDraftSave()
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
                                enableDraftSave()
                                persistDraft()
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
            if (lastResult == null) {
                clearPendingRecord()
            } else {
                enableDraftSave()
                persistDraft()
            }
            storylineRecoveryTarget.value = null
        }

        /** 완성 요청 시작. 레코드를 영속하고 재시도 승계용 마지막 명령을 기억한다. */
        suspend fun beginCompletion(command: StoryCompletionCommand) {
            lastCompletionCommand = command
            val generation = lastResult ?: return
            disableDraftSave()
            persistStage(
                PendingStoryCreation.CompletingStory(
                    generationCommand = lastCommand,
                    generation = generation,
                    command = command,
                    progress = progress,
                ),
            )
        }

        /** 서버가 완성을 확정적으로 거절했으면 생성 결과·입력을 다시 Draft 단계로 보존한다. */
        suspend fun restoreDraftAfterCompletionFailure() {
            mutableCompletionRecoveryTarget.value = null
            slotCompletionCommand = null
            enableDraftSave()
            persistDraft()
        }

        /**
         * 완성 레코드를 쥔 채로 임시 저장을 다시 연다. 응답을 못 받은 실패는 레코드를 보존해야
         * 복구 조회가 결과를 되찾는데, 저장까지 잠가 두면 실패 뒤의 편집이 디스크에 닿지 못한다.
         * 이 구간의 저장은 레코드를 바꾸지 않고 진행만 갈아 끼운다.
         */
        fun keepCompletionRecordEditable() {
            if (slotCompletionCommand == null) return
            enableDraftSave()
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
            updateProgress(progress.copy(activeStorylineIndex = index))
        }

        /** "선택하기"로 추가 정보 단계에 넘긴 스토리라인 순번 미러. */
        fun markStorylineSelected(index: Int) {
            updateProgress(progress.copy(selectedStorylineIndex = index))
        }

        /** 추가 정보 화면의 입력·추천 선택 미러. */
        fun updateAdditionalInfoProgress(
            inputs: List<String>,
            recommendations: List<String>,
        ) {
            updateProgress(
                progress.copy(additionalInfoInputs = inputs, selectedRecommendations = recommendations),
            )
        }

        /**
         * "다시 선택하기"로 스토리라인 단계에 되돌아간다. 입력·추천 선택과 함께 선택 순번도
         * 지운다 — 순번이 남으면 이후 이탈이 추가 정보 단계로 재개되어 이미 버린 입력 화면으로 돌아간다.
         */
        fun clearAdditionalInfoProgress() {
            updateProgress(
                progress.copy(
                    selectedStorylineIndex = null,
                    additionalInfoInputs = emptyList(),
                    selectedRecommendations = emptyList(),
                ),
            )
        }

        private fun updateProgress(updated: CreationProgress) {
            if (updated == progress) return
            progress = updated
            draftPersisted = false
            refreshDraftSave()
        }

        /**
         * 지금 상태를 진행 레코드로 내보낸다. 임시 저장 버튼과 백그라운드 전환이 부르는
         * 유일한 저장 경로다. 실행은 퍼널 스코프가 담아 화면이 사라져도 쓰기가 끝난다.
         */
        fun saveDraft() {
            // 쓰기가 도는 동안의 추가 요청은 버린다 — 같은 내용을 두 번 쓸 뿐이다.
            if (!draftSaveEnabled || draftSaveJob?.isActive == true) return
            // 이미 같은 내용이 디스크에 있으면 확인 표시만 다시 보여 준다. 연타로 눌러도
            // 디스크는 건드리지 않고, 버튼이 죽은 것처럼 보이지도 않는다.
            if (draftPersisted) {
                refreshDraftSave(DraftSaveStatus.SAVED)
                scheduleSavedDisplayReset()
                return
            }
            draftSaveJob = funnelScope.launch { persistDraft() }
        }

        /**
         * 이탈 시 보존할 내용이 있는지 — 진행 중 레코드 또는 생성 결과. 없으면 호출부가
         * 소실 경고 다이얼로그(3-1)를 띄운 뒤에야 [leaveFunnel] 을 부른다.
         */
        suspend fun hasContentToPreserve(): Boolean = lastResult != null || pendingCreationStore.read().isInFlight()

        /** 키워드 화면이 이미 받아 둔 목록. 스토리라인 단계가 같은 조회를 반복하지 않게 넘겨 둔다. */
        fun cacheTags(tags: List<StoryTag>) {
            cachedTags = tags
        }

        /**
         * 태그 ID 를 이름으로 바꾸는 데 쓰는 제공 태그 목록. 퍼널 수명 동안 한 번만 받아 온다 —
         * 재개 진입은 키워드 화면을 거치지 않아 넘겨받은 목록이 없다.
         */
        suspend fun tagCatalog(): DomainResult<List<StoryTag>> =
            tagsMutex.withLock {
                val cached = cachedTags
                if (cached != null) {
                    DomainResult.Success(cached)
                } else {
                    storyCreationRepository.tags().also { result ->
                        if (result is DomainResult.Success) cachedTags = result.value
                    }
                }
            }

        /** 완성 성공으로 퍼널이 닫혔다. 남은 결과가 다음 이탈에서 임시 저장으로 둔갑하지 않게 비운다. */
        fun resetAfterCompletion() {
            leftFunnel = true
            resetInMemory()
        }

        /**
         * 퍼널 이탈 처리. 저장하지 않은 편집은 사용자가 버리기로 한 것이므로 여기서 저장하지
         * 않는다 — 마지막으로 저장된 스냅숏이 그대로 재개 지점이 된다. 진행 중 레코드는
         * 서버에서 계속 돌고 있는 복구 대상이라 유지한다. 스토어는 초기화된다.
         */
        suspend fun leaveFunnel() {
            leftFunnel = true
            // 생성 직후 곧바로 닫아도 복구 requestId 가 디스크에 남도록 요청 단계를 한 번 더 확정한다.
            val generatingCommand =
                lastCommand.takeIf { mutableState.value is StorylineGenerationState.Generating }
            if (generatingCommand != null) {
                persistStage(PendingStoryCreation.GeneratingStorylines(generatingCommand))
            }
            // 서버는 끝까지 진행하므로 클라이언트 대기만 끊는다. 레코드가 복구 대상으로 남는다.
            runJob?.cancel()
            resetInMemory()
        }

        private fun enableDraftSave() {
            draftSaveEnabled = true
            refreshDraftSave()
        }

        private fun disableDraftSave() {
            draftSaveEnabled = false
            savedDisplayJob?.cancel()
            savedDisplayJob = null
            refreshDraftSave(DraftSaveStatus.IDLE)
        }

        /** 복원한 진행은 이미 디스크에 있는 값이므로 저장하지 않은 변경으로 세지 않는다. */
        private fun restoreProgress(restored: CreationProgress) {
            progress = restored
            savedProgress = restored
            draftPersisted = true
        }

        private fun refreshDraftSave(status: DraftSaveStatus = mutableDraftSave.value.status) {
            val hasUnsavedChanges = draftSaveEnabled && !progress.hasSameContentAs(savedProgress)
            mutableDraftSave.value =
                DraftSaveUiState(
                    // 저장한 뒤 다시 편집했으면 "임시 저장됨"은 지금 상태를 가리키지 않는다.
                    status = if (status == DraftSaveStatus.SAVED && hasUnsavedChanges) DraftSaveStatus.IDLE else status,
                    // 진행 중 요청이 슬롯을 쥐고 있거나 이미 같은 내용이 디스크에 있으면 잠근다.
                    // 활성 탭까지 따지는 [draftPersisted] 를 쓴다 — 탭을 옮겼으면 저장할 것이 있다.
                    canSave = draftSaveEnabled && lastResult != null && !draftPersisted,
                    hasUnsavedChanges = hasUnsavedChanges,
                )
        }

        private suspend fun persistDraft(): Boolean {
            val record = currentRecord() ?: return false
            val recordProgress = progress
            savedDisplayJob?.cancel()
            refreshDraftSave(DraftSaveStatus.SAVING)
            val saved =
                persistenceMutex.withLock {
                    // 대기하는 사이 요청 단계가 슬롯을 가져갔으면 오래된 초안으로 덮지 않는다.
                    if (draftSaveEnabled) pendingCreationStore.write(record) else false
                }
            if (saved) {
                savedProgress = recordProgress
                // 쓰는 사이에 진행이 바뀌었으면 디스크는 이미 한 박자 뒤처져 있다.
                draftPersisted = recordProgress == progress
                refreshDraftSave(DraftSaveStatus.SAVED)
                scheduleSavedDisplayReset()
            } else {
                draftPersisted = false
                refreshDraftSave(DraftSaveStatus.IDLE)
            }
            return saved
        }

        private fun scheduleSavedDisplayReset() {
            savedDisplayJob?.cancel()
            savedDisplayJob =
                funnelScope.launch {
                    delay(DRAFT_SAVED_DISPLAY_MS)
                    refreshDraftSave(DraftSaveStatus.IDLE)
                }
        }

        private suspend fun persistStage(record: PendingStoryCreation): Boolean {
            val saved = persistenceMutex.withLock { pendingCreationStore.write(record) }
            // 완성 레코드에는 현재 진행이 그대로 실려 나가 저장하지 않은 변경이 남지 않는다.
            if (saved && record is PendingStoryCreation.CompletingStory) savedProgress = record.progress
            slotCompletionCommand = (record as? PendingStoryCreation.CompletingStory)?.command?.takeIf { saved }
            draftPersisted = false
            refreshDraftSave()
            return saved
        }

        private suspend fun clearPendingRecord(): Boolean {
            disableDraftSave()
            slotCompletionCommand = null
            draftPersisted = false
            return persistenceMutex.withLock { pendingCreationStore.clear() }
        }

        /** 지금 저장하면 슬롯에 들어갈 레코드. 완성 레코드가 슬롯을 쥐고 있으면 진행만 갈아 끼운다. */
        private fun currentRecord(): PendingStoryCreation? {
            val result = lastResult ?: return null
            val completion = slotCompletionCommand
            return if (completion == null) {
                PendingStoryCreation.Draft(
                    generationCommand = lastCommand,
                    generation = result,
                    progress = progress,
                    lastCompletionCommand = lastCompletionCommand,
                )
            } else {
                PendingStoryCreation.CompletingStory(
                    generationCommand = lastCommand,
                    generation = result,
                    command = completion,
                    progress = progress,
                )
            }
        }

        private fun resetInMemory() {
            draftSaveJob?.cancel()
            draftSaveJob = null
            mutableState.value = StorylineGenerationState.Idle
            lastCommand = null
            lastResult = null
            lastCompletionCommand = null
            slotCompletionCommand = null
            progress = CreationProgress()
            savedProgress = CreationProgress()
            draftPersisted = false
            storylineRecoveryTarget.value = null
            mutableCompletionRecoveryTarget.value = null
            restoreAttempted = false
            disableDraftSave()
        }

        private fun newRequestId(): String = UUID.randomUUID().toString()

        companion object {
            const val RECOVERY_POLL_INTERVAL_MS: Long = 3_000
        }
    }

/**
 * 저장하지 않은 "내용"이 같은지. 활성 스토리라인 탭은 읽던 자리일 뿐이라 세지 않는다 —
 * 탭을 훑어보기만 해도 이탈 경고가 뜨면 경고가 신호를 잃는다. 저장할 때는 함께 나간다.
 */
private fun CreationProgress.hasSameContentAs(other: CreationProgress): Boolean =
    copy(activeStorylineIndex = other.activeStorylineIndex) == other

/** requestId 재사용이 서버 `PENDING` 과 겹친 409 판정. */
internal fun DomainError.isConflict(): Boolean = this is DomainError.Server && status == HTTP_CONFLICT

/** 서버에서 실제로 생성이 돌고 있(었)을 수 있는 복구 대상 레코드인지. 임시 저장본은 아니다. */
private fun PendingStoryCreation?.isInFlight(): Boolean =
    this is PendingStoryCreation.GeneratingStorylines || this is PendingStoryCreation.CompletingStory

private const val HTTP_CONFLICT = 409
