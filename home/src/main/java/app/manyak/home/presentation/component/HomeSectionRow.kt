package app.manyak.home.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.home.R as HomeR

/**
 * 홈 섹션 하나. 이름 아래 가로로 넘기는 카드 줄을 둔다. 카드 두 장이 한 화면에 들고 셋째 카드는 표지
 * 가장자리만 [PeekWidth] 만큼 보여 옆으로 넘길 수 있음을 알린다. 더 보기는 두지 않는다.
 */
@Composable
internal fun HomeSectionRow(
    name: String,
    modifier: Modifier = Modifier,
    cards: LazyListScope.(cardWidth: Dp) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component)) {
        Text(
            modifier =
                Modifier
                    .padding(horizontal = ManyakTheme.spacing.gutter)
                    .semantics { heading() },
            text = name,
            style = ManyakTheme.typography.titleMediumStrong,
            color = ManyakTheme.colors.text,
        )
        SectionCards(cards = cards)
    }
}

@Composable
private fun SectionCards(
    userScrollEnabled: Boolean = true,
    cards: LazyListScope.(cardWidth: Dp) -> Unit,
) {
    val gutter = ManyakTheme.spacing.gutter
    val cardGap = ManyakTheme.spacing.compact
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 둘째 카드 뒤 간격까지 지나 셋째 카드가 화면 오른쪽 끝에서 PeekWidth 만큼만 보이도록 두 장의 폭을 정한다.
        val cardWidth = (maxWidth - gutter * 2 - PeekWidth) / 2
        LazyRow(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(cardGap),
            userScrollEnabled = userScrollEnabled,
        ) {
            cards(cardWidth)
        }
    }
}

/**
 * 조회 중 자리를 잡아 두는 골격. 섹션 두 개와 섹션마다 카드 세 장으로 실제 섹션과 **같은 구조**라
 * 목록이 도착할 때 요소가 튀지 않는다. 표시 여부는 호출부가 지연 판정으로 정한다.
 */
@Composable
internal fun HomeSectionsSkeleton(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(HomeR.string.home_loading)
    val alpha = rememberSkeletonPulseAlpha()

    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
        // 아직 아무것도 없는 자리이므로 스크롤로 더 볼 것이 없다.
        userScrollEnabled = false,
    ) {
        items(PLACEHOLDER_SECTION_COUNT) {
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component)) {
                SkeletonPlaceholder(
                    modifier =
                        Modifier
                            .padding(horizontal = ManyakTheme.spacing.gutter)
                            .width(SectionNameWidth)
                            .height(SectionNameHeight),
                    alpha = alpha,
                )
                SectionCards(userScrollEnabled = false) { cardWidth ->
                    items(PLACEHOLDER_CARD_COUNT) {
                        CardPlaceholder(alpha = alpha, modifier = Modifier.width(cardWidth))
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPlaceholder(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth().aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO),
            alpha = alpha,
            shape = ManyakTheme.shapes.card,
        )
        Column(modifier = Modifier.padding(horizontal = ManyakTheme.spacing.inline)) {
            SkeletonPlaceholder(
                modifier = Modifier.fillMaxWidth(TITLE_WIDTH_FRACTION).height(TitleLineHeight),
                alpha = alpha,
            )
            // 소개 두 줄 자리 안에 글자 높이의 막대 둘을 가운데 둔다.
            Column(
                modifier = Modifier.padding(top = ManyakTheme.spacing.hairline).height(IntroLineHeight * 2),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.dense, Alignment.CenterVertically),
            ) {
                SkeletonPlaceholder(modifier = Modifier.fillMaxWidth().height(IntroGlyphHeight), alpha = alpha)
                SkeletonPlaceholder(
                    modifier = Modifier.fillMaxWidth(INTRO_LAST_LINE_WIDTH_FRACTION).height(IntroGlyphHeight),
                    alpha = alpha,
                )
            }
            Row(
                modifier = Modifier.padding(top = ManyakTheme.spacing.compact),
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                listOf(GenreWideWidth, GenreNarrowWidth).forEach { width ->
                    SkeletonPlaceholder(
                        modifier = Modifier.width(width).height(GenreBadgeHeight),
                        alpha = alpha,
                        shape = ManyakTheme.shapes.pill,
                    )
                }
            }
        }
    }
}

/** 셋째 카드가 화면 오른쪽 끝에 걸쳐 보이는 폭. */
private val PeekWidth = 16.dp

private const val PLACEHOLDER_SECTION_COUNT = 2
private const val PLACEHOLDER_CARD_COUNT = 3

private const val TITLE_WIDTH_FRACTION = 0.75f
private const val INTRO_LAST_LINE_WIDTH_FRACTION = 0.66f

private val SectionNameWidth = 128.dp
private val SectionNameHeight = 26.dp
private val TitleLineHeight = 24.dp
private val IntroLineHeight = 20.dp
private val IntroGlyphHeight = 16.dp
private val GenreBadgeHeight = 20.dp
private val GenreWideWidth = 48.dp
private val GenreNarrowWidth = 40.dp
