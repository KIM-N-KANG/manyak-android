package app.manyak.story.detail.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.ScrollEdgeFade
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.story.R as StoryR

/**
 * 본문 위에 떠 있는 하단 CTA. 버튼 위쪽 페이드가 본문이 버튼 뒤로 흘러 들어가는 경계를 만든다.
 *
 * 좋아요는 채팅 시작 왼쪽에 같은 높이의 아이콘 버튼으로 붙는다. [canLike] 가 거짓이면 — 내가
 * 만든 스토리다 — 자리를 비우지 않고 아예 그리지 않아 시작 버튼이 폭을 모두 쓴다.
 */
@Composable
internal fun StartChatCta(
    isStarting: Boolean,
    failed: Boolean,
    canLike: Boolean,
    isLiked: Boolean,
    isTogglingLike: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = ManyakTheme.colors.surface

    Column(modifier = modifier.fillMaxWidth()) {
        ScrollEdgeFade()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(surface)
                    // 배경은 화면 끝까지 깔고 내용만 시스템 바를 피한다 — 본문이 바 뒤로 비치지 않게.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    ).padding(horizontal = ManyakTheme.spacing.gutter)
                    // 위 여백은 실패 문구가 있을 때만 둔다 — 퍼널 푸터와 같은 규칙이다.
                    .padding(
                        top = if (failed) ManyakTheme.spacing.compact else 0.dp,
                        bottom = ManyakTheme.spacing.gutter,
                    ),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            if (failed) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(StoryR.string.story_detail_start_chat_failed),
                    style = ManyakTheme.typography.bodySmall,
                    color = ManyakTheme.colors.textDanger,
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
            ) {
                if (canLike) {
                    LikeButton(isLiked = isLiked, enabled = !isTogglingLike, onClick = onToggleLike)
                }
                StartChatButton(
                    modifier = Modifier.weight(1f),
                    isStarting = isStarting,
                    onClick = onClick,
                )
            }
        }
    }
}

/** 주 동작. 진행 중에도 라벨 자리를 유지해 버튼 크기와 접근성 이름이 그대로 남는다. */
@Composable
private fun StartChatButton(
    isStarting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.heightIn(min = ManyakTheme.sizes.control),
        onClick = onClick,
        enabled = !isStarting,
        shape = ManyakTheme.shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.brand,
                contentColor = ManyakTheme.colors.textInverse,
                disabledContainerColor = ManyakTheme.colors.brand,
                disabledContentColor = ManyakTheme.colors.textInverse,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                modifier = Modifier.alpha(if (isStarting) 0f else 1f),
                text = stringResource(StoryR.string.story_detail_start_chat),
                style = ManyakTheme.typography.labelLarge,
            )
            if (isStarting) {
                ManyakProgressIndicator(
                    modifier = Modifier.size(ManyakTheme.sizes.icon),
                    color = ManyakTheme.colors.textInverse,
                )
            }
        }
    }
}

/**
 * 좋아요 토글. 바탕 없이 하트의 모양과 색만으로 상태를 말한다 — 채운 붉은 하트가 눌린 상태다.
 * 요청 중에는 잠가 중복 전송을 막는다.
 */
@Composable
private fun LikeButton(
    isLiked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ManyakIconButton(
        iconRes = if (isLiked) DesignsystemR.drawable.ic_heart_filled else DesignsystemR.drawable.ic_heart_outline,
        contentDescription =
            stringResource(if (isLiked) StoryR.string.story_detail_unlike else StoryR.string.story_detail_like),
        onClick = onClick,
        // 옆 버튼과 같은 높이의 정사각이고, 리플도 같은 곡률로 돈다 — 원으로 돌면 짝이 어긋나 보인다.
        size = ManyakTheme.sizes.control,
        shape = ManyakTheme.shapes.control,
        tint = if (isLiked) ManyakTheme.colors.textDanger else ManyakTheme.colors.text,
        enabled = enabled,
    )
}
