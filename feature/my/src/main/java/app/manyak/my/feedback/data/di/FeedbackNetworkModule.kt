package app.manyak.my.feedback.data.di

import app.manyak.my.feedback.data.api.FeedbackApi
import app.manyak.network.data.di.AuthenticatedClient
import app.manyak.network.data.di.DataLayerConfig
import app.manyak.network.data.retrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FeedbackNetworkModule {
    @Provides
    @Singleton
    fun provideFeedbackApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): FeedbackApi = retrofit(client, config, json).create(FeedbackApi::class.java)
}
