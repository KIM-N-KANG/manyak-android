package app.manyak.core.domain.user

import app.manyak.core.domain.error.DomainResult
import kotlinx.coroutines.flow.StateFlow

/**
 * 프로필 보관소. 화면마다 `/auth/me` 를 부르지 않는다.
 *
 * 마지막 성공 응답을 로컬에 보관해 조회 실패·오프라인에서 표시한다. 이 캐시는 사용자 귀속
 * 데이터이므로 세션 종료 정리 대상이다.
 */
interface UserProfileRepository {
    /** 캐시된 값을 포함한 현재 프로필. 로그인 전이거나 정리된 뒤에는 null 이다. */
    val profile: StateFlow<UserProfile?>

    /** 서버에서 다시 읽는다. **실패해도 세션 상태를 바꾸지 않는다.** */
    suspend fun refresh(): DomainResult<UserProfile>
}
