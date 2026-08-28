package app.manyak.feature.create

import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
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
    draftSave: DraftSaveUiState,
    onSaveDraft: () -> Unit,
    onClose: () -> Unit,
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
        // 어느 단계에서든 퍼널 전체를 닫는 동작임을 분명히 알 수 있게 오른쪽 끝에 닫기를 둔다.
        actions = {
            // actions 슬롯의 Row 는 간격을 주지 않으므로 직접 감싼다. 닫기 버튼은 자기 터치 영역
            // 안에서 아이콘이 가운데 놓여 이미 안쪽 여백이 있고, 여기에 버튼 사이 간격만 더한다.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                DraftSaveButton(draftSave = draftSave, onClick = onSaveDraft)
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.create_close_funnel),
                        tint = ManyakTheme.colors.text,
                    )
                }
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

/**
 * 헤더의 임시 저장 버튼. 편집은 화면 안에 모아 두었다가 이 버튼이나 백그라운드 전환에서만
 * 디스크로 나가므로, 저장 여부를 사용자가 직접 결정한다.
 *
 * 저장 중에는 라벨 자리에 스피너를 겹치고, 성공하면 잠깐 체크와 "임시 저장됨"으로 바꾸며
 * 브랜드 배경을 입는다. 두 상태 모두 버튼을 잠근다 — 방금 저장한 것을 곧바로 다시 저장할
 * 이유가 없다.
 *
 * 상태마다 색을 직접 정해 잠금 여부와 무관하게 같은 값을 넘긴다. `enabled` 에 색까지 맡기면
 * 저장 완료로 잠긴 버튼이 "누를 수 없음" 회색으로 보인다.
 */
@Composable
private fun DraftSaveButton(
    draftSave: DraftSaveUiState,
    onClick: () -> Unit,
) {
    val isSaving = draftSave.status == DraftSaveStatus.SAVING
    val isSaved = draftSave.status == DraftSaveStatus.SAVED
    val isEnabled = draftSave.canSave && draftSave.status == DraftSaveStatus.IDLE
    val labelRes = if (isSaved) R.string.create_draft_saved else R.string.create_draft_save
    val (containerColor, contentColor) =
        when {
            isSaved -> ManyakTheme.colors.backgroundBrandSubtle to ManyakTheme.colors.textBrand
            isEnabled || isSaving -> ManyakTheme.colors.backgroundNeutral to ManyakTheme.colors.text
            else -> ManyakTheme.colors.backgroundDisabled to ManyakTheme.colors.textDisabled
        }
    Button(
        modifier = Modifier.height(ManyakTheme.sizes.input),
        enabled = isEnabled,
        onClick = onClick,
        shape = ManyakTheme.shapes.control,
        // 저장 완료는 브랜드 배경만으로 충분히 구분된다 — 테두리까지 두르면 과하다.
        border = if (isSaved) null else BorderStroke(1.dp, ManyakTheme.colors.border),
        contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.component),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor = contentColor,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 문구를 투명하게 남겨 스피너가 떠도 버튼 폭이 흔들리지 않게 한다.
            Row(
                modifier = Modifier.alpha(if (isSaving) 0f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                if (isSaved) {
                    Icon(
                        modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = LocalContentColor.current,
                    )
                }
                Text(text = stringResource(labelRes), style = ManyakTheme.typography.labelLarge)
            }
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

/**
 * 앱이 백그라운드로 갈 때 모아 둔 편집 스냅숏을 한 번에 저장한다.
 *
 * 목적지가 아니라 Activity 수명을 본다 — 목적지 수명은 퍼널을 떠나는 이동에서도 멈추므로,
 * 저장하지 않고 나가기로 한 조작까지 저장해 버린다.
 *
 * 회전·다크 모드·글자 크기 변경도 Activity 를 멈춘다. 그 `ON_STOP` 은 백그라운드 진입이 아니므로
 * 걸러 낸다 — 걸러 내지 않으면 저장하지 않고 나가려던 편집이 화면을 한 번 돌린 것만으로 디스크에 남는다.
 */
@Composable
internal fun SaveDraftWhenBackgrounded(onSave: () -> Unit) {
    val activity = LocalActivity.current
    val activityLifecycleOwner = activity as? LifecycleOwner
    if (activity != null && activityLifecycleOwner != null) {
        LifecycleEventEffect(Lifecycle.Event.ON_STOP, activityLifecycleOwner) {
            if (!activity.isChangingConfigurations) onSave()
        }
    }
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

/** 퍼널을 떠나면 무엇이 사라지는지 알리고 되돌아갈 길을 먼저 주는 이탈 경고. */
@Composable
internal fun FunnelExitWarningDialog(
    warning: FunnelExitWarning,
    onConfirmLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (warning) {
        FunnelExitWarning.UNSAVED_CHANGES ->
            FunnelWarningDialog(
                titleRes = R.string.create_unsaved_warning_title,
                descriptionRes = R.string.create_unsaved_warning_description,
                confirmRes = R.string.create_unsaved_warning_leave,
                dismissRes = R.string.create_unsaved_warning_stay,
                onConfirm = onConfirmLeave,
                onDismiss = onDismiss,
            )

        FunnelExitWarning.NOTHING_TO_PRESERVE ->
            FunnelWarningDialog(
                titleRes = R.string.create_exit_warning_title,
                descriptionRes = R.string.create_exit_warning_description,
                confirmRes = R.string.create_exit_warning_leave,
                dismissRes = R.string.create_exit_warning_stay,
                onConfirm = onConfirmLeave,
                onDismiss = onDismiss,
            )
    }
}

/** "다시 선택하기"가 추가 정보를 버린다고 알리는 다이얼로그. */
@Composable
internal fun ReselectWarningDialog(
    onConfirmReselect: () -> Unit,
    onDismiss: () -> Unit,
) {
    FunnelWarningDialog(
        titleRes = R.string.create_reselect_warning_title,
        descriptionRes = R.string.create_reselect_warning_description,
        confirmRes = R.string.create_reselect_warning_confirm,
        dismissRes = R.string.create_reselect_warning_cancel,
        onConfirm = onConfirmReselect,
        onDismiss = onDismiss,
    )
}

/**
 * 만들던 내용을 버리는 동작을 확인받는 다이얼로그.
 *
 * 버리는 쪽을 오른쪽 위험 버튼에, 되돌아가는 쪽을 왼쪽 텍스트 버튼에 둔다 — 퍼널의 모든
 * 경고가 같은 자리에 같은 성격의 버튼을 놓아야 어느 쪽이 무엇을 버리는지 매번 다시 읽지 않는다.
 */
@Composable
private fun FunnelWarningDialog(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    @StringRes confirmRes: Int,
    @StringRes dismissRes: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.overlay,
        title = {
            Text(
                text = stringResource(titleRes),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        text = {
            Text(
                text = stringResource(descriptionRes),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = ManyakTheme.shapes.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.backgroundDangerSubtle,
                        contentColor = ManyakTheme.colors.textDanger,
                    ),
            ) {
                Text(text = stringResource(confirmRes), style = ManyakTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(dismissRes),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
        },
    )
}
