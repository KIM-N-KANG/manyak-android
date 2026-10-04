package app.manyak.session

import app.manyak.auth.domain.SessionEndSignal
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.common.data.di.ApplicationScope
import app.manyak.common.domain.credit.TrialsRepository
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.errorOrNull
import app.manyak.common.domain.session.MemberConsent
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.session.SessionEndNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 회원 자동 조회는 화면 재생성과 무관하게 서버의 필수 동의 확인 뒤에 시작한다. */
@Singleton
class MemberDataLoader
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val memberConsent: MemberConsent,
        private val profileRepository: UserProfileRepository,
        private val trialsRepository: TrialsRepository,
        private val sessionEndSignal: SessionEndSignal,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        fun start() {
            applicationScope.launch {
                combine(sessionRepository.sessionState, memberConsent.isSatisfied) { session, satisfied ->
                    session == SessionState.Member && satisfied
                }.distinctUntilChanged().collectLatest { ready ->
                    if (!ready) return@collectLatest
                    // 동의 상태가 초기화되거나 세션이 종료되면 진행 중인 두 요청도 취소한다.
                    coroutineScope {
                        launch {
                            if (profileRepository.refresh().errorOrNull() == DomainError.AccountSuspended) {
                                sessionEndSignal.onSessionInvalidated(SessionEndNotice.ACCOUNT_SUSPENDED, null)
                            }
                        }
                        launch { trialsRepository.refresh() }
                    }
                }
            }
        }
    }
