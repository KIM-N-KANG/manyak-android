package app.manyak.create.additionalinfo.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.create.entity.ArtStyle
import app.manyak.create.keyword.presentation.TagsReloadButton
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.StoryCover
import app.manyak.designsystem.component.coverBackdrop
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.component.selectionRing
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR

/** 이미지 스타일 선택. 목록 순서와 이름은 서버가 준 그대로다. [artStyles] 가 null 이면 받는 중이다. */
@Composable
internal fun ArtStyleSection(
    artStyles: List<ArtStyle>?,
    failed: Boolean,
    selectedCode: String?,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        KeywordSectionLabel(text = stringResource(CreateR.string.create_art_style_label), required = true)
        when {
            failed -> ArtStyleLoadFailure(onRetry = onRetry)

            artStyles == null -> {
                val alpha = rememberSkeletonPulseAlpha()
                ThreeColumnGrid(items = List(SKELETON_COUNT) { it }) { _, cellModifier ->
                    SkeletonPlaceholder(
                        modifier = cellModifier.aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO),
                        alpha = alpha,
                        shape = ManyakTheme.shapes.card,
                    )
                }
            }

            else ->
                ThreeColumnGrid(items = artStyles, modifier = Modifier.selectableGroup()) { artStyle, cellModifier ->
                    ArtStyleTile(
                        modifier = cellModifier,
                        artStyle = artStyle,
                        selected = artStyle.code == selectedCode,
                        onClick = { onSelect(artStyle.code) },
                    )
                }
        }
    }
}

/** 스크롤 목록 안에 들어가 지연 그리드를 겹칠 수 없으므로, 줄마다 같은 폭의 칸을 나눠 놓는다. */
@Composable
private fun <T> ThreeColumnGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    cell: @Composable (item: T, cellModifier: Modifier) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        items.chunked(GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                row.forEach { item -> cell(item, Modifier.weight(1f)) }
                // 마지막 줄이 덜 차도 칸 폭은 윗줄과 같아야 한다.
                repeat(GRID_COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** 미리보기 표지 아래 가운데에 표지 위 뱃지와 같은 흐린 바탕을 깔고 이름을 얹는다. */
@Composable
private fun ArtStyleTile(
    artStyle: ArtStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(ManyakTheme.motion.screenTransitionMillis),
        label = "artStyleSelection",
    )
    StoryCover(
        thumbnailUrl = artStyle.previewImageUrl,
        modifier =
            modifier
                .selectionRing(
                    selection = selection,
                    color = ManyakTheme.colors.borderBrand,
                    width = ManyakTheme.sizes.selectionBorderWidth,
                    shape = ManyakTheme.shapes.card,
                )
                // 선택 변화 자체가 반응이라 눌림 리플을 그리지 않는다(키워드 칩과 같다).
                .selectable(
                    selected = selected,
                    interactionSource = null,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                // 표지 안의 기본 심벌 설명까지 읽히지 않게 화풍 이름 하나로 읽힌다.
                .clearAndSetSemantics { contentDescription = artStyle.name },
        showBorder = true,
    ) {
        Text(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(ManyakTheme.spacing.compact)
                    .coverBackdrop(backdrop, ManyakTheme.shapes.pill)
                    .padding(horizontal = ManyakTheme.spacing.compact, vertical = ManyakTheme.spacing.hairline),
            text = artStyle.name,
            style = ManyakTheme.typography.labelSmall,
            color = ManyakTheme.colors.textInverse,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 한 줄 안내 아래에 키워드 단계와 같은 "다시 불러오기" 버튼을 둔다. */
@Composable
private fun ArtStyleLoadFailure(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = ManyakTheme.spacing.compact),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Text(
            text = stringResource(CreateR.string.create_art_style_load_failed),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
            textAlign = TextAlign.Center,
        )
        TagsReloadButton(onClick = onRetry)
    }
}

private const val GRID_COLUMNS = 3

/** 목록을 받기 전에 보이는 자리 표시 칸 수. 3열 두 줄을 채운다. */
private const val SKELETON_COUNT = 6

private fun previewArtStyles(): List<ArtStyle> =
    listOf(
        ArtStyle(code = "NONE", name = "없음", previewImageUrl = null),
        ArtStyle(code = "ANIME", name = "애니메이션", previewImageUrl = null),
        ArtStyle(code = "SEMI_REAL", name = "반실사", previewImageUrl = null),
        ArtStyle(code = "LIGHT_NOVEL", name = "라노벨", previewImageUrl = null),
    )

@Preview(showBackground = true, name = "이미지 스타일 · 선택")
@Composable
private fun ArtStyleSectionPreview() {
    ManyakTheme(darkTheme = false) {
        ArtStyleSection(
            artStyles = previewArtStyles(),
            failed = false,
            selectedCode = "ANIME",
            onSelect = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, name = "이미지 스타일 · 조회 실패")
@Composable
private fun ArtStyleSectionFailedPreview() {
    ManyakTheme(darkTheme = false) {
        ArtStyleSection(artStyles = null, failed = true, selectedCode = null, onSelect = {}, onRetry = {})
    }
}
