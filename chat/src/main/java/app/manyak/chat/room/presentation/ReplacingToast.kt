package app.manyak.chat.room.presentation

import android.content.Context
import android.widget.Toast

/**
 * 직전 토스트를 지우고 새로 띄운다. 잠긴 입력창 탭이나 이프 부족처럼 연타를 거를 가드가 없는
 * 자리에서 토스트를 그대로 쌓으면 누른 횟수만큼 같은 문구가 이어진다.
 */
internal class ReplacingToast(
    private val context: Context,
) {
    private var current: Toast? = null

    fun show(text: String) {
        current?.cancel()
        current = Toast.makeText(context, text, Toast.LENGTH_SHORT).also { it.show() }
    }
}
