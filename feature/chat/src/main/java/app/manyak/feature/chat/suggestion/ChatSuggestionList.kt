package app.manyak.feature.chat.suggestion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import app.manyak.core.ui.R
import app.manyak.core.ui.component.SkeletonPlaceholder
import app.manyak.core.ui.component.rememberSkeletonPulseAlpha
import app.manyak.core.ui.text.storyAnnotatedString
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 추천 입력·선택지 영역.
 *
 * **컴포저 위가 아니라 메시지 목록 안, 마지막 턴 아래에 있다** — 프롤로그가 길면 함께 위로 밀려
 * 올라간다.
 *
 * 목록이 비어 있으면 생성 진행 상태를 그리고, 그 상태는 **대상 턴이 마지막 턴일 때만** 쓴다.
 * 늦게 끝난 요청이 이미 새 턴으로 넘어간 화면을 덮지 않게 하는 자리다.
 */
@Composable
internal fun ChatSuggestionArea(
    suggestions: ChatSuggestions,
    progress: ChoicesProgress?,
    lastTurnId: Long?,
    choicesEnabled: Boolean,
    showsHint: Boolean,
    onSend: (Int) -> Unit,
    onFill: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = suggestions.items
    val animated = revealsOnce(revealKeyOf(suggestions, progress, lastTurnId, choicesEnabled))
    when {
        suggestions.hasCandidate ->
            SuggestionColumn(modifier = modifier) {
                // 힌트도 목록의 한 항목처럼 차례에 낀다 — 웹과 같은 순서다.
                val hintCount = if (showsHint) 1 else 0
                if (showsHint) SuggestionHint(modifier = Modifier.revealed(0, animated))
                val candidates = remember(items) { items.withIndex().filter { it.value.isNotBlank() } }
                candidates.forEachIndexed { order, (position, text) ->
                    SuggestionRow(
                        modifier = Modifier.revealed(hintCount + order, animated),
                        text = text,
                        onSend = { onSend(position) },
                        onFill = { onFill(position) },
                    )
                }
            }

        progress?.failed == true && showsChoicesProgress(progress, lastTurnId, choicesEnabled) ->
            SuggestionColumn(modifier = modifier) {
                ChoicesFailure(onRetry = onRetry, modifier = Modifier.revealed(0, animated))
            }

        showsChoicesProgress(progress, lastTurnId, choicesEnabled) -> {
            // 진행 상태를 글자로 두지 않으므로 영역 설명으로만 읽힌다.
            val loadingLabel = stringResource(R.string.chat_room_choices_loading)
            SuggestionColumn(modifier = modifier.semantics { contentDescription = loadingLabel }) {
                ChoicesSkeleton(animated = animated)
            }
        }

        else -> Unit
    }
}

/**
 * 그릴 것이 있는지. **목록 항목 수를 세는 쪽이 이 판정을 그대로 써야 한다** — 갈리면 스크롤 앵커가
 * 한 칸씩 어긋난다.
 */
internal fun hasSuggestionArea(
    suggestions: ChatSuggestions,
    progress: ChoicesProgress?,
    lastTurnId: Long?,
    choicesEnabled: Boolean,
): Boolean = suggestions.hasCandidate || showsChoicesProgress(progress, lastTurnId, choicesEnabled)

/** 진행 상태는 대상 턴이 마지막 턴일 때만 쓴다. 끈 상태에서는 만들지도 그리지도 않는다. */
internal fun showsChoicesProgress(
    progress: ChoicesProgress?,
    lastTurnId: Long?,
    choicesEnabled: Boolean,
): Boolean = choicesEnabled && progress != null && progress.turnId == lastTurnId

/** 항목은 모두 오른쪽에 붙는다 — 이야기 본문과 달리 사용자가 고르는 것이라 입력 쪽에 가깝다. */
@Composable
private fun SuggestionColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = ManyakTheme.spacing.gutter,
                    end = ManyakTheme.spacing.gutter,
                    top = ManyakTheme.spacing.passage,
                    bottom = ManyakTheme.spacing.passage,
                ),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        horizontalAlignment = Alignment.End,
        content = content,
    )
}

/**
 * 추천 하나. **누르는 곳에 따라 동작이 갈린다** — 본문을 누르면 곧바로 전송하고, 채우기 버튼은
 * 입력창에 넣기만 한다.
 */
@Composable
private fun SuggestionRow(
    text: String,
    onSend: () -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fillLabel = stringResource(R.string.chat_room_suggestion_fill)
    // 본문 폭은 채우기 버튼을 뺀 나머지가 아니라 **줄 전체 폭**의 비율이다. Row 안에서 fillMaxWidth 로
    // 잡으면 앞서 측정한 버튼과 간격이 빠진 뒤 계산돼 좁아진다.
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        val suggestionWidth = maxWidth * SUGGESTION_WIDTH_FRACTION
        Row(
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(ManyakTheme.sizes.controlSmall)
                        .clip(ManyakTheme.shapes.menuItem)
                        .clickable(role = Role.Button, onClickLabel = fillLabel, onClick = onFill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                    painter = painterResource(R.drawable.ic_pen_circle),
                    contentDescription = fillLabel,
                    tint = ManyakTheme.colors.textSubtle,
                )
            }
            SuggestionButton(text = text, onClick = onSend, modifier = Modifier.width(suggestionWidth))
        }
    }
}

/** 추천 본문. 이야기와 같은 강조 규칙으로 그려 눌렀을 때 나갈 문장이 그대로 보인다. */
@Composable
private fun SuggestionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emphasisColor = ManyakTheme.colors.textSubtlest
    val annotated = remember(text, emphasisColor) { storyAnnotatedString(text, emphasisColor) }
    Box(
        modifier =
            modifier
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .background(ManyakTheme.colors.backgroundNeutral)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = annotated,
            style = ManyakTheme.typography.bodyReadingSmall,
            color = ManyakTheme.colors.text,
        )
    }
}

/** 첫 추천 목록 위 1회성 힌트. 두 번째 줄에는 채우기 버튼 아이콘이 그대로 들어간다. */
@Composable
private fun SuggestionHint(modifier: Modifier = Modifier) {
    val template = stringResource(R.string.chat_room_suggestion_hint_action)
    val annotated =
        remember(template) {
            buildAnnotatedString {
                val index = template.indexOf(HINT_ICON_PLACEHOLDER)
                if (index < 0) {
                    append(template)
                } else {
                    append(template.substring(0, index))
                    appendInlineContent(HINT_ICON_ID, HINT_ICON_PLACEHOLDER)
                    append(template.substring(index + HINT_ICON_PLACEHOLDER.length))
                }
            }
        }
    val inlineContent =
        mapOf(
            HINT_ICON_ID to
                InlineTextContent(
                    Placeholder(
                        width = HintIconSize,
                        height = HintIconSize,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(R.drawable.ic_pen_circle),
                        contentDescription = null,
                        tint = ManyakTheme.colors.textSubtle,
                    )
                },
        )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.chat_room_suggestion_hint_title),
            style = ManyakTheme.typography.labelSmall,
            color = ManyakTheme.colors.textSubtle,
            textAlign = TextAlign.End,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = annotated,
            inlineContent = inlineContent,
            style = ManyakTheme.typography.labelSmall,
            color = ManyakTheme.colors.textSubtle,
            textAlign = TextAlign.End,
        )
    }
}

/** 만드는 중. 선택지와 같은 폭·높이로 둬 결과가 도착해도 자리가 움직이지 않는다. */
@Composable
private fun ColumnScope.ChoicesSkeleton(animated: Boolean) {
    val alpha = rememberSkeletonPulseAlpha()
    repeat(SKELETON_COUNT) { index ->
        SkeletonPlaceholder(
            modifier =
                Modifier
                    .revealed(index, animated)
                    .fillMaxWidth(SUGGESTION_WIDTH_FRACTION)
                    .height(ManyakTheme.sizes.input),
            alpha = alpha,
            shape = ManyakTheme.shapes.control,
        )
    }
}

/** 턴은 이미 진행됐고 선택지만 없는 상태라 안내와 재시도만 둔다. 안내와 버튼은 함께 나타난다. */
@Composable
private fun ChoicesFailure(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.common_retry)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = stringResource(R.string.chat_room_choices_error),
            style = ManyakTheme.typography.bodySmall,
            color = ManyakTheme.colors.textSubtle,
        )
        Box(
            modifier =
                Modifier
                    .heightIn(min = ManyakTheme.sizes.input)
                    .clip(ManyakTheme.shapes.control)
                    .background(ManyakTheme.colors.backgroundNeutral)
                    .clickable(role = Role.Button, onClickLabel = label, onClick = onRetry)
                    .padding(horizontal = ManyakTheme.spacing.controlHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = ManyakTheme.typography.labelLarge, color = ManyakTheme.colors.text)
        }
    }
}

/** 선택지 버튼이 차지하는 폭. 채우기 버튼을 포함한 줄 전체 폭 기준이다. */
private const val SUGGESTION_WIDTH_FRACTION = 0.8f

private const val SKELETON_COUNT = 3

private const val HINT_ICON_PLACEHOLDER = "%1\$s"

private const val HINT_ICON_ID = "fill-icon"

private val HintIconSize = 14.sp

@Preview(showBackground = true, name = "추천 입력 · 힌트")
@Composable
private fun ChatSuggestionAreaPreview() {
    ManyakTheme(darkTheme = false) {
        ChatSuggestionArea(
            suggestions = ChatSuggestions(items = listOf("*문이 삐걱인다* 누구세요?", "조용히 뒤로 물러난다")),
            progress = null,
            lastTurnId = null,
            choicesEnabled = true,
            showsHint = true,
            onSend = {},
            onFill = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, name = "추천 입력 · 생성 실패")
@Composable
private fun ChatChoicesFailurePreview() {
    ManyakTheme(darkTheme = false) {
        ChatSuggestionArea(
            suggestions = ChatSuggestions(),
            progress = ChoicesProgress(turnId = 1, failed = true),
            lastTurnId = 1,
            choicesEnabled = true,
            showsHint = false,
            onSend = {},
            onFill = {},
            onRetry = {},
        )
    }
}
