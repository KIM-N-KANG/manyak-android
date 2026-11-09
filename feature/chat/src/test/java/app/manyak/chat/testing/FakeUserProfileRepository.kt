package app.manyak.chat.testing

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 처음엔 프로필이 없고, 다시 읽으면 [refreshedBalance] 잔액의 프로필이 들어온다. */
internal class FakeUserProfileRepository(
    private val refreshedBalance: Long = 1_630,
) : UserProfileRepository {
    private val cached = MutableStateFlow<UserProfile?>(null)

    override val profile: StateFlow<UserProfile?> = cached

    var refreshCount = 0
        private set

    override suspend fun refresh(): DomainResult<UserProfile> {
        refreshCount++
        val refreshed =
            UserProfile(
                id = "user-1",
                nickname = "낭만적인 표류자",
                profileImageUrl = null,
                profileThumbnailBase64 = null,
                status = AccountStatus.ACTIVE,
                creditBalance = refreshedBalance,
                attendedToday = false,
                linkedProviders = emptyList(),
            )
        cached.value = refreshed
        return DomainResult.Success(refreshed)
    }
}
