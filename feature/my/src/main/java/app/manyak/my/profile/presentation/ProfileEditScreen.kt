package app.manyak.my.profile.presentation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.designsystem.component.FocusScrollMargin
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakDestructiveDialog
import app.manyak.designsystem.component.ManyakInputCounter
import app.manyak.designsystem.component.ManyakTextField
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.my.presentation.component.MyDetailHeader
import app.manyak.my.presentation.component.MyFieldLabel
import app.manyak.my.presentation.component.MyFieldMessage
import app.manyak.my.presentation.component.MyPrimaryButton
import app.manyak.my.profile.domain.NICKNAME_MAX_LENGTH
import app.manyak.my.profile.presentation.component.ProfileImage
import app.manyak.my.profile.presentation.component.ProfilePresetSheet
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.my.R as MyR

/**
 * 프로필 수정. 저장하면 알리고 [onDone] 으로, 저장 없이 나가면 [onBack] 으로 들어온 화면에 돌아간다.
 * 뒤로가기는 헤더 버튼과 기기 버튼 모두 ViewModel 이 판정한다. 바뀐 내용이 있으면 먼저 묻는다.
 */
@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileEditEffects(viewModel = viewModel, onBack = onBack, onDone = onDone)
    BackHandler { viewModel.onIntent(ProfileEditIntent.Back) }

    FocusScrollMargin {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            MyDetailHeader(
                titleRes = MyR.string.profile_edit_header,
                onBack = { viewModel.onIntent(ProfileEditIntent.Back) },
            )
            when (state.load) {
                ProfileEditLoad.LOADING -> ProfileEditSkeleton(Modifier.weight(1f))

                ProfileEditLoad.FAILED ->
                    LoadFailedContent(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(ManyakTheme.spacing.gutter),
                        message = stringResource(MyR.string.profile_edit_load_failed),
                        onRetry = { viewModel.onIntent(ProfileEditIntent.Retry) },
                    )

                ProfileEditLoad.READY ->
                    ProfileEditContent(
                        state = state,
                        onIntent = viewModel::onIntent,
                        modifier = Modifier.weight(1f),
                    )
            }
        }
    }

    if (state.isPresetSheetOpen) {
        ProfilePresetSheet(
            load = state.presetLoad,
            presets = state.presets,
            selectedImageUrl = state.imageUrl,
            onSelect = { viewModel.onIntent(ProfileEditIntent.SelectPreset(it)) },
            onRetry = { viewModel.onIntent(ProfileEditIntent.RetryPresets) },
            onDismiss = { viewModel.onIntent(ProfileEditIntent.DismissPresetSheet) },
        )
    }
    if (state.isExitDialogOpen) {
        ManyakDestructiveDialog(
            title = stringResource(MyR.string.profile_edit_exit_title),
            description = stringResource(MyR.string.profile_edit_exit_description),
            confirmLabel = stringResource(MyR.string.profile_edit_exit_leave),
            cancelLabel = stringResource(MyR.string.profile_edit_exit_close),
            onConfirm = { viewModel.onIntent(ProfileEditIntent.ConfirmExit) },
            onDismiss = { viewModel.onIntent(ProfileEditIntent.DismissExitDialog) },
        )
    }
}

@Composable
private fun ProfileEditEffects(
    viewModel: ProfileEditViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    ProfileEditEffect.Close -> currentOnBack()
                    ProfileEditEffect.Saved -> {
                        Toast.makeText(context, MyR.string.profile_edit_saved, Toast.LENGTH_SHORT).show()
                        currentOnDone()
                    }
                    ProfileEditEffect.ShowSaveFailed ->
                        Toast.makeText(context, MyR.string.profile_edit_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun ProfileEditSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonPulseAlpha()
    Column(
        modifier = modifier.fillMaxWidth().padding(ManyakTheme.spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        SkeletonPlaceholder(alpha = alpha, modifier = Modifier.size(ProfileEditImageSize))
        SkeletonPlaceholder(
            alpha = alpha,
            modifier = Modifier.fillMaxWidth().height(ManyakTheme.sizes.input),
            shape = ManyakTheme.shapes.control,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditContent(
    state: ProfileEditUiState,
    onIntent: (ProfileEditIntent) -> Unit,
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
        ) {
            ProfileImageButton(
                imageUrl = state.imageUrl,
                thumbnailBase64 = state.imageThumbnailBase64,
                enabled = !state.isSubmitting,
                onClick = { onIntent(ProfileEditIntent.OpenPresetSheet) },
            )
            NicknameField(state = state, onIntent = onIntent)
        }
        // IME 가 열리면 버튼 자리를 콘텐츠에 돌려 입력 칸이 키보드 위로 스크롤되게 한다.
        if (!WindowInsets.isImeVisible) {
            val submittingLabel = stringResource(MyR.string.profile_edit_submitting)
            MyPrimaryButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = ManyakTheme.spacing.gutter)
                        .semantics { if (state.isSubmitting) contentDescription = submittingLabel },
                label = stringResource(MyR.string.profile_edit_submit),
                enabled = state.isDirty,
                isLoading = state.isSubmitting,
                onClick = { onIntent(ProfileEditIntent.Submit) },
            )
        }
    }
}

/** 연필 표시를 겹친 원형 이미지 버튼. 이미지 자체는 낭독하지 않고 버튼 이름으로 읽힌다. */
@Composable
private fun ProfileImageButton(
    imageUrl: String?,
    thumbnailBase64: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(MyR.string.profile_edit_image_button)
    Box(modifier = Modifier.size(ProfileEditImageSize)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(ManyakTheme.shapes.pill)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = label },
        ) {
            ProfileImage(imageUrl = imageUrl, thumbnailBase64 = thumbnailBase64, modifier = Modifier.fillMaxSize())
        }
        // 배지는 누르는 대상이 아니라 눌림이 아래 버튼으로 내려간다.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(ProfileEditBadgeSize)
                    .clip(ManyakTheme.shapes.pill)
                    .background(ManyakTheme.colors.text)
                    .border(ProfileEditBadgeBorder, ManyakTheme.colors.surface, ManyakTheme.shapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(DesignsystemR.drawable.ic_edit),
                contentDescription = null,
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                tint = ManyakTheme.colors.textInverse,
            )
        }
    }
}

@Composable
private fun NicknameField(
    state: ProfileEditUiState,
    onIntent: (ProfileEditIntent) -> Unit,
) {
    val label = stringResource(MyR.string.profile_edit_nickname)
    val error = state.nicknameError?.let { stringResource(it.messageRes) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        MyFieldLabel(text = label)
        ManyakTextField(
            modifier =
                Modifier.fillMaxWidth().semantics {
                    contentDescription = label
                    if (error != null) error(error)
                },
            value = state.nickname,
            onValueChange = { onIntent(ProfileEditIntent.NicknameChanged(it)) },
            placeholder = label,
            enabled = !state.isSubmitting,
            isError = error != null,
            trailing = { ManyakInputCounter(state.nickname.length, NICKNAME_MAX_LENGTH) },
        )
        MyFieldMessage(
            text = error ?: stringResource(MyR.string.profile_edit_nickname_hint),
            isError = error != null,
        )
    }
}

private val NicknameError.messageRes: Int
    get() =
        when (this) {
            NicknameError.TOO_SHORT -> MyR.string.profile_edit_error_too_short
            NicknameError.INVALID_CHARACTER -> MyR.string.profile_edit_error_invalid_character
            NicknameError.TAKEN -> MyR.string.profile_edit_error_taken
            NicknameError.REJECTED -> MyR.string.profile_edit_error_rejected
        }

/** 웹 수정 화면의 이미지(96px)와 연필 배지(32px)와 같은 크기다. */
private val ProfileEditImageSize = 96.dp
private val ProfileEditBadgeSize = 32.dp
private val ProfileEditBadgeBorder = 2.dp

@Preview(showBackground = true, name = "프로필 수정 오류")
@Composable
private fun ProfileEditPreview() {
    ManyakTheme(darkTheme = false) {
        ProfileEditContent(
            state =
                ProfileEditUiState(
                    load = ProfileEditLoad.READY,
                    original =
                        UserProfile(
                            id = "preview",
                            nickname = "낭만적인표류자",
                            profileImageUrl = null,
                            profileThumbnailBase64 = null,
                            status = AccountStatus.ACTIVE,
                            creditBalance = 0,
                            attendedToday = false,
                            linkedProviders = emptyList(),
                        ),
                    nickname = "달빛 작가",
                    nicknameError = NicknameError.INVALID_CHARACTER,
                ),
            onIntent = {},
        )
    }
}
