package app.manyak.notification.data

import app.manyak.auth.domain.SessionEndSignal
import app.manyak.auth.domain.SessionGate
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.common.data.di.ApplicationScope
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.session.SessionEndNotice
import app.manyak.network.data.api.emptyBodyApiCall
import app.manyak.notification.data.api.PushTokenApi
import app.manyak.notification.data.api.dto.PushTokenDeleteRequestDto
import app.manyak.notification.data.api.dto.PushTokenRegisterRequestDto
import app.manyak.notification.domain.PushTokenRegistrar
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 등록과 삭제를 하나의 잠금으로 직렬화한다. 등록은 세션 관문의 인증 작업이라 종료 장벽이 취소하지만,
 * 로그아웃의 토큰 삭제는 장벽 앞에서 일어나므로 그 사이의 새 등록은 [closed] 가 막는다.
 */
@Singleton
class PushTokenRegistrarImpl
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val gate: SessionGate,
        private val sessionEndSignal: Lazy<SessionEndSignal>,
        private val api: PushTokenApi,
        private val tokens: FcmTokenSource,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : PushTokenRegistrar {
        private val mutex = Mutex()

        @Volatile
        private var closed = false

        /** 잠금을 쥐고 등록 중인 코루틴. 닫을 때 이것만 취소하면 대기 중인 등록은 잠금을 얻은 뒤 [closed] 로 빠진다. */
        @Volatile
        private var active: Job? = null

        override fun start() {
            applicationScope.launch {
                sessionRepository.sessionState.collect { state ->
                    // 정리가 끝나 미로그인이 발행되거나 로그아웃이 취소돼 회원으로 돌아오면 다시 연다.
                    // 종료 중(미확정)에는 닫힌 채 두어야 장벽이 서기 전의 창에서 새 등록이 출발하지 않는다.
                    if (state != SessionState.Undetermined) closed = false
                    if (state == SessionState.Member) launch { register() }
                }
            }
        }

        override fun onTokenRefreshed() {
            if (closed || sessionRepository.sessionState.value != SessionState.Member) return
            applicationScope.launch { register() }
        }

        override suspend fun closeAndDeleteToken() {
            closed = true
            active?.cancel()
            mutex.withLock {
                // 조회와 삭제를 합쳐 상한을 둔다. 실패는 로그아웃을 막지 않는다 — 남은 토큰은 수신 시 검증이 걸러 낸다.
                withTimeoutOrNull(DELETE_TIMEOUT_MILLIS) {
                    gate.withAuthWork(onBlocked = {}) {
                        val token = tokens.current() ?: return@withAuthWork
                        emptyBodyApiCall { api.delete(PushTokenDeleteRequestDto(token)) }
                    }
                }
            }
        }

        private suspend fun register() =
            mutex.withLock {
                if (closed) return
                active = currentCoroutineContext()[Job]
                try {
                    gate.withAuthWork(onBlocked = {}) { work ->
                        val token = tokens.current() ?: return@withAuthWork
                        val request = PushTokenRegisterRequestDto(token = token, platform = PLATFORM_ANDROID)
                        val result = emptyBodyApiCall { api.register(request) }
                        // 일반 API 의 403 은 인터셉터가 세션을 끝내지 않는다. 이 API 의 403 은 정지 계정뿐이라 여기서 올린다.
                        // 관문 commit 안에서 보내야 이전 세대의 늦은 403 이 새 세션을 끝내지 않는다.
                        if (result is DomainResult.Failure && result.error == DomainError.AccountSuspended) {
                            gate.commit(work) {
                                sessionEndSignal.get().onSessionInvalidated(SessionEndNotice.ACCOUNT_SUSPENDED, null)
                            }
                        }
                    }
                } finally {
                    active = null
                }
            }

        private companion object {
            const val DELETE_TIMEOUT_MILLIS = 3_000L
            const val PLATFORM_ANDROID = "ANDROID"
        }
    }
