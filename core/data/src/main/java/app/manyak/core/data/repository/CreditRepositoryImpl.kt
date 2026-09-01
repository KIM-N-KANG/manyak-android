package app.manyak.core.data.repository

import app.manyak.core.data.api.UserApi
import app.manyak.core.data.api.apiCall
import app.manyak.core.data.api.dto.toDomain
import app.manyak.core.domain.credit.AttendanceResult
import app.manyak.core.domain.credit.CreditRepository
import app.manyak.core.domain.credit.CreditTransactionPage
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.error.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreditRepositoryImpl
    @Inject
    constructor(
        private val userApi: UserApi,
    ) : CreditRepository {
        override suspend fun claimAttendance(): DomainResult<AttendanceResult> =
            apiCall { userApi.claimAttendance() }
                .map { dto -> AttendanceResult(rewarded = dto.rewarded, amount = dto.amount) }

        override suspend fun getTransactions(cursor: String?): DomainResult<CreditTransactionPage> =
            apiCall { userApi.creditTransactions(cursor) }
                .map { dto -> dto.toDomain() }
    }
