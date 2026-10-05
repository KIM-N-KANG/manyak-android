package app.manyak.create.general.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.presentation.LocalAnalytics
import app.manyak.create.R
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMethodScreen(
    onClose: () -> Unit,
    onSelectSimple: () -> Unit,
    onSelectGeneral: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = LocalAnalytics.current
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.general_method_title), style = ManyakTheme.typography.titleLarge)
            },
            actions = {
                ManyakIconButton(
                    iconRes = DesignsystemR.drawable.ic_close,
                    contentDescription = stringResource(R.string.create_close_funnel),
                    onClick = onClose,
                )
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = ManyakTheme.colors.surface),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ManyakTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section),
        ) {
            Spacer(Modifier.weight(1f))
            MethodCard(
                title = stringResource(R.string.general_method_simple),
                description = stringResource(R.string.general_method_simple_description),
                onClick = {
                    analytics.track(AnalyticsEvent.StoryCreateMethodSelected("simple"))
                    onSelectSimple()
                },
            )
            MethodCard(
                title = stringResource(R.string.general_method_general),
                description = stringResource(R.string.general_method_general_description),
                onClick = {
                    analytics.track(AnalyticsEvent.StoryCreateMethodSelected("general"))
                    onSelectGeneral()
                },
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MethodCard(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ManyakTheme.shapes.control,
        border = BorderStroke(ManyakTheme.spacing.hairline, ManyakTheme.colors.border),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = ManyakTheme.colors.surface,
                contentColor = ManyakTheme.colors.text,
            ),
    ) {
        Column(
            Modifier.padding(ManyakTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
        ) {
            Text(title, style = ManyakTheme.typography.titleLarge, color = ManyakTheme.colors.text)
            Text(description, style = ManyakTheme.typography.bodyMedium, color = ManyakTheme.colors.textSubtle)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateMethodPreview() {
    ManyakTheme { CreateMethodScreen(onClose = {}, onSelectSimple = {}, onSelectGeneral = {}) }
}
