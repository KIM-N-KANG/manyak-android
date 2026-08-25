package app.manyak.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.domain.story.CreationResumePoint
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 홈 탭(스토리 목록). 헤더와 하단 탭은 셸이 그리므로 여기서는 콘텐츠만 둔다.
 *
 * 아직 목록이 없어 콘텐츠는 비어 있다. [contentPadding] 은 셸의 chrome 이 차지한 만큼이므로, 스크롤
 * 목록을 넣을 때는 `Modifier.padding` 이 아니라 목록의 `contentPadding` 으로 넘겨야 콘텐츠가 헤더
 * 아래로 흘러 들어간다.
 *
 * 제작 퍼널 진입 FAB 과 이어서 만들기 배너는 셸이 아니라 이 화면이 소유한다. 진행 레코드가 있으면
 * 상단에 배너를 표시하고, FAB 등 배너가 아닌 경로의 진입은 이어서/새로 만들기 다이얼로그로 묻는다.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onCreateStory: () -> Unit,
    onResumeCreation: (CreationResumePoint) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnCreateStory by rememberUpdatedState(onCreateStory)
    val currentOnResumeCreation by rememberUpdatedState(onResumeCreation)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    HomeEffect.NavigateToCreate -> currentOnCreateStory()
                    is HomeEffect.NavigateToResume -> currentOnResumeCreation(effect.resumePoint)
                }
            }
        }
    }

    HomeContent(
        state = state,
        contentPadding = contentPadding,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        state.pendingBanner?.let { banner ->
            PendingCreationBannerRow(
                banner = banner,
                onResume = { onIntent(HomeIntent.ResumeCreation) },
                onDismiss = { onIntent(HomeIntent.DismissPendingCreation) },
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = ManyakTheme.spacing.gutter)
                        .padding(top = ManyakTheme.spacing.compact),
            )
        }
        CreateStoryFab(
            onClick = { onIntent(HomeIntent.CreateStory) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(ManyakTheme.spacing.gutter),
        )
    }

    if (state.showResumeChoiceDialog) {
        ResumeChoiceDialog(
            onResume = { onIntent(HomeIntent.ResumeCreation) },
            onStartNew = { onIntent(HomeIntent.StartNewCreation) },
            onDismiss = { onIntent(HomeIntent.DismissResumeChoiceDialog) },
        )
    }
}

@Composable
private fun PendingCreationBannerRow(
    banner: PendingCreationBanner,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = ManyakTheme.colors.surfaceRaised, shape = ManyakTheme.shapes.card)
                .padding(start = ManyakTheme.spacing.component, end = ManyakTheme.spacing.dense)
                .padding(vertical = ManyakTheme.spacing.dense),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text =
                stringResource(
                    if (banner.isCompleting) {
                        R.string.home_pending_banner_completing
                    } else {
                        R.string.home_pending_banner_making
                    },
                ),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
        )
        TextButton(onClick = onResume) {
            Text(
                text = stringResource(R.string.home_pending_banner_resume),
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.brand,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.home_pending_banner_dismiss),
                tint = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

/** 배너가 아닌 경로로 진입할 때 임시 저장본을 이어갈지 새로 시작할지 묻는다(3-1 제작 임시 저장). */
@Composable
private fun ResumeChoiceDialog(
    onResume: () -> Unit,
    onStartNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.overlay,
        title = {
            Text(
                text = stringResource(R.string.home_pending_dialog_title),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.home_pending_dialog_description),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        },
        confirmButton = {
            Button(
                onClick = onResume,
                shape = ManyakTheme.shapes.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.brand,
                        contentColor = ManyakTheme.colors.textInverse,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.home_pending_banner_resume),
                    style = ManyakTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onStartNew) {
                Text(
                    text = stringResource(R.string.home_pending_dialog_start_new),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
        },
    )
}

@Composable
private fun CreateStoryFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        shape = ManyakTheme.shapes.pill,
        containerColor = ManyakTheme.colors.brand,
        contentColor = ManyakTheme.colors.textInverse,
        elevation =
            FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = stringResource(R.string.home_create_story),
        )
    }
}

@Preview(showBackground = true, name = "홈 · 라이트")
@Composable
private fun HomeScreenPreview() {
    ManyakTheme(darkTheme = false) {
        HomeContent(
            state = HomeUiState(),
            contentPadding = PaddingValues(0.dp),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "홈 · 이어서 만들기 배너")
@Composable
private fun HomeScreenPendingBannerPreview() {
    ManyakTheme(darkTheme = false) {
        HomeContent(
            state =
                HomeUiState(
                    pendingBanner =
                        PendingCreationBanner(
                            isCompleting = false,
                            resumePoint = CreationResumePoint.StorylineStep,
                        ),
                ),
            contentPadding = PaddingValues(0.dp),
            onIntent = {},
        )
    }
}
