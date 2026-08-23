package app.manyak.core.data.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 인증 작업이 세션에 결과를 남길 수 있는지 판정하는 관문.
 *
 * 세대만으로는 부족하다 — 검사와 저장 사이에 종료가 끼어들면 지운 토큰이 되살아난다. 그래서
 * **검사와 커밋을 같은 [Mutex] 안에서** 수행하고([commit]), 종료는 그 뒤에서만 장벽을 세운다
 * ([raiseBarrier]). 취소되지 않는 외부 호출이 늦게 돌아와도 커밋 시점의 세대가 다르면 결과는 버려진다.
 *
 * 장벽이 올라간 뒤에는 [withAuthWork] 가 새 인증 작업을 시작시키지 않고, 이미 등록된 앱 스코프
 * 작업은 취소한 뒤 완료를 기다린다.
 */
@Singleton
class SessionGate
    @Inject
    constructor() {
        private val commitMutex = Mutex()
        private val generation = AtomicLong(0)
        private val jobsLock = Any()
        private val activeAuthJobs = mutableSetOf<Job>()

        @Volatile
        private var terminating: Boolean = false

        val currentGeneration: Long get() = generation.get()

        /** 종료 절차가 시작돼 새 인증 작업을 받지 않는 상태. */
        val isTerminating: Boolean get() = terminating

        fun isCurrentGeneration(observed: Long): Boolean = !terminating && observed == generation.get()

        /**
         * 인증 작업을 관문 안에서 실행한다.
         *
         * 종료가 이미 시작됐으면 [onBlocked] 를 돌려주고 **시작하지 않는다**. 실행 중이면 종료가
         * 취소하고 완료를 기다릴 수 있도록 작업을 등록하며, 취소되면 결과 없이 [onBlocked] 로 끝난다.
         *
         * 블록을 **별도의 자식 코루틴**에서 돌리는 이유는 취소 범위를 좁히기 위해서다. 호출자의 Job 을
         * 그대로 등록하면 종료가 화면의 이벤트 루프까지 함께 끊는다.
         *
         * 이 블록 안에서 세션 종료를 기다리면 자기 자신을 기다리게 되므로, 종료는 신호로만 보낸다.
         */
        suspend fun <T> withAuthWork(
            onBlocked: () -> T,
            block: suspend (AuthWork) -> T,
        ): T {
            val work = beginAuthWork() ?: return onBlocked()
            return supervisorScope {
                val task = async { block(work) }
                register(task)
                try {
                    task.await()
                } catch (_: CancellationException) {
                    // 호출자 자신이 취소된 것이면 그대로 전파한다.
                    currentCoroutineContext().ensureActive()
                    // 종료 장벽이 이 작업을 취소했다. 결과 없이 끝낸다.
                    onBlocked()
                } finally {
                    unregister(task)
                }
            }
        }

        /**
         * 커밋 직전 세대를 다시 확인하고, 통과하면 [block] 을 **같은 잠금 안에서** 실행한다.
         *
         * 세대가 어긋나거나 종료가 시작됐으면 null 이며 호출부는 결과를 버려야 한다.
         */
        suspend fun <T : Any> commit(
            work: AuthWork,
            block: suspend () -> T,
        ): T? =
            commitMutex.withLock {
                if (!isCurrentGeneration(work.generation)) null else block()
            }

        /**
         * 종료 장벽을 세운다. 진행 중인 커밋이 끝난 뒤에 세대가 오르므로, 검사에 통과한 커밋과
         * 로그아웃이 서로를 앞지르지 않는다.
         *
         * 세대를 올린 뒤 등록된 인증 작업을 취소하고 완료를 기다린다. 기다림은 최선 노력이며,
         * 늦게 돌아오는 작업을 막는 실제 보증은 세대 검사다.
         */
        suspend fun raiseBarrier(): Long {
            val next =
                commitMutex.withLock {
                    terminating = true
                    generation.incrementAndGet()
                }
            val running = synchronized(jobsLock) { activeAuthJobs.toList() }
            running.forEach(Job::cancel)
            withTimeoutOrNull(JOIN_TIMEOUT_MILLIS) { running.forEach { it.join() } }
            return next
        }

        /** 정리가 모두 끝났다. 새 인증 작업을 다시 받는다. 세대는 되돌리지 않는다. */
        fun lowerBarrier() {
            terminating = false
        }

        /** 세대 캡처도 장벽과 같은 잠금 안에서 한다. 시작 직후 장벽이 서면 그 커밋은 반드시 거절된다. */
        private suspend fun beginAuthWork(): AuthWork? =
            commitMutex.withLock {
                if (terminating) null else AuthWork(generation.get())
            }

        private fun register(job: Job) {
            synchronized(jobsLock) { activeAuthJobs += job }
        }

        private fun unregister(job: Job) {
            synchronized(jobsLock) { activeAuthJobs -= job }
        }

        private companion object {
            const val JOIN_TIMEOUT_MILLIS = 3_000L
        }
    }

/**
 * 인증 작업 하나가 시작 시점에 관찰한 세션 세대.
 *
 * 이 값을 들고 [SessionGate.commit] 에 들어가야만 토큰 저장이나 상태 발행이 허용된다.
 */
class AuthWork internal constructor(
    internal val generation: Long,
)
