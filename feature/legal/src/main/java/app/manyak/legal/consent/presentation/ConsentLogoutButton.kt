package app.manyak.legal.consent.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.legal.R as LegalR

/** 동의하지 않고 나가는 보조 버튼. 다른 시트의 "닫기" 와 같은 모양이다. */
@Composable
internal fun ConsentLogoutButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManyakTextButton(
        modifier = modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            text = stringResource(LegalR.string.consent_logout),
            style = ManyakTheme.typography.labelLarge,
            color = if (enabled) ManyakTheme.colors.textSubtle else ManyakTheme.colors.textDisabled,
        )
    }
}
