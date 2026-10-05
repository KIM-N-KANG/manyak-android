package app.manyak.create.general.presentation.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.component.ManyakSwitch
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR

@Composable
internal fun GeneralFormScope.PublishSection(genres: GeneralGenreUi) {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section)) {
        GenreField(genres)
        Input(
            GeneralFieldTarget(GeneralTab.PUBLISH, GeneralField.DESCRIPTION),
            form.description,
            DESCRIPTION_MAX_LENGTH,
            { onChange(form.copy(description = it)) },
            required = false,
            height = GeneralTextHeight.LONG,
            placeholder = stringResource(CreateR.string.general_description_placeholder),
            hint = stringResource(CreateR.string.general_description_hint),
        )
        VisibilityField()
        GeneralNotice(stringResource(CreateR.string.general_moderation_notice))
    }
}

/** 둥근 회색 상자 안의 주의 문구. */
@Composable
internal fun GeneralNotice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(ManyakTheme.colors.backgroundNeutral, ManyakTheme.shapes.card)
            .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.component),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Icon(
            painterResource(CreateR.drawable.ic_alert_circle),
            null,
            Modifier.size(ManyakTheme.sizes.iconSmall),
            tint = ManyakTheme.colors.textSubtle,
        )
        Text(text, style = ManyakTheme.typography.bodyMedium, color = ManyakTheme.colors.textSubtle)
    }
}

@Composable
private fun GeneralFormScope.VisibilityField() {
    val label = stringResource(CreateR.string.general_visibility)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            KeywordSectionLabel(label, required = false)
            GeneralFieldHint(null, stringResource(CreateR.string.general_visibility_hint))
        }
        ManyakSwitch(
            checked = form.visibility == "PUBLIC",
            enabled = enabled,
            onCheckedChange = { onChange(form.copy(visibility = if (it) "PUBLIC" else "PRIVATE")) },
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

private const val DESCRIPTION_MAX_LENGTH = 1000
