package app.manyak.feature.my

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 회원 탈퇴. 되돌릴 수 없는 동작이라 무엇을 잃는지 항목마다 확인을 받고 나서야 버튼이 열린다.
 */
@Composable
fun WithdrawalScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WithdrawalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    WithdrawalEffect.Failed ->
                        Toast.makeText(context, R.string.withdrawal_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        MyDetailHeader(titleRes = R.string.my_withdrawal, onBack = onBack)
        WithdrawalContent(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WithdrawalContent(
    state: WithdrawalUiState,
    onIntent: (WithdrawalIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = ManyakTheme.spacing.gutter),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = ManyakTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
        ) {
            WithdrawalHeadline()
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section)) {
                WithdrawalConfirmations.forEachIndexed { index, confirmation ->
                    ConfirmationRow(
                        confirmation = confirmation,
                        isChecked = index in state.checkedIndices,
                        enabled = !state.isWithdrawing,
                        onToggle = { onIntent(WithdrawalIntent.ToggleConfirmation(index)) },
                    )
                }
            }
        }
        MyPrimaryButton(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = ManyakTheme.spacing.gutter),
            label = stringResource(R.string.withdrawal_submit),
            enabled = state.canWithdraw,
            isLoading = state.isWithdrawing,
            isDanger = true,
            onClick = { onIntent(WithdrawalIntent.Withdraw) },
        )
    }
}

@Composable
private fun WithdrawalHeadline(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.withdrawal_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(R.string.withdrawal_description),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/** 확인 항목 한 줄. 체크박스만이 아니라 줄 전체가 토글이라 문구를 눌러도 켜진다. */
@Composable
private fun ConfirmationRow(
    confirmation: WithdrawalConfirmation,
    isChecked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(
                    value = isChecked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                ),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        ConfirmationCheckbox(isChecked = isChecked)
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline)) {
            Text(
                text = stringResource(confirmation.titleRes),
                style = ManyakTheme.typography.bodyLargeStrong,
                color = ManyakTheme.colors.text,
            )
            Text(
                text = stringResource(confirmation.descriptionRes),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

/**
 * 체크 표시. 줄 전체가 토글을 맡으므로 여기에는 클릭도 접근성 이름도 붙이지 않는다.
 *
 * 첫 줄 글자와 눈높이를 맞추려고 위로 조금 내린다 — 20dp 네모를 줄 맨 위에 붙이면 글자보다 높이 뜬다.
 */
@Composable
private fun ConfirmationCheckbox(
    isChecked: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isChecked) ManyakTheme.colors.brand else ManyakTheme.colors.border
    Box(
        modifier =
            modifier
                .padding(top = CheckboxTopAlignment)
                .size(ManyakTheme.sizes.icon)
                .clip(ManyakTheme.shapes.checkbox)
                .background(if (isChecked) ManyakTheme.colors.brand else ManyakTheme.colors.surface)
                .border(1.dp, borderColor, ManyakTheme.shapes.checkbox),
        contentAlignment = Alignment.Center,
    ) {
        if (isChecked) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                tint = ManyakTheme.colors.textInverse,
            )
        }
    }
}

private val CheckboxTopAlignment = 2.dp

@Preview(showBackground = true, name = "회원 탈퇴 · 라이트")
@Composable
private fun WithdrawalScreenPreview() {
    ManyakTheme(darkTheme = false) {
        WithdrawalContent(state = WithdrawalUiState(checkedIndices = setOf(0, 1)), onIntent = {})
    }
}
