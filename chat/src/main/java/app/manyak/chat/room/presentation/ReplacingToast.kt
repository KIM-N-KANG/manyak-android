package app.manyak.chat.room.presentation

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * 직전 토스트를 지우고 새로 띄운다. 잠긴 입력창 탭처럼 요청이 아니라 연타를 거를 가드가 없는
 * 자리에서 토스트를 그대로 쌓으면 누른 횟수만큼 같은 문구가 이어진다.
 */
internal class ReplacingToast(
    private val context: Context,
    @param:StringRes private val textRes: Int,
) {
    private var current: Toast? = null

    fun show() {
        current?.cancel()
        current = Toast.makeText(context, textRes, Toast.LENGTH_SHORT).also { it.show() }
    }
}
