package app.manyak.chat.room.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.manyak.chat.list.presentation.label
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

/** AI 출력 아래 왼쪽에 붙는 다시 생성 버튼. */
@Composable
internal fun RegenerateButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(ChatR.string.chat_room_regenerate)
    Box(
        modifier =
            modifier
                // 좌측 12dp 는 본문 여백(16)보다 안쪽이다 — 글리프가 아니라 눌리는 사각형을 본문 옆에 둔다.
                // 아래 20dp 는 뒤따르는 추천 영역의 위 여백 12dp 와 합쳐 32dp 를 만든다.
                .padding(start = ManyakTheme.spacing.component, bottom = ManyakTheme.spacing.passage)
                .size(ManyakTheme.sizes.controlSmall)
                .clip(ManyakTheme.shapes.menuItem)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
            painter = painterResource(DesignsystemR.drawable.ic_refresh),
            contentDescription = label,
            tint = ManyakTheme.colors.textSubtle,
        )
    }
}
