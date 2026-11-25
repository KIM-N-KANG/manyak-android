package app.manyak.create.testing

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow

internal fun sampleProfile(id: String = "user-a"): UserProfile =
    UserProfile(
        id = id,
        nickname = "테스터",
        profileImageUrl = null,
        profileThumbnailBase64 = null,
        status = AccountStatus.ACTIVE,
        creditBalance = 0,
        attendedToday = false,
        linkedProviders = emptyList(),
    )

/** 로그인한 회원을 바꿔 가며 회원별 격리를 검증한다. null 은 로그아웃 상태다. */
internal class FakeUserProfileRepository(
    initial: UserProfile? = sampleProfile(),
) : UserProfileRepository {
    override val profile: MutableStateFlow<UserProfile?> = MutableStateFlow(initial)

    override suspend fun refresh(): DomainResult<UserProfile> =
        profile.value?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(app.manyak.common.domain.error.DomainError.Unauthorized)
}
