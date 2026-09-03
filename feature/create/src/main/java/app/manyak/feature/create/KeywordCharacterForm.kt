package app.manyak.feature.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakInputCounter
import app.manyak.core.ui.component.ManyakTextButton
import app.manyak.core.ui.component.ManyakTextField
import app.manyak.core.ui.theme.ManyakTheme

@Composable
internal fun CharacterForm(
    target: KeywordTarget,
    character: KeywordCharacter,
    featureRequired: Boolean,
    namePlaceholder: String,
    isDuplicateName: Boolean,
    providedTags: ProvidedTags,
    atSelectionCap: Boolean,
    onIntent: (CreateKeywordIntent) -> Unit,
    onOpenAddKeyword: (KeywordTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        CharacterBasicInfo(
            target = target,
            character = character,
            namePlaceholder = namePlaceholder,
            isDuplicateName = isDuplicateName,
            onIntent = onIntent,
        )
        KeywordSectionLabel(
            modifier = Modifier.padding(top = ManyakTheme.spacing.gutter),
            text = stringResource(R.string.create_section_feature),
            required = featureRequired,
        )
        KeywordChipArea(
            providedTags = providedTags,
            target = target,
            selectedTagIds = character.selectedTagIds,
            customTags = character.customTags,
            atSelectionCap = atSelectionCap,
            onIntent = onIntent,
            onOpenAddKeyword = onOpenAddKeyword,
        )
        if (!featureRequired) {
            Text(
                text = stringResource(R.string.create_feature_random_hint),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

@Composable
private fun CharacterBasicInfo(
    target: KeywordTarget,
    character: KeywordCharacter,
    namePlaceholder: String,
    isDuplicateName: Boolean,
    onIntent: (CreateKeywordIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        KeywordSectionLabel(
            text = stringResource(R.string.create_section_basic_info),
            required = false,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            ManyakTextField(
                modifier = Modifier.weight(3f),
                value = character.name,
                onValueChange = { name -> onIntent(CreateKeywordIntent.ChangeCharacterName(target, name)) },
                placeholder = namePlaceholder,
                isError = isDuplicateName,
                trailing = {
                    ManyakInputCounter(
                        length = character.name.length,
                        maxLength = CreateKeywordUiState.CHARACTER_NAME_MAX_LENGTH,
                    )
                },
            )
            GenderSelectField(
                modifier = Modifier.weight(2f),
                gender = character.gender,
                onGenderChange = { gender -> onIntent(CreateKeywordIntent.ChangeCharacterGender(target, gender)) },
            )
        }
        if (isDuplicateName) {
            Text(
                text = stringResource(R.string.create_error_duplicate_name),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textDanger,
            )
        }
        Text(
            text = stringResource(R.string.create_character_random_hint),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeywordChipArea(
    providedTags: ProvidedTags,
    target: KeywordTarget,
    selectedTagIds: Set<Long>,
    customTags: List<CustomTag>,
    atSelectionCap: Boolean,
    onIntent: (CreateKeywordIntent) -> Unit,
    onOpenAddKeyword: (KeywordTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        when (providedTags) {
            ProvidedTags.Loading -> {
                KeywordChipSkeleton()
            }

            is ProvidedTags.Loaded -> {
                val tags = providedTags.byCategory[target.category].orEmpty()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
                    verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
                ) {
                    tags.forEach { tag ->
                        val selected = tag.id in selectedTagIds
                        KeywordChip(
                            name = tag.name,
                            selected = selected,
                            enabled = selected || !atSelectionCap,
                            onClick = { onIntent(CreateKeywordIntent.ToggleProvidedTag(target, tag.id)) },
                        )
                    }
                    customTags.forEachIndexed { index, customTag ->
                        KeywordChip(
                            name = customTag.name,
                            selected = customTag.selected,
                            enabled = customTag.selected || !atSelectionCap,
                            onClick = { onIntent(CreateKeywordIntent.ToggleCustomTag(target, index)) },
                        )
                    }
                    AddTrigger(
                        label = stringResource(R.string.create_add_keyword),
                        enabled = !atSelectionCap,
                        onClick = { onOpenAddKeyword(target) },
                    )
                }
            }

            ProvidedTags.Failed -> {
                TagsLoadFailure(onRetry = { onIntent(CreateKeywordIntent.RetryTags) })
            }
        }
    }
}

@Composable
internal fun TagsLoadFailure(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            Text(
                text = stringResource(R.string.create_tags_load_failed),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.textDanger,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.create_tags_reload_description),
                style = ManyakTheme.typography.bodyLarge,
                color = ManyakTheme.colors.text,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            modifier = Modifier.height(ManyakTheme.sizes.control),
            onClick = onRetry,
            shape = ManyakTheme.shapes.control,
            border = BorderStroke(1.dp, ManyakTheme.colors.border),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.surfaceRaised,
                    contentColor = ManyakTheme.colors.text,
                ),
        ) {
            Text(
                text = stringResource(R.string.create_tags_reload),
                style = ManyakTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
internal fun SupportingCharacterList(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
    onOpenAddKeyword: (KeywordTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val namePlaceholders = stringArrayResource(R.array.create_name_placeholders_supporting)
    val addedCharacterRequester = remember { BringIntoViewRequester() }
    var previousCharacterCount by remember { mutableIntStateOf(state.supportingCharacters.size) }

    LaunchedEffect(state.supportingCharacters.size) {
        val currentCount = state.supportingCharacters.size
        val characterWasAdded = currentCount > previousCharacterCount
        previousCharacterCount = currentCount

        if (characterWasAdded) {
            withFrameNanos { }
            addedCharacterRequester.bringIntoView()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        state.supportingCharacters.forEachIndexed { index, character ->
            val order = index + 1
            val fallbackLabel = stringResource(R.string.create_supporting_character_label, order)
            SupportingCharacterSection(
                headerLabel = character.name.ifBlank { fallbackLabel },
                countLabel =
                    stringResource(
                        R.string.create_supporting_character_count,
                        order,
                        CreateKeywordUiState.SUPPORTING_CHARACTER_MAX,
                    ),
                target = KeywordTarget.Supporting(character.id),
                character = character,
                namePlaceholder = namePlaceholders[index % namePlaceholders.size],
                isDuplicateName = character.id in state.duplicateNameCharacterIds,
                providedTags = state.providedTags,
                atSelectionCap = state.isAtSelectionCap(KeywordTarget.Supporting(character.id)),
                onIntent = onIntent,
                onOpenAddKeyword = onOpenAddKeyword,
                // 요청 대상은 기본 정보가 아니라 섹션 전체다 — 기본 정보만 겨누면 특징 칸이
                // 화면 밖에 남는다. 섹션이 뷰포트보다 크면 컨테이너가 위 모서리를 맞춰 세우므로,
                // 들어가는 만큼의 특징 칸이 기본 정보와 함께 보인다.
                modifier =
                    if (index == state.supportingCharacters.lastIndex) {
                        Modifier.bringIntoViewRequester(addedCharacterRequester)
                    } else {
                        Modifier
                    },
            )
        }
        AddCharacterTrigger(
            modifier =
                if (state.supportingCharacters.isEmpty()) {
                    Modifier.padding(top = ManyakTheme.spacing.gutter)
                } else {
                    Modifier
                },
            enabled = state.supportingCharacters.size < CreateKeywordUiState.SUPPORTING_CHARACTER_MAX,
            onClick = { onIntent(CreateKeywordIntent.AddSupportingCharacter) },
        )
    }
}

@Composable
private fun AddCharacterTrigger(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AddTrigger(
            label = stringResource(R.string.create_add_character),
            enabled = enabled,
            onClick = onClick,
        )
    }
}

@Composable
private fun SupportingCharacterSection(
    headerLabel: String,
    countLabel: String,
    target: KeywordTarget,
    character: KeywordCharacter,
    namePlaceholder: String,
    isDuplicateName: Boolean,
    providedTags: ProvidedTags,
    atSelectionCap: Boolean,
    onIntent: (CreateKeywordIntent) -> Unit,
    onOpenAddKeyword: (KeywordTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        SupportingCharacterHeader(
            headerLabel = headerLabel,
            countLabel = countLabel,
            onDelete = {
                (target as? KeywordTarget.Supporting)?.let {
                    onIntent(CreateKeywordIntent.RemoveSupportingCharacter(it.characterId))
                }
            },
        )
        CharacterForm(
            modifier = Modifier.padding(horizontal = ManyakTheme.spacing.gutter),
            target = target,
            character = character,
            featureRequired = false,
            namePlaceholder = namePlaceholder,
            isDuplicateName = isDuplicateName,
            providedTags = providedTags,
            atSelectionCap = atSelectionCap,
            onIntent = onIntent,
            onOpenAddKeyword = onOpenAddKeyword,
        )
    }
}

@Composable
private fun SupportingCharacterHeader(
    headerLabel: String,
    countLabel: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deleteDescription = stringResource(R.string.create_supporting_delete_description, headerLabel)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ManyakTheme.colors.backgroundNeutral)
                .padding(horizontal = ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = headerLabel,
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.textSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier =
                    Modifier
                        .clip(ManyakTheme.shapes.pill)
                        .background(ManyakTheme.colors.backgroundNeutralPressed)
                        .padding(
                            horizontal = ManyakTheme.spacing.compact,
                            vertical = ManyakTheme.spacing.inline,
                        ),
                text = countLabel,
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
        ManyakTextButton(
            modifier =
                Modifier
                    .width(ManyakTheme.sizes.control)
                    .semantics { contentDescription = deleteDescription },
            onClick = onDelete,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.create_supporting_delete),
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.textSubtle,
                textAlign = TextAlign.End,
            )
        }
    }
}
