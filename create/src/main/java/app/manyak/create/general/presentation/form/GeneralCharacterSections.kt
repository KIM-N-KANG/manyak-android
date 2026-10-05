package app.manyak.create.general.presentation.form

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralGender
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.presentation.component.AddTrigger
import app.manyak.create.presentation.component.CollapsibleInputSection
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.component.ManyakInputCounter
import app.manyak.designsystem.component.ManyakSelectField
import app.manyak.designsystem.component.ManyakSelectOption
import app.manyak.designsystem.component.ManyakTextField
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR

@Composable
internal fun GeneralFormScope.ProtagonistSection() {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section)) {
        CharacterFields(
            GeneralTab.PROTAGONIST,
            form.protagonist,
            stringResource(CreateR.string.general_protagonist_name_placeholder),
        ) { onChange(form.copy(protagonist = it)) }
    }
}

@Composable
internal fun GeneralFormScope.SupportingSection(
    collapsed: List<String>,
    onToggle: (String) -> Unit,
    onDelete: (String, Boolean) -> Unit,
    onAdded: (GeneralFieldTarget) -> Unit,
) {
    val namePlaceholders = stringArrayResource(CreateR.array.general_supporting_name_placeholders)
    Column {
        form.supporting.forEachIndexed { index, character ->
            key(character.id) {
                SupportingItem(
                    character,
                    index,
                    namePlaceholders[index % namePlaceholders.size],
                    character.id !in collapsed,
                    onToggle,
                    onDelete,
                )
            }
        }
        GeneralListAddButton(
            label = stringResource(CreateR.string.create_add_character),
            enabled = enabled && form.supporting.size < SUPPORTING_MAX,
            lastCollapsed = form.supporting.lastOrNull()?.id in collapsed,
            onClick = {
                val added = GeneralCharacter()
                onChange(form.copy(supporting = form.supporting + added))
                onAdded(GeneralFieldTarget(GeneralTab.SUPPORTING, GeneralField.NAME, added.id))
            },
        )
    }
}

/**
 * 접히는 목록 아래의 추가 버튼. 펼친 항목은 아래 여백을 깔고 있으니 마지막 항목이 접혔을 때만 간격을 채운다.
 * 접히는 동안 간격이 한 번에 붙으면 버튼이 툭 밀려서 항목과 같은 스프링으로 채운다.
 */
@Composable
internal fun GeneralListAddButton(
    label: String,
    enabled: Boolean,
    lastCollapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alwaysSpaced: Boolean = false,
) {
    val gap by animateDpAsState(
        targetValue = if (alwaysSpaced || lastCollapsed) ManyakTheme.spacing.gutter else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Dp.VisibilityThreshold),
        label = "general-list-add-gap",
    )
    Box(modifier.fillMaxWidth().padding(top = gap), contentAlignment = Alignment.Center) {
        AddTrigger(label = label, enabled = enabled, onClick = onClick)
    }
}

@Composable
private fun GeneralFormScope.CharacterFields(
    tab: GeneralTab,
    character: GeneralCharacter,
    namePlaceholder: String,
    onChange: (GeneralCharacter) -> Unit,
) {
    val supporting = tab == GeneralTab.SUPPORTING
    CharacterBasicInfo(tab, character, namePlaceholder, onChange)
    if (supporting) {
        Input(
            GeneralFieldTarget(tab, GeneralField.CHARACTER_DESCRIPTION, character.id),
            character.description,
            DESCRIPTION_MAX_LENGTH,
            { onChange(character.copy(description = it)) },
            required = false,
            height = GeneralTextHeight.COMPACT,
            placeholder = stringResource(CreateR.string.general_character_description_placeholder),
            hint = stringResource(CreateR.string.general_character_description_hint),
        )
    }
    Input(
        GeneralFieldTarget(tab, GeneralField.FEATURE, character.id.takeIf { supporting }),
        character.feature,
        FEATURE_MAX_LENGTH,
        { onChange(character.copy(feature = it)) },
        required = !supporting,
        height = GeneralTextHeight.LONG,
        placeholder =
            stringResource(
                if (supporting) {
                    CreateR.string.general_supporting_feature_placeholder
                } else {
                    CreateR.string.general_protagonist_feature_placeholder
                },
            ),
        hint =
            stringResource(
                if (supporting) {
                    CreateR.string.general_supporting_feature_hint
                } else {
                    CreateR.string.general_protagonist_feature_hint
                },
            ),
    )
}

@Composable
private fun GeneralFormScope.CharacterBasicInfo(
    tab: GeneralTab,
    character: GeneralCharacter,
    namePlaceholder: String,
    onChange: (GeneralCharacter) -> Unit,
) {
    val itemId = character.id.takeIf { tab == GeneralTab.SUPPORTING }
    val nameTarget = GeneralFieldTarget(tab, GeneralField.NAME, itemId)
    val genderTarget = GeneralFieldTarget(tab, GeneralField.GENDER, itemId)
    val nameMessage = message(nameTarget)
    val genderMessage = message(genderTarget)
    val label = stringResource(CreateR.string.general_name)
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        KeywordSectionLabel(stringResource(CreateR.string.general_basic_info), required = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            ManyakTextField(
                value = character.name,
                onValueChange = {
                    onChange(character.copy(name = it.replace(Regex("[\\r\\n\\t]"), " ").take(NAME_MAX_LENGTH)))
                },
                placeholder = namePlaceholder,
                enabled = enabled,
                isError = nameMessage != null,
                modifier =
                    Modifier
                        .weight(NAME_WEIGHT)
                        .then(anchor(nameTarget))
                        .semantics {
                            contentDescription = label
                            if (nameMessage != null) error(nameMessage)
                        }.onFocusChanged {
                            if (focused && !it.isFocused) onFieldBlur(nameTarget)
                            focused = it.isFocused
                        },
                trailing = { ManyakInputCounter(character.name.length, NAME_MAX_LENGTH) },
            )
            GenderField(character.gender, genderTarget, genderMessage, Modifier.weight(GENDER_WEIGHT)) {
                onChange(character.copy(gender = it))
            }
        }
        GeneralFieldHint(
            nameMessage ?: genderMessage,
            stringResource(
                if (tab == GeneralTab.SUPPORTING) {
                    CreateR.string.general_supporting_basic_hint
                } else {
                    CreateR.string.general_protagonist_basic_hint
                },
            ),
        )
    }
}

@Composable
private fun GeneralFormScope.GenderField(
    gender: GeneralGender?,
    target: GeneralFieldTarget,
    message: String?,
    modifier: Modifier,
    onChange: (GeneralGender?) -> Unit,
) {
    val label = stringResource(CreateR.string.general_gender)
    // 성별은 필수라 고르지 않은 상태는 메뉴 항목으로 두지 않고 앵커의 흐린 문구로만 보인다.
    val options =
        listOf(
            ManyakSelectOption<GeneralGender?>(GeneralGender.MALE, stringResource(CreateR.string.create_gender_male)),
            ManyakSelectOption<GeneralGender?>(
                GeneralGender.FEMALE,
                stringResource(CreateR.string.create_gender_female),
            ),
        )
    val errorBorder =
        if (message != null) {
            Modifier.border(
                ManyakTheme.sizes.inputBorderWidth,
                ManyakTheme.colors.borderDanger,
                ManyakTheme.shapes.control,
            )
        } else {
            Modifier
        }
    val fieldModifier =
        modifier.then(anchor(target)).then(errorBorder).semantics {
            contentDescription = label
            if (message != null) error(message)
        }
    if (enabled) {
        ManyakSelectField(options, gender, onChange, fieldModifier, placeholder = label)
    } else {
        ManyakTextField(
            options.firstOrNull { it.value == gender }?.label.orEmpty(),
            {},
            label,
            fieldModifier,
            enabled = false,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun GeneralFormScope.SupportingItem(
    character: GeneralCharacter,
    index: Int,
    namePlaceholder: String,
    expanded: Boolean,
    onToggle: (String) -> Unit,
    onDelete: (String, Boolean) -> Unit,
) {
    val update: (GeneralCharacter) -> Unit = { changed ->
        onChange(form.copy(supporting = form.supporting.map { if (it.id == character.id) changed else it }))
    }
    CollapsibleInputSection(
        headerLabel =
            character.name.trim().ifEmpty {
                stringResource(CreateR.string.general_character_fallback, index + 1)
            },
        countLabel = stringResource(CreateR.string.general_item_count, index + 1, SUPPORTING_MAX),
        expanded = expanded,
        onToggle = { onToggle(character.id) },
        onDelete = if (form.supporting.size > 1) ({ onDelete(character.id, character.hasInput) }) else null,
        contentSpacing = ManyakTheme.spacing.section,
    ) {
        ImageField(
            GeneralFieldTarget(GeneralTab.SUPPORTING, GeneralField.IMAGE, character.id),
            character.image,
            CHARACTER_IMAGE_RATIO,
            stringResource(CreateR.string.general_character_image_ratio_hint),
            stringResource(CreateR.string.general_character_image_hint),
        ) { update(character.copy(image = it)) }
        CharacterFields(GeneralTab.SUPPORTING, character, namePlaceholder, update)
    }
}

private const val SUPPORTING_MAX = 5
private const val NAME_MAX_LENGTH = 30
private const val DESCRIPTION_MAX_LENGTH = 150
private const val FEATURE_MAX_LENGTH = 1000
private const val NAME_WEIGHT = 3f
private const val GENDER_WEIGHT = 2f
private const val CHARACTER_IMAGE_RATIO = 4f / 3f
