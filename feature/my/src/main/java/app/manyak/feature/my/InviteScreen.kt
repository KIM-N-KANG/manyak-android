package app.manyak.feature.my

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.domain.invite.Invite
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakTextField
import app.manyak.core.ui.component.SkeletonPlaceholder
import app.manyak.core.ui.component.rememberSkeletonPulseAlpha
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 친구 초대. 내 코드를 나눠 주는 일과 받은 코드를 등록하는 일이 한 화면에 있다.
 */
@Composable
fun InviteScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InviteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(viewModel) { viewModel.onIntent(InviteIntent.Load) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    InviteEffect.Redeemed ->
                        Toast.makeText(context, R.string.invite_code_redeemed, Toast.LENGTH_SHORT).show()
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
        MyDetailHeader(titleRes = R.string.my_invite, onBack = onBack)
        InviteContent(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InviteContent(
    state: InviteUiState,
    onIntent: (InviteIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.gutter, bottom = ManyakTheme.spacing.screenBottom),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        InviteHeadline()
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            InviteCodeCard(state = state, onRetry = { onIntent(InviteIntent.Retry) })
            InviteShareActions(state = state)
        }
        InviteRedeemSection(state = state, onIntent = onIntent)
        InviteGuide()
    }
}

@Composable
private fun InviteHeadline(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Text(
            text = stringResource(R.string.invite_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(R.string.invite_description),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/** 내 초대 코드 상자. 조회 중·실패·성공 셋 다 같은 자리에서 말한다. */
@Composable
private fun InviteCodeCard(
    state: InviteUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ManyakTheme.colors.backgroundNeutral, ManyakTheme.shapes.card)
                .padding(ManyakTheme.spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.invite_my_code),
            style = ManyakTheme.typography.bodyMediumStrong,
            color = ManyakTheme.colors.textSubtle,
        )
        when {
            state.isLoading ->
                SkeletonPlaceholder(
                    alpha = rememberSkeletonPulseAlpha(),
                    modifier = Modifier.width(CodeSkeletonWidth).heightIn(min = CodeSkeletonHeight),
                )

            state.isCodeUnavailable -> InviteCodeUnavailable(onRetry = onRetry)

            else ->
                Text(
                    text = state.inviteCode.orEmpty(),
                    style = ManyakTheme.typography.titleLarge.copy(letterSpacing = CodeLetterSpacing),
                    color = ManyakTheme.colors.text,
                )
        }
        val count = state.invite?.monthlyRewardCount
        val limit = state.invite?.monthlyRewardLimit
        if (state.inviteCode != null && count != null && limit != null) {
            Text(
                text = stringResource(R.string.invite_monthly_reward, count, limit),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

@Composable
private fun InviteCodeUnavailable(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.invite_code_load_failed),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
            textAlign = TextAlign.Center,
        )
        MyOutlineButton(label = stringResource(R.string.invite_retry), onClick = onRetry)
    }
}

/**
 * 복사와 공유. 둘 다 코드가 있어야 뜻이 있으므로 조회 전·실패에는 잠근다.
 *
 * 복사·공유는 도메인에 남는 일이 아니라 플랫폼 동작이라 화면이 직접 한다.
 */
@Composable
private fun InviteShareActions(
    state: InviteUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val code = state.inviteCode
    val copied = stringResource(R.string.invite_code_copied)
    val copyFailed = stringResource(R.string.invite_code_copy_failed)
    val shareFailed = stringResource(R.string.invite_share_failed)
    val shareSubject = stringResource(R.string.invite_share_subject)
    val shareMessage = stringResource(R.string.invite_share_message, code.orEmpty(), state.shareUrl)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        MyOutlineButton(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.invite_copy_code),
            iconRes = R.drawable.ic_copy,
            enabled = code != null,
            onClick = {
                val message = if (context.copyToClipboard(code.orEmpty())) copied else copyFailed
                // 안드로이드 13 부터는 시스템이 복사 확인을 직접 띄운다. 같은 말을 두 번 하지 않는다.
                if (message != copied || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
        )
        MyPrimaryButton(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.invite_share),
            iconRes = R.drawable.ic_share,
            enabled = code != null,
            onClick = {
                if (!context.shareText(subject = shareSubject, message = shareMessage)) {
                    Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun InviteRedeemSection(
    state: InviteUiState,
    onIntent: (InviteIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Text(
            text = stringResource(R.string.invite_redeem_title),
            style = ManyakTheme.typography.titleMediumStrong,
            color = ManyakTheme.colors.text,
        )
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            MyFieldLabel(text = stringResource(R.string.invite_code_label))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ManyakTextField(
                    modifier = Modifier.weight(1f),
                    value = state.code,
                    onValueChange = { onIntent(InviteIntent.CodeChanged(it)) },
                    placeholder = stringResource(R.string.invite_code_placeholder),
                    enabled = !state.isSubmitting,
                    isError = state.errorRes != null,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            // 코드가 영문·숫자라 한글 자판이 먼저 뜨지 않게 한다.
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { onIntent(InviteIntent.Redeem) }),
                )
                MyPrimaryButton(
                    label = stringResource(R.string.invite_redeem_submit),
                    isLoading = state.isSubmitting,
                    onClick = { onIntent(InviteIntent.Redeem) },
                )
            }
            state.errorRes?.let { errorRes ->
                MyFieldMessage(text = stringResource(errorRes), isError = true)
            }
        }
    }
}

@Composable
private fun InviteGuide(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Text(
            text = stringResource(R.string.invite_guide_title),
            style = ManyakTheme.typography.titleMediumStrong,
            color = ManyakTheme.colors.text,
        )
        // 항목 사이는 벌리지 않는다. 글머리 목록의 리듬은 행간이 만든다.
        Column {
            stringArrayResource(R.array.invite_guide_lines).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                    Text(
                        text = GUIDE_BULLET,
                        style = ManyakTheme.typography.bodyLarge,
                        color = ManyakTheme.colors.textSubtle,
                    )
                    Text(
                        text = line,
                        style = ManyakTheme.typography.bodyLarge,
                        color = ManyakTheme.colors.textSubtle,
                    )
                }
            }
        }
    }
}

/** @return 클립보드에 담았으면 true. 기기가 클립보드를 내주지 않으면 false 다. */
private fun Context.copyToClipboard(code: String): Boolean {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.invite_my_code), code))
    return true
}

/**
 * 시스템 공유 시트로 초대 문구를 보낸다.
 *
 * 웹은 카카오 SDK 로 공유 카드를 띄우지만 앱은 안드로이드 공유 시트를 쓴다 — 카카오톡을 포함해
 * 기기에 있는 앱을 사용자가 고르고, 공유 SDK 를 따로 싣지 않는다.
 *
 * @return 시트를 열었으면 true.
 */
private fun Context.shareText(
    subject: String,
    message: String,
): Boolean {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
        }
    val chooser = Intent.createChooser(intent, subject)
    return runCatching { startActivity(chooser) }.isSuccess
}

/** 이용 안내의 글머리 기호. 목록 마크업이 없는 Compose 에서는 문자로 그린다. */
private const val GUIDE_BULLET = "·"

private val CodeSkeletonWidth = 128.dp
private val CodeSkeletonHeight = 28.dp

/** 코드는 한 글자씩 읽어 옮겨 적는 값이라 자간을 벌린다. */
private val CodeLetterSpacing = 2.sp

@Preview(showBackground = true, name = "친구 초대 · 라이트")
@Composable
private fun InviteScreenPreview() {
    ManyakTheme(darkTheme = false) {
        InviteContent(
            state =
                InviteUiState(
                    isLoading = false,
                    invite = Invite(code = "ABCD1234", monthlyRewardCount = 2, monthlyRewardLimit = 10),
                    shareUrl = "https://manyak.app",
                ),
            onIntent = {},
        )
    }
}
