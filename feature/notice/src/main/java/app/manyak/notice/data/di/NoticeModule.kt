package app.manyak.notice.data.di

import app.manyak.network.data.di.DataLayerConfig
import app.manyak.network.data.di.PlainClient
import app.manyak.network.data.retrofit
import app.manyak.notice.data.NoticeReadStore
import app.manyak.notice.data.api.NoticeApi
import app.manyak.notice.data.repository.NoticeRepositoryImpl
import app.manyak.notice.domain.NoticeReadRepository
import app.manyak.notice.domain.NoticeRepository
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
abstract class NoticeModule {
    @Binds
    @Singleton
    abstract fun bindNoticeRepository(impl: NoticeRepositoryImpl): NoticeRepository

    @Binds
    @Singleton
    abstract fun bindNoticeReadRepository(impl: NoticeReadStore): NoticeReadRepository

    companion object {
        /** 공개 API 라 토큰을 싣지 않는다. 만료 토큰 재발급에 공지 조회가 엮이지 않는다. */
        @Provides
        @Singleton
        fun provideNoticeApi(
            @PlainClient client: OkHttpClient,
            config: DataLayerConfig,
            json: Json,
        ): NoticeApi = retrofit(client, config, json).create(NoticeApi::class.java)
    }
}
