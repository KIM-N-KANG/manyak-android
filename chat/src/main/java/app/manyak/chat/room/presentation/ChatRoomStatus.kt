package app.manyak.chat.room.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.chat.R as ChatR
import app.manyak.designsystem.R as DesignsystemR

@Composable
internal fun ChatRoomLoadFailed(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(ChatR.string.chat_room_load_error),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        Button(
            modifier = Modifier.heightIn(min = ManyakTheme.sizes.control),
            onClick = onRetry,
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.brand,
                    contentColor = ManyakTheme.colors.textInverse,
                ),
        ) {
            Text(text = stringResource(DesignsystemR.string.common_retry), style = ManyakTheme.typography.labelLarge)
        }
    }
}

@Preview(showBackground = true, name = "채팅방 · 로드 실패")
@Composable
private fun ChatRoomLoadFailedPreview() {
    ManyakTheme(darkTheme = false) {
        ChatRoomLoadFailed(onRetry = {}, modifier = Modifier.fillMaxSize())
    }
}
