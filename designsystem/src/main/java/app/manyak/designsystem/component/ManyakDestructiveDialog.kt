package app.manyak.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 무언가를 잃는 동작의 확인 다이얼로그.
 *
 * **앱의 모든 파괴적 확인이 같은 표기를 쓴다** — 버리는 동작은 오른쪽에 danger 색으로, 되돌아가는
 * 동작은 왼쪽 텍스트 버튼으로 둔다. 경고마다 자리·색이 다르면 어느 쪽이 무엇을 버리는지 매번 다시
 * 읽어야 한다.
 *
 * [inProgress] 동안 두 버튼을 잠근다 — 결과가 나오기 전에 조작이 겹치면 어떤 요청이 무엇이 됐는지
 * 알 수 없다. 바깥 탭·뒤로가기의 닫힘 무시는 부르는 쪽이 판정한다.
 */
@Composable
fun ManyakDestructiveDialog(
    title: String,
    description: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    inProgress: Boolean = false,
) {
    ManyakDialog(modifier = modifier, onDismissRequest = onDismiss) {
        ManyakDestructiveDialogContent(
            title = title,
            description = description,
            confirmLabel = confirmLabel,
            cancelLabel = cancelLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            inProgress = inProgress,
        )
    }
}

/**
 * 확인 다이얼로그의 내용. 다른 내용을 보여 주던 [ManyakDialog] 창 안에서 갈아 끼울 때 쓴다 —
 * 옵션 목록에서 "삭제하기"를 고른 뒤 같은 창이 확인으로 바뀌는 자리다.
 */
@Composable
fun ManyakDestructiveDialogContent(
    title: String,
    description: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    inProgress: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth().padding(ManyakTheme.spacing.section)) {
        Text(
            text = title,
            style = ManyakTheme.typography.titleMedium,
            color = ManyakTheme.colors.text,
        )
        Text(
            modifier = Modifier.padding(top = ManyakTheme.spacing.gutter),
            text = description,
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.section),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ManyakTextButton(onClick = onDismiss, enabled = !inProgress) {
                Text(
                    text = cancelLabel,
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
            Button(
                onClick = onConfirm,
                enabled = !inProgress,
                shape = ManyakTheme.shapes.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.backgroundDangerSubtle,
                        contentColor = ManyakTheme.colors.textDanger,
                        disabledContainerColor = ManyakTheme.colors.backgroundDangerSubtle,
                        disabledContentColor = ManyakTheme.colors.textDanger,
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // 진행 중에도 라벨 자리를 유지해 버튼 폭이 스피너 폭으로 줄지 않게 한다.
                    Text(
                        modifier = Modifier.alpha(if (inProgress) 0f else 1f),
                        text = confirmLabel,
                        style = ManyakTheme.typography.labelLarge,
                    )
                    if (inProgress) {
                        ManyakProgressIndicator(
                            modifier = Modifier.size(ManyakTheme.sizes.icon),
                            color = ManyakTheme.colors.textDanger,
                        )
                    }
                }
            }
        }
    }
}
