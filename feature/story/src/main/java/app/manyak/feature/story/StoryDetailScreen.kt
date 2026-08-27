package app.manyak.feature.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.domain.story.StoryDetail
import app.manyak.core.ui.R
import app.manyak.core.ui.component.LoadFailedContent
import app.manyak.core.ui.component.rememberDelayedProgressVisibility
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 스토리 상세. 셸 없는 전체 화면이며 홈·제작 목록의 카드 탭으로 들어온다.
 *
 * 조회는 화면이 보일 때마다 시작한다 — 채팅방에서 뒤로가기로 돌아오는 자리라 플레이한 만큼
 * 턴 수가 늘고 본 엔딩이 새로 붙는다.
 */
@Composable
fun StoryDetailScreen(
    storyId: String,
    onBack: () -> Unit,
    onEnterChat: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoryDetailViewModel =
        hiltViewModel<StoryDetailViewModel, StoryDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(storyId) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnEnterChat by rememberUpdatedState(onEnterChat)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is StoryDetailEffect.NavigateToChat -> currentOnEnterChat(effect.chatId)
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onIntent(StoryDetailIntent.ScreenShown) }

    StoryDetailContent(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun StoryDetailContent(
    state: StoryDetailUiState,
    onBack: () -> Unit,
    onIntent: (StoryDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 본문 제목이 헤더 아래로 완전히 나갔을 때만 헤더가 제목을 대신 든다.
    val showHeaderTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > OVERVIEW_ITEM_INDEX }
    }

    val thumbnailUrl = state.story?.thumbnailUrl

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            StoryDetailHeader(
                title = state.story?.title.orEmpty(),
                showTitle = showHeaderTitle && state.story != null,
                onBack = onBack,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                StoryDetailStatus(state = state, listState = listState, onIntent = onIntent)
            }
        }

        if (state.isImageViewerOpen && thumbnailUrl != null) {
            StoryImageViewer(
                imageUrl = thumbnailUrl,
                onClose = { onIntent(StoryDetailIntent.CloseImageViewer) },
            )
        }
    }
}

@Composable
private fun StoryDetailStatus(
    state: StoryDetailUiState,
    listState: LazyListState,
    onIntent: (StoryDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSkeleton = rememberDelayedProgressVisibility(state.isLoading && state.story == null)

    when {
        state.story != null ->
            StoryDetailLoaded(
                modifier = modifier,
                state = state,
                story = state.story,
                listState = listState,
                onIntent = onIntent,
            )

        state.loadError == StoryDetailLoadError.NOT_FOUND ->
            // 같은 요청의 결과가 달라지지 않아 재시도를 두지 않는다. 남는 동작은 헤더 뒤로가기뿐이다.
            Box(
                modifier = modifier.fillMaxSize().padding(horizontal = ManyakTheme.spacing.gutter),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.story_detail_not_found),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.text,
                    textAlign = TextAlign.Center,
                )
            }

        state.loadError != null ->
            LoadFailedContent(
                message = stringResource(R.string.story_load_failed),
                onRetry = { onIntent(StoryDetailIntent.Retry) },
                modifier = modifier.fillMaxSize().padding(horizontal = ManyakTheme.spacing.gutter),
            )

        // 금방 끝나는 조회에서 자리만 잡았다 사라지는 깜빡임을 만들지 않는다.
        showSkeleton -> StoryDetailSkeleton(modifier = modifier)

        else -> Box(modifier = modifier.fillMaxSize())
    }
}

@Composable
private fun StoryDetailLoaded(
    state: StoryDetailUiState,
    story: StoryDetail,
    listState: LazyListState,
    onIntent: (StoryDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // 큰 글자 크기에서 CTA 가 커져도 본문 마지막 줄을 덮지 않도록 실제 높이를 재서 여백에 싣는다.
    var ctaHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = ManyakTheme.spacing.gutter,
                    end = ManyakTheme.spacing.gutter,
                    bottom = ctaHeight,
                ),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
        ) {
            storyDetailBody(
                story = story,
                selectedStartSettingId = state.selectedStartSettingId,
                selectedStartSituation = state.selectedStartSetting?.startSituation,
                onThumbnailClick = { onIntent(StoryDetailIntent.OpenImageViewer) },
                onSelectStartSetting = { id -> onIntent(StoryDetailIntent.SelectStartSetting(id)) },
            )
        }

        StartChatCta(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size -> ctaHeight = with(density) { size.height.toDp() } },
            isStarting = state.isStartingChat,
            failed = state.startChatFailed,
            onClick = { onIntent(StoryDetailIntent.StartChat) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryDetailHeader(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                // 제목이 나타났다 사라지는 자리라 자리 자체는 늘 잡아 둔다.
                modifier = Modifier.alpha(if (showTitle) 1f else 0f),
                text = title,
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

/** 표지와 제목 묶음이 한 항목(0번)이다. 이 항목을 지나야 제목이 헤더로 올라간다. */
private const val OVERVIEW_ITEM_INDEX = 0

@Preview(showBackground = true, name = "스토리 상세 · 기본")
@Composable
private fun StoryDetailPreview() {
    ManyakTheme(darkTheme = false) {
        StoryDetailContent(
            state =
                StoryDetailUiState(
                    isLoading = false,
                    story = previewStory(),
                    selectedStartSettingId = "a",
                ),
            onBack = {},
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "스토리 상세 · 시작 설정 1개")
@Composable
private fun StoryDetailSingleStartSettingPreview() {
    ManyakTheme(darkTheme = false) {
        StoryDetailContent(
            state =
                StoryDetailUiState(
                    isLoading = false,
                    story = previewStory(startSettings = previewStartSettings().take(1), reachedEndings = emptyList()),
                    selectedStartSettingId = "a",
                ),
            onBack = {},
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "스토리 상세 · 없는 스토리")
@Composable
private fun StoryDetailNotFoundPreview() {
    ManyakTheme(darkTheme = false) {
        StoryDetailContent(
            state = StoryDetailUiState(isLoading = false, loadError = StoryDetailLoadError.NOT_FOUND),
            onBack = {},
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "스토리 상세 · 조회 실패")
@Composable
private fun StoryDetailLoadFailedPreview() {
    ManyakTheme(darkTheme = false) {
        StoryDetailContent(
            state = StoryDetailUiState(isLoading = false, loadError = StoryDetailLoadError.GENERAL),
            onBack = {},
            onIntent = {},
        )
    }
}
