package app.manyak.studio.data.di

import app.manyak.network.data.di.AuthenticatedClient
import app.manyak.network.data.di.DataLayerConfig
import app.manyak.network.data.retrofit
import app.manyak.studio.data.api.StudioApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StudioNetworkModule {
    @Provides
    @Singleton
    fun provideStudioApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): StudioApi = retrofit(client, config, json).create(StudioApi::class.java)
}
