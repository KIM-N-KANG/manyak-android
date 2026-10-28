package app.manyak.home.onboarding.domain

import app.manyak.common.domain.invite.SignupOnboardingWriter
import kotlinx.coroutines.flow.Flow

/**
 * 신규 가입 회원이 아직 첫 스토리 온보딩을 마치지 않았다는 표시.
 *
 * 가입 응답의 신호는 그 순간에만 있고 온보딩은 채팅을 시작하거나 간편 제작으로 가야 끝나므로, 그 사이에
 * 앱이 꺼져도 다시 온보딩으로 열리게 지속 상태로 둔다.
 */
interface StoryOnboardingRepository : SignupOnboardingWriter {
    val pending: Flow<Boolean>

    /** 온보딩을 마쳤다. 저장에 실패하면 다음 실행에서 온보딩이 다시 열린다. */
    suspend fun complete()
}
