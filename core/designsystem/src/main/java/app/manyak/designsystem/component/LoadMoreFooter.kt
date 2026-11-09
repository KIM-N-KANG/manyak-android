package app.manyak.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.R
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 커서 목록 끝의 다음 페이지 자리. 받는 중에는 진행 표시, 실패하면 재시도 버튼이고 **이미 그린 목록은
 * 그대로 둔다**.
 */
@Composable
fun LoadMoreFooter(
    isLoading: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = ManyakTheme.spacing.component),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            ManyakProgressIndicator(modifier = Modifier.size(ManyakTheme.sizes.icon))
        } else {
            ManyakTextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.common_retry),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textBrand,
                )
            }
        }
    }
}
