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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.ManyakMoreButton
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.designsystem.component.moreButtonTitleAlignment
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.theme.insetForBorder
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.studio.R as StudioR

/**
 * 초안·완성 요청 카드. 내 스토리 카드와 같은 가로 행이고 표지 폭·행 여백·정보 배치를 그대로 따라
 * 목록에서 한 종류로 읽힌다. 실제 스토리가 없으므로 [MyStoryCard] 를 가짜 요약으로 재사용하지 않고
 * 상태별로 필요한 줄만 그린다.
 */
@Composable
internal fun CreationProgressCard(
    kind: CreationProgressCardKind,
    modifier: Modifier = Modifier,
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
            val actionRes = kind.primaryActionRes()
            if (actionRes != null && onPrimaryAction != null) {
                ProgressActionButton(
                    label = stringResource(actionRes),
                    onClick = onPrimaryAction,
                    modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
                )
            }
        }
    }
}

/** 진행 카드가 그리는 상태. 문구·표지·버튼·옵션이 상태마다 갈린다. */
sealed interface CreationProgressCardKind {
    /** 편집 중 초안 — 이어서 만들기와 삭제만 있다. */
    data object Draft : CreationProgressCardKind

    /** 완성 중 — 스피너만 있고 아무 동작도 없다. */
    data object Completing : CreationProgressCardKind

    /** 완료됐지만 아직 목록에 실리지 않은 스토리. 실제 제목으로 상세에 들어갈 수 있다. */
    data class Completed(
        val title: String,
    ) : CreationProgressCardKind

    /** 확정 실패 — 같은 요청으로 다시 시도하거나 삭제한다. */
    data object Failed : CreationProgressCardKind
}

/** 옵션 다이얼로그 상단의 미리보기. 목록 카드보다 한 단계 작고 눌리지 않는다. */
@Composable
internal fun CreationProgressCardPreview(
    kind: CreationProgressCardKind,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.Top,
    ) {
        ProgressCover(kind = kind, modifier = Modifier.width(CompactCoverWidth), compact = true)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = CompactCoverWidth / STORY_THUMBNAIL_ASPECT_RATIO)
                    .padding(bottom = ManyakTheme.spacing.hairline),
        ) {
            Text(
                text = stringResource(kind.titleRes()),
                style = ManyakTheme.typography.bodyMediumStrong,
                color = kind.titleColor(),
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 회색 3:4 표지. 초안·실패는 가운데에 더 진한 회색 캐릭터 심벌을, 완성 중에는 스피너를 둔다.
 * 테두리 처리는 목록 표지와 같다 — 밝은 표지의 가장자리가 배경에 묻히지 않게 바탕으로 그린다.
 */
@Composable
private fun ProgressCover(
    kind: CreationProgressCardKind,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = if (compact) ManyakTheme.shapes.thumbnailSmall else ManyakTheme.shapes.thumbnail
    val completingDescription = stringResource(StudioR.string.studio_progress_completing_state)
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
        when (kind) {
            CreationProgressCardKind.Completing ->
                ManyakProgressIndicator(
                    modifier =
                        Modifier
                            .size(if (compact) CompactSymbolSize else SymbolSize)
                            // 회전만으로는 읽히지 않으므로 상태를 접근성 이름으로도 알린다.
                            .semantics { contentDescription = completingDescription },
                )

            // 심벌은 제목 줄이 이미 말하는 상태를 되풀이하는 장식이라 낭독 대상이 아니다.
            else ->
                Icon(
                    modifier = Modifier.size(if (compact) CompactSymbolSize else SymbolSize),
                    painter = painterResource(DesignsystemR.drawable.ic_manyak_symbol),
                    contentDescription = null,
                    tint = ManyakTheme.colors.textDisabled,
                )
        }
    }
}

@Composable
private fun ProgressTitleRow(
    kind: CreationProgressCardKind,
    onOptionsClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val titleStyle = ManyakTheme.typography.bodyLargeStrong
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            modifier = Modifier.weight(1f).alignBy(FirstBaseline),
            text = stringResource(kind.titleRes()),
            style = titleStyle,
            color = kind.titleColor(),
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
        CreationProgressCardKind.Draft -> StudioR.string.studio_progress_draft_title
        CreationProgressCardKind.Completing -> StudioR.string.studio_progress_completing_title
        is CreationProgressCardKind.Completed -> StudioR.string.studio_progress_completed_title
        CreationProgressCardKind.Failed -> StudioR.string.studio_progress_failed_title
    }

private fun CreationProgressCardKind.descriptionRes(): Int =
    when (this) {
        CreationProgressCardKind.Completing -> StudioR.string.studio_progress_completing_description
        is CreationProgressCardKind.Completed -> StudioR.string.studio_progress_completed_description
        CreationProgressCardKind.Failed -> StudioR.string.studio_progress_failed_description
        CreationProgressCardKind.Draft -> StudioR.string.studio_progress_draft_description
    }

private fun CreationProgressCardKind.primaryActionRes(): Int? =
    when (this) {
        CreationProgressCardKind.Draft -> StudioR.string.studio_progress_resume
        CreationProgressCardKind.Failed -> StudioR.string.studio_progress_retry
        CreationProgressCardKind.Completing, is CreationProgressCardKind.Completed -> null
    }

/** 초안 제목은 실제 스토리와 구분되게 회색이고, 완료된 요청은 실제 제목이라 본문 색이다. */
@Composable
private fun CreationProgressCardKind.titleColor() =
    if (this is CreationProgressCardKind.Completed) ManyakTheme.colors.text else ManyakTheme.colors.textSubtle

/** 다이얼로그 미리보기의 표지 폭. 목록보다 한 단계 작다. */
private val CompactCoverWidth = 96.dp

private val CoverBorderWidth: Dp = 1.dp

/** 표지 가운데 심벌·스피너 크기. 표지 placeholder 아이콘과 같은 값이다. */
private val SymbolSize = 32.dp

private val CompactSymbolSize = 24.dp

private const val TITLE_MAX_LINES = 2

@Preview(showBackground = true, name = "제작 · 진행 카드")
@Composable
private fun CreationProgressCardPreviews() {
    ManyakTheme(darkTheme = false) {
        Column {
            CreationProgressCard(kind = CreationProgressCardKind.Draft, onPrimaryAction = {}, onOptionsClick = {})
            CreationProgressCard(kind = CreationProgressCardKind.Completing)
            CreationProgressCard(kind = CreationProgressCardKind.Completed("잿빛 왕관"), onClick = {})
            CreationProgressCard(kind = CreationProgressCardKind.Failed, onPrimaryAction = {}, onOptionsClick = {})
        }
    }
}
