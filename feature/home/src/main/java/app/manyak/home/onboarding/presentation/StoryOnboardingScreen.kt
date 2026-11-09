package app.manyak.home.onboarding.presentation

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.common.R as CommonR
import app.manyak.home.R as HomeR

/**
 * 신규 가입 회원의 첫 화면. 셸도 뒤로가기 버튼도 없다 — 채팅을 시작하거나 간편 제작으로 가야 벗어난다.
 *
 * 화면 이동은 전부 앱이 맡는다. [onChatStarted] 와 [onCreateStory] 는 온보딩을 끝내는 길이라 앱이 완료를
 * 기록하고 백스택의 루트를 셸로 바꾼다.
 */
@Composable
fun StoryOnboardingScreen(
    onOpenStory: (storyId: String) -> Unit,
    onChatStarted: (storyId: String, chatId: String) -> Unit,
    onCreateStory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoryOnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val latestOnOpenStory by rememberUpdatedState(onOpenStory)
    val latestOnChatStarted by rememberUpdatedState(onChatStarted)
    val latestOnCreateStory by rememberUpdatedState(onCreateStory)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is StoryOnboardingEffect.OpenStory -> latestOnOpenStory(effect.storyId)
                    is StoryOnboardingEffect.ChatStarted -> latestOnChatStarted(effect.storyId, effect.chatId)
                    StoryOnboardingEffect.OpenCreate -> latestOnCreateStory()
                    StoryOnboardingEffect.ShowChatStartFailed ->
                        Toast
                            .makeText(
                                context,
                                HomeR.string.story_onboarding_start_chat_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                }
            }
        }
    }

    StoryOnboardingContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
internal fun StoryOnboardingContent(
    state: StoryOnboardingUiState,
    onIntent: (StoryOnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(ManyakTheme.colors.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // 가로 화면처럼 낮은 창에서는 남은 높이로 카드를 줄이면 엄지손톱만 해진다. 캐러셀 높이를 고정하고 화면을 세로로 스크롤한다.
        val compactHeight = maxHeight < CompactHeight
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (compactHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        ) {
            OnboardingHeader(
                onCreateStory = { onIntent(StoryOnboardingIntent.CreateStory) },
                modifier =
                    Modifier
                        .padding(horizontal = ManyakTheme.spacing.gutter)
                        .padding(top = ManyakTheme.spacing.block),
            )
            CarouselArea(
                state = state,
                onIntent = onIntent,
                modifier = if (compactHeight) Modifier.height(CompactCarouselHeight) else Modifier.weight(1f),
            )
            StartChatButton(
                enabled = state.selectedStory != null,
                isStarting = state.isStartingChat,
                onClick = { onIntent(StoryOnboardingIntent.StartChat) },
                modifier =
                    Modifier
                        .padding(horizontal = ManyakTheme.spacing.gutter)
                        .padding(top = ManyakTheme.spacing.compact, bottom = ManyakTheme.spacing.gutter),
            )
        }
    }
}

/** 캐러셀 자리. 받는 동안과 실패, 빈 목록도 같은 자리에 그려 헤더와 버튼이 움직이지 않는다. */
@Composable
private fun CarouselArea(
    state: StoryOnboardingUiState,
    onIntent: (StoryOnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Crossfade(
            targetState = state.phase,
            animationSpec = tween(ManyakTheme.motion.listItemEnterMillis),
            label = "storyOnboardingPhase",
        ) { phase ->
            when (phase) {
                CarouselPhase.LOADING -> CarouselSkeleton()
                CarouselPhase.FAILED ->
                    LoadFailedContent(
                        message = stringResource(CommonR.string.story_load_failed),
                        onRetry = { onIntent(StoryOnboardingIntent.Retry) },
                        modifier = Modifier.fillMaxSize().padding(horizontal = ManyakTheme.spacing.gutter),
                    )
                CarouselPhase.EMPTY -> EmptyStories()
                CarouselPhase.READY -> StoryCarousel(stories = state.stories, onIntent = onIntent)
            }
        }
    }
}

/** 헤드라인과 그 아래 대안 한 줄. 직접 만들기는 주 동작이 아니라 글자 버튼으로 낮춘다. */
@Composable
private fun OnboardingHeader(
    onCreateStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(HomeR.string.story_onboarding_title),
            style = ManyakTheme.typography.headlineSmall,
            color = ManyakTheme.colors.text,
        )
        Spacer(modifier = Modifier.height(ManyakTheme.spacing.inline))
        // 좁은 화면과 큰 글자에서는 버튼이 다음 줄로 내려간다. 문구 옆에 붙을 때는 글자 기준선이 맞도록 가운데 정렬한다.
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(HomeR.string.story_onboarding_create_prompt),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
            ManyakTextButton(
                onClick = onCreateStory,
                contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.compact),
            ) {
                Text(
                    text = stringResource(HomeR.string.story_onboarding_create),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textBrand,
                )
            }
        }
    }
}

@Composable
private fun EmptyStories() {
    Text(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ManyakTheme.spacing.gutter),
        text = stringResource(HomeR.string.story_onboarding_empty),
        style = ManyakTheme.typography.bodyLargeStrong,
        color = ManyakTheme.colors.text,
        textAlign = TextAlign.Center,
    )
}

/**
 * 화면의 주 동작. 가운데 카드로 시작한다. 진행 중에는 라벨 자리를 유지한 채 스피너를 얹어 버튼 크기와
 * 접근성 이름이 그대로 남는다.
 */
@Composable
private fun StartChatButton(
    enabled: Boolean,
    isStarting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
        onClick = onClick,
        enabled = enabled && !isStarting,
        shape = ManyakTheme.shapes.control,
        contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.gutter),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.brand,
                contentColor = ManyakTheme.colors.textInverse,
                // 생성 중에는 같은 색으로 스피너만 얹고, 고를 스토리가 없을 때만 비활성 색으로 누른다.
                disabledContainerColor =
                    if (isStarting) ManyakTheme.colors.brand else ManyakTheme.colors.backgroundDisabled,
                disabledContentColor =
                    if (isStarting) ManyakTheme.colors.textInverse else ManyakTheme.colors.textDisabled,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                modifier = Modifier.alpha(if (isStarting) 0f else 1f),
                text = stringResource(HomeR.string.story_onboarding_start_chat),
                style = ManyakTheme.typography.labelLarge,
            )
            if (isStarting) {
                ManyakProgressIndicator(
                    modifier = Modifier.size(ManyakTheme.sizes.icon),
                    color = ManyakTheme.colors.textInverse,
                )
            }
        }
    }
}

internal enum class CarouselPhase { LOADING, FAILED, EMPTY, READY }

internal val StoryOnboardingUiState.phase: CarouselPhase
    get() =
        when {
            loadFailed -> CarouselPhase.FAILED
            isLoading -> CarouselPhase.LOADING
            stories.isEmpty() -> CarouselPhase.EMPTY
            else -> CarouselPhase.READY
        }

/** 헤더와 버튼을 뺀 캐러셀 자리가 카드 한 장을 읽을 만큼 남지 않는 창 높이. */
private val CompactHeight = 600.dp

/** 낮은 창에서 캐러셀에 고정하는 높이. 카드가 휴대폰 세로 화면의 작은 쪽과 비슷한 크기로 남는다. */
private val CompactCarouselHeight = 420.dp
