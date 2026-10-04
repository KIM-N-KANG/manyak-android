package app.manyak.root

import app.manyak.auth.entity.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEntryStateTest {
    @Test
    fun `저장 세션 복원은 시작 화면으로 이어지고 로그아웃 뒤 로그인만 인증 화면을 쓴다`() =
        runTest {
            val session = MutableStateFlow<SessionState>(SessionState.Undetermined)
            val entries = mutableListOf<AppEntryState>()
            backgroundScope.launch { session.appEntryStates().collect(entries::add) }
            testScheduler.runCurrent()
            assertTrue(entries.last().isStartup)

            session.value = SessionState.Member
            testScheduler.runCurrent()
            assertEquals(AppEntryState(SessionState.Member, isStartup = true), entries.last())
            session.value = SessionState.Undetermined
            testScheduler.runCurrent()
            session.value = SessionState.SignedOut(null)
            testScheduler.runCurrent()
            assertFalse(entries.last().isStartup)

            session.value = SessionState.Member
            testScheduler.runCurrent()
            assertEquals(AppEntryState(SessionState.Member, isStartup = false), entries.last())
        }

    @Test
    fun `루트보다 먼저 복원된 회원도 시작 화면에서 확인한다`() =
        runTest {
            val session = MutableStateFlow<SessionState>(SessionState.Member)
            val entries = mutableListOf<AppEntryState>()
            backgroundScope.launch { session.appEntryStates().collect(entries::add) }
            testScheduler.runCurrent()
            assertTrue(entries.all { it.isStartup })
        }

    @Test
    fun `새 로그인은 종료 정리와 재로그인 뒤에도 시작 화면으로 돌아가지 않는다`() {
        val entry =
            AppEntryState(SessionState.SignedOut(null))
                .onSessionChanged(SessionState.Member)
                .onSessionChanged(SessionState.Undetermined)
                .onSessionChanged(SessionState.SignedOut(null))
                .onSessionChanged(SessionState.Member)
        assertFalse(entry.isStartup)
    }
}
