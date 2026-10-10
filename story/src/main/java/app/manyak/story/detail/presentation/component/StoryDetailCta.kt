package app.manyak.story.detail.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
@Suppress("LongParameterList")
internal fun StartChatCta(
    isStarting: Boolean,
    summary: String,
    canLike: Boolean,
    isLiked: Boolean,
    isTogglingLike: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ManyakTheme.colors.surface,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ScrollEdgeFade(surface = backgroundColor)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    // 배경은 화면 끝까지 깔고 내용만 시스템 바를 피한다 — 본문이 바 뒤로 비치지 않게.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    ).padding(horizontal = ManyakTheme.spacing.gutter)
                    .padding(bottom = ManyakTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
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
                    summary = summary,
                    onClick = onClick,
                )
            }
        }
    }
}

/**
 * 주 동작. 진행 중에도 라벨 자리를 유지해 버튼 크기와 접근성 이름이 그대로 남는다.
 *
 * 라벨 아래 [summary] 한 줄로 무엇으로 시작하는지 보인다. 두 줄이 옆 하트 버튼과 같은 높이 안에 들도록
 * 세로 안쪽 여백을 걷는다.
 */
@Composable
private fun StartChatButton(
    isStarting: Boolean,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.heightIn(min = ManyakTheme.sizes.control),
        onClick = onClick,
        enabled = !isStarting,
        shape = ManyakTheme.shapes.control,
        contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.gutter),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.brand,
                contentColor = ManyakTheme.colors.textInverse,
                disabledContainerColor = ManyakTheme.colors.brand,
                disabledContentColor = ManyakTheme.colors.textInverse,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.alpha(if (isStarting) 0f else 1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(StoryR.string.story_detail_start_chat),
                    style = ManyakTheme.typography.labelLarge,
                )
                Text(
                    text = summary,
                    style = ManyakTheme.typography.labelTiny,
                    // 라벨보다 한 단계 옅게 둬 버튼 이름이 먼저 읽히게 한다(웹과 같은 80%).
                    color = ManyakTheme.colors.textInverse.copy(alpha = SUMMARY_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

/**
 * 채팅 시작 버튼 아래 요약. 페르소나 이름은 5자, 시작 상황 이름은 10자까지 보이고 넘치면 말줄임표를 붙인다.
 * 시작 상황이 없으면 페르소나 부분만 보인다.
 *
 * @param personaName 고른 페르소나. null 이면 기본 주인공이다.
 */
@Composable
internal fun chatStartSummary(
    personaName: String?,
    startSettingName: String?,
): String {
    val persona =
        truncateSummaryPart(
            personaName ?: stringResource(StoryR.string.story_detail_persona_default),
            SUMMARY_PERSONA_MAX_LENGTH,
        )
    val setting = startSettingName?.let { truncateSummaryPart(it, SUMMARY_SETTING_MAX_LENGTH) }.orEmpty()
    return if (setting.isEmpty()) {
        stringResource(StoryR.string.story_detail_start_chat_summary_persona_only, persona)
    } else {
        stringResource(StoryR.string.story_detail_start_chat_summary, persona, setting)
    }
}

/** 앞뒤 공백을 뺀 글을 [maxLength] 글자까지 남긴다. 이모지가 반으로 갈리지 않게 코드 포인트로 센다. */
internal fun truncateSummaryPart(
    text: String,
    maxLength: Int,
): String {
    val trimmed = text.trim()
    if (trimmed.codePointCount(0, trimmed.length) <= maxLength) return trimmed
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, maxLength)) + ELLIPSIS
}

private const val SUMMARY_PERSONA_MAX_LENGTH = 5
private const val SUMMARY_SETTING_MAX_LENGTH = 10
private const val SUMMARY_ALPHA = 0.8f
private const val ELLIPSIS = "…"
