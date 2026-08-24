package app.manyak.feature.create

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/** 진행 표시기가 노출하는 단계 수. 완료(생성 로딩)는 단계로 세지 않는다. */
internal const val INDICATOR_STEP_COUNT = 3

/**
 * 클릭 가능한 자식이 이벤트를 소비하기 전에 기존 입력 포커스를 해제한다.
 *
 * 손가락이 슬롭 안에 머문 채 떨어진 제스처만 탭으로 본다. 눌림만 보고 지우면 목록을 넘기려는
 * 첫 접촉에도 포커스가 풀려 입력 중에 스크롤을 할 수 없다.
 */
internal fun Modifier.clearFocusOnTap(focusManager: FocusManager): Modifier =
    pointerInput(focusManager) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dragged = false
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    dragged = true
                }
                pressed = change?.pressed == true
            }
            if (!dragged) focusManager.clearFocus()
        }
    }

/**
 * 포커스가 들어온 요소를 끌어올릴 때 그 아래로 남길 여백.
 *
 * 스크롤 컨테이너는 대상을 뷰포트 가장자리에 딱 맞춰 세운다. 키보드가 올라온 상태에서는 입력
 * 필드가 키보드에 붙어버리므로 목표 영역을 이만큼 키운다. 스크롤을 따로 요청하지 않고 판정
 * 기준만 바꾸는 것이 핵심이다 — 요청을 얹으면 컨테이너가 진행 중이던 애니메이션과 경쟁해
 * 두 번 튀는 스크롤이 된다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FunnelFocusScroll(content: @Composable () -> Unit) {
    val marginPx = with(LocalDensity.current) { ManyakTheme.spacing.gutter.toPx() }
    val spec =
        remember(marginPx) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float,
                ): Float = super.calculateScrollDistance(offset, size + marginPx, containerSize)
            }
        }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
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

/** 퍼널 하단의 주 동작 버튼(다음·스토리라인 만들기·선택하기). [loading] 이면 비활성화하고 문구 자리에 스피너를 겹친다. */
@Composable
internal fun FunnelPrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Button(
        modifier = modifier.heightIn(min = ManyakTheme.sizes.control),
        enabled = enabled && !loading,
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
        Box(contentAlignment = Alignment.Center) {
            // 문구를 투명하게 남겨 스피너가 떠도 버튼 크기가 흔들리지 않게 한다.
            Text(
                modifier = Modifier.alpha(if (loading) 0f else 1f),
                text = label,
                style = ManyakTheme.typography.labelLarge,
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ManyakTheme.sizes.icon),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            }
        }
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
