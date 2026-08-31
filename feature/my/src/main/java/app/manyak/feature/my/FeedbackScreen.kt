package app.manyak.feature.my

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.ui.R
import app.manyak.core.ui.component.FocusScrollMargin
import app.manyak.core.ui.component.ManyakInputCounter
import app.manyak.core.ui.component.ManyakMultilineTextField
import app.manyak.core.ui.component.ManyakTextField
import app.manyak.core.ui.component.clearFocusOnTap
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 피드백. 본문만 필수이고 이메일은 답변이 필요할 때만 받는다.
 */
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedbackViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                val message =
                    when (effect) {
                        FeedbackEffect.Submitted -> R.string.feedback_submitted
                        FeedbackEffect.SubmitFailed -> R.string.feedback_submit_failed
                    }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    FocusScrollMargin {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .clearFocusOnTap(focusManager),
        ) {
            MyDetailHeader(titleRes = R.string.my_feedback, onBack = onBack)
            FeedbackContent(
                state = state,
                onIntent = viewModel::onIntent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackContent(
    state: FeedbackUiState,
    onIntent: (FeedbackIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 루트가 safeDrawing 으로 IME 여백을 이미 먹고 소비하므로 여기서 다시 끼지 않는다.
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
            FeedbackHeadline()
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section)) {
                FeedbackBodyField(state = state, onIntent = onIntent)
                FeedbackEmailField(state = state, onIntent = onIntent)
            }
        }
        // IME 가 열리면 전송 버튼 자리를 콘텐츠에 돌려 입력 필드가 키보드 위로 스크롤되게 한다.
        if (!WindowInsets.isImeVisible) {
            FeedbackSubmit(state = state, onIntent = onIntent)
        }
    }
}

@Composable
private fun FeedbackHeadline(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.feedback_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(R.string.feedback_description),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

@Composable
private fun FeedbackBodyField(
    state: FeedbackUiState,
    onIntent: (FeedbackIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        MyFieldLabel(text = stringResource(R.string.feedback_body_label), isRequired = true)
        ManyakMultilineTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.body,
            onValueChange = { onIntent(FeedbackIntent.BodyChanged(it)) },
            placeholder = stringResource(R.string.feedback_body_placeholder),
            enabled = !state.isSubmitting,
            isError = state.errorRes != null,
            footer = {
                ManyakInputCounter(
                    length = state.body.length,
                    maxLength = FeedbackUiState.BODY_MAX_LENGTH,
                )
            },
        )
    }
}

@Composable
private fun FeedbackEmailField(
    state: FeedbackUiState,
    onIntent: (FeedbackIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        MyFieldLabel(text = stringResource(R.string.feedback_email_label))
        ManyakTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.email,
            onValueChange = { onIntent(FeedbackIntent.EmailChanged(it)) },
            placeholder = stringResource(R.string.feedback_email_placeholder),
            enabled = !state.isSubmitting,
            keyboardOptions =
                KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
        )
        MyFieldMessage(text = stringResource(R.string.feedback_email_description))
    }
}

@Composable
private fun FeedbackSubmit(
    state: FeedbackUiState,
    onIntent: (FeedbackIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        state.errorRes?.let { errorRes ->
            MyFieldMessage(text = stringResource(errorRes), isError = true)
        }
        MyPrimaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.feedback_submit),
            isLoading = state.isSubmitting,
            onClick = { onIntent(FeedbackIntent.Submit) },
        )
    }
}

@Preview(showBackground = true, name = "피드백 · 라이트")
@Composable
private fun FeedbackScreenPreview() {
    ManyakTheme(darkTheme = false) {
        FeedbackContent(state = FeedbackUiState(body = "채팅 답장이 조금 느린 것 같아요"), onIntent = {})
    }
}
