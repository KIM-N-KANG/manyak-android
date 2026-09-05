package app.manyak.core.data.di

import app.manyak.core.data.api.CreationRequestApi
import app.manyak.core.data.api.CreditPolicyApi
import app.manyak.core.data.api.FeedbackApi
import app.manyak.core.data.api.SimpleStoryApi
import app.manyak.core.data.api.StoryGenerationApi
import app.manyak.core.data.api.StoryRatingApi
import app.manyak.core.data.api.UserApi
import app.manyak.network.data.di.AuthenticatedClient
import app.manyak.network.data.di.DataLayerConfig
import app.manyak.network.data.di.PlainClient
import app.manyak.network.data.retrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// API 인터페이스마다 provider 가 하나씩 필요해 함수 수 상한과 구조적으로 충돌한다. 나누면
// retrofit() 구성이 흩어지므로 이 모듈만 예외로 둔다.
@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
object ApiNetworkModule {
    @Provides
    @Singleton
    fun provideSimpleStoryApi(
        @PlainClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): SimpleStoryApi = retrofit(client, config, json).create(SimpleStoryApi::class.java)

    /**
     * 생성 계열 API 는 서버가 AI 를 동기 호출한 뒤에야 응답해 기본 읽기 상한(10초)으로는 항상
     * 끊긴다. 인증 클라이언트에서 파생해 연결 풀·인터셉터는 공유하되 읽기와 전체 요청 상한만
     * 늘린다.
     */
    @Provides
    @Singleton
    fun provideStoryGenerationApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): StoryGenerationApi {
        val generationClient =
            client
                .newBuilder()
                .readTimeout(GENERATION_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(GENERATION_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        return retrofit(generationClient, config, json).create(StoryGenerationApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCreationRequestApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): CreationRequestApi = retrofit(client, config, json).create(CreationRequestApi::class.java)

    @Provides
    @Singleton
    fun provideStoryRatingApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): StoryRatingApi = retrofit(client, config, json).create(StoryRatingApi::class.java)

    @Provides
    @Singleton
    fun provideFeedbackApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): FeedbackApi = retrofit(client, config, json).create(FeedbackApi::class.java)

    /** 이프 수치는 인증이 필요 없는 공개 조회라 토큰 없는 클라이언트로 부른다. */
    @Provides
    @Singleton
    fun provideCreditPolicyApi(
        @PlainClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): CreditPolicyApi = retrofit(client, config, json).create(CreditPolicyApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): UserApi = retrofit(client, config, json).create(UserApi::class.java)

    private const val GENERATION_READ_TIMEOUT_SECONDS = 120L
    private const val GENERATION_CALL_TIMEOUT_SECONDS = 150L
}
