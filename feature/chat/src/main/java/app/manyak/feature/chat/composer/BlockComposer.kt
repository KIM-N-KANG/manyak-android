package app.manyak.feature.chat.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme
import kotlinx.coroutines.withTimeoutOrNull

/** 블럭 모드. 칸마다 상자가 따로라 툴바는 상자 밖 아래에 선다. */
@Composable
internal fun BlockComposer(
    blocks: List<InputBlock>,
    enabled: Boolean,
    actions: ChatComposerActions,
    toolbar: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (blocks.isNotEmpty()) {
            BlockInputList(
                blocks = blocks,
                enabled = enabled,
                onValueChange = actions.onBlockValueChange,
                onRemoveBlock = actions.onRemoveBlock,
            )
        }
        toolbar(
            Modifier.padding(
                start = ManyakTheme.spacing.gutter,
                end = ManyakTheme.spacing.gutter,
                bottom = ManyakTheme.spacing.gutter,
            ),
        )
    }
}

/**
 * 블럭 목록. 창 높이의 [BLOCK_LIST_HEIGHT_FRACTION] 을 넘으면 그 안에서 스크롤한다 — 키보드가 올라온
 * 상태에서 목록이 화면을 다 먹지 않게 하는 상한이고, 시스템 글자 크기를 키우면 줄 수보다 이 상한이
 * 먼저 걸린다.
 */
@Composable
private fun BlockInputList(
    blocks: List<InputBlock>,
    enabled: Boolean,
    onValueChange: (Long, String) -> Unit,
    onRemoveBlock: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 내용이 있는 블럭을 지울 때만 묻는다. 확인 대상은 회전에서 사라지면 안 되므로 saveable 이다.
    var pendingRemoveId by rememberSaveable { mutableStateOf<Long?>(null) }
    // 지우는 중인 칸. 퇴장이 끝난 뒤에야 목록에서 뺀다 — 먼저 빼면 컴포저블이 사라져 애니메이션이
    // 나오지 않는다. 구성 변경으로 이 표시를 잃으면 칸은 그대로 남는다(지워지지 않는 쪽이 안전하다).
    var exitingIds by remember { mutableStateOf(emptySet<Long>()) }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * BLOCK_LIST_HEIGHT_FRACTION
    val scrollState = rememberScrollState()
    FollowNewBlock(blocks.size, scrollState)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = ManyakTheme.spacing.gutter,
                    end = ManyakTheme.spacing.gutter,
                    bottom = ManyakTheme.spacing.compact,
                ).heightIn(max = maxHeight)
                .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        val ordinals = remember(blocks) { blocks.typeOrdinals() }
        val ordinalWidth = rememberOrdinalWidth()
        // 처음부터 있던 칸은 그대로 그린다 — 방을 열 때 컴포저가 스스로 조립되는 것처럼 보이면 안 된다.
        val initialIds = remember { blocks.map { block -> block.id }.toSet() }
        blocks.forEach { block ->
            val exiting = block.id in exitingIds
            key(block.id) {
                BlockRowTransition(
                    entering = block.id !in initialIds,
                    exiting = exiting,
                    onExited = {
                        exitingIds = exitingIds - block.id
                        onRemoveBlock(block.id)
                    },
                ) {
                    BlockInputRow(
                        block = block,
                        ordinal = ordinals[block.id] ?: 1,
                        ordinalWidth = ordinalWidth,
                        // 접히는 동안에는 손대지 못하게 한다 — 사라지는 칸에 글자를 넣을 수는 없다.
                        enabled = enabled && !exiting,
                        onValueChange = { value -> onValueChange(block.id, value) },
                        onRemove = {
                            if (block.value.isBlank()) {
                                exitingIds = exitingIds + block.id
                            } else {
                                pendingRemoveId = block.id
                            }
                        },
                    )
                }
            }
        }
    }

    PendingRemoveDialog(
        pendingId = pendingRemoveId,
        onDismiss = { pendingRemoveId = null },
        onConfirm = { id ->
            pendingRemoveId = null
            exitingIds = exitingIds + id
        },
    )
}

/** 내용이 있는 칸을 지우기 전 확인. 확인하면 목록에서 바로 빼지 않고 접힘부터 시작한다. */
@Composable
private fun PendingRemoveDialog(
    pendingId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    if (pendingId == null) return
    RemoveBlockDialog(onDismiss = onDismiss, onConfirm = { onConfirm(pendingId) })
}

@Composable
private fun BlockInputRow(
    block: InputBlock,
    ordinal: Int,
    ordinalWidth: Dp,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val isSituation = block.type == InputBlockType.SITUATION
    // 라벨과 입력 글자를 같은 색으로 둔다 — 쓰는 중에도 상황이 강조로 나갈 것임이 보여야 한다.
    val contentColor = if (isSituation) ManyakTheme.colors.textNarration else ManyakTheme.colors.text
    // 라벨에 종류별 순번을 붙인다 — 칸이 여럿일 때 방금 늘어난 자리가 어디인지 라벨만 보고 안다.
    val label =
        stringResource(
            if (isSituation) {
                R.string.chat_composer_situation_label
            } else {
                R.string.chat_composer_dialogue_label
            },
        )
    val placeholder =
        stringResource(
            if (isSituation) {
                R.string.chat_composer_situation_placeholder
            } else {
                R.string.chat_composer_dialogue_placeholder
            },
        )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SyncedTextField(
            modifier = Modifier.weight(1f),
            text = block.value,
            onTextChange = onValueChange,
            placeholder = placeholder,
            enabled = enabled,
            maxLines = BLOCK_MAX_LINES,
            textColor = contentColor,
            leading = {
                Row(
                    // 라벨 뒤 간격을 필드 안쪽 여백과 같게 둔다 — 고정 폭으로 잡으면 글자 폭에 따라
                    // 남는 자리가 달라져 좌우가 어긋난다.
                    modifier = Modifier.padding(end = ManyakTheme.spacing.controlHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
                ) {
                    Text(
                        text = label,
                        style = ManyakTheme.typography.labelSmall,
                        color = contentColor,
                    )
                    Text(
                        // 순번 자리는 두 자리만큼 미리 잡는다 — 한 자리에서 두 자리로 넘어갈 때
                        // 입력 글자의 시작 위치가 밀리면 안 된다.
                        modifier = Modifier.widthIn(min = ordinalWidth),
                        text = ordinal.toString(),
                        style = ManyakTheme.typography.labelSmall,
                        color = contentColor,
                    )
                }
            },
        )
        ComposerIconButton(
            // 포커스 순서에서 빼 블럭 사이를 키보드로 바로 오가게 한다.
            modifier = Modifier.focusProperties { canFocus = false },
            iconRes = R.drawable.ic_close,
            contentDescription = stringResource(R.string.chat_composer_remove_block),
            enabled = enabled,
            onClick = onRemove,
        )
    }
}

/**
 * 칸 하나의 등장·퇴장. 새로 더한 칸은 아래에서 자라 오르고, 지운 칸은 같은 변을 붙잡은 채 접힌다.
 *
 * 퇴장이 등장보다 짧다 — 지우기는 이미 결정한 동작이라 사라지는 것을 붙잡아 두면 다음 칸으로 넘어가는
 * 손을 기다리게 한다. 접힘이 끝나면 [onExited] 로 목록에서 뺀다.
 *
 * 모드 전환·추천 채우기·전송 뒤 초기화처럼 목록 전체가 갈리는 경로는 이 표시를 거치지 않으므로 칸이
 * 한꺼번에 교체된다 — 한 칸씩 접히면 우수수 무너지는 것처럼 보인다.
 */
@Composable
private fun BlockRowTransition(
    entering: Boolean,
    exiting: Boolean,
    onExited: () -> Unit,
    content: @Composable () -> Unit,
) {
    val enterMillis = ManyakTheme.motion.elementEnterMillis
    val exitMillis = ManyakTheme.motion.elementExitMillis
    val visibleState = remember { MutableTransitionState(!entering).apply { targetState = true } }
    LaunchedEffect(exiting) { visibleState.targetState = !exiting }
    LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
        if (visibleState.isIdle && !visibleState.currentState) onExited()
    }
    AnimatedVisibility(
        visibleState = visibleState,
        // 아래 변을 붙잡고 높이를 키우고 줄인다. 위를 붙잡으면 위 칸들이 밀렸다 당겨진다.
        enter =
            expandVertically(
                animationSpec = tween(enterMillis, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Bottom,
            ) + fadeIn(animationSpec = tween(enterMillis)),
        exit =
            shrinkVertically(
                animationSpec = tween(exitMillis, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Bottom,
            ) + fadeOut(animationSpec = tween(exitMillis)),
        content = { content() },
    )
}

/**
 * 칸이 늘어나면 목록 끝을 따라간다. 상한 높이를 넘긴 목록에서는 새 칸이 접힌 자리에 생겨 추가가
 * 일어났는지 보이지 않는다.
 *
 * **높이가 자라는 프레임마다 곧바로 끝으로 붙인다** — 스크롤을 따로 애니메이션하면 등장 애니메이션이
 * 끝난 뒤에야 움직이기 시작해 누르고 한 박자 뒤에 반응하는 것처럼 보인다. 움직임은 칸이 자라는
 * 애니메이션이 이미 만들고 있다.
 *
 * 등장이 끝날 때까지만 따라간다 — 그 뒤로도 붙잡으면 사용자가 위로 올린 스크롤을 되돌리게 된다.
 * 구성 변경에서 다시 붙잡지 않도록 직전 개수는 saveable 로 든다.
 */
@Composable
private fun FollowNewBlock(
    count: Int,
    scrollState: ScrollState,
) {
    var lastCount by rememberSaveable { mutableIntStateOf(count) }
    val millis = ManyakTheme.motion.elementEnterMillis
    LaunchedEffect(count) {
        val grew = count > lastCount
        lastCount = count
        if (!grew) return@LaunchedEffect
        withTimeoutOrNull(millis * FOLLOW_TIMEOUT_FACTOR) {
            snapshotFlow { scrollState.maxValue }.collect { max -> scrollState.scrollTo(max) }
        }
    }
}

/**
 * 순번 자리의 폭. 가장 넓은 숫자를 자릿수만큼 늘려 잡는다 — 글꼴이 숫자마다 폭이 다를 수 있어
 * "00" 하나로 재면 조합에 따라 모자란다. 상한을 넘겨 파싱된 목록에서는 세 자리가 나올 수 있으므로
 * 이 값은 최소 폭으로만 쓴다.
 */
@Composable
private fun rememberOrdinalWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = ManyakTheme.typography.labelSmall
    val density = LocalDensity.current
    return remember(measurer, style, density) {
        val widestDigit = (0..9).maxOf { digit -> measurer.measure(digit.toString(), style).size.width }
        with(density) { (widestDigit * ORDINAL_RESERVED_DIGITS).toDp() }
    }
}

/**
 * 블럭 목록이 창에서 차지할 수 있는 최대 비율. 창 높이는 키보드가 올라와도 줄지 않으므로, 키보드가
 * 뜬 상태에서 남는 자리를 기준으로 잡아야 목록이 화면을 다 먹지 않는다.
 */
private const val BLOCK_LIST_HEIGHT_FRACTION = 0.2f

private const val BLOCK_MAX_LINES = 4

/** 순번 자리에 미리 잡아 둘 자릿수. 블럭 상한이 두 자리라 그만큼만 잡는다. */
private const val ORDINAL_RESERVED_DIGITS = 2

/** 등장 시간의 몇 배까지 목록 끝을 따라갈지. 마지막 프레임의 높이 변화까지 담을 여유다. */
private const val FOLLOW_TIMEOUT_FACTOR = 2L
