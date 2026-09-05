package app.manyak.story.detail.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.StoryBadgeScale
import app.manyak.designsystem.component.StoryGenreBadge
import app.manyak.designsystem.component.StoryThumbnail
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.story.entity.StoryDetail
import app.manyak.story.entity.StoryStartSetting
import app.manyak.story.R as StoryR

/**
 * 상세 본문. 순서는 히어로 → 제목 → 한 줄 소개 → 장르 → 본 엔딩 → 주요 내용 → 주변 인물 →
 * 시작 상황 → 제작자·생성일이다.
 *
 * 값이 없는 항목은 자리를 비우지 않고 아예 그리지 않는다 — 이유 없는 공백이 생기지 않게 한다.
 *
 * 표지만 화면 폭을 꽉 채우므로 좌우 여백은 목록이 한 번에 두지 않고 항목마다 각자 건다.
 */
@Suppress("LongParameterList")
internal fun LazyListScope.storyDetailBody(
    story: StoryDetail,
    selectedStartSettingId: String?,
    selectedStartSetting: StoryStartSetting?,
    onThumbnailClick: () -> Unit,
    onSelectStartSetting: (String) -> Unit,
    onTitleBottomChanged: (Float) -> Unit,
) {
    item(key = OVERVIEW_KEY) {
        Overview(
            story = story,
            onThumbnailClick = onThumbnailClick,
            onTitleBottomChanged = onTitleBottomChanged,
        )
    }
    story.description?.let { description ->
        item(key = DESCRIPTION_KEY) {
            LabeledSection(
                labelRes = StoryR.string.story_detail_description,
                modifier = Modifier.padding(horizontal = ManyakTheme.spacing.gutter),
            ) {
                Text(
                    text = description,
                    style = ManyakTheme.typography.bodyLarge,
                    color = ManyakTheme.colors.text,
                )
            }
        }
    }
    if (story.characters.isNotEmpty()) {
        item(key = CHARACTERS_KEY) {
            LabeledSection(
                labelRes = StoryR.string.story_detail_characters,
                modifier = Modifier.padding(horizontal = ManyakTheme.spacing.gutter),
            ) {
                CharacterSection(characters = story.characters)
            }
        }
    }
    if (selectedStartSetting != null) {
        item(key = START_SETTING_KEY) {
            LabeledSection(
                labelRes = StoryR.string.story_detail_start_settings,
                modifier = Modifier.padding(horizontal = ManyakTheme.spacing.gutter),
            ) {
                StartSettingSection(
                    startSettings = story.startSettings,
                    selectedId = selectedStartSettingId,
                    selected = selectedStartSetting,
                    onSelect = onSelectStartSetting,
                )
            }
        }
    }
    if (story.authorNickname != null || story.createdDate != null) {
        item(key = META_KEY) {
            MetaBlock(authorNickname = story.authorNickname, date = story.createdDate)
        }
    }
}

/**
 * 표지와 제목 묶음. 둘은 다른 구획이 아니라 한 화면의 머리라, 구획 사이보다 좁게 붙인다.
 */
@Composable
private fun Overview(
    story: StoryDetail,
    onThumbnailClick: () -> Unit,
    onTitleBottomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        StoryHero(story = story, onClick = onThumbnailClick)
        Headline(
            story = story,
            onTitleBottomChanged = onTitleBottomChanged,
            modifier = Modifier.padding(horizontal = ManyakTheme.spacing.gutter),
        )
    }
}

/**
 * 히어로는 목록 카드와 같은 표지 컴포넌트를 쓰되 축소본이 아니라 원본을 받고, 뱃지도 한 단계 크다.
 * 화면 폭을 그대로 채우고 모서리를 각지게 두는 대신, 아래로 이어지는 본문과 붙어 보이지 않게
 * 옅은 경계선 한 줄을 깐다. 이미지가 있을 때만 누를 수 있다.
 */
@Composable
private fun StoryHero(
    story: StoryDetail,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(StoryR.string.story_detail_thumbnail_open)
    Column(modifier = modifier.fillMaxWidth()) {
        StoryThumbnail(
            modifier =
                if (story.thumbnailUrl == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
                },
            thumbnailUrl = story.thumbnailUrl,
            turnCount = story.turnCount,
            badgeScale = StoryBadgeScale.Large,
            shape = RectangleShape,
        )
        HorizontalDivider(thickness = StoryHeroBorderWidth, color = ManyakTheme.colors.border)
    }
}

/**
 * 제목·한 줄 소개·장르 뱃지는 한 덩어리다. 셋을 따로 항목으로 두면 구획 사이 간격이 이 안에도
 * 들어와 무엇이 한 묶음인지 드러나지 않는다.
 *
 * 제목은 앱바가 이어받을 자리라 제 아래 끝을 [onTitleBottomChanged] 로 올린다. 글자 크기나
 * 줄 수에 따라 달라지는 값이라 계산으로 맞히지 않고 실제 배치를 잰다.
 */
@Composable
private fun Headline(
    story: StoryDetail,
    onTitleBottomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            Text(
                modifier =
                    Modifier.onGloballyPositioned { coordinates ->
                        onTitleBottomChanged(coordinates.positionInRoot().y + coordinates.size.height)
                    },
                text = story.title,
                style = ManyakTheme.typography.headlineSmall,
                color = ManyakTheme.colors.text,
            )
            if (story.oneLineIntro.isNotBlank()) {
                Text(
                    text = story.oneLineIntro,
                    style = ManyakTheme.typography.bodyLarge,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
        }
        if (story.genres.isNotEmpty()) {
            GenreBadges(genres = story.genres)
        }
        if (story.reachedEndings.isNotEmpty()) {
            GenreBadges(genres = story.reachedEndings)
        }
    }
}

/** 상세는 카드와 달리 폭에 맞춰 접지 않고 줄바꿈으로 모두 보인다 — 가릴 만큼 좁은 자리가 아니다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreBadges(
    genres: List<String>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        genres.forEach { genre -> StoryGenreBadge(text = genre, scale = StoryBadgeScale.Large) }
    }
}

/**
 * 시작 상황은 이름·설명·엔딩 세 갈래다. 이름은 고를 수 있는 값이고 나머지 둘은 그 값에 딸리므로,
 * 한 덩어리로 두면 무엇을 바꿀 수 있는지 드러나지 않는다.
 */
@Composable
private fun StartSettingSection(
    startSettings: List<StoryStartSetting>,
    selectedId: String?,
    selected: StoryStartSetting,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        // 갈래 사이는 갈래 안(라벨↔내용)보다 넓다 — 같으면 무엇이 한 묶음인지 드러나지 않는다.
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section),
    ) {
        if (startSettings.isNotEmpty()) {
            SubLabeledBlock(label = stringResource(StoryR.string.story_detail_start_setting_name)) {
                StartSettingSelect(
                    startSettings = startSettings,
                    selectedId = selectedId,
                    onSelect = onSelect,
                )
            }
        }
        SubLabeledBlock(label = stringResource(StoryR.string.story_detail_start_setting_situation)) {
            Text(
                text = selected.startSituation,
                style = ManyakTheme.typography.bodyLarge,
                color = ManyakTheme.colors.text,
            )
        }
        if (selected.endings.isNotEmpty()) {
            SubLabeledBlock(
                label = stringResource(StoryR.string.story_detail_start_setting_endings),
                labelTrailing = { EndingInfoButton() },
            ) {
                EndingList(endings = selected.endings)
            }
        }
    }
}

/**
 * 고른 갈래에서 닿을 수 있는 엔딩들. 이름만 늘어놓고 도달 여부는 구분하지 않는다 — 도달한 엔딩은
 * 제목 아래 뱃지가 이미 알리고, 같은 사실을 한 화면에서 두 가지로 말하면 어느 쪽이 정본인지
 * 흐려진다.
 */
@Composable
private fun EndingList(
    endings: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        endings.forEach { ending -> EndingRow(name = ending) }
    }
}

/**
 * 엔딩 한 줄. 바로 위 셀렉트 앵커와 같은 높이·모서리·좌우 여백이라 두 갈래가 한 묶음으로 읽히고,
 * 테두리 대신 회색 바탕만 깔아 누를 수 없는 자리임을 드러낸다.
 *
 * 높이는 고정하지 않고 최소치로 둔다 — 이름이 100자까지 허용되는데 셀렉트와 달리 펼쳐서 전문을
 * 볼 수단이 없어 잘라내면 무슨 엔딩인지 읽히지 않는다.
 */
@Composable
private fun EndingRow(
    name: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .background(ManyakTheme.colors.backgroundNeutral)
                .padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.compact,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
        )
    }
}

/**
 * 섹션 안의 작은 갈래. 섹션 라벨보다 한 단계 작은 굵은 제목을 얹는다.
 *
 * [labelTrailing] 은 제목 오른쪽에 붙는 자리다 — 갈래 하나에만 딸리는 안내를 내용 위에 문장으로
 * 깔면 무엇이 라벨이고 무엇이 내용인지 흐려진다.
 */
@Composable
internal fun SubLabeledBlock(
    label: String,
    modifier: Modifier = Modifier,
    labelTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = ManyakTheme.typography.bodyLargeStrong,
                color = ManyakTheme.colors.text,
            )
            labelTrailing?.invoke()
        }
        content()
    }
}

@Composable
private fun LabeledSection(
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Text(
            text = stringResource(labelRes),
            style = ManyakTheme.typography.titleMediumStrong,
            color = ManyakTheme.colors.text,
        )
        content()
    }
}

/** 표지 아래 경계선. 골격도 같은 선을 그려야 본문이 도착할 때 구조가 어긋나지 않는다. */
internal val StoryHeroBorderWidth = 1.dp

private const val OVERVIEW_KEY = "overview"
private const val DESCRIPTION_KEY = "description"
private const val CHARACTERS_KEY = "characters"
private const val START_SETTING_KEY = "start-setting"
private const val META_KEY = "meta"
