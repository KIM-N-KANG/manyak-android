package app.manyak.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.core.domain.chat.ChatSummary
import app.manyak.core.ui.R
import app.manyak.core.ui.component.MetaChip
import app.manyak.core.ui.component.StoryCover
import app.manyak.core.ui.theme.ManyakTheme
import java.text.NumberFormat

/**
 * 채팅 목록 카드. 스토리 목록의 2열 그리드와 달리 세로 1열의 가로 행인데, 채팅을 가려내는 단서가
 * 표지가 아니라 "어디까지 읽었는지"이기 때문이다 — 같은 스토리로 채팅을 여럿 만들 수 있어 표지만으로는
 * 구분되지 않는다.
 *
 * 카드 전체가 채팅방 진입이고 옵션 메뉴는 두지 않는다. 삭제 진입점은 채팅방 옵션 메뉴 하나다.
 *
 * 제목·미리보기가 **1줄 고정**이라 텍스트 길이와 무관하게 카드 높이가 같다. 표지가 3:4 라 폭을
 * 정하면 높이가 따라오고, 세로 여백까지 더해 터치 타깃 48dp 는 자연히 넘는다.
 */
@Composable
internal fun ChatCard(
    chat: ChatSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.chat_list_card_action),
                    onClick = onClick,
                ).padding(
                    horizontal = ManyakTheme.spacing.gutter,
                    vertical = ManyakTheme.spacing.compact,
                ),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryCover(
            thumbnailUrl = chat.thumbnailUrl,
            // 표지는 카드의 텍스트 줄이 이미 말하는 것을 되풀이하므로 낭독 대상이 아니다.
            modifier = Modifier.width(CoverWidth).clearAndSetSemantics { },
            showBorder = true,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            Text(
                text = chat.storyTitle,
                style = ManyakTheme.typography.bodyLargeStrong,
                color = ManyakTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LastStoryPreview(preview = chat.lastStoryPreview)
            ChatMeta(turnCount = chat.turnCount, updatedAtEpochMillis = chat.updatedAtEpochMillis)
        }
    }
}

/**
 * 마지막 장면 미리보기. 서버는 마지막 AI 출력을 자르지 않고 보내므로 절단은 여기서 한다.
 * 완료 턴이 없는 채팅(생성 직후)은 빈 문자열이 정상 값이라 안내 문구가 그 자리를 맡는다.
 */
@Composable
private fun LastStoryPreview(
    preview: String,
    modifier: Modifier = Modifier,
) {
    val isEmpty = preview.isBlank()
    Text(
        modifier = modifier,
        text = if (isEmpty) stringResource(R.string.chat_list_preview_empty) else preview,
        style = ManyakTheme.typography.bodyMedium,
        color = if (isEmpty) ManyakTheme.colors.textSubtlest else ManyakTheme.colors.textSubtle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 턴 수와 마지막 활동 시각. 시각을 읽을 수 없으면 그 칩만 빠지고 턴 수는 남는다. */
@Composable
private fun ChatMeta(
    turnCount: Long,
    updatedAtEpochMillis: Long?,
    modifier: Modifier = Modifier,
) {
    val formattedTurnCount = remember(turnCount) { NumberFormat.getIntegerInstance().format(turnCount) }
    // 화면에 떠 있는 동안 다시 세지 않는다 — 1분마다 도는 타이머를 두면 목록 전체가 주기적으로 다시 그려진다.
    val relativeTime =
        remember(updatedAtEpochMillis) {
            updatedAtEpochMillis?.let { millis -> relativeTimeOf(millis, System.currentTimeMillis()) }
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetaChip(
            iconRes = R.drawable.ic_comment_dots,
            text = formattedTurnCount,
            description = stringResource(R.string.chat_list_turn_count_description, formattedTurnCount),
        )
        relativeTime?.let { time ->
            val label = time.label()
            MetaChip(
                iconRes = R.drawable.ic_calendar,
                text = label,
                description = stringResource(R.string.chat_list_updated_at_description, label),
            )
        }
    }
}

/** 카드에서 표지가 차지하는 폭. 3:4 라 높이는 약 69dp 가 된다. */
internal val CoverWidth: Dp = 52.dp
