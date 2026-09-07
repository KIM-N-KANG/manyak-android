package app.manyak.my.invite.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.my.invite.data.api.InviteApi
import app.manyak.my.invite.data.dto.RedeemInviteCodeRequestDto
import app.manyak.my.invite.domain.InviteRepository
import app.manyak.my.invite.entity.Invite
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InviteRepositoryImpl
    @Inject
    constructor(
        private val userApi: InviteApi,
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
