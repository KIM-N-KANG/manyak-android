package app.manyak.create.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR
import app.manyak.designsystem.R as DesignR

@Composable
internal fun CollapsibleInputHeader(
    headerLabel: String,
    countLabel: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "section-chevron")
    val toggleLabel =
        stringResource(if (expanded) CreateR.string.create_section_collapse else CreateR.string.create_section_expand)
    val deleteDescription = stringResource(CreateR.string.create_supporting_delete_description, headerLabel)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ManyakTheme.colors.backgroundNeutral)
                .padding(horizontal = ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InputHeaderLabel(headerLabel, countLabel, Modifier.weight(1f))
        ManyakTextButton(
            modifier = Modifier.height(ManyakTheme.sizes.controlSmall),
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.compact),
        ) {
            Text(text = toggleLabel, style = ManyakTheme.typography.labelSmall, color = ManyakTheme.colors.textSubtle)
            Icon(
                painter = painterResource(DesignR.drawable.ic_chevron_down),
                contentDescription = null,
                tint = ManyakTheme.colors.textSubtle,
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall).rotate(rotation),
            )
        }
        ManyakTextButton(
            modifier =
                Modifier
                    .width(ManyakTheme.sizes.control)
                    .height(ManyakTheme.sizes.controlSmall)
                    .semantics { contentDescription = deleteDescription },
            onClick = onDelete,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(CreateR.string.create_supporting_delete),
                style = ManyakTheme.typography.labelSmall,
                color = ManyakTheme.colors.textSubtle,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun InputHeaderLabel(
    headerLabel: String,
    countLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = headerLabel,
            style = ManyakTheme.typography.labelLarge,
            color = ManyakTheme.colors.textSubtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            modifier =
                Modifier
                    .clip(ManyakTheme.shapes.pill)
                    .background(ManyakTheme.colors.backgroundNeutralPressed)
                    .padding(
                        horizontal = ManyakTheme.spacing.compact,
                        vertical = ManyakTheme.spacing.inline,
                    ),
            text = countLabel,
            style = ManyakTheme.typography.bodySmall,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}
