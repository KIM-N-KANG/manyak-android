package app.manyak.feature.create

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 인물 폼. 주인공과 주변 인물이 같은 폼을 쓴다 — 기본 정보(이름·성별) 아래 특징 칩이 온다.
 * 특징이 필수인 주인공만 라벨에 `*` 가 붙고, 주변 인물은 칩 아래에 랜덤 안내를 둔다.
 */
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
        KeywordSectionLabel(text = stringResource(R.string.create_section_basic_info), required = false)
        KeywordTextField(
            value = character.name,
            onValueChange = { name -> onIntent(CreateKeywordIntent.ChangeCharacterName(target, name)) },
            placeholder = namePlaceholder,
            isError = isDuplicateName,
            trailing = {
                InputCounter(
                    length = character.name.length,
                    maxLength = CreateKeywordUiState.CHARACTER_NAME_MAX_LENGTH,
                )
            },
        )
        if (isDuplicateName) {
            Text(
                text = stringResource(R.string.create_error_duplicate_name),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textDanger,
            )
        }
        GenderSelectField(
            gender = character.gender,
            onGenderChange = { gender -> onIntent(CreateKeywordIntent.ChangeCharacterGender(target, gender)) },
        )
        Text(
            text = stringResource(R.string.create_character_random_hint),
            style = ManyakTheme.typography.bodySmall,
            color = ManyakTheme.colors.textSubtle,
        )

        KeywordSectionLabel(
            modifier = Modifier.padding(top = ManyakTheme.spacing.component),
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
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

/**
 * 제공 태그·커스텀 태그 칩과 "키워드 추가" 트리거. 로딩은 스켈레톤으로, 실패는 인라인 오류로
 * 표시하되 직접 추가는 계속 할 수 있다.
 */
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
        if (providedTags is ProvidedTags.Failed) {
            Text(
                text = stringResource(R.string.create_tags_load_failed),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textDanger,
            )
        }
        when (providedTags) {
            ProvidedTags.Loading -> KeywordChipSkeleton()

            is ProvidedTags.Loaded, ProvidedTags.Failed -> {
                val tags = (providedTags as? ProvidedTags.Loaded)?.byCategory[target.category].orEmpty()
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
                    AddKeywordTrigger(enabled = !atSelectionCap, onClick = { onOpenAddKeyword(target) })
                }
            }
        }
    }
}

/**
 * 주변 인물 목록. 카드마다 삭제 버튼이 있어 0명까지 줄일 수 있고, "인물 추가"로 5명까지 늘린다.
 * 항목을 채우지 않은 카드도 인원으로 센다.
 */
@Composable
internal fun SupportingCharacterList(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
    onOpenAddKeyword: (KeywordTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val namePlaceholders = stringArrayResource(R.array.create_name_placeholders_supporting)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        state.supportingCharacters.forEachIndexed { index, character ->
            SupportingCharacterCard(
                orderLabel = stringResource(R.string.create_supporting_card_label, index + 1),
                target = KeywordTarget.Supporting(character.id),
                character = character,
                namePlaceholder = namePlaceholders[index % namePlaceholders.size],
                isDuplicateName = character.id in state.duplicateNameCharacterIds,
                providedTags = state.providedTags,
                atSelectionCap = state.isAtSelectionCap(KeywordTarget.Supporting(character.id)),
                onIntent = onIntent,
                onOpenAddKeyword = onOpenAddKeyword,
            )
        }
        Button(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ManyakTheme.sizes.control),
            onClick = { onIntent(CreateKeywordIntent.AddSupportingCharacter) },
            enabled = state.supportingCharacters.size < CreateKeywordUiState.SUPPORTING_CHARACTER_MAX,
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.backgroundNeutral,
                    contentColor = ManyakTheme.colors.text,
                    disabledContainerColor = ManyakTheme.colors.backgroundDisabled,
                    disabledContentColor = ManyakTheme.colors.textDisabled,
                ),
        ) {
            Text(text = stringResource(R.string.create_add_character), style = ManyakTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SupportingCharacterCard(
    orderLabel: String,
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
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, ManyakTheme.colors.border, ManyakTheme.shapes.card)
                .padding(ManyakTheme.spacing.component),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = orderLabel,
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.text,
            )
            IconButton(
                onClick = {
                    (target as? KeywordTarget.Supporting)?.let {
                        onIntent(CreateKeywordIntent.RemoveSupportingCharacter(it.characterId))
                    }
                },
            ) {
                Icon(
                    modifier = Modifier.size(ManyakTheme.sizes.icon),
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.create_supporting_delete, orderLabel),
                    tint = ManyakTheme.colors.textSubtle,
                )
            }
        }
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
        )
    }
}
