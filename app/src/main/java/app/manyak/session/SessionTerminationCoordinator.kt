package app.manyak.session

import app.manyak.auth.data.datastore.TerminationJournal
import app.manyak.auth.data.datastore.TerminationJournalStore
import app.manyak.auth.data.datastore.TerminationStep
import app.manyak.auth.data.session.SessionStateHolder
import app.manyak.auth.domain.SessionEndSignal
import app.manyak.auth.domain.SessionGate
import app.manyak.common.data.di.ApplicationScope
import app.manyak.common.entity.session.SessionEndNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 세션 종료의 단일 실행자. 여러 `:core:*` 를 조합해야 하므로 composition root 인 `:app` 이 소유한다.
 *
 * 순서가 계약이다 — **먼저 쓰기를 닫고 나서 정리한다**. 삭제부터 시작하면 이미 출발한 프로필 조회·
 * 재발급·캐시 쓰기가 늦게 완료되어 지운 데이터를 다시 채운다. 쓰기를 닫는 것은
 * [SessionGate.raiseBarrier] 이며, 그 뒤에는 새 인증 작업이 시작되지 않고 진행 중이던 작업은 취소된
 * 뒤 커밋을 거절당한다.
 *
 * **실패는 완료가 아니다.** 각 단계는 성공을 확인한 뒤에야 다음 저널 단계를 기록하고, 일시적 실패는
 * 유한한 backoff 로 재시도한다. 재시도를 소진하면 저널과 장벽을 그대로 둔 채
 * [SessionState.CleanupFailed][app.manyak.auth.entity.SessionState.CleanupFailed] 를 공개한다 —
 * 정리가 끝나지 않았는데 로그인을 허용하면 다음 실행의 재개가 새 사용자의 데이터를 지운다.
 */
@Singleton
class SessionTerminationCoordinator
    @Inject
    constructor(
        private val steps: SessionCleanupSteps,
        private val journalStore: TerminationJournalStore,
        private val stateHolder: SessionStateHolder,
        private val gate: SessionGate,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : SessionEndSignal {
        private val mutex = Mutex()
        private var inFlight: Deferred<Unit>? = null

        /** 데이터 계층이 보낸 종료 신호. 호출자를 기다리게 하지 않고 앱 스코프에서 진행한다. */
        override fun onSessionInvalidated(
            notice: SessionEndNotice,
            serverLogoutToken: String?,
        ) {
            applicationScope.launch { terminate(notice, serverLogoutToken) }
        }

        /**
         * 종료 절차를 실행한다. 이미 진행 중이면 그 작업에 합류하고 새로 만들지 않는다.
         *
         * @param serverLogoutToken 회전 직후 저장에 실패한 경우의 새 refresh 토큰. 구 토큰은 서버에서
         *  이미 폐기됐으므로 이 값으로 서버 로그아웃을 시도한다.
         */
        suspend fun terminate(
            notice: SessionEndNotice,
            serverLogoutToken: String? = null,
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
            terminate(journal.endNotice())
            return true
        }

        /**
         * 차단 오류 상태에서 사용자가 다시 시도했다. 저널이 있으면 남은 단계부터, 없으면 처음부터 정리한다.
         *
         * 저널을 남기지도 못한 채 막힌 경우까지 재시도가 닿아야 한다. 그렇지 않으면 사용자가
         * 재시도 화면에 갇힌다.
         */
        suspend fun retryCleanup() {
            terminate(journalStore.read()?.endNotice() ?: SessionEndNotice.REAUTHENTICATION_REQUIRED)
        }

        private suspend fun start(
            notice: SessionEndNotice,
            serverLogoutToken: String?,
        ) {
            closeWrites()
            // 이미 정리가 시작됐었다면(재개·재시도) 저널을 새로 만들지 않고 남은 단계를 잇는다.
            // 서버 로그아웃 표식 전에 죽었다면 토큰이 남아 있을 때 호출을 반복한다.
            journalStore.read()?.let { pending ->
                execute(pending, serverLogoutToken ?: steps.storedRefreshToken())
                return
            }
            // 로컬 토큰을 지우기 전에 서버 세션도 폐기할 기회를 갖기 위해 먼저 스냅숏한다.
            val logoutToken = serverLogoutToken ?: steps.storedRefreshToken()
            val journal =
                TerminationJournal(
                    step = TerminationStep.STARTED,
                    newDeviceId = UUID.randomUUID().toString(),
                    pendingProviders = steps.providerWireNames,
                    notice = notice.name,
                )
            if (!retryingCleanup { journalStore.write(journal) }) {
                // 저널을 남기지 못하면 삭제를 시작하지 않는다. 아직 아무것도 지우지 않았다.
                if (logoutToken != null) {
                    // 쓸 수 있는 세션이 그대로 있다. 로그아웃을 실패시키고 원래 상태로 되돌린다.
                    gate.lowerBarrier()
                    stateHolder.publishMember()
                } else {
                    // 복원할 세션도 없다. 정리를 시작하지 못했으므로 재시도만 남긴다.
                    stateHolder.publishCleanupFailed(notice)
                }
                return
            }
            execute(journal, logoutToken)
        }

        /**
         * 쓰기를 먼저 닫는다.
         *
         * 장벽을 세우면 새 인증 작업이 시작되지 않고, 진행 중이던 앱 스코프 재발급·로그인은 취소된 뒤
         * 완료를 기다린다. 취소되지 않는 외부 호출이 늦게 돌아와도 세대가 달라 커밋되지 않는다.
         */
        private suspend fun closeWrites() {
            gate.raiseBarrier()
            stateHolder.publishUndetermined()
        }

        private suspend fun execute(
            journal: TerminationJournal,
            serverLogoutToken: String?,
        ) {
            var current = journal
            while (current.step != TerminationStep.IDENTIFIERS_ROTATED) {
                val completed = runStep(current, serverLogoutToken)
                if (completed == null) {
                    stateHolder.publishCleanupFailed(current.endNotice())
                    return
                }
                current = advance(completed) ?: return
            }
            finish(current)
        }

        /** 현재 단계를 수행한다. 성공하면 (필요하면 갱신된) 저널을, 확인하지 못했으면 null 을 돌려준다. */
        private suspend fun runStep(
            journal: TerminationJournal,
            serverLogoutToken: String?,
        ): TerminationJournal? =
            when (journal.step) {
                TerminationStep.STARTED -> journal.also { steps.attemptServerLogout(serverLogoutToken) }
                TerminationStep.SERVER_LOGOUT_ATTEMPTED -> journal.takeIf { steps.clearTokens() }
                TerminationStep.TOKENS_CLEARED -> journal.takeIf { steps.clearUserScopedStores() }
                TerminationStep.USER_DATA_CLEARED -> clearProviders(journal)
                TerminationStep.PROVIDERS_CLEARED -> journal.takeIf { steps.rotateDeviceId(journal.newDeviceId) }
                TerminationStep.IDENTIFIERS_ROTATED -> journal
            }

        private suspend fun clearProviders(journal: TerminationJournal): TerminationJournal? {
            val stillPending = steps.clearProviders(journal.pendingProviders)
            if (stillPending.isEmpty()) return journal.copy(pendingProviders = emptyList())
            // 남은 제공자만 저널에 남겨 재개가 이미 끝난 제공자를 다시 열지 않게 한다.
            retryingCleanup { journalStore.write(journal.copy(pendingProviders = stillPending)) }
            return null
        }

        /** 단계가 실제로 끝난 뒤에만 다음 단계를 기록한다. 기록하지 못하면 장벽을 유지한 채 멈춘다. */
        private suspend fun advance(journal: TerminationJournal): TerminationJournal? {
            val next = journal.copy(step = journal.step.next())
            if (retryingCleanup { journalStore.write(next) }) return next
            stateHolder.publishCleanupFailed(journal.endNotice())
            return null
        }

        /** 저널 삭제까지 성공해야 정리가 끝난 것이다. 남겨 두면 다음 실행의 재개가 새 사용자 데이터를 지운다. */
        private suspend fun finish(journal: TerminationJournal) {
            if (!retryingCleanup { journalStore.clear() }) {
                stateHolder.publishCleanupFailed(journal.endNotice())
                return
            }
            gate.lowerBarrier()
            stateHolder.publishSignedOut(journal.endNotice())
        }
    }

/** 저널이 남긴 종료 사유. 알 수 없는 값이면 재로그인 안내로 되돌린다. */
private fun TerminationJournal.endNotice(): SessionEndNotice =
    runCatching { SessionEndNotice.valueOf(notice) }.getOrDefault(SessionEndNotice.REAUTHENTICATION_REQUIRED)

/** 단계는 선언 순서대로만 진행한다. 마지막 단계에서는 자기 자신을 돌려준다. */
private fun TerminationStep.next(): TerminationStep = TerminationStep.entries.getOrElse(ordinal + 1) { this }
