package app.manyak.create.general.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralMainEvent
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.presentation.component.AddTrigger
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR

@Composable
internal fun GeneralFormScope.EventsSection(
    collapsed: List<String>,
    onToggle: (String) -> Unit,
    onDelete: (String, Boolean) -> Unit,
    onAdded: (GeneralFieldTarget) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter)) {
        Text(
            stringResource(CreateR.string.general_events_hint),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
        Column {
            form.mainEvents.forEachIndexed { index, event ->
                key(event.id) {
                    GeneralRepeatedSection(
                        title = event.name.ifBlank { stringResource(CreateR.string.general_event_fallback, index + 1) },
                        index = index,
                        maximum = 10,
                        expanded = event.id !in collapsed,
                        enabled = enabled,
                        onToggle = { onToggle(event.id) },
                        onDelete = { onDelete(event.id, event.hasInput) },
                    ) {
                        val target = GeneralFieldTarget(GeneralTab.EVENTS, GeneralField.NAME, event.id)
                        Input(target, event.name, 100, {
                            updateEvent(event.copy(name = it))
                        }, label = stringResource(CreateR.string.general_event_name))
                        Input(target.copy(field = GeneralField.EVENT_DESCRIPTION), event.description, 1000, {
                            updateEvent(event.copy(description = it))
                        }, multiline = true)
                        Input(target.copy(field = GeneralField.KEY_SENTENCE), event.keySentence, 200, {
                            updateEvent(event.copy(keySentence = it))
                        }, multiline = true)
                    }
                }
            }
        }
        AddTrigger(stringResource(CreateR.string.general_add_event), enabled && form.mainEvents.size < 10, {
            val event = GeneralMainEvent()
            onChange(form.copy(mainEvents = form.mainEvents + event))
            onAdded(GeneralFieldTarget(GeneralTab.EVENTS, GeneralField.NAME, event.id))
        })
    }
}

private fun GeneralFormScope.updateEvent(event: GeneralMainEvent) {
    onChange(form.copy(mainEvents = form.mainEvents.map { if (it.id == event.id) event else it }))
}
