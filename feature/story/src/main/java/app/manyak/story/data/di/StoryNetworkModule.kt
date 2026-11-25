package app.manyak.story.data.di

import app.manyak.network.data.di.AuthenticatedClient
import app.manyak.network.data.di.DataLayerConfig
import app.manyak.network.data.retrofit
import app.manyak.story.data.api.StoryDetailApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StoryNetworkModule {
    /**
     * 상세는 목록과 달리 인증 클라이언트를 쓴다 — 비공개 스토리 읽기와 본 엔딩 집계가 토큰에 걸려
     * 있다. AI 동기 호출이 없는 조회라 기본 타임아웃 그대로다.
     */
    @Provides
    @Singleton
    fun provideStoryDetailApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): StoryDetailApi = retrofit(client, config, json).create(StoryDetailApi::class.java)
}
