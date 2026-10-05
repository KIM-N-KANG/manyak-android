package app.manyak.create.general.presentation.form

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralGender
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.presentation.component.AddTrigger
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.component.ManyakInputCounter
import app.manyak.designsystem.component.ManyakSelectField
import app.manyak.designsystem.component.ManyakSelectOption
import app.manyak.designsystem.component.ManyakTextField
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR

@Composable
internal fun GeneralFormScope.ProtagonistSection() {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block)) {
        CharacterFields(GeneralTab.PROTAGONIST, form.protagonist) { onChange(form.copy(protagonist = it)) }
    }
}

@Composable
internal fun GeneralFormScope.SupportingSection(
    collapsed: List<String>,
    onToggle: (String) -> Unit,
    onDelete: (String, Boolean) -> Unit,
    onAdded: (GeneralFieldTarget) -> Unit,
) {
    Column {
        form.supporting.forEachIndexed { index, character ->
            key(character.id) {
                SupportingItem(character, index, collapsed, onToggle, onDelete)
            }
        }
        AddTrigger(
            label = stringResource(CreateR.string.create_add_character),
            enabled = enabled && form.supporting.size < 5,
            onClick = {
                val added = GeneralCharacter()
                onChange(form.copy(supporting = form.supporting + added))
                onAdded(GeneralFieldTarget(GeneralTab.SUPPORTING, GeneralField.NAME, added.id))
            },
        )
    }
}

@Composable
private fun GeneralFormScope.CharacterFields(
    tab: GeneralTab,
    character: GeneralCharacter,
    onChange: (GeneralCharacter) -> Unit,
) {
    CharacterBasicInfo(tab, character, onChange)
    if (tab == GeneralTab.SUPPORTING) {
        Input(
            GeneralFieldTarget(tab, GeneralField.CHARACTER_DESCRIPTION, character.id),
            character.description,
            80,
            { onChange(character.copy(description = it)) },
            required = false,
            hint = stringResource(CreateR.string.general_character_description_hint),
        )
    }
    Input(
        GeneralFieldTarget(
            tab,
            GeneralField.FEATURE,
            character.id.takeIf {
                tab == GeneralTab.SUPPORTING
            },
        ),
        character.feature,
        1000,
        { onChange(character.copy(feature = it)) },
        required = tab == GeneralTab.PROTAGONIST,
        multiline = true,
        hint = stringResource(CreateR.string.general_feature_hint),
    )
}

@Composable
private fun GeneralFormScope.CharacterBasicInfo(
    tab: GeneralTab,
    character: GeneralCharacter,
    onChange: (GeneralCharacter) -> Unit,
) {
    val nameTarget = GeneralFieldTarget(tab, GeneralField.NAME, character.id.takeIf { tab == GeneralTab.SUPPORTING })
    val genderTarget =
        GeneralFieldTarget(tab, GeneralField.GENDER, character.id.takeIf { tab == GeneralTab.SUPPORTING })
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
                    onChange(character.copy(name = it.replace(Regex("[\\r\\n\\t]"), " ").take(30)))
                },
                placeholder = label,
                enabled = enabled,
                isError = nameMessage != null,
                modifier =
                    Modifier
                        .weight(3f)
                        .then(anchor(nameTarget))
                        .semantics {
                            contentDescription = label
                            if (nameMessage != null) error(nameMessage)
                        }.onFocusChanged {
                            if (focused && !it.isFocused) onFieldBlur(nameTarget)
                            focused = it.isFocused
                        },
                trailing = { ManyakInputCounter(character.name.length, 30) },
            )
            GenderField(character.gender, genderTarget, genderMessage, Modifier.weight(2f)) {
                onChange(character.copy(gender = it))
            }
        }
        GeneralFieldHint(nameMessage ?: genderMessage)
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
    val options =
        listOf(
            ManyakSelectOption<GeneralGender?>(null, stringResource(CreateR.string.general_gender_choose)),
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
        ManyakSelectField(options, gender, onChange, fieldModifier, isPlaceholder = gender == null)
    } else {
        ManyakTextField(options.first { it.value == gender }.label, {}, label, fieldModifier, enabled = false)
    }
}

@Composable
private fun GeneralFormScope.SupportingItem(
    character: GeneralCharacter,
    index: Int,
    collapsed: List<String>,
    onToggle: (String) -> Unit,
    onDelete: (String, Boolean) -> Unit,
) {
    GeneralRepeatedSection(
        title =
            character.name.ifBlank {
                stringResource(
                    CreateR.string.general_character_fallback,
                    index + 1,
                )
            },
        index = index,
        maximum = 5,
        expanded = character.id !in collapsed,
        enabled = enabled,
        onToggle = { onToggle(character.id) },
        onDelete = if (form.supporting.size > 1) ({ onDelete(character.id, character.hasInput) }) else null,
    ) {
        ImageField(
            GeneralFieldTarget(GeneralTab.SUPPORTING, GeneralField.IMAGE, character.id),
            character.image,
            4f / 3f,
        ) {
            onChange(
                form.copy(
                    supporting =
                        form.supporting.map { old ->
                            if (old.id ==
                                character.id
                            ) {
                                character.copy(image = it)
                            } else {
                                old
                            }
                        },
                ),
            )
        }
        CharacterFields(GeneralTab.SUPPORTING, character) { changed ->
            onChange(
                form.copy(
                    supporting =
                        form.supporting.map {
                            if (it.id ==
                                character.id
                            ) {
                                changed
                            } else {
                                it
                            }
                        },
                ),
            )
        }
    }
}
