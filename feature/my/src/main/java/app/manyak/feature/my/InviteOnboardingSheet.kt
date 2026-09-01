package app.manyak.feature.my

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.credit.LocalCreditPolicy
import app.manyak.core.ui.credit.creditAmountAlpha
import app.manyak.core.ui.credit.creditAmountText
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 신규 가입 직후 뜨는 초대 코드 안내.
 *
 * 회원 그래프 위에 얹는다 — 로그인 성공과 동시에 인증 백스택이 사라져 로그인 화면에서는 띄울 수 없다.
 * 건너뛰어도 자격은 서버가 들고 있으므로 나중에 친구 초대에서 다시 입력할 수 있다.
 */
@Composable
fun InviteOnboardingSheet(
    modifier: Modifier = Modifier,
    viewModel: InviteOnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // 토스트는 그 순간 문자열이 필요해 자리표시·쉬머를 둘 수 없다. 늦게 도착한 수치도 읽도록
    // 갱신되는 상태로 들고 있는다 — 화면을 띄운 뒤 코드를 내는 흐름이라 보통은 이미 도착해 있다.
    val redeemedMessage by
        rememberUpdatedState(
            stringResource(
                R.string.invite_code_redeemed,
                creditAmountText(LocalCreditPolicy.current?.inviteReward),
            ),
        )

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    InviteOnboardingEffect.Redeemed ->
                        Toast.makeText(context, redeemedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (state.isVisible) {
        InviteOnboardingContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteOnboardingContent(
    state: InviteOnboardingUiState,
    onIntent: (InviteOnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val submitting by rememberUpdatedState(state.isSubmitting)
    // 등록 중에는 끌어내려 닫는 것도 막는다 — 결과가 나오기 전에 화면이 사라지면 어떤 요청이
    // 무엇이 됐는지 알 수 없다.
    val sheetState =
        rememberModalBottomSheetState(
            confirmValueChange = { value -> !(submitting && value == SheetValue.Hidden) },
        )
    val skip = { onIntent(InviteOnboardingIntent.Skip) }

    ModalBottomSheet(
        modifier = modifier,
        // 스크림 탭·끌어내리기·뒤로가기는 "나중에 하기"와 같은 처리다.
        onDismissRequest = { if (!state.isSubmitting) skip() },
        sheetState = sheetState,
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.sheet,
        // 하단 안전 영역과 키보드 높이는 내용이 직접 낀다.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = { BottomSheetDefaults.DragHandle(color = ManyakTheme.colors.border) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = ManyakTheme.spacing.gutter)
                    // 위쪽은 드래그 핸들이 자체 여백을 갖고 있어 더 두지 않는다.
                    .padding(bottom = ManyakTheme.spacing.gutter),
            // 안내 · 입력 · 동작 세 덩어리를 32로 떼어 놓고, 한 덩어리 안은 8로 붙인다.
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
        ) {
            InviteOnboardingHeadline()
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                Text(
                    text = stringResource(R.string.invite_code_label),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.text,
                )
                InviteCodeField(
                    code = state.code,
                    enabled = !state.isSubmitting,
                    isError = state.errorRes != null,
                    onCodeChange = { onIntent(InviteOnboardingIntent.CodeChanged(it)) },
                    onSubmit = { onIntent(InviteOnboardingIntent.Submit) },
                )
                state.errorRes?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        style = ManyakTheme.typography.bodySmall,
                        color = ManyakTheme.colors.textDanger,
                    )
                }
            }
            InviteOnboardingActions(
                isSubmitting = state.isSubmitting,
                onSkip = skip,
                onSubmit = { onIntent(InviteOnboardingIntent.Submit) },
            )
        }
    }
}

@Composable
private fun InviteOnboardingHeadline(modifier: Modifier = Modifier) {
    val inviteReward = LocalCreditPolicy.current?.inviteReward
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            modifier = Modifier.alpha(creditAmountAlpha(inviteReward == null)),
            text = stringResource(R.string.invite_onboarding_title, creditAmountText(inviteReward)),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(R.string.invite_onboarding_description),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/**
 * 주 동작은 전체 폭으로 두고 닫기는 그 아래 한 단 작게 둔다 — 같은 폭으로 나란히 두면 둘의 무게가
 * 같아 보여, 이 시트에서 무엇이 다음 행동인지 흐려진다.
 */
@Composable
private fun InviteOnboardingActions(
    isSubmitting: Boolean,
    onSkip: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Button(
            modifier = Modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
            onClick = onSubmit,
            enabled = !isSubmitting,
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.brand,
                    contentColor = ManyakTheme.colors.textInverse,
                    disabledContainerColor = ManyakTheme.colors.brand,
                    disabledContentColor = ManyakTheme.colors.textInverse,
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 진행 중에도 라벨 자리를 유지해 버튼 폭이 스피너 폭으로 줄지 않게 한다.
                Text(
                    modifier = Modifier.alpha(if (isSubmitting) 0f else 1f),
                    text = stringResource(R.string.invite_onboarding_submit),
                    style = ManyakTheme.typography.labelLarge,
                )
                if (isSubmitting) {
                    ManyakProgressIndicator(
                        modifier = Modifier.size(ManyakTheme.sizes.icon),
                        color = ManyakTheme.colors.textInverse,
                    )
                }
            }
        }
        // M3 `TextButton` 은 최소 터치 타깃 48dp 를 레이아웃 높이로 밀어 올려 32dp 가 나오지 않는다.
        // 다른 작은 버튼들과 같이 Box + clickable 로 그린다.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ManyakTheme.sizes.controlSmall)
                    .clip(ManyakTheme.shapes.control)
                    .clickable(enabled = !isSubmitting, role = Role.Button, onClick = onSkip),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.invite_onboarding_skip),
                style = ManyakTheme.typography.labelSmall,
                color = if (isSubmitting) ManyakTheme.colors.textDisabled else ManyakTheme.colors.textSubtle,
            )
        }
    }
}

/**
 * 초대 코드 입력창.
 *
 * **자동으로 초점을 주지 않는다** — 앱 진입과 동시에 뜨는 시트라 키보드가 함께 올라오면
 * 안내와 버튼을 가린다.
 */
@Composable
private fun InviteCodeField(
    code: String,
    enabled: Boolean,
    isError: Boolean,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor =
        when {
            isError -> ManyakTheme.colors.borderDanger
            focused -> ManyakTheme.colors.borderInput
            else -> ManyakTheme.colors.border
        }
    BasicTextField(
        modifier = modifier.fillMaxWidth(),
        value = code,
        onValueChange = onCodeChange,
        enabled = enabled,
        textStyle = ManyakTheme.typography.bodyMedium.copy(color = ManyakTheme.colors.text),
        cursorBrush = SolidColor(ManyakTheme.colors.text),
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                // 코드가 영문·숫자라 한글 자판이 먼저 뜨지 않게 한다.
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ManyakTheme.sizes.input)
                        .clip(ManyakTheme.shapes.control)
                        .background(ManyakTheme.colors.surfaceRaised)
                        .border(1.dp, borderColor, ManyakTheme.shapes.control)
                        .padding(
                            horizontal = ManyakTheme.spacing.controlHorizontal,
                            vertical = ManyakTheme.spacing.controlVertical,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (code.isEmpty()) {
                        Text(
                            text = stringResource(R.string.invite_code_placeholder),
                            style = ManyakTheme.typography.bodyMedium,
                            color = ManyakTheme.colors.textDisabled,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Preview(name = "초대 코드 안내 · 라이트")
@Composable
private fun InviteOnboardingSheetPreview() {
    ManyakTheme(darkTheme = false) {
        InviteOnboardingContent(
            state = InviteOnboardingUiState(pending = true, code = "ABCD1234"),
            onIntent = {},
        )
    }
}
