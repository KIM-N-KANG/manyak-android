package app.manyak.core.data.di

import app.manyak.common.domain.credit.CreditPolicyRepository
import app.manyak.common.domain.credit.CreditRepository
import app.manyak.core.data.repository.CreditPolicyRepositoryImpl
import app.manyak.core.data.repository.CreditRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindCreditRepository(impl: CreditRepositoryImpl): CreditRepository

    @Binds
    @Singleton
    abstract fun bindCreditPolicyRepository(impl: CreditPolicyRepositoryImpl): CreditPolicyRepository
}
