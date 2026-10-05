package app.manyak.create.general.presentation.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.manyak.create.general.entity.GeneralEnding
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStartSetting
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.presentation.component.AddTrigger
import app.manyak.create.presentation.component.CollapsibleInputSection
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR
import app.manyak.designsystem.R as DesignR

@Composable
internal fun GeneralFormScope.StartSection(
    selectedId: String?,
    onSelect: (String) -> Unit,
    collapsed: List<String>,
    onToggle: (String) -> Unit,
    onDelete: (String, Boolean) -> Unit,
    onAdded: (GeneralFieldTarget) -> Unit,
) {
    val selected = form.startSettings.firstOrNull { it.id == selectedId } ?: form.startSettings.firstOrNull() ?: return
    Column {
        StartChips(selected.id, onSelect, onDelete)
        key(selected.id) {
            StartFields(selected)
            Endings(selected, collapsed, onToggle, onDelete, onAdded)
        }
    }
}

@Composable
private fun GeneralFormScope.StartFields(selected: GeneralStartSetting) {
    val target = GeneralFieldTarget(GeneralTab.START, GeneralField.NAME, selected.id, selected.id)
    Column(
        Modifier.padding(horizontal = ManyakTheme.spacing.gutter).padding(top = ManyakTheme.spacing.compact),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section),
    ) {
        Input(
            target,
            selected.name,
            NAME_MAX_LENGTH,
            { updateStart(selected.copy(name = it)) },
            label = stringResource(CreateR.string.general_start_name),
            placeholder = stringResource(CreateR.string.general_start_name_placeholder),
            hint = stringResource(CreateR.string.general_start_name_hint),
        )
        Input(
            target.copy(field = GeneralField.PROLOGUE),
            selected.prologue,
            LONG_TEXT_MAX_LENGTH,
            { updateStart(selected.copy(prologue = it)) },
            height = GeneralTextHeight.LONG,
            placeholder = stringResource(CreateR.string.general_prologue_placeholder),
            hint = stringResource(CreateR.string.general_prologue_hint),
        )
        Input(
            target.copy(field = GeneralField.SITUATION),
            selected.situation,
            LONG_TEXT_MAX_LENGTH,
            { updateStart(selected.copy(situation = it)) },
            height = GeneralTextHeight.LONG,
            placeholder = stringResource(CreateR.string.general_situation_placeholder),
            hint = stringResource(CreateR.string.general_situation_hint),
        )
        SuggestedInputs(selected, target)
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            KeywordSectionLabel(stringResource(CreateR.string.general_ending), required = false)
            GeneralFieldHint(null, stringResource(CreateR.string.general_ending_hint))
        }
    }
}

@Composable
private fun GeneralFormScope.StartChips(
    selectedId: String,
    onSelect: (String) -> Unit,
    onDelete: (String, Boolean) -> Unit,
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ManyakTheme.spacing.gutter)
            .padding(top = ManyakTheme.spacing.gutter, bottom = ManyakTheme.spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        form.startSettings.forEachIndexed { index, start ->
            val fallback = stringResource(CreateR.string.general_start_fallback, index + 1)
            val fullLabel = start.name.trim().ifEmpty { fallback }
            val label =
                if (fullLabel.length >
                    CHIP_LABEL_MAX_LENGTH
                ) {
                    fullLabel.take(CHIP_LABEL_MAX_LENGTH) + "…"
                } else {
                    fullLabel
                }
            val hasError =
                errors.any { it.target.startId == start.id } || serverErrors.keys.any { it.startId == start.id }
            StartChip(
                label = label,
                selected = start.id == selectedId,
                hasError = hasError,
                onClick = { onSelect(start.id) },
                onRemove = if (index > 0) ({ onDelete(start.id, start.hasInput) }) else null,
            )
        }
        AddTrigger(
            label = stringResource(CreateR.string.general_add),
            enabled = enabled && form.startSettings.size < START_MAX,
            shape = ManyakTheme.shapes.pill,
            onClick = {
                val start = GeneralStartSetting()
                onChange(form.copy(startSettings = form.startSettings + start))
                onSelect(start.id)
            },
        )
    }
}

/** 시작 상황 칩. 고른 칩은 주 색으로 채우고, 지울 수 있는 칩은 오른쪽 끝에 지우기를 둔다. */
@Composable
private fun GeneralFormScope.StartChip(
    label: String,
    selected: Boolean,
    hasError: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val shape = ManyakTheme.shapes.pill
    val contentColor =
        when {
            selected -> ManyakTheme.colors.textInverse
            hasError -> ManyakTheme.colors.textDanger
            else -> ManyakTheme.colors.text
        }
    Row(
        Modifier
            .heightIn(min = ManyakTheme.sizes.input)
            .clip(shape)
            .background(if (selected) ManyakTheme.colors.brand else ManyakTheme.colors.surfaceRaised)
            .border(
                ManyakTheme.sizes.inputBorderWidth,
                if (selected) ManyakTheme.colors.brand else ManyakTheme.colors.border,
                shape,
            ).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            ).semantics { this.selected = selected }
            .padding(
                start = ManyakTheme.spacing.controlHorizontal,
                end = if (onRemove == null) ManyakTheme.spacing.controlHorizontal else ManyakTheme.spacing.compact,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Text(label, style = ManyakTheme.typography.bodyMedium, color = contentColor, maxLines = 1)
        if (onRemove != null) {
            ManyakIconButton(
                iconRes = DesignR.drawable.ic_close,
                contentDescription = stringResource(CreateR.string.general_remove_named, label),
                onClick = onRemove,
                enabled = enabled,
                size = ManyakTheme.sizes.tabIcon,
                iconSize = ManyakTheme.sizes.iconSmall,
                tint = if (selected) ManyakTheme.colors.textInverse else ManyakTheme.colors.textSubtle,
            )
        }
    }
}

@Composable
private fun GeneralFormScope.SuggestedInputs(
    start: GeneralStartSetting,
    target: GeneralFieldTarget,
) {
    val placeholders = stringArrayResource(CreateR.array.general_suggested_input_placeholders)
    val label = stringResource(CreateR.string.general_suggested_input)
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        KeywordSectionLabel(label, required = true)
        repeat(SUGGESTED_INPUT_COUNT) { index ->
            Input(
                target.copy(field = GeneralField.SUGGESTED_INPUT, inputIndex = index),
                start.suggestedInputs.getOrElse(index) { "" },
                SUGGESTED_INPUT_MAX_LENGTH,
                { changed ->
                    val inputs = List(SUGGESTED_INPUT_COUNT) { start.suggestedInputs.getOrElse(it) { "" } }
                    updateStart(
                        start.copy(
                            suggestedInputs =
                                inputs.mapIndexed { i, old ->
                                    if (i ==
                                        index
                                    ) {
                                        changed
                                    } else {
                                        old
                                    }
                                },
                        ),
                    )
                },
                height = GeneralTextHeight.SUGGESTED,
                placeholder = placeholders[index],
                label = stringResource(CreateR.string.general_suggested_number, index + 1),
                showLabel = false,
                showHint = false,
            )
        }
        val message =
            (0 until SUGGESTED_INPUT_COUNT).firstNotNullOfOrNull { index ->
                message(target.copy(field = GeneralField.SUGGESTED_INPUT, inputIndex = index))
            }
        GeneralFieldHint(message, stringResource(CreateR.string.general_suggested_input_hint))
    }
}

@Composable
private fun GeneralFormScope.Endings(
    start: GeneralStartSetting,
    collapsed: List<String>,
    onToggle: (String) -> Unit,
    onDelete: (String, Boolean) -> Unit,
    onAdded: (GeneralFieldTarget) -> Unit,
) {
    Column(Modifier.padding(top = ManyakTheme.spacing.gutter)) {
        start.endings.forEachIndexed { index, ending ->
            key(ending.id) {
                CollapsibleInputSection(
                    headerLabel =
                        ending.name.trim().ifEmpty {
                            stringResource(CreateR.string.general_ending_fallback, index + 1)
                        },
                    countLabel = stringResource(CreateR.string.general_item_count, index + 1, ENDING_MAX),
                    expanded = ending.id !in collapsed,
                    onToggle = { onToggle(ending.id) },
                    onDelete = { onDelete(ending.id, ending.hasInput) },
                    contentSpacing = ManyakTheme.spacing.section,
                ) {
                    EndingFields(ending, start.id) { changed ->
                        updateStart(start.copy(endings = start.endings.map { if (it.id == ending.id) changed else it }))
                    }
                }
            }
        }
        GeneralListAddButton(
            label = stringResource(CreateR.string.general_add_ending),
            enabled = enabled && start.endings.size < ENDING_MAX,
            lastCollapsed = start.endings.lastOrNull()?.id in collapsed,
            onClick = {
                val ending = GeneralEnding()
                updateStart(start.copy(endings = start.endings + ending))
                onAdded(GeneralFieldTarget(GeneralTab.START, GeneralField.NAME, ending.id, start.id))
            },
        )
    }
}

@Composable
private fun GeneralFormScope.EndingFields(
    ending: GeneralEnding,
    startId: String,
    change: (GeneralEnding) -> Unit,
) {
    val target = GeneralFieldTarget(GeneralTab.START, GeneralField.NAME, ending.id, startId)
    Input(
        target,
        ending.name,
        NAME_MAX_LENGTH,
        { change(ending.copy(name = it)) },
        label = stringResource(CreateR.string.general_ending_name),
        placeholder = stringResource(CreateR.string.general_ending_name_placeholder),
        hint = stringResource(CreateR.string.general_ending_name_hint),
    )
    Input(
        target.copy(field = GeneralField.MIN_TURNS),
        ending.minTurns,
        MIN_TURNS_MAX_DIGITS,
        {
            val digits = it.filter(Char::isDigit)
            val turns =
                if (digits.isEmpty()) {
                    ""
                } else {
                    (digits.toIntOrNull() ?: MIN_TURNS_MAX)
                        .coerceAtMost(
                            MIN_TURNS_MAX,
                        ).toString()
                }
            change(ending.copy(minTurns = turns))
        },
        placeholder = stringResource(CreateR.string.general_min_turns_placeholder),
        hint = stringResource(CreateR.string.general_min_turns_hint),
        suffix = stringResource(CreateR.string.general_min_turns_unit),
    )
    Input(
        target.copy(field = GeneralField.CONDITION),
        ending.condition,
        ENDING_TEXT_MAX_LENGTH,
        { change(ending.copy(condition = it)) },
        height = GeneralTextHeight.SHORT,
        placeholder = stringResource(CreateR.string.general_condition_placeholder),
        hint = stringResource(CreateR.string.general_condition_hint),
    )
    Input(
        target.copy(field = GeneralField.EPILOGUE),
        ending.epilogue,
        ENDING_TEXT_MAX_LENGTH,
        { change(ending.copy(epilogue = it)) },
        height = GeneralTextHeight.SHORT,
        placeholder = stringResource(CreateR.string.general_epilogue_placeholder),
        hint = stringResource(CreateR.string.general_epilogue_hint),
    )
}

private fun GeneralFormScope.updateStart(start: GeneralStartSetting) {
    onChange(form.copy(startSettings = form.startSettings.map { if (it.id == start.id) start else it }))
}

private const val START_MAX = 3
private const val ENDING_MAX = 3
private const val CHIP_LABEL_MAX_LENGTH = 8
private const val NAME_MAX_LENGTH = 100
private const val LONG_TEXT_MAX_LENGTH = 1000
private const val ENDING_TEXT_MAX_LENGTH = 500
private const val SUGGESTED_INPUT_COUNT = 3
private const val SUGGESTED_INPUT_MAX_LENGTH = 200
private const val MIN_TURNS_MAX = 50
private const val MIN_TURNS_MAX_DIGITS = 2
