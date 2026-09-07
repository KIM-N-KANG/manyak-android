package app.manyak.my.credit.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.my.credit.data.api.CreditApi
import app.manyak.my.credit.data.dto.toDomain
import app.manyak.my.credit.domain.CreditRepository
import app.manyak.my.credit.entity.AttendanceResult
import app.manyak.my.credit.entity.CreditTransactionPage
import app.manyak.network.data.api.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreditRepositoryImpl
    @Inject
    constructor(
        private val userApi: CreditApi,
    ) : CreditRepository {
        override suspend fun claimAttendance(): DomainResult<AttendanceResult> =
            apiCall { userApi.claimAttendance() }
                .map { dto -> AttendanceResult(rewarded = dto.rewarded, amount = dto.amount) }

        override suspend fun getTransactions(cursor: String?): DomainResult<CreditTransactionPage> =
            apiCall { userApi.creditTransactions(cursor) }
                .map { dto -> dto.toDomain() }
    }
