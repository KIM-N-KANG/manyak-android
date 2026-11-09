package app.manyak.create.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.manyak.designsystem.component.ManyakSwitch
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR

/** "공개" 스위치. 켜면 공개, 끄면 비공개다. 일반 제작 등록 탭과 간편 제작 마지막 단계가 같이 쓴다. */
@Composable
internal fun VisibilityField(
    isPublic: Boolean,
    onPublicChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = stringResource(CreateR.string.general_visibility)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            KeywordSectionLabel(label, required = false)
            Text(
                stringResource(CreateR.string.general_visibility_hint),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        }
        ManyakSwitch(
            checked = isPublic,
            enabled = enabled,
            onCheckedChange = onPublicChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}
