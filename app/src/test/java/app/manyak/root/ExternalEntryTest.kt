package app.manyak.root

import app.manyak.auth.entity.SessionState
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.core.navigation.MainTabsRoute
import app.manyak.core.navigation.PushEntry
import app.manyak.core.navigation.StoryDetailRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalEntryTest {
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
