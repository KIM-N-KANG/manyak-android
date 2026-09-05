package app.manyak.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.common.entity.story.StoryReportReason
import app.manyak.core.ui.R
import app.manyak.core.ui.report.StoryReportAction
import app.manyak.core.ui.report.StoryReportUiState
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.component.ManyakInputCounter
import app.manyak.designsystem.component.ManyakMultilineTextField
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme

/** 서버가 받는 상세 서술의 상한. 넘겨 보내면 400 이라 입력 단계에서 막는다. */
const val STORY_REPORT_DETAIL_MAX_LENGTH: Int = 500

/**
 * 스토리 신고 시트. 스토리 상세와 채팅방이 함께 쓴다 — 채팅방에서 열어도 신고 대상은 그 채팅이
 * 참조하는 스토리다(서버 계약이 스토리 단위).
 *
 * 입력 상태를 안에 두지 않는 이유는 전송이 화면의 ViewModel 소유라서다. 시트가 값을 들고 있으면
 * 구성 변경으로 시트가 다시 만들어질 때 고른 사유가 사라진다.
 */
@Composable
fun StoryReportSheet(
    state: StoryReportUiState,
    onAction: (StoryReportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManyakBottomSheet(modifier = modifier, onDismissRequest = { onAction(StoryReportAction.Close) }) {
        StoryReportHeadline()
        Spacer(Modifier.height(ManyakTheme.spacing.block))
        StoryReportReasons(
            selectedReason = state.reason,
            enabled = !state.isSubmitting,
            onReasonSelect = { reason -> onAction(StoryReportAction.SelectReason(reason)) },
        )
        Spacer(Modifier.height(ManyakTheme.spacing.section))
        StoryReportDetailField(
            detail = state.detail,
            enabled = !state.isSubmitting,
            onDetailChange = { detail -> onAction(StoryReportAction.ChangeDetail(detail)) },
        )
        Spacer(Modifier.height(ManyakTheme.spacing.block))
        StoryReportSubmitButton(
            enabled = state.reason != null,
            isSubmitting = state.isSubmitting,
            onSubmit = { onAction(StoryReportAction.Submit) },
        )
        ManyakTextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAction(StoryReportAction.Close) },
            enabled = !state.isSubmitting,
        ) {
            Text(
                text = stringResource(R.string.story_report_close),
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

@Composable
private fun StoryReportHeadline(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.story_report_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(R.string.story_report_description),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

@Composable
private fun StoryReportReasons(
    selectedReason: StoryReportReason?,
    enabled: Boolean,
    onReasonSelect: (StoryReportReason) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        StoryReportReason.entries.forEach { reason ->
            val selected = reason == selectedReason
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ManyakTheme.sizes.input)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onReasonSelect(reason) },
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            ) {
                RadioButton(
                    selected = selected,
                    // 줄 전체가 선택 대상이라 라디오는 표시만 한다 — 두 번 읽히지 않게 한다.
                    onClick = null,
                    enabled = enabled,
                    colors =
                        RadioButtonDefaults.colors(
                            selectedColor = ManyakTheme.colors.brand,
                            unselectedColor = ManyakTheme.colors.border,
                        ),
                )
                Text(
                    text = stringResource(reason.labelRes()),
                    style = ManyakTheme.typography.bodyLarge,
                    color = ManyakTheme.colors.text,
                )
            }
        }
    }
}

@Composable
private fun StoryReportDetailField(
    detail: String,
    enabled: Boolean,
    onDetailChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.story_report_detail_label),
            style = ManyakTheme.typography.labelLarge,
            color = ManyakTheme.colors.text,
        )
        ManyakMultilineTextField(
            modifier = Modifier.fillMaxWidth(),
            value = detail,
            onValueChange = { input -> onDetailChange(input.take(STORY_REPORT_DETAIL_MAX_LENGTH)) },
            placeholder = stringResource(R.string.story_report_detail_placeholder),
            enabled = enabled,
            footer = {
                ManyakInputCounter(
                    length = detail.length,
                    maxLength = STORY_REPORT_DETAIL_MAX_LENGTH,
                )
            },
        )
    }
}

@Composable
private fun StoryReportSubmitButton(
    enabled: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
        onClick = onSubmit,
        enabled = enabled && !isSubmitting,
        shape = ManyakTheme.shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.backgroundDangerBold,
                contentColor = ManyakTheme.colors.textInverse,
                disabledContainerColor = ManyakTheme.colors.backgroundDisabled,
                disabledContentColor = ManyakTheme.colors.textDisabled,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 진행 중에도 라벨 자리를 유지해 버튼 폭이 스피너 폭으로 줄지 않게 한다.
            Text(
                modifier = Modifier.alpha(if (isSubmitting) 0f else 1f),
                text = stringResource(R.string.story_report_submit),
                style = ManyakTheme.typography.labelLarge,
            )
            if (isSubmitting) ManyakProgressIndicator()
        }
    }
}

private fun StoryReportReason.labelRes(): Int =
    when (this) {
        StoryReportReason.SPAM -> R.string.story_report_reason_spam
        StoryReportReason.INAPPROPRIATE -> R.string.story_report_reason_inappropriate
        StoryReportReason.ETC -> R.string.story_report_reason_etc
    }

@Preview
@Composable
private fun StoryReportSheetPreview() {
    ManyakTheme {
        StoryReportSheet(
            state = StoryReportUiState(isSheetOpen = true, reason = StoryReportReason.INAPPROPRIATE),
            onAction = {},
        )
    }
}
