package app.manyak.feature.studio

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 스토리 삭제 확인. 문구는 웹 스토리 옵션 메뉴의 확인 다이얼로그와 같은 계약을 쓴다.
 *
 * 삭제가 진행되는 동안 두 버튼을 잠근다 — 결과가 나오기 전에 조작이 겹치면 어떤 요청이
 * 무엇이 됐는지 알 수 없다. 바깥 탭·뒤로가기의 닫힘 무시는 ViewModel 이 판정한다.
 */
@Composable
internal fun DeleteStoryDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.overlay,
        title = {
            Text(
                text = stringResource(R.string.studio_delete_dialog_title),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.studio_delete_dialog_description),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                shape = ManyakTheme.shapes.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.backgroundDangerBold,
                        contentColor = ManyakTheme.colors.textInverse,
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            if (isDeleting) R.string.studio_deleting else R.string.studio_story_delete,
                        ),
                    style = ManyakTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(
                    text = stringResource(R.string.studio_delete_dialog_cancel),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
        },
    )
}
