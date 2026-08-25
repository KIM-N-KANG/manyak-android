package app.manyak.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.component.rememberDelayedProgressVisibility
import app.manyak.core.ui.text.storyAnnotatedString
import app.manyak.core.ui.theme.ManyakTheme

/** 사용자 버블이 화면 폭에서 차지할 수 있는 최대 비율. 왼쪽을 비워 말풍선임을 드러낸다. */
private const val USER_BUBBLE_MAX_WIDTH_FRACTION = 0.8f

/**
 * 채팅방. 셸 없는 전체 화면이며, 지금은 상세 조회 렌더(제목·프롤로그·턴 이력)까지만 담는다.
 * 입력 컴포저와 턴 진행(SSE)은 다음 단계에서 붙는다.
 */
@Composable
fun ChatRoomScreen(
    chatId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatRoomViewModel =
        hiltViewModel<ChatRoomViewModel, ChatRoomViewModel.Factory>(
            creationCallback = { factory -> factory.create(chatId) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatRoomContent(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun ChatRoomContent(
    state: ChatRoomUiState,
    onBack: () -> Unit,
    onIntent: (ChatRoomIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showProgress = rememberDelayedProgressVisibility(state.isLoading)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ChatRoomHeader(title = state.storyTitle, onBack = onBack)
        when {
            state.isLoading ->
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (showProgress) ManyakProgressIndicator()
                }

            state.loadFailed ->
                ChatRoomLoadFailed(
                    modifier = Modifier.weight(1f),
                    onRetry = { onIntent(ChatRoomIntent.Retry) },
                )

            else -> ChatTranscript(modifier = Modifier.weight(1f), state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
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

/** 렌더 순서는 프롤로그 → 각 턴(사용자 버블 → AI 출력)이다. */
@Composable
private fun ChatTranscript(
    state: ChatRoomUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        if (state.prologue.isNotBlank()) {
            item(key = "prologue") { StoryPassage(text = state.prologue) }
        }
        items(state.turns, key = { turn -> turn.id }) { turn ->
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block)) {
                UserBubble(text = turn.userInput)
                StoryPassage(text = turn.aiOutput)
            }
        }
        item { Spacer(modifier = Modifier.height(ManyakTheme.spacing.screenBottom)) }
    }
}

/** 프롤로그·AI 출력 공용. 서사 본문이므로 말풍선 없이 읽기용 서체로 그린다. */
@Composable
private fun StoryPassage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter),
        text = storyAnnotatedString(text),
        style = ManyakTheme.typography.bodyReading,
        color = ManyakTheme.colors.text,
    )
}

@Composable
private fun UserBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter),
    ) {
        Spacer(modifier = Modifier.weight(1f - USER_BUBBLE_MAX_WIDTH_FRACTION))
        Box(
            modifier = Modifier.weight(USER_BUBBLE_MAX_WIDTH_FRACTION),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                modifier =
                    Modifier
                        .clip(ManyakTheme.shapes.card)
                        .background(ManyakTheme.colors.backgroundNeutral)
                        .padding(
                            horizontal = ManyakTheme.spacing.component,
                            vertical = ManyakTheme.spacing.compact,
                        ),
                text = text,
                style = ManyakTheme.typography.bodyLarge,
                color = ManyakTheme.colors.text,
            )
        }
    }
}

@Composable
private fun ChatRoomLoadFailed(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.chat_room_load_error),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        Button(
            modifier = Modifier.heightIn(min = ManyakTheme.sizes.control),
            onClick = onRetry,
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.brand,
                    contentColor = ManyakTheme.colors.textInverse,
                ),
        ) {
            Text(text = stringResource(R.string.common_retry), style = ManyakTheme.typography.labelLarge)
        }
    }
}

private fun previewChatRoomState(): ChatRoomUiState =
    ChatRoomUiState(
        isLoading = false,
        storyTitle = "두 번째 시계공",
        prologue = "*낡은 시계탑 아래, 도시는 오늘도 같은 시간에 멈춘다.* 당신은 열쇠를 쥔 채 문 앞에 선다.",
        turns =
            listOf(
                ChatRoomTurn(
                    id = 1,
                    userInput = "문을 천천히 연다.",
                    aiOutput = "문이 열리자 **태엽 감기는 소리**가 쏟아진다. *심장이 빨라진다.*",
                ),
            ),
    )

@Preview(showBackground = true, name = "채팅방 · 라이트")
@Composable
private fun ChatRoomScreenPreview() {
    ManyakTheme(darkTheme = false) {
        ChatRoomContent(state = previewChatRoomState(), onBack = {}, onIntent = {})
    }
}

@Preview(showBackground = true, name = "채팅방 · 로드 실패")
@Composable
private fun ChatRoomScreenLoadFailedPreview() {
    ManyakTheme(darkTheme = false) {
        ChatRoomContent(
            state = ChatRoomUiState(isLoading = false, loadFailed = true),
            onBack = {},
            onIntent = {},
        )
    }
}
