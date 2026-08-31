package app.manyak.feature.my

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 연동 시작 확인.
 *
 * 버튼을 누르자마자 제공자 창으로 보내지 않는 이유는 두 가지다 — **대상이 아니라 현재 로그인한
 * 제공자의 인증 창이 먼저** 뜨고(재인증 선행), 한 번 연동하면 해제할 수 없다. 둘 다 예고가 없으면
 * 사용자가 흐름을 오해한다.
 *
 * 명시적 이탈 버튼이 있어 닫기(X)는 두지 않고, [inProgress] 동안에는 바깥 탭·뒤로가기로도 닫히지
 * 않는다 — 진행 중 이탈로 어느 단계까지 갔는지 알 수 없게 되는 것을 막는다.
 */
@Composable
internal fun AccountLinkConfirmDialog(
    current: AuthProvider,
    target: AuthProvider,
    inProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLabel = stringResource(current.labelRes)
    val targetLabel = stringResource(target.labelRes)
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!inProgress) onDismiss() },
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.overlay,
        title = {
            Text(
                text = stringResource(R.string.my_link_confirm_title, targetLabel),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        text = { ConfirmDescription(currentLabel = currentLabel, targetLabel = targetLabel) },
        confirmButton = { ConfirmActionButton(inProgress = inProgress, onConfirm = onConfirm) },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text(
                    text = stringResource(R.string.my_link_confirm_cancel),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
        },
    )
}

@Composable
private fun ConfirmDescription(
    currentLabel: String,
    targetLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        DialogBodyText(
            stringResource(
                R.string.my_link_confirm_benefit,
                stringResource(AuthProvider.KAKAO.labelRes),
                stringResource(AuthProvider.GOOGLE.labelRes),
            ),
        )
        DialogBodyText(stringResource(R.string.my_link_confirm_reauth, currentLabel, targetLabel))
        DialogBodyText(stringResource(R.string.my_link_confirm_irreversible))
    }
}

@Composable
private fun ConfirmActionButton(
    inProgress: Boolean,
    onConfirm: () -> Unit,
) {
    Button(
        onClick = onConfirm,
        enabled = !inProgress,
        shape = ManyakTheme.shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.brand,
                contentColor = ManyakTheme.colors.textInverse,
                disabledContainerColor = ManyakTheme.colors.brand,
                disabledContentColor = ManyakTheme.colors.textInverse,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 진행 중에도 라벨 자리를 유지해 버튼 폭이 스피너 폭으로 줄지 않게 한다.
            Text(
                modifier = Modifier.alpha(if (inProgress) 0f else 1f),
                text = stringResource(R.string.my_link_confirm_action),
                style = ManyakTheme.typography.labelLarge,
            )
            if (inProgress) {
                ManyakProgressIndicator(
                    modifier = Modifier.size(ManyakTheme.sizes.icon),
                    color = ManyakTheme.colors.textInverse,
                )
            }
        }
    }
}

/**
 * 이미 다른 마냑 계정에 붙어 있는 소셜 계정이었다.
 *
 * 사라지는 토스트로는 "그럼 어떻게 하나"에 답할 수 없어 다이얼로그로 안내한다. "기존 계정으로
 * 로그인하기" 버튼은 두지 않는다 — 현재 세션 로그아웃이라는 큰 부수효과를 한 번의 탭에 묶지 않는다.
 */
@Composable
internal fun LinkedToOtherUserDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.overlay,
        title = {
            Text(
                text = stringResource(R.string.my_link_other_user_title),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                DialogBodyText(stringResource(R.string.my_link_other_user_reason))
                DialogBodyText(stringResource(R.string.my_link_other_user_action_guide))
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = ManyakTheme.shapes.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.brand,
                        contentColor = ManyakTheme.colors.textInverse,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.common_confirm),
                    style = ManyakTheme.typography.labelLarge,
                )
            }
        },
    )
}

@Composable
private fun DialogBodyText(text: String) {
    Text(
        text = text,
        style = ManyakTheme.typography.bodyMedium,
        color = ManyakTheme.colors.textSubtle,
    )
}
