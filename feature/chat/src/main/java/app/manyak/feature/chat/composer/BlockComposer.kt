package app.manyak.feature.chat.composer

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * BLOCK_LIST_HEIGHT_FRACTION
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = ManyakTheme.spacing.gutter,
                    end = ManyakTheme.spacing.gutter,
                    bottom = ManyakTheme.spacing.compact,
                ).heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        val ordinals = remember(blocks) { blocks.typeOrdinals() }
        val ordinalWidth = rememberOrdinalWidth()
        blocks.forEach { block ->
            BlockInputRow(
                block = block,
                ordinal = ordinals[block.id] ?: 1,
                ordinalWidth = ordinalWidth,
                enabled = enabled,
                onValueChange = { value -> onValueChange(block.id, value) },
                onRemove = {
                    if (block.value.isBlank()) onRemoveBlock(block.id) else pendingRemoveId = block.id
                },
            )
        }
    }

    val removeId = pendingRemoveId
    if (removeId != null) {
        RemoveBlockDialog(
            onDismiss = { pendingRemoveId = null },
            onConfirm = {
                pendingRemoveId = null
                onRemoveBlock(removeId)
            },
        )
    }
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
    val contentColor = if (isSituation) ManyakTheme.colors.textSubtlest else ManyakTheme.colors.text
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
