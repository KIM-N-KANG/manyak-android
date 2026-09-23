package app.manyak.studio.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.designsystem.component.ImageGenerationLoading
import app.manyak.designsystem.component.ManyakMoreButton
import app.manyak.designsystem.component.MetaChip
import app.manyak.designsystem.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.designsystem.component.moreButtonTitleAlignment
import app.manyak.designsystem.component.rememberTextShimmerBrush
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.theme.insetForBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.studio.R as StudioR

/**
 * 초안·완성 요청 카드. 내 스토리 카드와 같은 가로 행이고 표지 폭·행 여백·정보 배치를 그대로 따라
 * 목록에서 한 종류로 읽힌다. 실제 스토리가 없으므로 [MyStoryCard] 를 가짜 요약으로 재사용하지 않고
 * 상태별로 필요한 줄만 그린다.
 *
 * [savedAt] 은 처음 임시 저장한 시각이다. 내 스토리 카드의 제작일 줄 자리에 두어 버튼이 있으면 그 위,
 * 없으면 글 영역 맨 아래에 온다. 여러 초안 이전에 저장한 카드는 시각이 없어 줄을 두지 않는다.
 */
@Composable
internal fun CreationProgressCard(
    kind: CreationProgressCardKind,
    modifier: Modifier = Modifier,
    savedAt: Long? = null,
    onPrimaryAction: (() -> Unit)? = null,
    onOptionsClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.Top,
    ) {
        ProgressCover(kind = kind, modifier = Modifier.width(CoverWidth))
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = CoverHeight)
                    .padding(bottom = ManyakTheme.spacing.hairline),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                ProgressTitleRow(kind = kind, onOptionsClick = onOptionsClick)
                Text(
                    modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
                    text = stringResource(kind.descriptionRes()),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
            val actionRes = kind.primaryActionRes()?.takeIf { onPrimaryAction != null }
            if (savedAt != null || actionRes != null) {
                Column(
                    modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
                    verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
                ) {
                    savedAt?.let { SavedAtRow(savedAt = it) }
                    if (actionRes != null && onPrimaryAction != null) {
                        ProgressActionButton(label = stringResource(actionRes), onClick = onPrimaryAction)
                    }
                }
            }
        }
    }
}

/** 진행 카드가 그리는 상태. 문구·표지·버튼·옵션이 상태마다 갈린다. */
sealed interface CreationProgressCardKind {
    /** 편집 중 초안 — 이어서 만들기와 삭제만 있다. 설명은 초안이 멈춘 단계를 알린다. */
    data class Draft(
        val stage: CreationStage,
        val resumePoint: CreationResumePoint,
    ) : CreationProgressCardKind

    /** 완성 중 — 이미지 생성 로딩만 있고 아무 동작도 없다. */
    data object Completing : CreationProgressCardKind

    /** 완료됐지만 아직 목록에 실리지 않은 스토리. 실제 제목으로 상세에 들어갈 수 있다. */
    data class Completed(
        val title: String,
    ) : CreationProgressCardKind

    /** 확정 실패 — 같은 요청으로 다시 시도하거나 삭제한다. */
    data object Failed : CreationProgressCardKind
}

/**
 * 회색 3:4 표지. 초안·실패는 가운데에 더 진한 회색 캐릭터 심벌을, 완성 중에는 이미지 생성 로딩을 둔다.
 * 테두리 처리는 목록 표지와 같다 — 밝은 표지의 가장자리가 배경에 묻히지 않게 바탕으로 그린다.
 */
@Composable
private fun ProgressCover(
    kind: CreationProgressCardKind,
    modifier: Modifier = Modifier,
) {
    val shape = ManyakTheme.shapes.thumbnail
    if (kind == CreationProgressCardKind.Completing) {
        ImageGenerationLoading(
            modifier = modifier,
            aspectRatio = STORY_THUMBNAIL_ASPECT_RATIO,
            shape = shape,
            label = stringResource(StudioR.string.studio_progress_completing_state),
        )
        return
    }
    Box(
        modifier =
            modifier
                .aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO)
                .clip(shape)
                .background(ManyakTheme.colors.border)
                .padding(CoverBorderWidth)
                .clip(shape.insetForBorder(CoverBorderWidth))
                .background(ManyakTheme.colors.backgroundNeutral),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(SymbolSize),
            painter = painterResource(DesignsystemR.drawable.ic_manyak_symbol),
            contentDescription = null,
            tint = ManyakTheme.colors.textDisabled,
        )
    }
}

@Composable
private fun ProgressTitleRow(
    kind: CreationProgressCardKind,
    onOptionsClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val titleStyle = ManyakTheme.typography.bodyLargeStrong
    val shimmer = if (kind == CreationProgressCardKind.Completing) rememberTextShimmerBrush() else null
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            modifier = Modifier.weight(1f).alignBy(FirstBaseline),
            text = stringResource(kind.titleRes()),
            style = titleStyle.merge(TextStyle(brush = shimmer)),
            color = if (shimmer == null) kind.titleColor() else Color.Unspecified,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        if (onOptionsClick != null) {
            ManyakMoreButton(
                contentDescription = stringResource(StudioR.string.studio_story_more),
                onClick = onOptionsClick,
                modifier = moreButtonTitleAlignment(titleStyle),
            )
        }
    }
}

/** 처음 임시 저장한 시각. 내 스토리 카드의 제작일 줄과 같은 칩으로 오른쪽 끝에 붙는다. */
@Composable
private fun SavedAtRow(
    savedAt: Long,
    modifier: Modifier = Modifier,
) {
    val text = remember(savedAt) { savedAt.toSavedAtText() }
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        MetaChip(
            iconRes = DesignsystemR.drawable.ic_calendar,
            text = text,
            description = stringResource(StudioR.string.studio_progress_saved_at_description, text),
        )
    }
}

/**
 * 기기에 기록한 시각을 KST `yyyy-MM-dd HH:mm` 로 옮긴다. 내 스토리 제작일처럼 기기 시간대가 아니라
 * KST 로 고정해, 같은 초안을 웹에서 보든 해외에서 보든 같은 시각이 찍힌다.
 */
internal fun Long.toSavedAtText(): String =
    SimpleDateFormat(SAVED_AT_PATTERN, Locale.US)
        .apply { timeZone = TimeZone.getTimeZone(DISPLAY_TIME_ZONE) }
        .format(Date(this))

/**
 * 기존 지표 줄 자리의 주 버튼. 보이는 높이는 40dp 로 카드 안에서 낮게 앉히되, 눌리는 영역은
 * 버튼이 기본으로 확보하는 최소 48dp 그대로다.
 */
@Composable
private fun ProgressActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.fillMaxWidth().height(ManyakTheme.sizes.controlCompact),
        onClick = onClick,
        shape = ManyakTheme.shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.brand,
                contentColor = ManyakTheme.colors.textInverse,
            ),
        contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.component),
    ) {
        Text(text = label, style = ManyakTheme.typography.labelLarge)
    }
}

private fun CreationProgressCardKind.titleRes(): Int =
    when (this) {
        is CreationProgressCardKind.Draft -> StudioR.string.studio_progress_draft_title
        CreationProgressCardKind.Completing -> StudioR.string.studio_progress_completing_title
        is CreationProgressCardKind.Completed -> StudioR.string.studio_progress_completed_title
        CreationProgressCardKind.Failed -> StudioR.string.studio_progress_failed_title
    }

private fun CreationProgressCardKind.descriptionRes(): Int =
    when (this) {
        CreationProgressCardKind.Completing -> StudioR.string.studio_progress_completing_description
        is CreationProgressCardKind.Completed -> StudioR.string.studio_progress_completed_description
        CreationProgressCardKind.Failed -> StudioR.string.studio_progress_failed_description
        // 초안이 멈춘 단계를 알린다. 스토리라인 생성은 서버에서 실제로 도는 중이라 현재형이다.
        is CreationProgressCardKind.Draft ->
            when (stage) {
                CreationStage.KEYWORD_DRAFT -> StudioR.string.studio_progress_draft_description_keyword
                CreationStage.STORYLINE_GENERATION -> StudioR.string.studio_progress_draft_description_generating
                CreationStage.STORY_DRAFT ->
                    if (resumePoint is CreationResumePoint.AdditionalInfoStep) {
                        StudioR.string.studio_progress_draft_description_additional_info
                    } else {
                        StudioR.string.studio_progress_draft_description_storyline
                    }
            }
    }

private fun CreationProgressCardKind.primaryActionRes(): Int? =
    when (this) {
        is CreationProgressCardKind.Draft -> StudioR.string.studio_progress_resume
        CreationProgressCardKind.Failed -> StudioR.string.studio_progress_retry
        CreationProgressCardKind.Completing, is CreationProgressCardKind.Completed -> null
    }

/** 초안 제목은 실제 스토리와 구분되게 회색이고, 완료된 요청은 실제 제목이라 본문 색이다. */
@Composable
private fun CreationProgressCardKind.titleColor() =
    if (this is CreationProgressCardKind.Completed) ManyakTheme.colors.text else ManyakTheme.colors.textSubtle

private val CoverBorderWidth: Dp = 1.dp

/** 표지 가운데 심벌·스피너 크기. 표지 placeholder 아이콘과 같은 값이다. */
private val SymbolSize = 32.dp

private const val TITLE_MAX_LINES = 2

private const val SAVED_AT_PATTERN = "yyyy-MM-dd HH:mm"

private const val DISPLAY_TIME_ZONE = "Asia/Seoul"

@Preview(showBackground = true, name = "제작 · 진행 카드")
@Composable
private fun CreationProgressCardPreviews() {
    ManyakTheme {
        Column {
            CreationProgressCard(
                kind =
                    CreationProgressCardKind.Draft(
                        CreationStage.STORY_DRAFT,
                        CreationResumePoint.AdditionalInfoStep(0),
                    ),
                savedAt = PREVIEW_SAVED_AT,
                onPrimaryAction = {},
                onOptionsClick = {},
            )
            CreationProgressCard(kind = CreationProgressCardKind.Completing, savedAt = PREVIEW_SAVED_AT)
            CreationProgressCard(kind = CreationProgressCardKind.Completed("잿빛 왕관"), onClick = {})
            CreationProgressCard(kind = CreationProgressCardKind.Failed, onPrimaryAction = {}, onOptionsClick = {})
        }
    }
}

/** 2026-09-23 14:05 KST. */
private const val PREVIEW_SAVED_AT = 1_790_139_900_000L
