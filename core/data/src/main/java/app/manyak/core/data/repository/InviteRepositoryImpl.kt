package app.manyak.core.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.common.domain.invite.InviteRepository
import app.manyak.common.entity.invite.Invite
import app.manyak.core.data.api.UserApi
import app.manyak.core.data.api.dto.RedeemInviteCodeRequestDto
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InviteRepositoryImpl
    @Inject
    constructor(
        private val userApi: UserApi,
    ) : InviteRepository {
        override suspend fun getMyInvite(): DomainResult<Invite> =
            apiCall { userApi.myInvite() }.map { dto ->
                Invite(
                    code = dto.inviteCode,
                    monthlyRewardCount = dto.monthlyRewardCount,
                    monthlyRewardLimit = dto.monthlyRewardLimit,
                )
            }

        override suspend fun redeemInviteCode(code: String): DomainResult<Unit> =
            emptyBodyApiCall { userApi.redeemInviteCode(RedeemInviteCodeRequestDto(code)) }
    }
