package app.manyak.studio.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.studio.presentation.PendingCreationBanner
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.studio.R as StudioR

/** 이어서 만들기 배너 한 줄. 목록 그리드에서는 전폭 아이템으로 함께 스크롤된다. */
@Composable
internal fun PendingCreationBannerRow(
    banner: PendingCreationBanner,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = ManyakTheme.colors.backgroundNeutral, shape = ManyakTheme.shapes.card)
                .padding(start = ManyakTheme.spacing.gutter, end = ManyakTheme.spacing.inline)
                .padding(vertical = ManyakTheme.spacing.dense),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text =
                stringResource(
                    if (banner.isCompleting) {
                        StudioR.string.studio_pending_banner_completing
                    } else {
                        StudioR.string.studio_pending_banner_making
                    },
                ),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
        )
        ManyakTextButton(onClick = onResume) {
            Text(
                text = stringResource(StudioR.string.studio_pending_banner_resume),
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.brand,
            )
        }
    }
}

/**
 * 배너가 아닌 경로로 진입할 때, 임시 저장본을 버리고 새로 시작할지 묻는다.
 *
 * 이어가는 길은 이 다이얼로그가 아니라 목록 위 배너가 맡는다 — 여기서는 임시 저장본을 버리는
 * 것만 확인받고, 닫으면 아무 일도 일어나지 않는다.
 */
@Composable
internal fun ResumeChoiceDialog(
    onStartNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.overlay,
        title = {
            Text(
                text = stringResource(StudioR.string.studio_pending_dialog_title),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        text = {
            Text(
                text = stringResource(StudioR.string.studio_pending_dialog_description),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        },
        confirmButton = {
            Button(
                onClick = onStartNew,
                shape = ManyakTheme.shapes.control,
                // 임시 저장본을 버리는 쪽이라 다른 경고 다이얼로그와 같은 위험 색을 쓴다.
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.backgroundDangerSubtle,
                        contentColor = ManyakTheme.colors.textDanger,
                    ),
            ) {
                Text(
                    text = stringResource(StudioR.string.studio_pending_dialog_start_new),
                    style = ManyakTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            ManyakTextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(StudioR.string.studio_pending_dialog_close),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
        },
    )
}

@Composable
internal fun CreateStoryFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        shape = ManyakTheme.shapes.pill,
        containerColor = ManyakTheme.colors.brand,
        contentColor = ManyakTheme.colors.textInverse,
        elevation =
            FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
    ) {
        Icon(
            painter = painterResource(DesignsystemR.drawable.ic_add),
            contentDescription = stringResource(StudioR.string.studio_create_story),
        )
    }
}
