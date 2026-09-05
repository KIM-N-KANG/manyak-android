package app.manyak.analytics.data

import app.manyak.analytics.domain.CrashReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

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
