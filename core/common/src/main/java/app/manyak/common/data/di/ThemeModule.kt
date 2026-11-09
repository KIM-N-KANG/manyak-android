package app.manyak.common.data.di

import app.manyak.common.data.datastore.ThemePreferencesStore
import app.manyak.common.domain.settings.ThemePreferenceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeModule {
    @Binds
    @Singleton
    abstract fun bindThemePreferenceRepository(impl: ThemePreferencesStore): ThemePreferenceRepository
}
