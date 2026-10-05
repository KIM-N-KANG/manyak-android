package app.manyak.studio.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.designsystem.component.ManyakMoreButton
import app.manyak.designsystem.component.StoryCover
import app.manyak.designsystem.component.moreButtonTitleAlignment
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.studio.R
import app.manyak.studio.entity.StorySubmission
import app.manyak.studio.entity.SubmissionStatus

@Composable
internal fun SubmissionCard(
    submission: StorySubmission,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.Top,
    ) {
        StoryCover(thumbnailUrl = submission.thumbnailUrl, modifier = Modifier.width(CoverWidth), showBorder = true)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = CoverHeight)
                    .padding(bottom = ManyakTheme.spacing.hairline),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline)) {
                SubmissionBadge(submission.status)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = submission.title.ifBlank { stringResource(R.string.studio_submission_fallback_title) },
                        modifier = Modifier.weight(1f).alignBy(FirstBaseline),
                        style = ManyakTheme.typography.bodyLargeStrong,
                        color = ManyakTheme.colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ManyakMoreButton(
                        contentDescription = stringResource(R.string.studio_submission_more),
                        onClick = onOptionsClick,
                        modifier = moreButtonTitleAlignment(ManyakTheme.typography.bodyLargeStrong),
                    )
                }
                Text(
                    text = submissionDescription(submission),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
            SubmissionActions(submission, onEdit)
        }
    }
}

/** 초안 카드의 이어서 만들기와 같은 자리·모양이다 — 날짜 아래에 주 버튼을 둔다. */
@Composable
private fun SubmissionActions(
    submission: StorySubmission,
    onEdit: () -> Unit,
) {
    val editable = submission.status != SubmissionStatus.PENDING
    if (submission.submittedAt == null && !editable) return
    Column(
        modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        submission.submittedAt?.let {
            SavedAtRow(
                savedAt = it,
                descriptionRes = R.string.studio_submission_submitted_at,
            )
        }
        if (editable) ProgressActionButton(label = stringResource(R.string.studio_edit_story), onClick = onEdit)
    }
}

@Composable
private fun SubmissionBadge(status: SubmissionStatus) {
    val pending = status == SubmissionStatus.PENDING
    Text(
        text =
            stringResource(
                when (status) {
                    SubmissionStatus.PENDING -> R.string.studio_submission_pending
                    SubmissionStatus.REJECTED -> R.string.studio_submission_rejected
                    SubmissionStatus.FAILED -> R.string.studio_submission_failed
                },
            ),
        style = ManyakTheme.typography.bodySmall,
        color = if (pending) ManyakTheme.colors.textSubtle else ManyakTheme.colors.textDanger,
        modifier =
            Modifier
                .clip(ManyakTheme.shapes.pill)
                .background(
                    if (pending) ManyakTheme.colors.backgroundNeutral else ManyakTheme.colors.backgroundDangerSubtle,
                ).padding(horizontal = ManyakTheme.spacing.compact, vertical = ManyakTheme.spacing.hairline),
    )
}

@Composable
private fun submissionDescription(submission: StorySubmission): String =
    when (submission.status) {
        SubmissionStatus.PENDING -> stringResource(R.string.studio_submission_pending_description)
        SubmissionStatus.REJECTED ->
            if (submission.issueCount > 0) {
                stringResource(R.string.studio_submission_rejected_count, submission.issueCount)
            } else {
                stringResource(R.string.studio_submission_rejected_description)
            }
        SubmissionStatus.FAILED ->
            stringResource(
                if (submission.hasImageError) {
                    R.string.studio_submission_failed_image
                } else {
                    R.string.studio_submission_failed_description
                },
            )
    }

@Preview(showBackground = true)
@Composable
private fun SubmissionCardsPreview() {
    ManyakTheme {
        Column {
            SubmissionStatus.entries.forEach { status ->
                SubmissionCard(
                    submission = StorySubmission("preview", status, "등록을 요청한 스토리", null, 1_790_139_900_000L, 2, true),
                    onOptionsClick = {},
                )
            }
        }
    }
}
