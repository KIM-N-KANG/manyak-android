package app.manyak.credit.data.di

import app.manyak.common.domain.credit.AttendanceClaimer
import app.manyak.common.domain.credit.CreditPolicyRepository
import app.manyak.common.domain.credit.CreditTransactionReader
import app.manyak.common.domain.credit.TrialsRepository
import app.manyak.credit.data.repository.CreditPolicyRepositoryImpl
import app.manyak.credit.data.repository.CreditRepositoryImpl
import app.manyak.credit.data.repository.TrialsRepositoryImpl
import app.manyak.credit.domain.CreditRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CreditModule {
    @Binds
    @Singleton
    abstract fun bindCreditRepository(impl: CreditRepositoryImpl): CreditRepository

    @Binds
    @Singleton
    abstract fun bindAttendanceClaimer(impl: CreditRepositoryImpl): AttendanceClaimer

    @Binds
    @Singleton
    abstract fun bindCreditTransactionReader(impl: CreditRepositoryImpl): CreditTransactionReader

    @Binds
    @Singleton
    abstract fun bindCreditPolicyRepository(impl: CreditPolicyRepositoryImpl): CreditPolicyRepository

    @Binds
    @Singleton
    abstract fun bindTrialsRepository(impl: TrialsRepositoryImpl): TrialsRepository
}
