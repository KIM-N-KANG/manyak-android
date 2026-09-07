package app.manyak.home.data.di

import app.manyak.home.data.api.StoryApi
import app.manyak.network.data.di.DataLayerConfig
import app.manyak.network.data.di.PlainClient
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
object HomeNetworkModule {
    /** 오리지널 목록은 인증을 요구하지 않아 토큰 없는 클라이언트로 부른다. */
    @Provides
    @Singleton
    fun provideStoryApi(
        @PlainClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): StoryApi = retrofit(client, config, json).create(StoryApi::class.java)
}
