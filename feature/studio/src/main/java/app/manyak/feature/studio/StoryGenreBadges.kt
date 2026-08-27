package app.manyak.feature.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 카드의 장르 뱃지 한 줄. 폭에 들어가는 만큼만 보이고 나머지는 `+N` 뱃지로 접는다.
 *
 * 웹과 같은 규칙이다 — 몇 개까지라는 상수가 아니라 실제 폭을 재서 정하고, 접을 때는 `+N` 자리를
 * 남길 수 있는 지점까지만 센다. 뱃지가 최소 하나는 보이므로 아주 좁은 폭에서는 그 하나가 잘린다.
 * 낭독기에는 접힌 것까지 장르 전체를 한 문장으로 읽힌다.
 */
@Composable
internal fun StoryGenreBadges(
    genres: List<String>,
    modifier: Modifier = Modifier,
) {
    val description = genres.joinToString(separator = ", ")
    // 측정 스코프는 composition 이 아니라 테마 토큰을 읽을 수 없으므로 여기서 값으로 붙잡는다.
    val badgeGap = ManyakTheme.spacing.inline

    SubcomposeLayout(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) { constraints ->
        val gap = badgeGap.roundToPx()
        val badgeConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val badges =
            subcompose(GenreBadgeSlot.Genres) {
                genres.forEach { genre -> GenreBadge(text = genre) }
            }.map { measurable -> measurable.measure(badgeConstraints) }

        val totalWidth = badges.sumOf { badge -> badge.width } + gap * (badges.size - 1)
        val visibleCount: Int
        val overflowBadge: Placeable?
        if (totalWidth <= constraints.maxWidth) {
            visibleCount = badges.size
            overflowBadge = null
        } else {
            // 접힘 개수가 가장 클 때의 +N 폭으로 자리를 재면, 실제 +N 이 더 좁을 수는 있어도 넘칠 일은 없다.
            val widestOverflow =
                subcompose(GenreBadgeSlot.OverflowProbe) {
                    OverflowBadge(hiddenCount = genres.size - 1)
                }.first().measure(badgeConstraints)
            visibleCount =
                visibleBadgeCount(
                    widths = badges.map { badge -> badge.width },
                    gap = gap,
                    overflowWidth = widestOverflow.width,
                    maxWidth = constraints.maxWidth,
                )
            overflowBadge =
                subcompose(GenreBadgeSlot.Overflow) {
                    OverflowBadge(hiddenCount = genres.size - visibleCount)
                }.first().measure(badgeConstraints)
        }

        val placeables = badges.take(visibleCount) + listOfNotNull(overflowBadge)
        val contentWidth = placeables.sumOf { placeable -> placeable.width } + gap * (placeables.size - 1)
        val height = constraints.constrainHeight(placeables.maxOf { placeable -> placeable.height })
        layout(constraints.constrainWidth(contentWidth), height) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x = x, y = (height - placeable.height) / 2)
                x += placeable.width + gap
            }
        }
    }
}

/**
 * 폭 [maxWidth] 에 `+N` 뱃지 자리를 남기면서 앞에서부터 몇 개를 보일 수 있는지 센다.
 * 하나도 못 들어가는 폭에서도 최소 1개는 보인다.
 */
internal fun visibleBadgeCount(
    widths: List<Int>,
    gap: Int,
    overflowWidth: Int,
    maxWidth: Int,
): Int {
    var used = 0
    var count = 0
    for ((index, width) in widths.withIndex()) {
        val candidate = used + width + if (index > 0) gap else 0
        if (candidate + gap + overflowWidth > maxWidth) break
        used = candidate
        count += 1
    }
    return maxOf(count, 1)
}

@Composable
private fun GenreBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier =
            modifier
                .clip(ManyakTheme.shapes.pill)
                .background(ManyakTheme.colors.backgroundNeutral)
                .padding(
                    horizontal = ManyakTheme.spacing.compact,
                    vertical = ManyakTheme.spacing.hairline,
                ),
        text = text,
        style = ManyakTheme.typography.labelSmall,
        color = ManyakTheme.colors.textSubtle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun OverflowBadge(
    hiddenCount: Int,
    modifier: Modifier = Modifier,
) {
    GenreBadge(text = stringResource(R.string.studio_genre_overflow, hiddenCount), modifier = modifier)
}

private enum class GenreBadgeSlot { Genres, OverflowProbe, Overflow }
