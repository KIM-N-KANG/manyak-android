package app.manyak.create.general.data

import android.os.SystemClock
import app.manyak.common.domain.story.ReviewWatch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 일반 제작과 수정 화면이 검수 결과를 기다리는 제출본. 알림은 FCM 스레드에서 묻고 화면은 메인에서 바꾸므로
 * 동시 접근에 안전한 맵을 쓴다. 프로세스가 죽으면 기록도 사라져 그 뒤의 알림은 그대로 뜬다.
 */
@Singleton
class GeneralReviewWatch internal constructor(
    private val now: () -> Long,
) : ReviewWatch {
    @Inject
    constructor() : this(SystemClock::elapsedRealtime)

    private val watchers = ConcurrentHashMap<String, Int>()
    private val shownResults = ConcurrentHashMap<String, Pair<String, Long>>()

    override suspend fun <T> watching(
        submissionId: String,
        block: suspend () -> T,
    ): T {
        // 다시 제출해 새로 기다리면 이전 결과 기록은 새 결과 알림을 거르지 않게 지운다.
        shownResults.remove(submissionId)
        watchers.merge(submissionId, 1, Int::plus)
        try {
            return block()
        } finally {
            watchers.computeIfPresent(submissionId) { _, count -> (count - 1).takeIf { it > 0 } }
        }
    }

    override fun shown(
        submissionId: String,
        status: String,
    ) {
        shownResults[submissionId] = status to now()
    }

    override fun suppresses(
        submissionId: String,
        status: String?,
    ): Boolean {
        if (watchers.containsKey(submissionId)) return true
        val (shownStatus, shownAt) = shownResults[submissionId] ?: return false
        // 화면은 1초마다 조회해 알림보다 먼저 결과를 받기도 한다. 그 직후 도착한 같은 결과 알림만 거른다.
        return shownStatus == status && now() - shownAt <= SHOWN_RESULT_WINDOW_MILLIS
    }

    private companion object {
        const val SHOWN_RESULT_WINDOW_MILLIS = 2 * 60 * 1000L
    }
}
