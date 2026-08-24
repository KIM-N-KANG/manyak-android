package app.manyak.feature.create

import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.StoryCharacterInput
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.StorylineGeneration
import app.manyak.core.domain.story.StorylineGenerationCommand
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * 따라 결과 본문은 여기에 둔다. 실행은 호출한 ViewModel 의 스코프를 쓴다 — 퍼널을 완전히
 * 이탈하면 생성도 함께 취소된다(백그라운드 복구 대응은 §3-3-5 확정 전까지 보류).
 */
@ActivityRetainedScoped
class StorylineGenerationStore
    @Inject
    constructor(
        private val storyCreationRepository: StoryCreationRepository,
    ) {
        private val mutableState = MutableStateFlow<StorylineGenerationState>(StorylineGenerationState.Idle)
        val state: StateFlow<StorylineGenerationState> = mutableState.asStateFlow()

        private var lastCommand: StorylineGenerationCommand? = null
        private var lastResult: StorylineGeneration? = null

        /** 키워드 입력으로 새 생성을 시작한다. 이전 퍼널의 결과는 덮인다. */
        suspend fun generate(input: StorylineGenerationInput) {
            if (mutableState.value is StorylineGenerationState.Generating) return
            lastResult = null
            run(
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
        suspend fun regenerate() {
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
            run(command)
        }

        private suspend fun run(command: StorylineGenerationCommand) {
            lastCommand = command
            mutableState.value = StorylineGenerationState.Generating
            val result =
                try {
                    storyCreationRepository.generateStorylines(command)
                } catch (cancellation: CancellationException) {
                    // 퍼널 이탈로 취소된 요청이 Generating 을 남기면 다음 생성 시작이 잠긴다.
                    mutableState.value = StorylineGenerationState.Failed(lastResult)
                    throw cancellation
                }
            mutableState.value =
                when (result) {
                    is DomainResult.Success -> {
                        lastResult = result.value
                        StorylineGenerationState.Generated(result.value)
                    }

                    is DomainResult.Failure -> StorylineGenerationState.Failed(lastResult)
                }
        }

        private fun newRequestId(): String = UUID.randomUUID().toString()
    }
