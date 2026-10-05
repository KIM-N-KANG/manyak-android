package app.manyak.chat.room.presentation.tour

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import app.manyak.chat.entity.ChatInputMode
import app.manyak.chat.R as ChatR

/** 투어가 하이라이트하는 컴포저 버튼. */
internal enum class ChatTourTarget {
    ADD_SITUATION,
    ADD_DIALOGUE,
    SETTINGS,
    SEND,
}

/** 투어 스텝. [wire] 는 분석 이벤트의 `step_id` 로 웹과 같은 값이다. */
enum class ChatTourStep(
    val wire: String,
    @param:StringRes internal val titleRes: Int,
    @param:StringRes internal val descriptionRes: Int,
    internal val targets: List<ChatTourTarget>,
) {
    /** 블럭 모드의 첫 스텝. 상황·대사 추가 버튼이 각각 입력 칸을 늘린다. */
    ADD_BLOCKS(
        wire = "add-blocks",
        titleRes = ChatR.string.chat_tour_add_blocks_title,
        descriptionRes = ChatR.string.chat_tour_add_blocks_description,
        targets = listOf(ChatTourTarget.ADD_SITUATION, ChatTourTarget.ADD_DIALOGUE),
    ),

    /** 일반 모드의 첫 스텝. 대사 추가가 없고, 상황 추가는 강조 마커를 넣는다. */
    ADD_EMPHASIS(
        wire = "add-emphasis",
        titleRes = ChatR.string.chat_tour_add_emphasis_title,
        descriptionRes = ChatR.string.chat_tour_add_emphasis_description,
        targets = listOf(ChatTourTarget.ADD_SITUATION),
    ),
    SETTINGS(
        wire = "settings",
        titleRes = ChatR.string.chat_tour_settings_title,
        descriptionRes = ChatR.string.chat_tour_settings_description,
        targets = listOf(ChatTourTarget.SETTINGS),
    ),
    RANDOM_SEND(
        wire = "random-send",
        titleRes = ChatR.string.chat_tour_random_send_title,
        descriptionRes = ChatR.string.chat_tour_random_send_description,
        targets = listOf(ChatTourTarget.SEND),
    ),
}

/** 입력 모드에 맞는 스텝 목록. 첫 스텝은 모드마다 버튼 구성과 동작이 달라 문구와 대상이 갈린다. */
internal fun chatTourSteps(mode: ChatInputMode): List<ChatTourStep> =
    listOf(
        if (mode == ChatInputMode.BLOCK) ChatTourStep.ADD_BLOCKS else ChatTourStep.ADD_EMPHASIS,
        ChatTourStep.SETTINGS,
        ChatTourStep.RANDOM_SEND,
    )

/**
 * 투어 대상 버튼의 화면 위치. 버튼이 그려질 때마다 갱신돼, 추천 입력이 나타나며 컴포저가 움직여도
 * 하이라이트가 따라간다.
 */
@Stable
internal class ChatTourTargets {
    private val bounds = mutableStateMapOf<ChatTourTarget, Rect>()

    fun update(
        target: ChatTourTarget,
        rect: Rect,
    ) {
        bounds[target] = rect
    }

    /** 스텝 대상들을 감싸는 영역. 그려진 대상이 없으면 null 이다. */
    fun stepBounds(step: ChatTourStep): Rect? =
        step.targets
            .mapNotNull { target -> bounds[target] }
            .reduceOrNull { union, rect ->
                Rect(
                    left = minOf(union.left, rect.left),
                    top = minOf(union.top, rect.top),
                    right = maxOf(union.right, rect.right),
                    bottom = maxOf(union.bottom, rect.bottom),
                )
            }
}

/** 버튼 위치를 [targets] 에 올린다. 투어가 없는 미리보기에서는 [targets] 가 null 이다. */
internal fun Modifier.chatTourTarget(
    targets: ChatTourTargets?,
    target: ChatTourTarget,
): Modifier =
    if (targets == null) {
        this
    } else {
        onGloballyPositioned { coordinates -> targets.update(target, coordinates.boundsInRoot()) }
    }

/**
 * [from] 부터 실제로 보여 줄 수 있는 첫 스텝의 자리. 대상이 없거나 화면 밖인 스텝은 건너뛴다.
 *
 * @param bounds 스텝 대상 영역. 좌표는 [viewportHeight] 와 같은 기준이다.
 * @return 남은 스텝이 없으면 null
 */
internal fun nextChatTourStep(
    steps: List<ChatTourStep>,
    from: Int,
    viewportHeight: Float,
    bounds: (ChatTourStep) -> Rect?,
): Int? =
    (from until steps.size).firstOrNull { index ->
        val rect = bounds(steps[index])
        rect != null && rect.top < viewportHeight && rect.bottom > 0f
    }

/**
 * 카드를 하이라이트 위에 둘지. 아래 공간이 카드 높이와 여백에 모자랄 때만 위다.
 */
internal fun isTourCardAbove(
    highlight: Rect,
    viewportHeight: Float,
    cardHeight: Float,
    gap: Float,
): Boolean = viewportHeight - highlight.bottom < cardHeight + gap * 2

/**
 * 카드의 왼쪽 좌표.
 *
 * 기본은 하이라이트 중앙 정렬이고, 한쪽으로 치우쳐 중앙에 둘 수 없으면 그쪽 하이라이트 변에 카드 변을
 * 맞춘다. 여백에만 맞추면 카드가 하이라이트보다 안쪽으로 들어가 어긋나 보인다. 어느 경우든 화면은
 * 넘지 않는다.
 */
internal fun tourCardLeft(
    highlight: Rect,
    cardWidth: Float,
    viewportWidth: Float,
    margin: Float,
): Float {
    val centered = highlight.center.x - cardWidth / 2
    val min = margin
    val max = maxOf(viewportWidth - cardWidth - margin, min)
    return when {
        centered < min -> maxOf(0f, minOf(highlight.left, min))
        centered > max -> minOf(viewportWidth - cardWidth, maxOf(highlight.right - cardWidth, max))
        else -> centered
    }
}
