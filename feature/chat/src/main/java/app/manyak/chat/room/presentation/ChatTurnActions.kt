package app.manyak.chat.room.presentation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.manyak.chat.room.presentation.composer.ComposerTextField
import app.manyak.chat.room.presentation.composer.rememberSyncedTextFieldState
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.chat.R as ChatR
import app.manyak.designsystem.R as DesignsystemR

/**
 * 턴 자체의 재생성 조건. **마지막 턴인지는 그리는 쪽이 판단한다** — 목록 위치는 턴이 모르는 정보다.
 *
 * 식별자는 [ChatRoomTurn] 이 이미 갖고 있어 조건에 넣지 않는다. **엔딩에 도달한 턴은 서버가 409 로
 * 거절하므로** 버튼을 두면 누를 수 있는 실패가 된다.
 */
internal fun canRegenerate(turn: ChatRoomTurn): Boolean = turn.aiOutput.isNotBlank() && turn.reachedEnding == null

/**
 * AI 출력 아래 왼쪽의 버튼 행. 마지막 턴의 다시 생성, 그 옆에 포크 순이다. 둘 다 없으면 그리지 않는다.
 *
 * 좌측 12dp 는 본문 여백(16)보다 안쪽이다. 글리프가 아니라 눌리는 사각형을 본문 옆에 둔다. 아래 20dp 는
 * 뒤따르는 추천 영역의 위 여백 12dp 와 합쳐 32dp 를 만든다.
 */
@Composable
internal fun TurnActionRow(
    showsRegenerate: Boolean,
    showsFork: Boolean,
    forkEnabled: Boolean,
    onRegenerate: () -> Unit,
    onFork: () -> Unit,
) {
    if (!showsRegenerate && !showsFork) return
    Row(modifier = Modifier.padding(start = ManyakTheme.spacing.component, bottom = ManyakTheme.spacing.passage)) {
        if (showsRegenerate) {
            TurnActionButton(
                iconRes = DesignsystemR.drawable.ic_refresh,
                label = stringResource(ChatR.string.chat_room_regenerate),
                onClick = onRegenerate,
            )
        }
        if (showsFork) {
            TurnActionButton(
                iconRes = DesignsystemR.drawable.ic_split,
                label = stringResource(ChatR.string.chat_room_fork),
                enabled = forkEnabled,
                onClick = onFork,
            )
        }
    }
}

/** 사용자 입력 배경 안 오른쪽 아래의 입력 수정 버튼. AI 출력 아래 버튼과 같은 간격이다. */
@Composable
internal fun EditTurnButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TurnActionButton(
        modifier =
            modifier.padding(end = ManyakTheme.spacing.component, bottom = ManyakTheme.spacing.passage),
        iconRes = DesignsystemR.drawable.ic_pen,
        label = stringResource(ChatR.string.chat_room_edit),
        enabled = enabled,
        onClick = onClick,
    )
}

/** 턴에 붙는 32dp 고스트 아이콘 버튼. 잠기면 아이콘을 비활성 색으로 바꾼다. */
@Composable
private fun TurnActionButton(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .size(ManyakTheme.sizes.controlSmall)
                .clip(ManyakTheme.shapes.menuItem)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (enabled) ManyakTheme.colors.textSubtle else ManyakTheme.colors.textDisabled,
        )
    }
}

/**
 * 사용자 입력 자리에서 입력을 고치는 편집기. 블럭 입력과 추천 채우기 없이 일반 글로만 고친다.
 *
 * 열리면 입력칸에 초점을 주고 커서를 끝에 둔다. 구성 변경으로 다시 그려져도 고치던 입력은 ViewModel 에 있다.
 * 키보드가 오르내리면 편집기 전체를 화면 안으로 당긴다. 입력칸은 커서 줄만 따라와 아래 버튼이 키보드에 가린다.
 *
 * @param busy 다른 생성이 진행 중이다. 고치던 입력은 두고 보내기만 막는다.
 */
@Composable
internal fun ChatTurnEditor(
    editing: TurnEditing,
    original: String,
    busy: Boolean,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    // 키보드가 움직이는 프레임마다 다시 시작해 마지막 높이에서 한 번 맞는다.
    LaunchedEffect(imeBottom) { if (imeBottom > 0) bringIntoView.bringIntoView() }
    Column(
        modifier =
            Modifier
                .bringIntoViewRequester(bringIntoView)
                .fillMaxWidth()
                .background(ManyakTheme.colors.backgroundNeutral)
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.passage),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        ComposerTextField(
            modifier = Modifier.focusRequester(focusRequester),
            state = rememberSyncedTextFieldState(editing.draft, onDraftChange),
            placeholder = "",
            contentDescription = stringResource(ChatR.string.chat_room_edit_field),
        )
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            ChatSmallButton(
                text = stringResource(ChatR.string.chat_room_edit_cancel),
                onClick = onCancel,
                containerColor = Color.Transparent,
                contentColor = ManyakTheme.colors.text,
            )
            ChatSmallButton(
                text = stringResource(ChatR.string.chat_room_edit_submit),
                onClick = onSubmit,
                enabled = !busy && canSubmitEdit(original, editing.draft),
            )
        }
    }
}
