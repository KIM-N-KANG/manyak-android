package app.manyak.session

import app.manyak.auth.data.session.SessionStateHolder
import app.manyak.auth.domain.SessionBootstrap
import app.manyak.auth.entity.SessionRestoreResult
import app.manyak.common.data.di.ApplicationScope
import app.manyak.common.entity.session.SessionEndNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 시작 시 공개 세션 상태를 처음으로 확정한다.
 *
 * **미완료 종료 저널이 있으면 그것부터 재개한다**. 정리가 끝나기 전에 인증 화면을 열면
 * 새 로그인이 이전 사용자의 잔여 상태 위에서 시작된다.
 *
 * 복원이 실패해도 이 코루틴이 조용히 죽으면 상태가 미확정에 갇혀 영구 로딩이 된다. 그래서 예상치
 * 못한 실패까지 잡아 재시도 가능한 차단 상태로 드러낸다.
 */
@Singleton
class SessionBootstrapper
    @Inject
    constructor(
        private val coordinator: SessionTerminationCoordinator,
        private val sessionBootstrap: SessionBootstrap,
        private val stateHolder: SessionStateHolder,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        fun start() {
            applicationScope.launch {
                try {
                    bootstrap()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    stateHolder.publishCleanupFailed(SessionEndNotice.REAUTHENTICATION_REQUIRED)
                }
            }
        }

        private suspend fun bootstrap() {
            if (coordinator.resumeIfNeeded()) return
            when (sessionBootstrap.restore()) {
                SessionRestoreResult.MEMBER, SessionRestoreResult.NO_SESSION -> Unit
                // 토큰이 손상됐거나 읽지 못했다. 토큰만이 아니라 사용자 귀속 데이터 전체를 지운다.
                SessionRestoreResult.CLEANUP_REQUIRED ->
                    coordinator.terminate(SessionEndNotice.REAUTHENTICATION_REQUIRED)
            }
        }
    }
