package app.manyak.notification.settings.data.di

import app.manyak.network.data.di.AuthenticatedClient
import app.manyak.network.data.di.DataLayerConfig
import app.manyak.network.data.retrofit
import app.manyak.notification.settings.data.api.PushSettingsApi
import app.manyak.notification.settings.data.repository.PushSettingsRepositoryImpl
import app.manyak.notification.settings.domain.PushSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushSettingsModule {
    @Binds
    @Singleton
    abstract fun bindPushSettingsRepository(impl: PushSettingsRepositoryImpl): PushSettingsRepository

    companion object {
        @Provides
        @Singleton
        fun providePushSettingsApi(
            @AuthenticatedClient client: OkHttpClient,
            config: DataLayerConfig,
            json: Json,
        ): PushSettingsApi = retrofit(client, config, json).create(PushSettingsApi::class.java)
    }
}
