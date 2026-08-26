package app.manyak.feature.studio

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

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
                        R.string.studio_pending_banner_completing
                    } else {
                        R.string.studio_pending_banner_making
                    },
                ),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
        )
        TextButton(onClick = onResume) {
            Text(
                text = stringResource(R.string.studio_pending_banner_resume),
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.brand,
            )
        }
    }
}

/** 배너가 아닌 경로로 진입할 때 임시 저장본을 이어갈지 새로 시작할지 묻는다(3-1 제작 임시 저장). */
@Composable
internal fun ResumeChoiceDialog(
    onResume: () -> Unit,
    onStartNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.overlay,
        title = {
            Text(
                text = stringResource(R.string.studio_pending_dialog_title),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.studio_pending_dialog_description),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        },
        confirmButton = {
            Button(
                onClick = onResume,
                shape = ManyakTheme.shapes.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.brand,
                        contentColor = ManyakTheme.colors.textInverse,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.studio_pending_banner_resume),
                    style = ManyakTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onStartNew) {
                Text(
                    text = stringResource(R.string.studio_pending_dialog_start_new),
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
            painter = painterResource(R.drawable.ic_add),
            contentDescription = stringResource(R.string.studio_create_story),
        )
    }
}
