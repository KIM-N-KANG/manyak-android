package app.manyak.feature.story

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.domain.story.StoryDetail
import app.manyak.core.ui.R
import app.manyak.core.ui.component.LoadFailedContent
import app.manyak.core.ui.component.ManyakDestructiveDialog
import app.manyak.core.ui.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.core.ui.component.ScrollEdgeFadeHeight
import app.manyak.core.ui.component.StoryOverlayScrim
import app.manyak.core.ui.component.StoryReportSheet
import app.manyak.core.ui.component.rememberDelayedProgressVisibility
import app.manyak.core.ui.report.StoryReportAction
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
    val currentOnBack by rememberUpdatedState(onBack)
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is StoryDetailEffect.NavigateToChat -> currentOnEnterChat(effect.chatId)

                    StoryDetailEffect.StoryDeleted -> {
                        Toast.makeText(context, R.string.studio_story_deleted, Toast.LENGTH_SHORT).show()
                        currentOnBack()
                    }

                    StoryDetailEffect.ShowDeleteFailed ->
                        Toast.makeText(context, R.string.studio_story_delete_failed, Toast.LENGTH_SHORT).show()

                    StoryDetailEffect.ShowReportSubmitted ->
                        Toast.makeText(context, R.string.story_report_submitted, Toast.LENGTH_SHORT).show()

                    StoryDetailEffect.ShowReportFailed ->
                        Toast.makeText(context, R.string.story_report_failed, Toast.LENGTH_SHORT).show()
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

/**
 * 앱바는 본문 위에 얹혀 있다 — 표지가 상태바 뒤까지 올라가야 해서 앱바가 세로 흐름의 한 칸을
 * 차지할 수 없다. 대신 무엇이 앱바 뒤로 지나갔는지를 레이아웃 결과에서 직접 재어, 앱바가 표지
 * 위에서는 투명하게 있다가 표지가 끝나는 지점에서 제 배경과 제목을 되찾게 한다.
 */
@Composable
private fun StoryDetailContent(
    state: StoryDetailUiState,
    onBack: () -> Unit,
    onIntent: (StoryDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var headerHeight by remember { mutableFloatStateOf(0f) }
    var headerBottom by remember { mutableFloatStateOf(0f) }
    // 재기 전에는 제목이 아직 앱바 아래에 있는 것으로 둔다.
    var titleBottom by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var contentWidth by remember { mutableIntStateOf(0) }

    val thumbnailUrl = state.story?.thumbnailUrl
    // 표지 사진이 있을 때만 앱바가 투명하게 시작한다 — 자리만 채운 회색 위에서는 흰 아이콘이 묻힌다.
    // 상태로 들고 있어야 아래 파생 상태들이 조회 결과가 바뀔 때마다 다시 만들어지지 않는다.
    val hasHeroImage by rememberUpdatedState(thumbnailUrl != null)

    // 본문 제목이 앱바 뒤로 다 지나간 뒤에 앱바가 제목을 이어받는다.
    val showHeaderTitle by remember { derivedStateOf { titleBottom <= headerBottom } }

    // 표지가 앱바 뒤로 지나간 정도(0..1). 표지는 화면 폭을 그대로 채우므로 높이는 폭에서 나온다.
    val headerSurfaceAlpha by remember {
        derivedStateOf {
            val scrollRange = contentWidth / STORY_THUMBNAIL_ASPECT_RATIO - headerHeight
            when {
                !hasHeroImage -> 1f
                listState.firstVisibleItemIndex > OVERVIEW_ITEM_INDEX -> 1f
                scrollRange <= 0f -> 1f
                else -> (listState.firstVisibleItemScrollOffset / scrollRange).coerceIn(0f, 1f)
            }
        }
    }
    val overHeroImage by remember {
        derivedStateOf { headerSurfaceAlpha < SYSTEM_BAR_FLIP_ALPHA }
    }

    if (overHeroImage || state.isImageViewerOpen) {
        LightSystemBarIcons()
    }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { size -> contentWidth = size.width }) {
        StoryDetailStatus(
            state = state,
            listState = listState,
            headerHeight = with(LocalDensity.current) { headerHeight.toDp() },
            onIntent = onIntent,
            onTitleBottomChanged = { bottom -> titleBottom = bottom },
        )

        StoryDetailHeader(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { coordinates ->
                        headerHeight = coordinates.size.height.toFloat()
                        headerBottom = coordinates.positionInRoot().y + coordinates.size.height
                    },
            title = state.story?.title.orEmpty(),
            showTitle = showHeaderTitle && state.story != null,
            // 스크롤 값은 앱바 안에서만 읽는다 — 여기서 읽으면 본문까지 매 프레임 다시 구성된다.
            surfaceAlpha = { headerSurfaceAlpha },
            onBack = onBack,
            // 신고할 대상이 아직 없으면 진입점을 두지 않는다.
            onReport = { onIntent(StoryDetailIntent.Report(StoryReportAction.Open)) }.takeIf { state.story != null },
            // 삭제는 서버가 내 것이라고 한 스토리에만 — 소유 판정은 응답의 몫이다.
            onDelete = { onIntent(StoryDetailIntent.RequestDelete) }.takeIf { state.story?.isOwner == true },
        )

        StoryDetailOverlays(state = state, thumbnailUrl = thumbnailUrl, onIntent = onIntent)
    }
}

/** 본문 위에 얹히는 것들 — 이미지 뷰어와 신고 시트. 본문 배치와 섞이지 않게 따로 둔다. */
@Composable
private fun StoryDetailOverlays(
    state: StoryDetailUiState,
    thumbnailUrl: String?,
    onIntent: (StoryDetailIntent) -> Unit,
) {
    if (state.isImageViewerOpen && thumbnailUrl != null) {
        StoryImageViewer(
            imageUrl = thumbnailUrl,
            onClose = { onIntent(StoryDetailIntent.CloseImageViewer) },
        )
    }

    if (state.report.isSheetOpen) {
        StoryReportSheet(
            state = state.report,
            onAction = { action -> onIntent(StoryDetailIntent.Report(action)) },
        )
    }

    if (state.isDeleteDialogOpen) {
        ManyakDestructiveDialog(
            title = stringResource(R.string.studio_delete_dialog_title),
            description = stringResource(R.string.studio_delete_dialog_description),
            confirmLabel = stringResource(R.string.studio_story_delete),
            cancelLabel = stringResource(R.string.studio_delete_dialog_cancel),
            onConfirm = { onIntent(StoryDetailIntent.ConfirmDelete) },
            onDismiss = { onIntent(StoryDetailIntent.DismissDeleteDialog) },
            inProgress = state.isDeleting,
        )
    }
}

@Composable
private fun StoryDetailStatus(
    state: StoryDetailUiState,
    listState: LazyListState,
    headerHeight: Dp,
    onIntent: (StoryDetailIntent) -> Unit,
    onTitleBottomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSkeleton = rememberDelayedProgressVisibility(state.isLoading && state.story == null)
    // 표지가 없는 상태들은 앱바 뒤로 들어갈 이유가 없어 앱바가 차지한 만큼 비켜 둔다.
    val messageModifier =
        modifier
            .fillMaxSize()
            .padding(top = headerHeight)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ).padding(horizontal = ManyakTheme.spacing.gutter)

    when {
        state.story != null ->
            StoryDetailLoaded(
                modifier = modifier,
                state = state,
                story = state.story,
                listState = listState,
                onIntent = onIntent,
                onTitleBottomChanged = onTitleBottomChanged,
            )

        state.loadError == StoryDetailLoadError.NOT_FOUND ->
            // 같은 요청의 결과가 달라지지 않아 재시도를 두지 않는다. 남는 동작은 헤더 뒤로가기뿐이다.
            Box(modifier = messageModifier, contentAlignment = Alignment.Center) {
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
                modifier = messageModifier,
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
    onTitleBottomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // 큰 글자 크기에서 CTA 가 커져도 본문 마지막 줄을 덮지 않도록 실제 높이를 재서 여백에 싣는다.
    var ctaHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            // 표지가 앱바 뒤까지 올라가야 해서 위쪽 여백은 두지 않고, 좌우 여백은 본문 항목이 각자 건다.
            // 아래는 CTA 의 불투명한 부분만큼만 비운다 — 그 위 페이드 띠는 콘텐츠를 덮는 자리라
            // 여백까지 잡으면 마지막 줄 아래가 그만큼 비어 보인다.
            contentPadding =
                PaddingValues(bottom = (ctaHeight - ScrollEdgeFadeHeight).coerceAtLeast(0.dp)),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
        ) {
            storyDetailBody(
                story = story,
                selectedStartSettingId = state.selectedStartSettingId,
                selectedStartSetting = state.selectedStartSetting,
                onThumbnailClick = { onIntent(StoryDetailIntent.OpenImageViewer) },
                onSelectStartSetting = { id -> onIntent(StoryDetailIntent.SelectStartSetting(id)) },
                onTitleBottomChanged = onTitleBottomChanged,
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
    surfaceAlpha: () -> Float,
    onBack: () -> Unit,
    onReport: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val alpha = surfaceAlpha()
    // 표지 위에 얹힌 동안에는 앱바 아이콘 색을 테마가 아니라 표지 대비로 정한다.
    val contentColor = lerp(Color.White, ManyakTheme.colors.text, alpha)
    // 밝은 표지에서도 흰 아이콘이 읽히도록, 배경이 없는 만큼만 표지를 눌러 준다.
    val scrim = remember { Brush.verticalGradient(listOf(StoryOverlayScrim, Color.Transparent)) }
    val titleAlpha by animateFloatAsState(targetValue = if (showTitle) 1f else 0f, label = "headerTitle")

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(brush = scrim, alpha = 1f - alpha)
                .background(color = ManyakTheme.colors.surface.copy(alpha = alpha)),
    ) {
        TopAppBar(
            title = {
                Text(
                    // 제목이 나타났다 사라지는 자리라 자리 자체는 늘 잡아 둔다.
                    modifier = Modifier.alpha(titleAlpha),
                    text = title,
                    style = ManyakTheme.typography.bodyLargeStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.common_back),
                        tint = contentColor,
                    )
                }
            },
            actions = {
                if (onReport != null) {
                    StoryDetailHeaderMenu(onReport = onReport, onDelete = onDelete, tint = contentColor)
                }
            },
            // 표지가 상태바 뒤까지 올라가므로 상태바 자리는 앱바가 직접 낀다.
            windowInsets =
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = contentColor,
                ),
        )
    }
}

/**
 * 어두운 표지·뷰어 위에서는 시스템 바 아이콘도 밝은 쪽이라야 읽힌다 — 앱바 아이콘만 희게 하면
 * 바로 위의 시계·배터리가 표지에 묻힌다. 걷히면 원래 값으로 돌려놓는다: 반대로 덮어쓰면
 * 다크 테마에서 밝아야 할 아이콘까지 어둡게 만든다.
 */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = ViewCompat.getWindowInsetsController(view)
        val wasLightStatusBars = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            wasLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
        }
    }
}

/** 표지와 제목 묶음이 한 항목(0번)이다. 이 항목을 지나면 표지도 이미 다 지나갔다. */
private const val OVERVIEW_ITEM_INDEX = 0

/** 앱바 배경이 이만큼 차기 전까지는 아직 표지 위로 본다 — 아이콘 색은 중간값 없이 한 번에 뒤집힌다. */
private const val SYSTEM_BAR_FLIP_ALPHA = 0.5f

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
