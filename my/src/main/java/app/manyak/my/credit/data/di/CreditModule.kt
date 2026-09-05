package app.manyak.my.credit.data.di

import app.manyak.common.domain.credit.CreditPolicyRepository
import app.manyak.my.credit.data.repository.CreditPolicyRepositoryImpl
import app.manyak.my.credit.data.repository.CreditRepositoryImpl
import app.manyak.my.credit.domain.CreditRepository
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
    abstract fun bindCreditPolicyRepository(impl: CreditPolicyRepositoryImpl): CreditPolicyRepository
}
