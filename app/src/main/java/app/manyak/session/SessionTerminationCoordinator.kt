package app.manyak.session

import app.manyak.core.data.api.AuthApi
import app.manyak.core.data.api.dto.LogoutRequestDto
import app.manyak.core.data.datastore.DeviceIdStore
import app.manyak.core.data.datastore.TerminationJournal
import app.manyak.core.data.datastore.TerminationJournalStore
import app.manyak.core.data.datastore.TerminationStep
import app.manyak.core.data.di.ApplicationScope
import app.manyak.core.data.provider.ProviderCleanupResult
import app.manyak.core.data.provider.SocialIdTokenProvider
import app.manyak.core.data.session.SessionEndSignal
import app.manyak.core.data.session.SessionStateHolder
import app.manyak.core.data.session.SessionTerminator
import app.manyak.core.data.session.TokenStorage
import app.manyak.core.data.session.UserScopedStore
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.session.SessionEndNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 세션 종료의 단일 실행자. 여러 `:core:*` 를 조합해야 하므로 composition root 인 `:app` 이 소유한다.
 *
 * 순서가 계약이다 — **먼저 쓰기를 닫고 나서 정리한다**(하네스 §3-3-4 로그아웃 절차). 삭제부터 시작하면
 * 이미 출발한 프로필 조회·재발급·캐시 쓰기가 늦게 완료되어 지운 데이터를 다시 채운다.
 *
 * 각 단계가 끝나면 저널에 다음 단계를 원자 기록한다. 프로세스가 중간에 죽으면 다음 시작에서 그 단계부터
 * 멱등하게 재개하며, 재개 전에는 인증 화면을 열지 않는다.
 */
@Singleton
class SessionTerminationCoordinator
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val tokenStorage: TokenStorage,
        private val journalStore: TerminationJournalStore,
        private val userScopedStores: Set<@JvmSuppressWildcards UserScopedStore>,
        private val providers: Map<AuthProvider, @JvmSuppressWildcards SocialIdTokenProvider>,
        private val deviceIdStore: DeviceIdStore,
        private val stateHolder: SessionStateHolder,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : SessionTerminator,
        SessionEndSignal {
        private val mutex = Mutex()
        private var inFlight: Deferred<Unit>? = null

        /** 데이터 계층이 보낸 종료 신호. 호출자를 기다리게 하지 않고 앱 스코프에서 진행한다. */
        override fun onSessionInvalidated(
            notice: SessionEndNotice,
            serverLogoutToken: String?,
        ) {
            applicationScope.launch { terminate(notice, serverLogoutToken) }
        }

        override suspend fun terminate(
            notice: SessionEndNotice,
            serverLogoutToken: String?,
        ) {
            val running =
                mutex.withLock {
                    // 중복 로그아웃·401·403 은 같은 작업에 합류한다. 별도 종료 작업을 만들지 않는다.
                    inFlight ?: applicationScope
                        .async { start(notice, serverLogoutToken) }
                        .also { inFlight = it }
                }
            try {
                running.await()
            } finally {
                mutex.withLock { if (inFlight === running) inFlight = null }
            }
        }

        /** 앱 시작 시 미완료 저널이 있으면 이어서 정리한다. 있으면 true. */
        suspend fun resumeIfNeeded(): Boolean {
            val journal = journalStore.read() ?: return false
            stateHolder.beginTermination()
            // 서버 로그아웃 완료 표식 전에 죽었다면 토큰이 남아 있을 때 호출을 반복한다.
            execute(journal, tokenStorage.read()?.refreshToken)
            return true
        }

        private suspend fun start(
            notice: SessionEndNotice,
            serverLogoutToken: String?,
        ) {
            stateHolder.beginTermination()
            // 로컬 토큰을 지우기 전에 서버 세션도 폐기할 기회를 갖기 위해 먼저 스냅숏한다.
            val logoutToken = serverLogoutToken ?: tokenStorage.read()?.refreshToken
            val journal =
                TerminationJournal(
                    step = TerminationStep.STARTED,
                    newDeviceId = UUID.randomUUID().toString(),
                    pendingProviders = providers.keys.map(AuthProvider::wireName),
                    notice = notice.name,
                )
            if (!writeJournalWithRetry(journal)) {
                // 저널을 남기지 못하면 삭제를 시작하지 않는다. 아무것도 지우지 않았으므로 세션을 되돌린다.
                stateHolder.publishMember()
                return
            }
            execute(journal, logoutToken)
        }

        @Suppress("ReturnCount")
        private suspend fun execute(
            journal: TerminationJournal,
            serverLogoutToken: String?,
        ) {
            var current = journal
            if (current.step == TerminationStep.STARTED) {
                attemptServerLogout(serverLogoutToken)
                current = advance(current, TerminationStep.SERVER_LOGOUT_ATTEMPTED) ?: return
            }
            if (current.step == TerminationStep.SERVER_LOGOUT_ATTEMPTED) {
                tokenStorage.clear()
                current = advance(current, TerminationStep.TOKENS_CLEARED) ?: return
            }
            if (current.step == TerminationStep.TOKENS_CLEARED) {
                userScopedStores.forEach { store -> runCatching { store.clearUserData() } }
                current = advance(current, TerminationStep.USER_DATA_CLEARED) ?: return
            }
            if (current.step == TerminationStep.USER_DATA_CLEARED) {
                val stillPending = clearProviders(current.pendingProviders)
                if (stillPending.isNotEmpty()) {
                    // 다음 로그인의 격리에 필요한 정리라 완료를 확인할 때까지 `종료 중`을 유지한다.
                    journalStore.write(current.copy(pendingProviders = stillPending))
                    return
                }
                current =
                    advance(current.copy(pendingProviders = emptyList()), TerminationStep.PROVIDERS_CLEARED) ?: return
            }
            if (current.step == TerminationStep.PROVIDERS_CLEARED) {
                // 분석·Crashlytics 사용자 식별자 해제는 해당 SDK 를 도입하는 단계에서 이 자리에 들어간다.
                deviceIdStore.replaceWith(current.newDeviceId)
                current = advance(current, TerminationStep.IDENTIFIERS_ROTATED) ?: return
            }
            journalStore.clear()
            stateHolder.publishSignedOut(SessionEndNotice.valueOf(current.notice))
        }

        /** 서버 실패는 로컬 정리를 막지 않는다. 오프라인에서도 로그아웃은 되어야 한다. */
        private suspend fun attemptServerLogout(refreshToken: String?) {
            if (refreshToken.isNullOrBlank()) return
            try {
                authApi.logout(LogoutRequestDto(refreshToken))
            } catch (_: IOException) {
                // 비민감 진단만 남기고 사용자에게 알리지 않는다.
            }
        }

        /** 두 제공자를 모두 정리한다. 연동 뒤에는 로그인 제공자와 연동 제공자의 상태가 함께 남는다. */
        private suspend fun clearProviders(pending: List<String>): List<String> =
            pending.filter { wireName ->
                val provider = AuthProvider.fromWireName(wireName)
                val adapter = provider?.let(providers::get) ?: return@filter false
                adapter.clearLocalState() == ProviderCleanupResult.RETRY_REQUIRED
            }

        private suspend fun advance(
            journal: TerminationJournal,
            step: TerminationStep,
        ): TerminationJournal? {
            val next = journal.copy(step = step)
            return if (writeJournalWithRetry(next)) next else null
        }

        private suspend fun writeJournalWithRetry(journal: TerminationJournal): Boolean =
            (1..JOURNAL_WRITE_ATTEMPTS).any { journalStore.write(journal) }

        private companion object {
            const val JOURNAL_WRITE_ATTEMPTS = 3
        }
    }
