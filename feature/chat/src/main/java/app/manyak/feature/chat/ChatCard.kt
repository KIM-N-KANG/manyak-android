package app.manyak.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.common.entity.chat.ChatSummary
import app.manyak.core.ui.R
import app.manyak.designsystem.component.ManyakMoreButton
import app.manyak.designsystem.component.MetaChip
import app.manyak.designsystem.component.StoryCover
import app.manyak.designsystem.component.moreButtonTitleAlignment
import app.manyak.designsystem.theme.ManyakTheme
import java.text.NumberFormat
import app.manyak.designsystem.R as DesignsystemR

/**
 * 채팅 목록 카드. 스토리 목록의 2열 그리드와 달리 세로 1열의 가로 행인데, 채팅을 가려내는 단서가
 * 표지가 아니라 "어디까지 읽었는지"이기 때문이다 — 같은 스토리로 채팅을 여럿 만들 수 있어 표지만으로는
 * 구분되지 않는다.
 *
 * 카드 전체가 채팅방 진입이고, 제목 줄 오른쪽 더보기 버튼과 길게 누르기가 같은 옵션 다이얼로그를
 * 연다 — 내 스토리 카드와 같은 문법이다.
 *
 * 제목·미리보기가 **1줄 고정**이라 텍스트 길이와 무관하게 카드 높이가 같다. 표지가 3:4 라 폭을
 * 정하면 높이가 따라오고, 세로 여백까지 더해 터치 타깃 48dp 는 자연히 넘는다.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ChatCard(
    chat: ChatSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    ChatCardContent(
        chat = chat,
        compact = false,
        onOptionsClick = onLongClick,
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.chat_list_card_action),
                    onLongClickLabel = stringResource(R.string.chat_list_card_options),
                    onClick = onClick,
                    // 길게 누르기는 화면에 드러나지 않는 제스처라 다이얼로그가 열리는 순간 손으로도 알린다.
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                ).padding(
                    horizontal = ManyakTheme.spacing.gutter,
                    vertical = ManyakTheme.spacing.compact,
                ),
    )
}

/**
 * 옵션 다이얼로그 상단에 놓는 카드 미리보기. 목록 카드와 같은 정보를 한 단계씩 작게 그려, 어느 채팅의
 * 옵션인지 다이얼로그 안에서 확인하게 한다. 눌리지 않는다.
 */
@Composable
internal fun ChatCardPreview(
    chat: ChatSummary,
    modifier: Modifier = Modifier,
) {
    ChatCardContent(chat = chat, compact = true, onOptionsClick = null, modifier = modifier.fillMaxWidth())
}

/**
 * @param compact 다이얼로그 미리보기용. 표지·서체·간격이 목록 카드보다 한 단계 작다.
 */
@Composable
private fun ChatCardContent(
    chat: ChatSummary,
    compact: Boolean,
    onOptionsClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement =
            Arrangement.spacedBy(if (compact) ManyakTheme.spacing.compact else ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryCover(
            thumbnailUrl = chat.thumbnailUrl,
            // 표지는 카드의 텍스트 줄이 이미 말하는 것을 되풀이하므로 낭독 대상이 아니다.
            modifier = Modifier.width(if (compact) CompactCoverWidth else CoverWidth).clearAndSetSemantics { },
            shape = if (compact) ManyakTheme.shapes.thumbnailSmall else ManyakTheme.shapes.thumbnail,
            showBorder = true,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(if (compact) ManyakTheme.spacing.hairline else ManyakTheme.spacing.inline),
        ) {
            val titleStyle =
                if (compact) ManyakTheme.typography.bodyMediumStrong else ManyakTheme.typography.bodyLargeStrong
            Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                StoryTitle(
                    title = chat.storyTitle,
                    style = titleStyle,
                    modifier = Modifier.weight(1f).alignBy(FirstBaseline),
                )
                // 더보기 버튼은 목록 카드에만 — 미리보기는 다이얼로그 안이라 열 것이 없다.
                if (onOptionsClick != null) {
                    ManyakMoreButton(
                        contentDescription = stringResource(R.string.chat_list_card_options),
                        onClick = onOptionsClick,
                        modifier = moreButtonTitleAlignment(titleStyle),
                    )
                }
            }
            LastStoryPreview(preview = chat.lastStoryPreview, compact = compact)
            ChatMeta(turnCount = chat.turnCount, updatedAtEpochMillis = chat.updatedAtEpochMillis, compact = compact)
        }
    }
}

/**
 * 스토리 제목. 참조 스토리가 삭제되면 빈 문자열이 정상 값이라 그 자리에 상태를 적는다 —
 * 스토리는 사라져도 채팅은 계속 열 수 있고, 줄을 비워 두면 무엇의 채팅인지 알 수 없다.
 */
@Composable
private fun StoryTitle(
    title: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val isDeleted = title.isBlank()
    Text(
        modifier = modifier,
        text = if (isDeleted) stringResource(R.string.chat_list_deleted_story) else title,
        style = style,
        color = if (isDeleted) ManyakTheme.colors.textSubtlest else ManyakTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 마지막 장면 미리보기. 서버는 마지막 AI 출력을 자르지 않고 보내므로 절단은 여기서 한다.
 * 완료 턴이 없는 채팅(생성 직후)은 빈 문자열이 정상 값이라 안내 문구가 그 자리를 맡는다.
 */
@Composable
private fun LastStoryPreview(
    preview: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val isEmpty = preview.isBlank()
    Text(
        modifier = modifier,
        text = if (isEmpty) stringResource(R.string.chat_list_preview_empty) else preview,
        style = if (compact) ManyakTheme.typography.bodySmall else ManyakTheme.typography.bodyMedium,
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
    compact: Boolean,
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
            iconRes = DesignsystemR.drawable.ic_comment_dots,
            text = formattedTurnCount,
            description = stringResource(R.string.chat_list_turn_count_description, formattedTurnCount),
            compact = compact,
        )
        relativeTime?.let { time ->
            val label = time.label()
            MetaChip(
                iconRes = DesignsystemR.drawable.ic_calendar,
                text = label,
                description = stringResource(R.string.chat_list_updated_at_description, label),
                compact = compact,
            )
        }
    }
}

/** 카드에서 표지가 차지하는 폭. 3:4 라 높이는 약 69dp 가 된다. */
internal val CoverWidth: Dp = 52.dp

/** 다이얼로그 미리보기의 표지 폭. 목록보다 한 단계 작아 3:4 높이가 약 53dp 다. */
private val CompactCoverWidth: Dp = 40.dp
