package app.manyak.create.general.presentation.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.manyak.create.general.entity.GeneralEnding
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStartSetting
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.presentation.component.AddTrigger
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
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block)) {
        StartChips(selected.id, onSelect, onDelete)
        key(selected.id) {
            val target = GeneralFieldTarget(GeneralTab.START, GeneralField.NAME, selected.id, selected.id)
            Input(target, selected.name, 100, {
                updateStart(selected.copy(name = it))
            }, label = stringResource(CreateR.string.general_start_name))
            Input(target.copy(field = GeneralField.PROLOGUE), selected.prologue, 1000, {
                updateStart(selected.copy(prologue = it))
            }, multiline = true)
            Input(target.copy(field = GeneralField.SITUATION), selected.situation, 1000, {
                updateStart(selected.copy(situation = it))
            }, multiline = true)
            SuggestedInputs(selected, target)
            Endings(selected, collapsed, onToggle, onDelete, onAdded)
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
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        form.startSettings.forEachIndexed { index, start ->
            val fallback = stringResource(CreateR.string.general_start_fallback, index + 1)
            val fullLabel = start.name.trim().ifEmpty { fallback }
            val label = if (fullLabel.length > 8) fullLabel.take(8) + "…" else fullLabel
            val selected = start.id == selectedId
            val hasError =
                errors.any { it.target.startId == start.id } || serverErrors.keys.any { it.startId == start.id }
            Row(
                Modifier
                    .clip(ManyakTheme.shapes.pill)
                    .background(if (selected) ManyakTheme.colors.brand else ManyakTheme.colors.backgroundNeutral)
                    .heightIn(min = ManyakTheme.sizes.input)
                    .clickable(enabled = enabled, role = Role.Tab, onClick = { onSelect(start.id) })
                    .padding(start = ManyakTheme.spacing.controlHorizontal, end = ManyakTheme.spacing.compact),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = ManyakTheme.typography.labelLarge,
                    color =
                        when {
                            selected -> ManyakTheme.colors.textInverse
                            hasError -> ManyakTheme.colors.textDanger
                            else -> ManyakTheme.colors.text
                        },
                    modifier = Modifier.padding(end = ManyakTheme.spacing.compact),
                )
                if (index > 0) {
                    ManyakIconButton(
                        iconRes = DesignR.drawable.ic_close,
                        contentDescription = stringResource(CreateR.string.general_remove_named, fullLabel),
                        onClick = { onDelete(start.id, start.hasInput) },
                        enabled = enabled,
                        size = ManyakTheme.sizes.controlSmall,
                        iconSize = ManyakTheme.sizes.iconSmall,
                        tint = if (selected) ManyakTheme.colors.textInverse else ManyakTheme.colors.textSubtle,
                    )
                }
            }
        }
        AddTrigger(stringResource(CreateR.string.general_add), enabled && form.startSettings.size < 3, {
            val start = GeneralStartSetting()
            onChange(form.copy(startSettings = form.startSettings + start))
            onSelect(start.id)
        })
    }
}

@Composable
private fun GeneralFormScope.SuggestedInputs(
    start: GeneralStartSetting,
    target: GeneralFieldTarget,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        repeat(3) { index ->
            Input(
                target.copy(field = GeneralField.SUGGESTED_INPUT, inputIndex = index),
                start.suggestedInputs.getOrElse(index) { "" },
                200,
                { changed ->
                    updateStart(
                        start.copy(
                            suggestedInputs =
                                List(3) {
                                    if (it ==
                                        index
                                    ) {
                                        changed
                                    } else {
                                        start.suggestedInputs.getOrElse(it) { "" }
                                    }
                                },
                        ),
                    )
                },
                label = stringResource(CreateR.string.general_suggested_number, index + 1),
                showHint = false,
            )
        }
        val message =
            (0..2).firstNotNullOfOrNull { index ->
                message(target.copy(field = GeneralField.SUGGESTED_INPUT, inputIndex = index))
            }
        GeneralFieldHint(message, stringResource(CreateR.string.general_suggested_hint))
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
    Column {
        start.endings.forEachIndexed { index, ending ->
            key(ending.id) {
                GeneralRepeatedSection(
                    title = ending.name.ifBlank { stringResource(CreateR.string.general_ending_fallback, index + 1) },
                    index = index,
                    maximum = 3,
                    expanded = ending.id !in collapsed,
                    enabled = enabled,
                    onToggle = { onToggle(ending.id) },
                    onDelete = { onDelete(ending.id, ending.hasInput) },
                ) {
                    EndingFields(ending, start.id) { changed ->
                        updateStart(start.copy(endings = start.endings.map { if (it.id == ending.id) changed else it }))
                    }
                }
            }
        }
        AddTrigger(stringResource(CreateR.string.general_add_ending), enabled && start.endings.size < 3, {
            val ending = GeneralEnding()
            updateStart(start.copy(endings = start.endings + ending))
            onAdded(GeneralFieldTarget(GeneralTab.START, GeneralField.NAME, ending.id, start.id))
        })
    }
}

@Composable
private fun GeneralFormScope.EndingFields(
    ending: GeneralEnding,
    startId: String,
    change: (GeneralEnding) -> Unit,
) {
    val target = GeneralFieldTarget(GeneralTab.START, GeneralField.NAME, ending.id, startId)
    Input(target, ending.name, 100, {
        change(ending.copy(name = it))
    }, label = stringResource(CreateR.string.general_ending_name))
    Input(target.copy(field = GeneralField.MIN_TURNS), ending.minTurns, 2, {
        val digits = it.filter(Char::isDigit)
        val turns = if (digits.isEmpty()) "" else (digits.toIntOrNull() ?: 50).coerceAtMost(50).toString()
        change(ending.copy(minTurns = turns))
    })
    Input(target.copy(field = GeneralField.CONDITION), ending.condition, 500, {
        change(ending.copy(condition = it))
    }, multiline = true)
    Input(target.copy(field = GeneralField.EPILOGUE), ending.epilogue, 500, {
        change(ending.copy(epilogue = it))
    }, multiline = true)
}

private fun GeneralFormScope.updateStart(start: GeneralStartSetting) {
    onChange(form.copy(startSettings = form.startSettings.map { if (it.id == start.id) start else it }))
}
