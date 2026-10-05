package app.manyak.create.general.presentation.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR
import app.manyak.designsystem.R as DesignR

@Composable
internal fun GeneralRepeatedSection(
    title: String,
    index: Int,
    maximum: Int,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        GeneralRepeatedHeader(title, index, maximum, expanded, enabled, onToggle, onDelete)
        val sizeSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold)
        val fadeSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(sizeSpec, expandFrom = Alignment.Top) + fadeIn(fadeSpec),
            exit = shrinkVertically(sizeSpec, shrinkTowards = Alignment.Top) + fadeOut(fadeSpec),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = ManyakTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
            ) { content() }
        }
    }
}

@Composable
internal fun GeneralDeleteDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.overlay,
        title = {
            Text(
                stringResource(CreateR.string.create_remove_input_title),
                style = ManyakTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                stringResource(CreateR.string.create_remove_input_description),
                style = ManyakTheme.typography.bodyMedium,
            )
        },
        dismissButton = {
            ManyakTextButton(onClick = onDismiss) { Text(stringResource(CreateR.string.create_remove_input_cancel)) }
        },
        confirmButton = {
            ManyakTextButton(onClick = onDelete) {
                Text(stringResource(CreateR.string.create_remove_input_confirm), color = ManyakTheme.colors.textDanger)
            }
        },
    )
}

@Composable
private fun GeneralRepeatedHeader(
    title: String,
    index: Int,
    maximum: Int,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, spring(stiffness = Spring.StiffnessMediumLow))
    Row(
        Modifier
            .fillMaxWidth()
            .background(ManyakTheme.colors.backgroundNeutral)
            .padding(horizontal = ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ManyakTheme.typography.labelLarge,
            color = ManyakTheme.colors.textSubtle,
        )
        Text(
            stringResource(CreateR.string.general_item_count, index + 1, maximum),
            style = ManyakTheme.typography.labelSmall,
            color = ManyakTheme.colors.textSubtle,
        )
        ManyakTextButton(
            modifier = Modifier.heightIn(min = ManyakTheme.sizes.controlSmall),
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.inline),
        ) {
            Text(
                stringResource(
                    if (expanded) CreateR.string.create_section_collapse else CreateR.string.create_section_expand,
                ),
                style = ManyakTheme.typography.labelSmall,
                color = ManyakTheme.colors.textSubtle,
            )
            Icon(
                painterResource(DesignR.drawable.ic_chevron_down),
                null,
                Modifier.size(ManyakTheme.sizes.iconTiny).rotate(rotation),
                tint = ManyakTheme.colors.textSubtle,
            )
        }
        if (onDelete != null) {
            ManyakTextButton(
                enabled = enabled,
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.inline),
            ) {
                Text(
                    stringResource(CreateR.string.create_supporting_delete),
                    style = ManyakTheme.typography.labelSmall,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
        }
    }
}
