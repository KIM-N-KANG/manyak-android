package app.manyak.create.general.data.di

import app.manyak.create.general.data.GeneralStoryRepositoryImpl
import app.manyak.create.general.data.api.GeneralImageUploadApi
import app.manyak.create.general.data.api.GeneralStoryApi
import app.manyak.create.general.data.api.GeneralSubmissionInterceptor
import app.manyak.create.general.data.database.GeneralDraftRoomStore
import app.manyak.create.general.domain.GeneralDraftStore
import app.manyak.create.general.domain.GeneralStoryRepository
import app.manyak.network.data.di.AuthenticatedClient
import app.manyak.network.data.di.DataLayerConfig
import app.manyak.network.data.retrofit
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GeneralStoryModule {
    @Binds
    @Singleton
    abstract fun bindGeneralStoryRepository(impl: GeneralStoryRepositoryImpl): GeneralStoryRepository

    @Binds
    @Singleton
    abstract fun bindGeneralDraftStore(impl: GeneralDraftRoomStore): GeneralDraftStore

    companion object {
        @Provides
        @Singleton
        fun provideGeneralStoryApi(
            @AuthenticatedClient client: OkHttpClient,
            config: DataLayerConfig,
            json: Json,
        ): GeneralStoryApi =
            retrofit(
                client
                    .newBuilder()
                    .addInterceptor(GeneralSubmissionInterceptor())
                    .retryOnConnectionFailure(
                        false,
                    ).callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build(),
                config,
                json,
            ).create(GeneralStoryApi::class.java)

        @Provides
        @Singleton
        fun provideGeneralImageUploadApi(
            config: DataLayerConfig,
            json: Json,
        ): GeneralImageUploadApi =
            retrofit(
                // 서명 URL의 호스트에는 인증 토큰과 기기 식별자를 보내지 않는다.
                OkHttpClient.Builder().callTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS).build(),
                config,
                json,
            ).create(GeneralImageUploadApi::class.java)

        private const val REQUEST_TIMEOUT_SECONDS = 45L
        private const val UPLOAD_TIMEOUT_SECONDS = 90L
    }
}
