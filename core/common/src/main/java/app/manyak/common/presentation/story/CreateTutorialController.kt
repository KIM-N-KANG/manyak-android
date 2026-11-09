package app.manyak.common.presentation.story

import app.manyak.common.domain.story.CreateTutorialProgress
import app.manyak.common.entity.story.CreateTutorialStage
import app.manyak.common.entity.story.openableAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 안내가 분석에 알리는 일. 이벤트 이름은 분석 모듈이 붙인다. */
sealed interface CreateTutorialReport {
    data class StepViewed(
        val stage: CreateTutorialStage,
    ) : CreateTutorialReport

    data class Skipped(
        val stage: CreateTutorialStage,
    ) : CreateTutorialReport

    /** 마지막 단계를 닫았다. [toOriginals] 는 "오리지널 스토리 보기"로 닫았는지다. */
    data class Completed(
        val toOriginals: Boolean,
    ) : CreateTutorialReport
}

/**
 * 간편 제작 첫 진입 안내의 절차. 간편 제작 세 화면과 제작 탭이 같은 규칙을 쓰므로 화면마다 되풀이하지 않고 여기 둔다.
 *
 * 열린 단계는 화면 상태에 섞지 않고 [stage] 하나로만 둔다. 대화창은 다른 화면 상태와 얽히지 않고, 화면의
 * ViewModel 이 이 객체를 들고 있어 구성 변경에도 같은 단계가 남는다. 다음에 보일 단계는 처음 한 번 읽고 마칠
 * 때마다 여기서 바로 바꾼다. 기록이 끝나기를 기다렸다가 다시 읽으면 그 사이 같은 단계가 다시 열린다.
 *
 * 버튼이나 뒤로가기로 닫을 때만 마친 것으로 기록한다. 열린 채 화면을 떠나면 다음 진입에 같은 단계를 다시 연다.
 *
 * @param screen 지금 화면이 안내를 받을 수 있으면 그 화면의 단계, 아니면 null
 * @param prepare 단계를 열기 직전에 문구 재료를 마련한다
 */
class CreateTutorialController(
    private val scope: CoroutineScope,
    private val progress: CreateTutorialProgress,
    private val screen: Flow<CreateTutorialStage?>,
    private val report: (CreateTutorialReport) -> Unit,
    private val prepare: suspend (CreateTutorialStage) -> Unit = {},
) {
    private val open = MutableStateFlow<CreateTutorialStage?>(null)

    /** 열려 있는 단계. 없으면 대화창을 그리지 않는다. */
    val stage: StateFlow<CreateTutorialStage?> = open.asStateFlow()

    private var next: CreateTutorialStage? = null
    private var loaded = false

    /** 화면이 보이는 동안 부른다. 화면이 안내를 받을 수 있게 되면 잠깐 뒤 다음 단계를 연다. 그 사이 바뀌면 열지 않는다. */
    suspend fun drive() {
        if (!loaded) {
            next = progress.next()
            loaded = true
        }
        screen.distinctUntilChanged().collectLatest { current ->
            if (open.value != null) return@collectLatest
            val stage = next.openableAt(current) ?: return@collectLatest
            delay(OPEN_DELAY_MS)
            prepare(stage)
            show(stage)
        }
    }

    /** 주 버튼. 시작하기는 닫지 않고 키워드 안내로 이어 간다. 보조 버튼이 없는 단계의 뒤로가기도 여기로 온다. */
    fun primary() {
        when (val stage = open.value ?: return) {
            CreateTutorialStage.INTRO -> {
                next = CreateTutorialStage.KEYWORD
                show(CreateTutorialStage.KEYWORD)
                save { progress.finish(stage) }
            }

            CreateTutorialStage.COMPLETING -> {
                report(CreateTutorialReport.Completed(toOriginals = true))
                finishAll()
            }

            else -> {
                next = stage.following
                open.value = null
                save { progress.finish(stage) }
            }
        }
    }

    /** 보조 버튼과 그 단계의 뒤로가기. 첫 단계는 건너뛰기, 마지막 단계는 닫기다. */
    fun secondary() {
        when (val stage = open.value) {
            CreateTutorialStage.INTRO -> {
                report(CreateTutorialReport.Skipped(stage))
                finishAll()
            }

            CreateTutorialStage.COMPLETING -> {
                report(CreateTutorialReport.Completed(toOriginals = false))
                finishAll()
            }

            else -> Unit
        }
    }

    private fun finishAll() {
        next = null
        open.value = null
        save { progress.finishAll() }
    }

    private fun show(stage: CreateTutorialStage) {
        open.value = stage
        report(CreateTutorialReport.StepViewed(stage))
    }

    private fun save(write: suspend () -> Unit) {
        scope.launch { write() }
    }

    companion object {
        /** 화면이 준비된 뒤 여는 지연. 채팅 첫 진입 안내와 같다. */
        const val OPEN_DELAY_MS: Long = 200
    }
}
