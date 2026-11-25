package app.manyak.common.presentation.share

import android.content.Context
import android.content.Intent

/**
 * 시스템 공유 시트로 글을 보낸다. 초대 코드와 채팅 공유 링크가 같은 경로를 쓴다.
 *
 * 웹은 카카오 SDK 로 공유 카드를 띄우지만 앱은 안드로이드 공유 시트를 쓴다 — 카카오톡을 포함해
 * 기기에 있는 앱을 사용자가 고르고, 공유 SDK 를 따로 싣지 않는다.
 *
 * @return 시트를 열었으면 true.
 */
fun Context.shareText(
    subject: String,
    message: String,
): Boolean {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
        }
    val chooser = Intent.createChooser(intent, subject)
    return runCatching { startActivity(chooser) }.isSuccess
}
