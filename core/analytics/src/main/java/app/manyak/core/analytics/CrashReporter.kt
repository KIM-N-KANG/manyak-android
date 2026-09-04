package app.manyak.core.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 크래시 리포트에 붙는 진단 맥락.
 *
 * 화면과 ViewModel 은 이 계약을 직접 부르지 않는다 — 분석 퍼널이 이미 모든 이벤트를 지나가므로
 * breadcrumb 도 거기서 함께 남긴다. 화면이 같은 사실을 두 번 알리게 하면 한쪽만 빠뜨린다.
 *
 * 남기는 것은 이벤트·화면 이름과 공개 식별자뿐이다. 사용자 입력·토큰·링크 코드·URL 원문은 넣지 않는다.
 */
interface CrashReporter {
    fun recordEvent(
        name: String,
        screenName: String,
        properties: Map<String, Any?>,
    )

    /** null 이면 빈 값으로 지운다. 이후 리포트의 귀속만 끊고 이미 올라간 리포트는 남는다. */
    fun setUser(userId: String?)
}

@Singleton
class FirebaseCrashReporter
    @Inject
    constructor() : CrashReporter {
        private val crashlytics = FirebaseCrashlytics.getInstance()

        override fun recordEvent(
            name: String,
            screenName: String,
            properties: Map<String, Any?>,
        ) {
            crashlytics.setCustomKey(KEY_SCREEN_NAME, screenName)
            crashlytics.log(breadcrumb(name, properties))
        }

        override fun setUser(userId: String?) {
            crashlytics.setUserId(userId.orEmpty())
        }

        /**
         * 식별자는 지속 custom key 가 아니라 이 줄에만 싣는다. key 로 두면 스토리를 떠난 뒤에 난
         * 크래시에도 옛 `story_id` 가 붙어 무관한 리포트를 그 스토리 탓으로 읽게 된다.
         */
        private fun breadcrumb(
            name: String,
            properties: Map<String, Any?>,
        ): String {
            val ids =
                BREADCRUMB_KEYS
                    .mapNotNull { key -> (properties[key] as? String)?.let { "$key=$it" } }
                    .joinToString(" ")
            return if (ids.isEmpty()) name else "$name $ids"
        }

        private companion object {
            const val KEY_SCREEN_NAME = "screen_name"

            /** 공개 식별자만 남긴다. 사용자 입력·토큰·링크 코드·URL 원문은 어느 쪽으로도 나가지 않는다. */
            val BREADCRUMB_KEYS = listOf("story_id", "chat_id", "creation_id")
        }
    }
