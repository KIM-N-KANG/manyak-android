package app.manyak.core.domain.user

import app.manyak.core.domain.auth.AuthProvider

/** `GET /auth/me` 가 권위 있는 출처다. 화면은 이 모델 하나만 관찰한다. */
data class UserProfile(
    /** 사용자 공개 ID. 분석·크래시 리포트의 사용자 식별자로도 쓴다. */
    val id: String,
    val nickname: String,
    val profileImageUrl: String?,
    /** 48×48 인라인 썸네일(base64). 세션 복원 직후 첫 페인트용이며 없을 수 있다. */
    val profileThumbnailBase64: String?,
    val status: AccountStatus,
    val creditBalance: Long,
    val attendedToday: Boolean,
    val linkedProviders: List<AuthProvider>,
)

enum class AccountStatus {
    ACTIVE,
    SUSPENDED,
    DELETED,

    /** 서버가 앱이 모르는 상태를 보냈다. 알 수 없는 값 때문에 로그인을 막지 않는다. */
    UNKNOWN,
}
