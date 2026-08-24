package app.manyak.feature.create

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
                append(" ")
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

@Composable
internal fun CategoryContent(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
    onOpenAddKeyword: (KeywordTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        when (state.activeCategory) {
            StoryTagCategory.GENRE -> {
                Column(
                    modifier = Modifier.padding(ManyakTheme.spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
                ) {
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
            }

            StoryTagCategory.PROTAGONIST -> {
                CharacterForm(
                    modifier = Modifier.padding(ManyakTheme.spacing.gutter),
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
            }

            StoryTagCategory.SUPPORTING_CHARACTER -> {
                SupportingCharacterList(
                    state = state,
                    onIntent = onIntent,
                    onOpenAddKeyword = onOpenAddKeyword,
                )
            }
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

internal val StoryTagCategory.addKeywordPlaceholderRes: Int
    @StringRes
    get() =
        when (this) {
            StoryTagCategory.GENRE -> R.string.create_add_keyword_placeholder_genre
            StoryTagCategory.PROTAGONIST -> R.string.create_add_keyword_placeholder_protagonist
            StoryTagCategory.SUPPORTING_CHARACTER -> R.string.create_add_keyword_placeholder_supporting_character
        }
