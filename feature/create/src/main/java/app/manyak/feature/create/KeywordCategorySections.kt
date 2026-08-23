package app.manyak.feature.create

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.manyak.core.domain.story.StoryTagCategory
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 카테고리 탭. 탭을 누르면 라벨 색과 표시선이 곧바로 바뀌므로 그 변화 자체가 반응이다 —
 * 하단 내비게이션과 같은 이유로 리플을 끈다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryTabs(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        SecondaryTabRow(
            modifier = modifier.fillMaxWidth(),
            selectedTabIndex = state.activeCategory.ordinal,
            containerColor = ManyakTheme.colors.surface,
            contentColor = ManyakTheme.colors.text,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(state.activeCategory.ordinal),
                    height = 1.5.dp,
                    color = ManyakTheme.colors.text,
                )
            },
        ) {
            StoryTagCategory.entries.forEach { category ->
                val unlocked = state.isUnlocked(category)
                Tab(
                    selected = category == state.activeCategory,
                    onClick = { onIntent(CreateKeywordIntent.SelectCategory(category)) },
                    enabled = unlocked,
                    text = {
                        CategoryTabLabel(
                            category = category,
                            selected = category == state.activeCategory,
                            unlocked = unlocked,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryTabLabel(
    category: StoryTagCategory,
    selected: Boolean,
    unlocked: Boolean,
) {
    val labelColor =
        when {
            !unlocked -> ManyakTheme.colors.textDisabled
            selected -> ManyakTheme.colors.text
            else -> ManyakTheme.colors.textSubtle
        }
    val requiredMarkColor = if (unlocked) ManyakTheme.colors.textDanger else ManyakTheme.colors.textDisabled
    val label =
        buildAnnotatedString {
            append(stringResource(category.labelRes))
            if (category.required) {
                withStyle(SpanStyle(color = requiredMarkColor)) { append("*") }
            }
        }
    Text(
        text = label,
        style = ManyakTheme.typography.labelLarge,
        color = labelColor,
        maxLines = 1,
    )
}

/**
 * 탭 아래의 카테고리 설명 줄. 주변 인물 탭에서는 오른쪽 끝에 현재 인원을 표시한다.
 */
@Composable
internal fun CategoryDescription(
    state: CreateKeywordUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.component),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(state.activeCategory.descriptionRes),
            style = ManyakTheme.typography.bodySmall,
            color = ManyakTheme.colors.textSubtle,
        )
        if (state.activeCategory == StoryTagCategory.SUPPORTING_CHARACTER) {
            Text(
                text =
                    stringResource(
                        R.string.create_supporting_character_count,
                        state.supportingCharacters.size,
                    ),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

/** 활성 카테고리의 키워드 입력 본문. 탭을 오가도 각 카테고리의 입력은 상태에 남는다. */
@Composable
internal fun CategoryContent(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
    onOpenAddKeyword: (KeywordTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = ManyakTheme.spacing.gutter)) {
        when (state.activeCategory) {
            StoryTagCategory.GENRE ->
                Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                    KeywordSectionLabel(
                        text = stringResource(R.string.create_section_genre),
                        required = true,
                    )
                    KeywordChipArea(
                        providedTags = state.providedTags,
                        target = KeywordTarget.Genre,
                        selectedTagIds = state.selectedGenreTagIds,
                        customTags = state.customGenreTags,
                        atSelectionCap = state.isAtSelectionCap(KeywordTarget.Genre),
                        onIntent = onIntent,
                        onOpenAddKeyword = onOpenAddKeyword,
                    )
                }

            StoryTagCategory.PROTAGONIST ->
                CharacterForm(
                    target = KeywordTarget.Protagonist,
                    character = state.protagonist,
                    featureRequired = true,
                    namePlaceholder = stringResource(R.string.create_name_placeholder_protagonist),
                    isDuplicateName = state.protagonist.id in state.duplicateNameCharacterIds,
                    providedTags = state.providedTags,
                    atSelectionCap = state.isAtSelectionCap(KeywordTarget.Protagonist),
                    onIntent = onIntent,
                    onOpenAddKeyword = onOpenAddKeyword,
                )

            StoryTagCategory.SUPPORTING_CHARACTER ->
                SupportingCharacterList(
                    state = state,
                    onIntent = onIntent,
                    onOpenAddKeyword = onOpenAddKeyword,
                )
        }
    }
}

internal val StoryTagCategory.labelRes: Int
    @StringRes
    get() =
        when (this) {
            StoryTagCategory.GENRE -> R.string.create_tab_genre
            StoryTagCategory.PROTAGONIST -> R.string.create_tab_protagonist
            StoryTagCategory.SUPPORTING_CHARACTER -> R.string.create_tab_supporting_character
        }

private val StoryTagCategory.descriptionRes: Int
    @StringRes
    get() =
        when (this) {
            StoryTagCategory.GENRE -> R.string.create_tab_genre_description
            StoryTagCategory.PROTAGONIST -> R.string.create_tab_protagonist_description
            StoryTagCategory.SUPPORTING_CHARACTER -> R.string.create_tab_supporting_character_description
        }
