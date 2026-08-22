package app.manyak.session

import app.manyak.core.data.di.ApplicationScope
import app.manyak.core.data.session.SessionBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 시작 시 공개 세션 상태를 처음으로 확정한다.
 *
 * **미완료 종료 저널이 있으면 그것부터 재개한다**. 정리가 끝나기 전에 인증 화면을 열면
 * 새 로그인이 이전 사용자의 잔여 상태 위에서 시작된다.
 */
@Singleton
class SessionBootstrapper
    @Inject
    constructor(
        private val coordinator: SessionTerminationCoordinator,
        private val sessionBootstrap: SessionBootstrap,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        fun start() {
            applicationScope.launch {
                if (!coordinator.resumeIfNeeded()) sessionBootstrap.restore()
            }
        }
    }
