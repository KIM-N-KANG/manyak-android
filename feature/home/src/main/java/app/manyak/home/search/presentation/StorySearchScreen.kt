package app.manyak.home.search.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.analytics.entity.StoryListSection
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.component.ManyakTextField
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.home.explore.presentation.component.ExploreGridSkeleton
import app.manyak.home.explore.presentation.component.StoryTileGrid
import app.manyak.home.search.presentation.component.StorySearchHome
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.home.R as HomeR

/**
 * 스토리 검색. 셸 밖 전체 화면이라 하단 탭이 없고 헤더에 뒤로가기와 검색 입력을 둔다.
 *
 * 입력 중인 글자는 화면이 들고, 검색은 제출할 때만 한다. 제출한 검색어가 없으면 최근 검색어와 대표 장르를,
 * 있으면 탐색과 같은 타일 그리드로 결과를 보인다. 상세에서 돌아오면 검색어, 결과와 위치가 그대로다.
 */
@Composable
fun StorySearchScreen(
    onBack: () -> Unit,
    onOpenStory: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StorySearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf(state.query.orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // 처음 열 때만 입력에 초점을 둔다. 결과를 보던 중 돌아오거나 구성이 바뀌어도 키보드를 다시 올리지 않는다.
    var focusedOnOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusedOnOpen && state.query == null) focusRequester.requestFocus()
        focusedOnOpen = true
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    StorySearchEffect.ShowQueryTooShort ->
                        Toast.makeText(context, HomeR.string.search_query_too_short, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val submit: (String) -> Unit = { term ->
        if (term.trim().length >= QUERY_MIN_LENGTH) {
            input = term.trim()
            focusManager.clearFocus()
        }
        viewModel.onIntent(StorySearchIntent.Submit(term))
    }

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        SearchHeader(
            input = input,
            onInputChange = { input = it.take(QUERY_MAX_LENGTH) },
            onSubmit = { submit(input) },
            onClear = {
                input = ""
                viewModel.onIntent(StorySearchIntent.Clear)
                focusRequester.requestFocus()
            },
            onBack = onBack,
            focusRequester = focusRequester,
        )
        Box(modifier = Modifier.weight(1f)) {
            if (state.query == null) {
                StorySearchHome(
                    recentTerms = state.recentTerms,
                    featuredGenres = state.featuredGenres,
                    onSearch = submit,
                    onRemoveRecent = { viewModel.onIntent(StorySearchIntent.RemoveRecent(it)) },
                    onClearRecent = { viewModel.onIntent(StorySearchIntent.ClearRecent) },
                )
            } else {
                SearchResults(state = state, onOpenStory = onOpenStory, onIntent = viewModel::onIntent)
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun SearchHeader(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
) {
    val label = stringResource(HomeR.string.search_label)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    end = ManyakTheme.spacing.gutter,
                    top = ManyakTheme.spacing.inline,
                    bottom = ManyakTheme.spacing.inline,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManyakIconButton(
            iconRes = DesignsystemR.drawable.ic_arrow_back,
            contentDescription = stringResource(CommonR.string.common_back),
            onClick = onBack,
        )
        ManyakTextField(
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = label },
            value = input,
            onValueChange = onInputChange,
            placeholder = stringResource(HomeR.string.search_placeholder),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            leading = {
                Icon(
                    modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                    painter = painterResource(DesignsystemR.drawable.ic_search),
                    contentDescription = null,
                    tint = ManyakTheme.colors.textSubtle,
                )
            },
            trailing =
                if (input.isEmpty()) {
                    null
                } else {
                    {
                        ManyakIconButton(
                            iconRes = DesignsystemR.drawable.ic_close,
                            contentDescription = stringResource(HomeR.string.search_clear),
                            onClick = onClear,
                            // 입력 줄 높이 안에 들어 칸 높이가 바뀌지 않는다. 눌림 영역은 Compose 가 최소 터치 크기로 넓힌다.
                            size = ManyakTheme.sizes.icon,
                            iconSize = ManyakTheme.sizes.iconSmall,
                            tint = ManyakTheme.colors.textSubtlest,
                        )
                    }
                },
        )
    }
}

@Composable
private fun SearchResults(
    state: StorySearchUiState,
    onOpenStory: (String) -> Unit,
    onIntent: (StorySearchIntent) -> Unit,
) {
    // 금방 끝나는 조회에서 자리만 잡았다 사라지는 깜빡임을 만들지 않는다.
    val showProgress = rememberDelayedProgressVisibility(state.isLoading)
    // 새 검색어를 제출하면 맨 위에서 시작하고, 그 사이 구성 변경과 상세 복귀에서는 위치를 지킨다.
    val gridState = rememberSaveable(state.searchVersion, saver = LazyGridState.Saver) { LazyGridState() }
    val noPadding = PaddingValues()

    when {
        state.isLoading && state.stories.isEmpty() ->
            if (showProgress) ExploreGridSkeleton(contentPadding = noPadding)

        state.loadFailed ->
            LoadFailedContent(
                message = stringResource(HomeR.string.search_load_failed),
                onRetry = { onIntent(StorySearchIntent.Retry) },
                modifier = Modifier.fillMaxSize().padding(horizontal = ManyakTheme.spacing.gutter),
            )

        state.stories.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(HomeR.string.search_empty),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                )
            }

        else ->
            StoryTileGrid(
                stories = state.stories,
                section = StoryListSection.SEARCH,
                gridState = gridState,
                contentPadding = noPadding,
                isLoadingMore = state.isLoadingMore,
                loadMoreFailed = state.loadMoreFailed,
                canLoadMore = state.hasMore && !state.isLoadingMore && !state.loadMoreFailed,
                onLoadMore = { onIntent(StorySearchIntent.LoadMore) },
                onOpenStory = onOpenStory,
            )
    }
}

private const val QUERY_MIN_LENGTH = 2
private const val QUERY_MAX_LENGTH = 100
