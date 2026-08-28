package app.manyak.feature.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.manyak.core.domain.story.StorySummary
import app.manyak.core.ui.R
import app.manyak.core.ui.component.MetaChip
import app.manyak.core.ui.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.core.ui.component.StoryCover
import app.manyak.core.ui.theme.ManyakTheme
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * 내가 만든 스토리 카드. 채팅 목록 카드와 같은 가로 행이지만 표지가 훨씬 크다 — 내가 만든 표지가
 * 스토리를 가려내는 첫 단서라 목록에서 그림이 먼저 읽혀야 한다.
 *
 * 표지 폭은 카드 폭의 [COVER_WIDTH_FRACTION] 이고 3:4 라 높이가 따라온다. 카드 높이는 이 표지가
 * 정하므로 텍스트 길이와 무관하게 같다. 텍스트 영역에 고정 높이를 두면 시스템 글자 크기를 키웠을 때
 * 잘리므로 높이를 지정하지 않는다.
 *
 * 제목은 두 줄, 한 줄 소개도 두 줄까지 쓰고 넘치면 자른다. 누적 턴 수는 표지 위 뱃지가 아니라
 * 제작일과 함께 오른쪽 메타 줄이 맡는다 — 표지가 커져 뱃지가 그림을 가린다. ORIGINAL 태그는
 * 공식 스토리 표시라 붙이지 않는다.
 */
@Composable
internal fun MyStoryCard(
    story: StorySummary,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 표지 폭이 카드 폭에서 나오므로 표지 높이도 여기서만 알 수 있다. 글 영역이 그 높이를 최소치로
    // 삼아야 메타 줄이 표지 아랫변에 맞는다.
    BoxWithConstraints(
        // 카드 전체가 상세로 가는 링크다. 표지 위 더보기 버튼은 자기 클릭을 먹어 상세로 가지 않는다.
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    ) {
        val coverWidth = maxWidth * COVER_WIDTH_FRACTION
        val coverHeight = coverWidth / STORY_THUMBNAIL_ASPECT_RATIO

        Row(
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
            // 텍스트는 표지보다 짧아 가운데 정렬하면 제목이 표지 한가운데에서 시작한다.
            verticalAlignment = Alignment.Top,
        ) {
            StoryCover(
                thumbnailUrl = story.thumbnailUrl,
                modifier = Modifier.width(coverWidth),
                showBorder = true,
            )
            StoryInfo(
                story = story,
                onDeleteClick = onDeleteClick,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = coverHeight)
                        // 글줄 상자가 글자에 바짝 붙어 있어, 표지 윗변·아랫변과 같은 줄에서 시작하면 눌려 보인다.
                        .padding(vertical = ManyakTheme.spacing.hairline),
            )
        }
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
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // 줄 간격이 줄마다 다르므로 묶음 간격(spacedBy) 대신 각 줄이 자기 위 여백을 갖는다.
        Column {
            TitleRow(title = story.title, onDeleteClick = onDeleteClick)
            // 서버가 없는 소개를 빈 문자열로 주므로, 비면 줄 자체를 그리지 않는다.
            if (story.oneLineIntro.isNotBlank()) {
                Text(
                    modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
                    text = story.oneLineIntro,
                    style =
                        ManyakTheme.typography.bodyLarge.copy(
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
                    modifier = Modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.compact),
                )
            }
        }
        StoryMeta(
            turnCount = story.turnCount,
            createdDate = story.createdDate,
            // 글이 길어 남는 자리가 없을 때도 뱃지와 붙지 않을 만큼은 띄운다.
            modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
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
        )
        createdDate?.let { date ->
            MetaChip(
                iconRes = R.drawable.ic_calendar,
                text = date,
                description = stringResource(R.string.studio_story_created_date_description, date),
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
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 메뉴 펼침은 이 줄 밖에서 알 필요가 없는 표현 상태라 로컬에 둔다.
    var menuExpanded by remember { mutableStateOf(false) }

    val titleStyle =
        ManyakTheme.typography.titleMedium.copy(
            lineBreak = PhraseLineBreak,
            localeList = KoreanLocale,
        )
    // 아이콘 가운데를 제목 첫 줄 **글자**의 가운데에 맞춘다. 글줄 상자에 맞추면 위아래 여백까지
    // 세는 탓에 아이콘이 글자보다 낮게 보인다. 한글은 베이스라인 위로만 글자를 채우므로, 그 절반만큼
    // 위가 글자의 가운데다.
    val halfGlyphHeight =
        with(LocalDensity.current) { (titleStyle.fontSize.toPx() * HANGUL_GLYPH_HEIGHT_RATIO / 2f).roundToInt() }

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
        Box(
            modifier = Modifier.alignBy { measured -> measured.measuredHeight / 2 + halfGlyphHeight },
        ) {
            MoreMenuButton(onClick = { menuExpanded = true })
            if (menuExpanded) {
                StoryCardMenu(
                    onDismiss = { menuExpanded = false },
                    onDelete = {
                        menuExpanded = false
                        onDeleteClick()
                    },
                )
            }
        }
    }
}

/**
 * 카드별 드랍다운 메뉴를 여는 트리거. 카드 바탕 위에 놓이므로 필드를 깔지 않고 아이콘만 둔다.
 * 상자를 크게 잡을수록 제목 줄이 두꺼워져 제목이 아래로 밀리므로 아이콘에 바짝 붙인다.
 */
@Composable
private fun MoreMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(MoreButtonSize)
                .clip(ManyakTheme.shapes.pill)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(MoreIconSize),
            painter = painterResource(R.drawable.ic_more),
            contentDescription = stringResource(R.string.studio_story_more),
            tint = ManyakTheme.colors.textSubtle,
        )
    }
}

/**
 * 카드 더보기 메뉴. 트리거 오른쪽 끝에 맞춰 아래로 연다 — 트리거가 표지 우상단에 있어
 * 왼쪽 정렬로 열면 카드 밖으로 나간다.
 *
 * M3 `DropdownMenu` 대신 GenderSelect 와 같은 Popup 직접 구성을 쓴다. 항목이 하나뿐인 메뉴가
 * 공간에 따라 위로 뒤집히면 트리거를 가리기 때문이다.
 */
@Composable
private fun StoryCardMenu(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { ManyakTheme.spacing.inline.roundToPx() }
    val positionProvider =
        remember(gapPx) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset =
                    IntOffset(
                        x = (anchorBounds.right - popupContentSize.width).coerceAtLeast(0),
                        y = anchorBounds.bottom + gapPx,
                    )
            }
        }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier =
                Modifier
                    // 사진 배경 위에서도 경계가 보이도록 성별 선택 메뉴와 같은 깊이를 쓴다.
                    .shadow(elevation = MenuShadowElevation, shape = ManyakTheme.shapes.control)
                    .background(ManyakTheme.colors.surfaceRaised, ManyakTheme.shapes.control)
                    .border(1.dp, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                    .clip(ManyakTheme.shapes.control)
                    .padding(ManyakTheme.spacing.compact),
        ) {
            DeleteMenuItem(onClick = onDelete)
        }
    }
}

/** 파괴적 항목이라 아이콘·텍스트를 danger 색으로 둔다. 확인은 다이얼로그가 한 번 더 묻는다. */
@Composable
private fun DeleteMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                // 한 단어 항목이라 내용 폭만으로는 누를 자리가 좁다.
                .widthIn(min = MenuItemMinWidth)
                .clip(ManyakTheme.shapes.menuItem)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Icon(
            modifier = Modifier.size(MenuItemIconSize),
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = null,
            tint = ManyakTheme.colors.textDanger,
        )
        Text(
            text = stringResource(R.string.studio_story_delete),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textDanger,
        )
    }
}

/**
 * 카드 폭에서 표지가 차지하는 비율. 화면 좌우 여백을 뺀 카드 폭 기준이라 화면 폭의 3분의 1 언저리가
 * 된다. 골격도 같은 값을 써야 목록이 도착할 때 표지 자리가 튀지 않는다.
 */
internal const val COVER_WIDTH_FRACTION = 1f / 3f

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

private val MoreButtonSize = 24.dp

/**
 * 한글 글자의 가운데가 베이스라인 위 어디쯤인지의 배율. 글자는 베이스라인 위로 약 0.8em 을 채우고
 * 아래로 조금 넘치므로, 그 가운데는 0.73em 의 절반 지점이다. 기기에서 글자와 아이콘의 픽셀 범위를
 * 재서 맞춘 값이고, 글자 크기를 키워도 비율은 그대로라 dp 가 아니라 배율로 둔다.
 */
private const val HANGUL_GLYPH_HEIGHT_RATIO = 0.73f

private val MoreIconSize = 18.dp

private val MenuItemMinWidth = 120.dp

private val MenuItemIconSize = 16.dp

private val MenuShadowElevation = 4.dp
