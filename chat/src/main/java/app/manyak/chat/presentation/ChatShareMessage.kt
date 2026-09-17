package app.manyak.chat.presentation

import android.content.Context
import app.manyak.chat.R as ChatR

/**
 * 공유 시트에 싣는 문구. 채팅방 메뉴와 채팅 목록 카드가 같은 글을 보낸다.
 *
 * 아직 선택이 없는 방(턴 0)은 프롤로그만 보이는 공유라 "몇 턴 뒤" 대신 이야기 소개로 권한다.
 * 참조 스토리가 삭제된 방은 제목이 비어 오므로 카드와 같은 문구를 넣는다.
 */
internal fun Context.chatShareMessage(
    storyTitle: String,
    turnCount: Int,
    url: String,
): String {
    val title = storyTitle.ifBlank { getString(ChatR.string.chat_list_deleted_story) }
    return if (turnCount == 0) {
        getString(ChatR.string.chat_room_share_message_prologue, title, url)
    } else {
        getString(ChatR.string.chat_room_share_message, title, turnCount, url)
    }
}

/** 공유 시트의 제목. 삭제된 스토리는 카드와 같은 문구다. */
internal fun Context.chatShareSubject(storyTitle: String): String =
    storyTitle.ifBlank { getString(ChatR.string.chat_list_deleted_story) }
