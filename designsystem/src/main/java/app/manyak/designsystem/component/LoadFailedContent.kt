package app.manyak.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR

/** 목록 조회 실패 자리. 문구와 재시도 버튼을 세로 중앙에 둔다. */
@Composable
fun LoadFailedContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = message,
            style = ManyakTheme.typography.bodyLargeStrong,
            color = ManyakTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        ManyakNeutralButton(label = stringResource(DesignsystemR.string.common_retry), onClick = onRetry)
    }
}
