package app.manyak.feature.studio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.core.domain.story.StorySummary
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakMoreButton
import app.manyak.core.ui.component.MetaChip
import app.manyak.core.ui.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.core.ui.component.StoryBadgeScale
import app.manyak.core.ui.component.StoryCover
import app.manyak.core.ui.component.moreButtonTitleAlignment
import app.manyak.core.ui.theme.ManyakTheme
import java.text.NumberFormat

/**
 * 내가 만든 스토리 카드. 채팅 목록 카드와 같은 가로 행이지만 표지가 훨씬 크다 — 내가 만든 표지가
 * 스토리를 가려내는 첫 단서라 목록에서 그림이 먼저 읽혀야 한다.
 *
 * 표지 폭은 [CoverWidth] 로 고정이고 3:4 라 높이가 따라온다. 카드 높이는 이 표지가 정하므로
 * 텍스트 길이와도, 창 크기와도 무관하게 같다. 텍스트 영역에 고정 높이를 두면 시스템 글자 크기를 키웠을 때
 * 잘리므로 높이를 지정하지 않는다.
 *
 * 제목은 두 줄, 한 줄 소개도 두 줄까지 쓰고 넘치면 자른다. 누적 턴 수는 표지 위 뱃지가 아니라
 * 제작일과 함께 오른쪽 메타 줄이 맡는다 — 표지가 커져 뱃지가 그림을 가린다. ORIGINAL 태그는
 * 공식 스토리 표시라 붙이지 않는다.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MyStoryCard(
    story: StorySummary,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        // 카드 전체가 상세로 가는 링크다. 길게 누르기와 제목 줄 더보기 버튼은 같은 옵션 다이얼로그를 연다 —
        // 더보기 버튼은 자기 클릭을 먹어 상세로 가지 않는다.
        modifier =
            modifier.fillMaxWidth().combinedClickable(
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.studio_story_options),
                onClick = onClick,
                onLongClick = onOptionsClick,
            ),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        // 텍스트는 표지보다 짧아 가운데 정렬하면 제목이 표지 한가운데에서 시작한다.
        verticalAlignment = Alignment.Top,
    ) {
        StoryCover(
            thumbnailUrl = story.thumbnailUrl,
            modifier = Modifier.width(CoverWidth),
            showBorder = true,
        )
        StoryInfo(
            story = story,
            onOptionsClick = onOptionsClick,
            compact = false,
            modifier =
                Modifier
                    .weight(1f)
                    // 글 영역이 표지 높이를 최소치로 삼아야 메타 줄이 표지 아랫변에 맞는다.
                    .heightIn(min = CoverHeight)
                    // 글줄 상자가 글자에 바짝 붙어 있어, 표지 윗변·아랫변과 같은 줄에서 시작하면 눌려 보인다.
                    .padding(vertical = ManyakTheme.spacing.hairline),
        )
    }
}

/**
 * 옵션 다이얼로그 상단에 놓는 카드 미리보기. 목록 카드와 같은 정보를 한 단계씩 작게 그려, 어느 스토리의
 * 옵션인지 다이얼로그 안에서 확인하게 한다. 눌리지 않고 더보기 버튼도 없다 — 다이얼로그 자체가 그 메뉴다.
 */
@Composable
internal fun MyStoryCardPreview(
    story: StorySummary,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.Top,
    ) {
        StoryCover(
            thumbnailUrl = story.thumbnailUrl,
            modifier = Modifier.width(CompactCoverWidth),
            shape = ManyakTheme.shapes.thumbnailSmall,
            showBorder = true,
        )
        StoryInfo(
            story = story,
            onOptionsClick = null,
            compact = true,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = CompactCoverWidth / STORY_THUMBNAIL_ASPECT_RATIO)
                    .padding(vertical = ManyakTheme.spacing.hairline),
        )
    }
}

/**
 * 표지 오른쪽 글 영역. 제목·소개·장르 뱃지는 위에서부터 붙고 메타 줄만 아래 끝으로 내려간다 —
 * 남는 자리를 그 사이가 가져가 메타가 표지 아랫변과 같은 줄에서 끝난다. 글이 표지보다 길어지면
 * 남는 자리가 없어 메타 줄이 뱃지 바로 아래로 붙는다.
 */
@Composable
private fun StoryInfo(
    story: StorySummary,
    onOptionsClick: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    // 미리보기는 표지·서체·간격이 한 단계씩 작다.
    val lineGap = if (compact) ManyakTheme.spacing.hairline else ManyakTheme.spacing.inline
    val groupGap = if (compact) ManyakTheme.spacing.inline else ManyakTheme.spacing.compact
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // 줄 간격이 줄마다 다르므로 묶음 간격(spacedBy) 대신 각 줄이 자기 위 여백을 갖는다.
        Column {
            TitleRow(title = story.title, onOptionsClick = onOptionsClick, compact = compact)
            // 서버가 없는 소개를 빈 문자열로 주므로, 비면 줄 자체를 그리지 않는다.
            if (story.oneLineIntro.isNotBlank()) {
                Text(
                    modifier = Modifier.padding(top = lineGap),
                    text = story.oneLineIntro,
                    style =
                        (if (compact) ManyakTheme.typography.bodySmall else ManyakTheme.typography.bodyMedium).copy(
                            lineBreak = PhraseLineBreak,
                            localeList = KoreanLocale,
                        ),
                    color = ManyakTheme.colors.textSubtle,
                    maxLines = INTRO_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (story.genres.isNotEmpty()) {
                StoryGenreBadges(
                    genres = story.genres,
                    modifier = Modifier.fillMaxWidth().padding(top = groupGap),
                    scale = if (compact) StoryBadgeScale.Compact else StoryBadgeScale.Large,
                    // 미리보기는 회색 상자 위라 뱃지 바탕이 묻히지 않게 밝은 색을 쓴다.
                    containerColor =
                        if (compact) ManyakTheme.colors.surfaceRaised else ManyakTheme.colors.backgroundNeutral,
                )
            }
        }
        StoryMeta(
            turnCount = story.turnCount,
            createdDate = story.createdDate,
            compact = compact,
            // 글이 길어 남는 자리가 없을 때도 뱃지와 붙지 않을 만큼은 띄운다.
            modifier = Modifier.padding(top = lineGap),
        )
    }
}

/**
 * 누적 턴 수와 제작일. 채팅 목록 카드와 같이 오른쪽 끝에 붙는다 — 왼쪽에서 읽어 내려오는 제목·소개와
 * 성질이 달라 같은 줄머리에 두면 소개의 연장으로 읽힌다.
 *
 * 제작일은 서버 값을 읽을 수 없을 때 그 칩만 빠지고 턴 수는 남는다.
 */
@Composable
private fun StoryMeta(
    turnCount: Long,
    createdDate: String?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val formattedTurnCount = remember(turnCount) { NumberFormat.getIntegerInstance().format(turnCount) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetaChip(
            iconRes = R.drawable.ic_dialog,
            text = formattedTurnCount,
            description = stringResource(R.string.story_turn_count_description, formattedTurnCount),
            compact = compact,
        )
        createdDate?.let { date ->
            MetaChip(
                iconRes = R.drawable.ic_calendar,
                text = date,
                description = stringResource(R.string.studio_story_created_date_description, date),
                compact = compact,
            )
        }
    }
}

/**
 * 제목 줄. 더보기 버튼이 제목 오른쪽 끝에 붙는다 — 표지 위에 있으면 표지 그림을 가린다.
 * 제목이 두 줄이 되어도 버튼은 첫 줄 옆에 남는다.
 */
@Composable
private fun TitleRow(
    title: String,
    onOptionsClick: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleStyle =
        (if (compact) ManyakTheme.typography.bodyMediumStrong else ManyakTheme.typography.bodyLargeStrong).copy(
            lineBreak = PhraseLineBreak,
            localeList = KoreanLocale,
        )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            modifier = Modifier.weight(1f).alignBy(FirstBaseline),
            text = title,
            style = titleStyle,
            color = ManyakTheme.colors.text,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        if (onOptionsClick != null) {
            ManyakMoreButton(
                contentDescription = stringResource(R.string.studio_story_more),
                onClick = onOptionsClick,
                modifier = moreButtonTitleAlignment(titleStyle),
            )
        }
    }
}

/**
 * 표지 폭. 카드 폭의 비율이 아니라 고정 값이다 — 비율은 열 수가 창 폭을 따라가는 그리드의 셈이고,
 * 행 카드에 쓰면 가로 화면에서 표지 하나가 화면을 다 먹는다(채팅 카드도 같은 이유로 고정 폭이다).
 *
 * 골격도 같은 값을 써야 목록이 도착할 때 표지 자리가 튀지 않는다.
 */
internal val CoverWidth = 128.dp

/** 표지 높이. 3:4 라 폭에서 따라온다. 글 영역이 이 높이를 최소치로 삼는다. */
internal val CoverHeight = CoverWidth / STORY_THUMBNAIL_ASPECT_RATIO

/** 다이얼로그 미리보기의 표지 폭. 목록보다 한 단계 작다. */
private val CompactCoverWidth = 96.dp

/**
 * 제목·한 줄 소개의 줄바꿈. 한글은 기본값이 글자 단위로 끊어 "선행만 한 / 다" 처럼 어절 가운데가
 * 갈라지므로 어절 경계에서만 끊는다. 한 어절이 줄보다 길면 그때는 시스템이 그 안에서 끊어 넘치지 않는다.
 *
 * 구절 단위 줄바꿈은 API 33 부터 동작하고 그 아래에서는 기본 동작으로 남는다 — 줄이 갈라질 뿐
 * 글이 잘리거나 넘치지는 않아 하한을 올리지 않는다.
 */
private val PhraseLineBreak =
    LineBreak(
        strategy = LineBreak.Strategy.Simple,
        strictness = LineBreak.Strictness.Normal,
        wordBreak = LineBreak.WordBreak.Phrase,
    )

/**
 * 줄바꿈이 기준으로 삼을 언어. 기기 언어가 한국어가 아니면 구절 단위 줄바꿈이 걸리지 않으므로
 * 이 글이 한국어임을 글자 배치에 직접 알린다 — 앱 문구가 한국어 하나뿐이라 기기 설정을 따를 이유가 없다.
 */
private val KoreanLocale = LocaleList("ko-KR")

/** 제목·한 줄 소개가 각각 차지할 수 있는 줄 수. 카드 높이는 표지가 정하므로 두 줄이 되어도 흔들리지 않는다. */
private const val TITLE_MAX_LINES = 2

private const val INTRO_MAX_LINES = 2
