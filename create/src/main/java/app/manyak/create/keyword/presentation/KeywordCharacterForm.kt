package app.manyak.create.keyword.presentation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.create.presentation.component.AddTrigger
import app.manyak.create.presentation.component.CollapsibleInputSection
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.component.ManyakInputCounter
import app.manyak.designsystem.component.ManyakTextField
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.withTimeoutOrNull
import app.manyak.create.R as CreateR

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
    nameFieldModifier: Modifier = Modifier,
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
            nameFieldModifier = nameFieldModifier,
        )
        KeywordSectionLabel(
            modifier = Modifier.padding(top = ManyakTheme.spacing.gutter),
            text = stringResource(CreateR.string.create_section_feature),
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
                text = stringResource(CreateR.string.create_feature_random_hint),
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
    nameFieldModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        KeywordSectionLabel(
            text = stringResource(CreateR.string.create_section_basic_info),
            required = false,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            ManyakTextField(
                modifier = Modifier.weight(3f).then(nameFieldModifier),
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
                text = stringResource(CreateR.string.create_error_duplicate_name),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textDanger,
            )
        }
        Text(
            text = stringResource(CreateR.string.create_character_random_hint),
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
                        label = stringResource(CreateR.string.create_add_keyword),
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
                text = stringResource(CreateR.string.create_tags_load_failed),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.textDanger,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(CreateR.string.create_tags_reload_description),
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
                text = stringResource(CreateR.string.create_tags_reload),
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
    scrollToCenter: suspend (Float) -> Unit = {},
) {
    val namePlaceholders = stringArrayResource(CreateR.array.create_name_placeholders_supporting)
    val nameCenters = remember { mutableMapOf<Long, Float>() }
    var previousCharacterCount by remember { mutableIntStateOf(state.supportingCharacters.size) }

    // 웹과 같이 새 인물의 이름 칸을 화면 가운데로 옮긴다. 새 인물은 펼친 채 나타나므로 자리를 잡는 대로 옮긴다.
    LaunchedEffect(state.supportingCharacters.size) {
        val currentCount = state.supportingCharacters.size
        val characterWasAdded = currentCount > previousCharacterCount
        previousCharacterCount = currentCount
        val addedId = state.supportingCharacters.lastOrNull()?.id
        if (characterWasAdded && addedId != null) {
            val center =
                withTimeoutOrNull(ADDED_LAYOUT_TIMEOUT_MILLIS) {
                    while (addedId !in nameCenters) withFrameNanos { }
                    nameCenters.getValue(addedId)
                }
            center?.let { scrollToCenter(it) }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        state.supportingCharacters.forEachIndexed { index, character ->
            val order = index + 1
            val fallbackLabel = stringResource(CreateR.string.create_supporting_character_label, order)
            SupportingCharacterSection(
                headerLabel = character.name.ifBlank { fallbackLabel },
                countLabel =
                    stringResource(
                        CreateR.string.create_supporting_character_count,
                        order,
                        CreateKeywordUiState.SUPPORTING_CHARACTER_MAX,
                    ),
                expanded = character.id !in state.collapsedCharacterIds,
                target = KeywordTarget.Supporting(character.id),
                character = character,
                namePlaceholder = namePlaceholders[index % namePlaceholders.size],
                isDuplicateName = character.id in state.duplicateNameCharacterIds,
                providedTags = state.providedTags,
                atSelectionCap = state.isAtSelectionCap(KeywordTarget.Supporting(character.id)),
                onIntent = onIntent,
                onOpenAddKeyword = onOpenAddKeyword,
                nameFieldModifier =
                    Modifier.onGloballyPositioned {
                        nameCenters[character.id] = it.positionInRoot().y + it.size.height / 2f
                    },
            )
        }
        SupportingCharacterFooter(state, onIntent)
    }
}

@Composable
private fun SupportingCharacterFooter(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
) {
    val empty = state.supportingCharacters.isEmpty()
    if (empty) {
        Text(
            modifier = Modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.gutter),
            text = stringResource(CreateR.string.create_supporting_empty_description),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtlest,
            textAlign = TextAlign.Center,
        )
    }
    // 펼친 인물 폼은 아래 여백을 이미 깔고 있다. 빈 안내 문구나 접힌 머리 줄 뒤에만 간격을 둔다.
    // 폼이 접히는 동안 간격이 한 번에 붙으면 인물 추가 버튼이 툭 밀린다. 폼 높이와 같은 스프링으로 채운다.
    val lastCollapsed = state.supportingCharacters.lastOrNull()?.id in state.collapsedCharacterIds
    val topGap by animateDpAsState(
        targetValue = if (empty || lastCollapsed) ManyakTheme.spacing.gutter else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Dp.VisibilityThreshold),
        label = "supporting-footer-gap",
    )
    AddCharacterTrigger(
        enabled = state.supportingCharacters.size < CreateKeywordUiState.SUPPORTING_CHARACTER_MAX,
        onClick = { onIntent(CreateKeywordIntent.AddSupportingCharacter) },
        modifier = Modifier.padding(top = topGap),
    )
}

@Composable
private fun AddCharacterTrigger(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AddTrigger(
            label = stringResource(CreateR.string.create_add_character),
            enabled = enabled,
            onClick = onClick,
        )
    }
}

@Composable
private fun SupportingCharacterSection(
    headerLabel: String,
    countLabel: String,
    expanded: Boolean,
    target: KeywordTarget,
    character: KeywordCharacter,
    namePlaceholder: String,
    isDuplicateName: Boolean,
    providedTags: ProvidedTags,
    atSelectionCap: Boolean,
    onIntent: (CreateKeywordIntent) -> Unit,
    onOpenAddKeyword: (KeywordTarget) -> Unit,
    modifier: Modifier = Modifier,
    nameFieldModifier: Modifier = Modifier,
) {
    CollapsibleInputSection(
        modifier = modifier,
        headerLabel = headerLabel,
        countLabel = countLabel,
        expanded = expanded,
        onToggle = { onIntent(CreateKeywordIntent.ToggleSupportingCharacter(character.id)) },
        onDelete = {
            (target as? KeywordTarget.Supporting)?.let {
                onIntent(CreateKeywordIntent.RemoveSupportingCharacter(it.characterId))
            }
        },
    ) {
        CharacterForm(
            target = target,
            character = character,
            featureRequired = false,
            namePlaceholder = namePlaceholder,
            isDuplicateName = isDuplicateName,
            providedTags = providedTags,
            atSelectionCap = atSelectionCap,
            onIntent = onIntent,
            onOpenAddKeyword = onOpenAddKeyword,
            nameFieldModifier = nameFieldModifier,
        )
    }
}

@Preview(showBackground = true, name = "주변 인물 없음")
@Composable
private fun EmptySupportingCharactersPreview() {
    ManyakTheme {
        SupportingCharacterList(
            state = CreateKeywordUiState(supportingCharacters = emptyList()),
            onIntent = {},
            onOpenAddKeyword = {},
        )
    }
}

private const val ADDED_LAYOUT_TIMEOUT_MILLIS = 500L
