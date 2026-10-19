package app.manyak.my.profile.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.component.selectionRing
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.my.profile.entity.ProfilePreset
import app.manyak.my.profile.presentation.ProfileEditLoad
import app.manyak.my.R as MyR

/**
 * 프리셋을 서버 순서대로 4열 격자로 고르는 시트. 고르면 부른 쪽이 시트를 닫는다.
 *
 * 시트 본문이 이미 스크롤되므로 지연 격자 대신 줄을 직접 나눈다. 40개 남짓이라 한 번에 그려도 된다.
 */
@Composable
internal fun ProfilePresetSheet(
    load: ProfileEditLoad,
    presets: List<ProfilePreset>,
    selectedImageUrl: String?,
    onSelect: (ProfilePreset) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ManyakBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(MyR.string.profile_preset_sheet_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Spacer(Modifier.height(ManyakTheme.spacing.gutter))
        // 내용대로 두면 시트가 화면 꼭대기까지 차오른다. 격자만 높이를 묶어 스크롤하고 제목은 그대로 둔다.
        val gridMaxHeight = LocalConfiguration.current.screenHeightDp.dp * GRID_MAX_HEIGHT_FRACTION
        Box(
            modifier =
                Modifier
                    .heightIn(max = gridMaxHeight)
                    .verticalScroll(rememberScrollState())
                    // 고른 칸의 테두리는 칸 밖에 그려져 스크롤 경계에 잘리지 않게 그만큼 비운다.
                    .padding(vertical = ManyakTheme.sizes.selectionBorderWidth),
        ) {
            when (load) {
                ProfileEditLoad.FAILED ->
                    LoadFailedContent(
                        modifier = Modifier.fillMaxWidth().padding(vertical = ManyakTheme.spacing.block),
                        message = stringResource(MyR.string.profile_preset_load_failed),
                        onRetry = onRetry,
                    )

                ProfileEditLoad.LOADING -> {
                    val alpha = rememberSkeletonPulseAlpha()
                    PresetGrid(items = List(SKELETON_COUNT) { it }) { _, modifier ->
                        SkeletonPlaceholder(alpha = alpha, modifier = modifier)
                    }
                }

                ProfileEditLoad.READY ->
                    PresetGrid(items = presets) { preset, modifier ->
                        PresetItem(
                            preset = preset,
                            selected = preset.imageUrl == selectedImageUrl,
                            onClick = { onSelect(preset) },
                            modifier = modifier,
                        )
                    }
            }
        }
    }
}

@Composable
private fun <T> PresetGrid(
    items: List<T>,
    item: @Composable (T, Modifier) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        items.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                row.forEach { item(it, Modifier.weight(1f).aspectRatio(1f)) }
                // 마지막 줄이 덜 차도 칸 폭은 위 줄과 같다.
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 고른 칸은 바깥쪽 테두리를 서서히 채운다. 선택 변화 자체가 반응이라 눌림 리플을 그리지 않는다. */
@Composable
private fun PresetItem(
    preset: ProfilePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(ManyakTheme.motion.screenTransitionMillis),
        label = "presetSelection",
    )
    Box(
        modifier =
            modifier
                .selectionRing(
                    selection = selection,
                    color = ManyakTheme.colors.borderBrand,
                    width = ManyakTheme.sizes.selectionBorderWidth,
                    shape = ManyakTheme.shapes.pill,
                ).clip(ManyakTheme.shapes.pill)
                .selectable(
                    selected = selected,
                    interactionSource = null,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).semantics { contentDescription = preset.key },
    ) {
        ProfileImage(
            imageUrl = preset.imageUrl,
            thumbnailBase64 = preset.thumbnailBase64,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val COLUMNS = 4

/** 화면 높이 대비 격자 상한. 위쪽에 스크림이 남아 시트로 읽힌다. */
private const val GRID_MAX_HEIGHT_FRACTION = 0.7f

/** 서버 프리셋 수. 받는 동안 골격 칸 수로만 쓴다. */
private const val SKELETON_COUNT = 40
