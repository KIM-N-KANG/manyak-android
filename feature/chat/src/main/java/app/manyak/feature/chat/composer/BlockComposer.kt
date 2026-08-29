package app.manyak.feature.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
        blocks.forEach { block ->
            BlockInputRow(
                block = block,
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
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val isSituation = block.type == InputBlockType.SITUATION
    // 라벨과 입력 글자를 같은 색으로 둔다 — 쓰는 중에도 상황이 강조로 나갈 것임이 보여야 한다.
    val contentColor = if (isSituation) ManyakTheme.colors.textSubtlest else ManyakTheme.colors.text
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
                Text(
                    // 라벨 뒤 간격을 필드 안쪽 여백과 같게 둔다 — 고정 폭으로 잡으면 글자 폭에 따라
                    // 남는 자리가 달라져 좌우가 어긋난다.
                    modifier = Modifier.padding(end = ManyakTheme.spacing.controlHorizontal),
                    text = label,
                    style = ManyakTheme.typography.labelSmall,
                    color = contentColor,
                )
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
 * 블럭 목록이 창에서 차지할 수 있는 최대 비율. 창 높이는 키보드가 올라와도 줄지 않으므로, 키보드가
 * 뜬 상태에서 남는 자리를 기준으로 잡아야 목록이 화면을 다 먹지 않는다.
 */
private const val BLOCK_LIST_HEIGHT_FRACTION = 0.2f

private const val BLOCK_MAX_LINES = 4
