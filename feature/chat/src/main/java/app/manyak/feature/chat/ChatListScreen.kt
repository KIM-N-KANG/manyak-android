package app.manyak.feature.chat

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.presentation.LocalAnalytics
import app.manyak.analytics.presentation.rememberImpressionTracker
import app.manyak.analytics.presentation.trackImpression
import app.manyak.common.entity.chat.ChatSummary
import app.manyak.core.ui.R
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakDestructiveDialogContent
import app.manyak.designsystem.component.ManyakDialog
import app.manyak.designsystem.component.ManyakOptionsDialogContent
import app.manyak.designsystem.component.ManyakOptionsDialogItem
import app.manyak.designsystem.component.ManyakPullToRefreshBox
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.component.withRowListMargins
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.report.presentation.StoryReportAction
import app.manyak.report.presentation.component.StoryReportSheet
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.report.R as ReportR

/**
 * 채팅 탭(진행 중인 채팅 목록). 헤더와 하단 탭은 셸이 그리므로 여기서는 콘텐츠만 둔다.
 *
 * [contentPadding] 은 셸의 chrome 이 차지한 만큼이므로 목록에는 `Modifier.padding` 이 아니라
 * 목록의 `contentPadding` 으로 넘긴다 — 그래야 콘텐츠가 헤더 아래로 흘러 들어간다.
 *
 * 목록 조회는 화면이 보일 때 시작한다. 채팅방은 이 화면 위가 아니라 셸 위에 쌓여 돌아와도
 * ViewModel 이 그대로 살아 있으므로, 조회 시점을 화면 수명에 맞춰야 떠난 사이의 변화가 반영된다.
 */
@Composable
fun ChatListScreen(
    contentPadding: PaddingValues,
    onOpenChat: (String) -> Unit,
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    ChatListEffect.ShowRefreshFailed ->
                        Toast.makeText(context, CommonR.string.story_refresh_failed, Toast.LENGTH_SHORT).show()

                    ChatListEffect.ShowChatDeleted ->
                        Toast.makeText(context, R.string.chat_room_deleted, Toast.LENGTH_SHORT).show()

                    ChatListEffect.ShowChatDeleteFailed ->
                        Toast.makeText(context, R.string.chat_room_delete_failed, Toast.LENGTH_SHORT).show()

                    ChatListEffect.ShowReportSubmitted ->
                        Toast.makeText(context, ReportR.string.story_report_submitted, Toast.LENGTH_SHORT).show()

                    ChatListEffect.ShowReportFailed ->
                        Toast.makeText(context, ReportR.string.story_report_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 채팅방에서 턴을 진행하고 돌아온 자리라, 미리보기·턴 수·순서가 모두 바뀌어 있다.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onIntent(ChatListIntent.ScreenShown) }

    ChatListContent(
        state = state,
        contentPadding = contentPadding,
        onOpenChat = onOpenChat,
        onGoToStudio = onGoToStudio,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun ChatListContent(
    state: ChatListUiState,
    contentPadding: PaddingValues,
    onOpenChat: (String) -> Unit,
    onGoToStudio: () -> Unit,
    onIntent: (ChatListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSkeleton = rememberDelayedProgressVisibility(state.isLoading)

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading ->
                // 금방 끝나는 조회에서 자리만 잡았다 사라지는 깜빡임을 만들지 않는다.
                if (showSkeleton) ChatListSkeleton(contentPadding = contentPadding)

            state.loadFailed ->
                LoadFailedContent(
                    message = stringResource(R.string.chat_room_load_error),
                    onRetry = { onIntent(ChatListIntent.Retry) },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(horizontal = ManyakTheme.spacing.gutter),
                )

            state.chats.isEmpty() ->
                EmptyChats(
                    onGoToStudio = onGoToStudio,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(horizontal = ManyakTheme.spacing.gutter),
                )

            else ->
                Chats(
                    chats = state.chats,
                    isRefreshing = state.isRefreshing,
                    contentPadding = contentPadding,
                    onOpenChat = onOpenChat,
                    onIntent = onIntent,
                )
        }
    }

    ChatListOverlays(state = state, onIntent = onIntent)
}

/** 카드 위에 얹히는 것들 — 옵션 시트·삭제 확인·신고 시트. 목록 배치와 섞이지 않게 따로 둔다. */
@Composable
private fun ChatListOverlays(
    state: ChatListUiState,
    onIntent: (ChatListIntent) -> Unit,
) {
    // 옵션과 삭제 확인은 한 창을 나눠 쓴다 — 창을 닫고 새로 열면 스크림이 두 번 페이드돼 번쩍인다.
    val deleteTarget = state.deleteTarget
    val optionsTarget = state.optionsTarget
    if (optionsTarget != null || deleteTarget != null) {
        ManyakDialog(
            onDismissRequest = {
                onIntent(if (deleteTarget != null) ChatListIntent.DismissDeleteDialog else ChatListIntent.CloseOptions)
            },
        ) {
            Crossfade(
                targetState = deleteTarget,
                animationSpec = tween(ManyakTheme.motion.elementEnterMillis),
                label = "chatCardDialog",
            ) { target ->
                if (target != null) {
                    ManyakDestructiveDialogContent(
                        title = stringResource(R.string.chat_room_delete_dialog_title),
                        description = stringResource(R.string.chat_room_delete_dialog_description),
                        confirmLabel = stringResource(R.string.chat_room_delete),
                        cancelLabel = stringResource(R.string.chat_room_delete_dialog_cancel),
                        onConfirm = { onIntent(ChatListIntent.ConfirmDelete) },
                        onDismiss = { onIntent(ChatListIntent.DismissDeleteDialog) },
                        inProgress = state.isDeleting,
                    )
                } else if (optionsTarget != null) {
                    ChatOptions(chat = optionsTarget, onIntent = onIntent)
                }
            }
        }
    }

    if (state.report.isSheetOpen) {
        StoryReportSheet(
            state = state.report,
            onAction = { action -> onIntent(ChatListIntent.Report(action)) },
        )
    }
}

@Composable
private fun ChatOptions(
    chat: ChatSummary,
    onIntent: (ChatListIntent) -> Unit,
) {
    ManyakOptionsDialogContent(preview = { ChatCardPreview(chat = chat) }) {
        // 참조 스토리가 없으면 신고할 대상도 없다.
        if (chat.storyId.isNotBlank()) {
            ManyakOptionsDialogItem(
                iconRes = DesignsystemR.drawable.ic_info,
                label = stringResource(ReportR.string.story_report_action),
                onClick = { onIntent(ChatListIntent.Report(StoryReportAction.Open)) },
            )
        }
        ManyakOptionsDialogItem(
            iconRes = DesignsystemR.drawable.ic_delete,
            label = stringResource(R.string.chat_room_delete),
            onClick = { onIntent(ChatListIntent.RequestDelete) },
            isDanger = true,
        )
    }
}

@Composable
private fun Chats(
    chats: List<ChatSummary>,
    isRefreshing: Boolean,
    contentPadding: PaddingValues,
    onOpenChat: (String) -> Unit,
    onIntent: (ChatListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = LocalAnalytics.current
    val impressions = rememberImpressionTracker()
    ManyakPullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = { onIntent(ChatListIntent.Refresh) },
        contentPadding = contentPadding,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 좌우 여백은 카드가 스스로 갖는다 — 카드 전체가 눌리는 자리라 눌림 효과가 화면 폭을
            // 채워야 한다. 그래서 셸이 넘긴 여백에 하단 여유만 더한다.
            contentPadding = contentPadding.withRowListMargins(),
        ) {
            itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
                ChatCard(
                    chat = chat,
                    onClick = {
                        analytics.track(AnalyticsEvent.ChatCardClicked(chat.id, index))
                        onOpenChat(chat.id)
                    },
                    onLongClick = { onIntent(ChatListIntent.OpenOptions(chat)) },
                    modifier =
                        Modifier.trackImpression(impressions, key = chat.id) {
                            analytics.track(AnalyticsEvent.ChatCardImpressed(chat.id, index))
                        },
                )
            }
        }
    }
}

/**
 * 빈 목록. 웹은 저장한 스토리 유무로 안내를 가르지만, 앱에서 두 갈래의 종착지는 제작 탭 하나다 —
 * 스토리가 없으면 그 탭이 이미 빈 안내와 FAB 으로 만들기를 유도한다. 버튼은 목적지를 쌓지 않고
 * 탭을 바꾼다.
 */
@Composable
private fun EmptyChats(
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.chat_list_empty_title),
            style = ManyakTheme.typography.bodyLargeStrong,
            color = ManyakTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.fillMaxWidth().padding(bottom = ManyakTheme.spacing.inline),
            text = stringResource(R.string.chat_list_empty_description),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
            textAlign = TextAlign.Center,
        )
        Button(
            modifier = Modifier.heightIn(min = ManyakTheme.sizes.control),
            onClick = onGoToStudio,
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.brand,
                    contentColor = ManyakTheme.colors.textInverse,
                ),
        ) {
            Text(
                text = stringResource(R.string.chat_list_empty_action),
                style = ManyakTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(showBackground = true, name = "채팅 · 목록")
@Composable
private fun ChatListScreenPreview() {
    ManyakTheme(darkTheme = false) {
        ChatListContent(
            state = ChatListUiState(isLoading = false, chats = previewChats()),
            contentPadding = PaddingValues(0.dp),
            onOpenChat = {},
            onGoToStudio = {},
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "채팅 · 빈 목록")
@Composable
private fun ChatListScreenEmptyPreview() {
    ManyakTheme(darkTheme = false) {
        ChatListContent(
            state = ChatListUiState(isLoading = false),
            contentPadding = PaddingValues(0.dp),
            onOpenChat = {},
            onGoToStudio = {},
            onIntent = {},
        )
    }
}

private fun previewChats(): List<ChatSummary> {
    // 미리보기는 고정된 시각이어야 캡처가 매번 달라지지 않는다.
    val anchor = 1_756_000_000_000L
    return listOf(
        ChatSummary(
            id = "1",
            storyId = "story-1",
            storyTitle = "두 번째 시계공",
            thumbnailUrl = null,
            lastStoryPreview = "낡은 문이 열리자 태엽 소리가 쏟아진다. 당신은 한 발 물러섰다.",
            turnCount = 21,
            updatedAtEpochMillis = anchor - 90_000L,
        ),
        ChatSummary(
            id = "2",
            storyId = "story-2",
            storyTitle = "아주 긴 제목은 한 줄에서 잘려 카드 높이를 흔들지 않는다",
            thumbnailUrl = null,
            lastStoryPreview = "아주 긴 미리보기도 마찬가지로 한 줄에서 잘려 카드 높이를 흔들지 않는다",
            turnCount = 1_284,
            updatedAtEpochMillis = anchor - 30_000_000L,
        ),
        ChatSummary(
            id = "3",
            storyId = "story-3",
            storyTitle = "달빛 아래의 계약",
            thumbnailUrl = null,
            lastStoryPreview = "",
            turnCount = 0,
            updatedAtEpochMillis = anchor - 900_000_000L,
        ),
    )
}
