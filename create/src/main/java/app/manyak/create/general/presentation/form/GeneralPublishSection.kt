package app.manyak.create.general.presentation.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralTab
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR
import app.manyak.designsystem.R as DesignR

@Composable
internal fun GeneralFormScope.PublishSection(genres: GeneralGenreUi) {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block)) {
        GenreField(genres)
        Input(
            GeneralFieldTarget(GeneralTab.PUBLISH, GeneralField.DESCRIPTION),
            form.description,
            1000,
            { onChange(form.copy(description = it)) },
            required = false,
            multiline = true,
        )
        VisibilityField()
        Row(
            Modifier
                .fillMaxWidth()
                .background(ManyakTheme.colors.backgroundNeutral, ManyakTheme.shapes.control)
                .padding(ManyakTheme.spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            Icon(
                painterResource(DesignR.drawable.ic_alert_triangle),
                null,
                Modifier.size(ManyakTheme.sizes.icon),
                tint = ManyakTheme.colors.textSubtle,
            )
            Text(
                stringResource(CreateR.string.general_moderation_notice),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
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
            Text(label, style = ManyakTheme.typography.labelLarge, color = ManyakTheme.colors.text)
            Text(
                stringResource(CreateR.string.general_visibility_hint),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
        Switch(
            checked = form.visibility == "PUBLIC",
            enabled = enabled,
            onCheckedChange = { onChange(form.copy(visibility = if (it) "PUBLIC" else "PRIVATE")) },
            colors = SwitchDefaults.colors(checkedTrackColor = ManyakTheme.colors.brand),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}
