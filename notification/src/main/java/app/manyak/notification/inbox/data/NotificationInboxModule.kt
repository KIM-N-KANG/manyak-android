package app.manyak.notification.inbox.data

import app.manyak.network.data.di.AuthenticatedClient
import app.manyak.network.data.di.DataLayerConfig
import app.manyak.network.data.retrofit
import app.manyak.notification.inbox.domain.NotificationInboxRepository
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
abstract class NotificationInboxModule {
    @Binds
    @Singleton
    abstract fun bindNotificationInboxRepository(impl: NotificationInboxRepositoryImpl): NotificationInboxRepository

    companion object {
        @Provides
        @Singleton
        fun provideNotificationInboxApi(
            @AuthenticatedClient client: OkHttpClient,
            config: DataLayerConfig,
            json: Json,
        ): NotificationInboxApi = retrofit(client, config, json).create(NotificationInboxApi::class.java)
    }
}
