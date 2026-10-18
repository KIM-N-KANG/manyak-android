package app.manyak.chat.room.presentation

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.chat.presentation.chatShareMessage
import app.manyak.chat.presentation.chatShareSubject
import app.manyak.common.presentation.share.shareText
import app.manyak.chat.R as ChatR
import app.manyak.report.R as ReportR

/**
 * 채팅방의 일회성 효과 — 토스트, 화면 이동, 시트·다이얼로그 닫기, 공유 시트.
 *
 * STARTED 동안만 받는다. 정지 중에 온 효과는 큐에 남았다가 돌아오면 이어진다.
 *
 * @param onCloseMenu 메뉴 시트는 별도 창이라 닫지 않고 이동·공유 시트를 띄우면 그 위에 남는다.
 */
@Composable
internal fun ChatRoomEffects(
    viewModel: ChatRoomViewModel,
    storyTitle: String,
    onDeleted: () -> Unit,
    onReplaceChat: (String) -> Unit,
    onCloseMenu: () -> Unit,
    onCloseDeleteDialog: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val currentStoryTitle by rememberUpdatedState(storyTitle)
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val currentOnReplaceChat by rememberUpdatedState(onReplaceChat)
    val currentOnCloseMenu by rememberUpdatedState(onCloseMenu)
    val currentOnCloseDeleteDialog by rememberUpdatedState(onCloseDeleteDialog)
    // 채팅방의 토스트는 한 자리에서 띄운다 — 앞 문구가 남아 있으면 지우고 새 문구만 보인다.
    val toast = remember { ReplacingToast(context) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    ChatRoomEffect.ChatDeleted -> {
                        currentOnCloseDeleteDialog()
                        currentOnDeleted()
                    }

                    ChatRoomEffect.ShowDeleteFailed -> currentOnCloseDeleteDialog()

                    is ChatRoomEffect.NavigateToChat -> {
                        currentOnCloseMenu()
                        currentOnReplaceChat(effect.chatId)
                    }

                    is ChatRoomEffect.ShareLink -> {
                        currentOnCloseMenu()
                        context.shareText(
                            subject = context.chatShareSubject(currentStoryTitle),
                            message = context.chatShareMessage(currentStoryTitle, effect.turnCount, effect.url),
                        )
                    }

                    else -> Unit
                }
                effect.toastText(context)?.let(toast::show)
            }
        }
    }
}

/**
 * 앱의 백그라운드 전환과 복귀를 채팅방에 알린다.
 *
 * 회전, 다크 모드, 글자 크기 변경도 Activity 를 멈추지만 백그라운드 진입이 아니므로 거른다. 거르지 않으면
 * 화면을 돌릴 때마다 받던 응답을 끊고 처음부터 다시 받는다.
 */
@Composable
internal fun ChatRoomForegroundEffects(viewModel: ChatRoomViewModel) {
    val activity = LocalActivity.current
    val activityLifecycleOwner = activity as? LifecycleOwner
    if (activity != null && activityLifecycleOwner != null) {
        LifecycleEventEffect(Lifecycle.Event.ON_STOP, activityLifecycleOwner) {
            if (!activity.isChangingConfigurations) viewModel.onIntent(ChatRoomIntent.Backgrounded)
        }
        LifecycleEventEffect(Lifecycle.Event.ON_START, activityLifecycleOwner) {
            viewModel.onIntent(ChatRoomIntent.Foregrounded)
        }
    }
}

/** 토스트로 알리는 효과의 문구. 화면 이동·상태 변경을 동반하는 효과도 문구가 있으면 함께 띄운다. */
private fun ChatRoomEffect.toastText(context: Context): String? =
    when (this) {
        is ChatRoomEffect.ShowStreamFailure -> message ?: context.getString(ChatR.string.chat_room_stream_error)
        ChatRoomEffect.ShowCreditRequired -> context.getString(ChatR.string.chat_room_credit_required)
        ChatRoomEffect.ChatDeleted -> context.getString(ChatR.string.chat_room_deleted)
        ChatRoomEffect.ShowDeleteFailed -> context.getString(ChatR.string.chat_room_delete_failed)
        ChatRoomEffect.ShowReportSubmitted -> context.getString(ReportR.string.story_report_submitted)
        ChatRoomEffect.ShowReportFailed -> context.getString(ReportR.string.story_report_failed)
        ChatRoomEffect.ShowNewChatFailed -> context.getString(ChatR.string.chat_room_new_chat_failed)
        ChatRoomEffect.ShowShareFailed -> context.getString(ChatR.string.chat_room_share_failed)
        ChatRoomEffect.ShowComposerLocked -> context.getString(ChatR.string.chat_composer_locked_streaming)
        is ChatRoomEffect.NavigateToChat, is ChatRoomEffect.ShareLink -> null
    }
