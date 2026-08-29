package app.manyak.feature.chat

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
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

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
    val label = stringResource(R.string.chat_room_regenerate)
    Box(
        modifier =
            modifier
                // 48dp 타깃 안에서 20dp 글리프는 14dp 안쪽에 놓인다. 2dp 만 밀면 본문 좌측 여백(16)과 맞고,
                // 아래 6dp 는 글리프 아래 여백까지 더해 다음 덩이와의 간격을 본문 간격(20)으로 맞춘다.
                .padding(start = ManyakTheme.spacing.hairline, bottom = ManyakTheme.spacing.dense)
                .size(ManyakTheme.sizes.control)
                .clip(ManyakTheme.shapes.pill)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.icon),
            painter = painterResource(R.drawable.ic_refresh),
            contentDescription = label,
            tint = ManyakTheme.colors.textSubtle,
        )
    }
}
