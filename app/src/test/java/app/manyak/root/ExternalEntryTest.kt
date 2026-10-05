package app.manyak.root

import androidx.navigation3.runtime.NavKey
import app.manyak.auth.entity.SessionState
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.core.navigation.MainTabsRoute
import app.manyak.core.navigation.PushEntry
import app.manyak.core.navigation.StoryDetailRoute
import app.manyak.core.navigation.StudioRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalEntryTest {
    @Test
    fun `검수 알림은 인증을 기다리고 다른 계정이면 홈을 선택한다`() {
        val moderation = PushEntry(PushEntry.TYPE_STORY_MODERATION_COMPLETED, "s1", "me", "https://manyak.app/studio")
        assertNull(resolveEntryDestination(moderation, SessionState.SignedOut(null), profile("me")))
        assertNull(resolveEntryDestination(moderation, SessionState.Member, null))
        assertEquals(StudioRoute, resolveEntryDestination(moderation, SessionState.Member, profile("me")))
        assertEquals(MainTabsRoute, resolveEntryDestination(moderation, SessionState.Member, profile("other")))
    }

    @Test
    fun `제작 알림은 상세를 걷어내고 셸의 제작 탭을 선택한다`() {
        val stack = mutableListOf<NavKey>(MainTabsRoute, StoryDetailRoute("story"))
        assertEquals(MainTab.STUDIO, stack.openExternalDestination(StudioRoute))
        assertEquals(listOf(MainTabsRoute), stack)
        assertEquals(MainTab.STUDIO, stack.openExternalDestination(StudioRoute))
        assertEquals(listOf(MainTabsRoute), stack)
        assertEquals(MainTab.HOME, stack.openExternalDestination(MainTabsRoute))
    }

    private val entry = PushEntry(PushEntry.TYPE_STORY_COMPLETED, "s1", "me")

    @Test
    fun `미로그인·미확정·프로필 없음에서는 보류를 유지한다`() {
        assertNull(resolveEntryDestination(entry, SessionState.SignedOut(null), profile("me")))
        assertNull(resolveEntryDestination(entry, SessionState.Undetermined, profile("me")))
        assertNull(resolveEntryDestination(entry, SessionState.Member, null))
    }

    @Test
    fun `회원이 되고 프로필이 오면 도착지를 해석한다`() {
        assertEquals(StoryDetailRoute("s1"), resolveEntryDestination(entry, SessionState.Member, profile("me")))
    }

    @Test
    fun `로그인한 회원이 수신자와 다르면 홈이다`() {
        assertEquals(MainTabsRoute, resolveEntryDestination(entry, SessionState.Member, profile("other")))
    }

    private fun profile(id: String) =
        UserProfile(
            id = id,
            nickname = "n",
            profileImageUrl = null,
            profileThumbnailBase64 = null,
            status = AccountStatus.ACTIVE,
            creditBalance = 0,
            attendedToday = false,
            linkedProviders = emptyList(),
        )
}
