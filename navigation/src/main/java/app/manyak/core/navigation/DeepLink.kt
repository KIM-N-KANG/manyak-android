package app.manyak.core.navigation

import androidx.navigation3.runtime.NavKey
import java.net.URI
import java.net.URISyntaxException

/**
 * 서버가 알림에 실어 보내는 웹 주소를 내부 목적지로 바꾼다. 허용 호스트의 `https` 이고 아는 경로일 때만 값이 있다.
 *
 * `android.net.Uri` 가 아니라 `java.net.URI` 를 쓰는 이유는 JVM 유닛 테스트에서 전자가 빈 스텁이라서다.
 */
object DeepLink {
    private val allowedHosts = setOf("manyak.app", "www.manyak.app")

    fun routeOf(url: String): NavKey? {
        val uri =
            try {
                URI(url)
            } catch (_: URISyntaxException) {
                return null
            }
        if (uri.scheme != "https" || uri.host?.lowercase() !in allowedHosts) return null
        val segments =
            uri.path
                .orEmpty()
                .split('/')
                .filter { it.isNotEmpty() }
        return when {
            segments.size == 2 && segments[0] == "stories" -> StoryDetailRoute(segments[1])
            segments == listOf("my", "credits") -> MyCreditChargeRoute
            else -> null
        }
    }
}
