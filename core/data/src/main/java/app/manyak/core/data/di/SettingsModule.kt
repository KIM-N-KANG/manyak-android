package app.manyak.core.data.di

import app.manyak.core.data.datastore.ThemePreferencesStore
import app.manyak.core.data.repository.CreditRepositoryImpl
import app.manyak.core.domain.credit.CreditRepository
import app.manyak.core.domain.settings.ThemePreferenceRepository
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
    abstract fun bindThemePreferenceRepository(impl: ThemePreferencesStore): ThemePreferenceRepository

    @Binds
    @Singleton
    abstract fun bindCreditRepository(impl: CreditRepositoryImpl): CreditRepository
}
