package app.manyak.feature.create

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/** 진행 표시기가 노출하는 단계 수. 완료(생성 로딩)는 단계로 세지 않는다. */
internal const val INDICATOR_STEP_COUNT = 3

/** 클릭 가능한 자식이 이벤트를 소비하기 전에 기존 입력 포커스를 해제한다. */
internal fun Modifier.clearFocusOnTap(focusManager: FocusManager): Modifier =
    pointerInput(focusManager) {
        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            focusManager.clearFocus()
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateFunnelHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.create_title),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.common_back),
                    tint = ManyakTheme.colors.text,
                )
            }
        },
        // 화면 루트에서 적용한 safeDrawing 인셋이 중복되지 않게 한다.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = ManyakTheme.colors.surface,
                titleContentColor = ManyakTheme.colors.text,
            ),
    )
}

/** 장식용 막대 셋을 하나의 접근성 진행 문장으로 노출한다. */
@Composable
internal fun CreateStepIndicator(
    currentStep: Int,
    @StringRes stepNameRes: Int,
    modifier: Modifier = Modifier,
) {
    val description =
        stringResource(
            R.string.create_step_progress,
            stringResource(stepNameRes),
            currentStep + 1,
            INDICATOR_STEP_COUNT,
        )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(bottom = ManyakTheme.spacing.gutter)
                .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        repeat(INDICATOR_STEP_COUNT) { index ->
            val color =
                when {
                    index < currentStep -> ManyakTheme.colors.textDisabled
                    index == currentStep -> ManyakTheme.colors.stepIndicatorActive
                    else -> ManyakTheme.colors.border
                }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(ManyakTheme.shapes.pill)
                        .background(color),
            )
        }
    }
}

/** 퍼널 하단의 주 동작 버튼(다음·스토리라인 만들기·선택하기). */
@Composable
internal fun FunnelPrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.heightIn(min = ManyakTheme.sizes.control),
        enabled = enabled,
        onClick = onClick,
        shape = ManyakTheme.shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.brand,
                contentColor = ManyakTheme.colors.textInverse,
                disabledContainerColor = ManyakTheme.colors.backgroundDisabled,
                disabledContentColor = ManyakTheme.colors.textDisabled,
            ),
    ) {
        Text(text = label, style = ManyakTheme.typography.labelLarge)
    }
}

/** 퍼널 하단의 보조 동작 버튼(이전·다시 만들기). */
@Composable
internal fun FunnelNeutralButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.heightIn(min = ManyakTheme.sizes.control),
        enabled = enabled,
        onClick = onClick,
        shape = ManyakTheme.shapes.control,
        border = BorderStroke(1.dp, ManyakTheme.colors.border),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.backgroundNeutral,
                contentColor = ManyakTheme.colors.text,
                disabledContainerColor = ManyakTheme.colors.backgroundDisabled,
                disabledContentColor = ManyakTheme.colors.textDisabled,
            ),
    ) {
        Text(text = label, style = ManyakTheme.typography.labelLarge)
    }
}
