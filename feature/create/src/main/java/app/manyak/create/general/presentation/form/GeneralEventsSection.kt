package app.manyak.create.general.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralMainEvent
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.presentation.component.CollapsibleInputSection
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR

@Composable
internal fun GeneralFormScope.EventsSection(
    collapsed: List<String>,
    onToggle: (String) -> Unit,
    onDelete: (String, Boolean) -> Unit,
    onAdded: (GeneralFieldTarget) -> Unit,
) {
    Column {
        Column(
            Modifier
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.gutter, bottom = ManyakTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            KeywordSectionLabel(stringResource(CreateR.string.general_events), required = false)
            GeneralFieldHint(null, stringResource(CreateR.string.general_events_hint))
        }
        form.mainEvents.forEachIndexed { index, event ->
            key(event.id) {
                CollapsibleInputSection(
                    headerLabel =
                        event.name.trim().ifEmpty {
                            stringResource(CreateR.string.general_event_fallback, index + 1)
                        },
                    countLabel = stringResource(CreateR.string.general_item_count, index + 1, EVENT_MAX),
                    expanded = event.id !in collapsed,
                    onToggle = { onToggle(event.id) },
                    onDelete = { onDelete(event.id, event.hasInput) },
                    contentSpacing = ManyakTheme.spacing.section,
                ) { EventFields(event) }
            }
        }
        GeneralListAddButton(
            label = stringResource(CreateR.string.general_add_event),
            enabled = enabled && form.mainEvents.size < EVENT_MAX,
            lastCollapsed = form.mainEvents.lastOrNull()?.id in collapsed,
            onClick = {
                val event = GeneralMainEvent()
                onChange(form.copy(mainEvents = form.mainEvents + event))
                onAdded(GeneralFieldTarget(GeneralTab.EVENTS, GeneralField.NAME, event.id))
            },
        )
    }
}

@Composable
private fun GeneralFormScope.EventFields(event: GeneralMainEvent) {
    val target = GeneralFieldTarget(GeneralTab.EVENTS, GeneralField.NAME, event.id)
    Input(
        target,
        event.name,
        NAME_MAX_LENGTH,
        { updateEvent(event.copy(name = it)) },
        label = stringResource(CreateR.string.general_event_name),
        placeholder = stringResource(CreateR.string.general_event_name_placeholder),
        hint = stringResource(CreateR.string.general_event_name_hint),
    )
    Input(
        target.copy(field = GeneralField.EVENT_DESCRIPTION),
        event.description,
        DESCRIPTION_MAX_LENGTH,
        { updateEvent(event.copy(description = it)) },
        height = GeneralTextHeight.LONG,
        placeholder = stringResource(CreateR.string.general_event_description_placeholder),
        hint = stringResource(CreateR.string.general_event_description_hint),
    )
    Input(
        target.copy(field = GeneralField.KEY_SENTENCE),
        event.keySentence,
        KEY_SENTENCE_MAX_LENGTH,
        { updateEvent(event.copy(keySentence = it)) },
        height = GeneralTextHeight.SHORT,
        placeholder = stringResource(CreateR.string.general_key_sentence_placeholder),
        hint = stringResource(CreateR.string.general_key_sentence_hint),
    )
}

private fun GeneralFormScope.updateEvent(event: GeneralMainEvent) {
    onChange(form.copy(mainEvents = form.mainEvents.map { if (it.id == event.id) event else it }))
}

private const val EVENT_MAX = 10
private const val NAME_MAX_LENGTH = 100
private const val DESCRIPTION_MAX_LENGTH = 1000
private const val KEY_SENTENCE_MAX_LENGTH = 200
