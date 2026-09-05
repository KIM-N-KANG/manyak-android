package app.manyak.core.data.di

import app.manyak.core.data.api.ChatApi
import app.manyak.core.data.api.CreationRequestApi
import app.manyak.core.data.api.CreditPolicyApi
import app.manyak.core.data.api.FeedbackApi
import app.manyak.core.data.api.SimpleStoryApi
import app.manyak.core.data.api.StoryDetailApi
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
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** 채팅 턴 스트리밍 전용 클라이언트. 인증 클라이언트에서 파생해 상한만 바꾼다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SseClient

// API 인터페이스마다 provider 가 하나씩 필요해 함수 수 상한과 구조적으로 충돌한다. 나누면
// retrofit() 구성이 흩어지므로 이 모듈만 예외로 둔다.
@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
object ApiNetworkModule {
    /**
     * 스트리밍 전용 클라이언트. 연결 풀·디스패처·인터셉터를 인증 클라이언트와 공유하되 셋을 바꾼다.
     *
     * - 읽기 상한은 **연속된 읽기 사이의 간격**이라 SSE 에서는 "토큰이 끊긴 시간"이 된다. 무제한으로
     *   두면 죽은 연결이 영원히 남는다.
     * - 전체 요청 상한은 스트림 길이를 그대로 자르므로 푼다. 전체 상한은 서버가 이미 갖고 있다.
     * - 로깅 인터셉터는 뺀다. 스트리밍 응답에 개입하지 않게 하고 진단은 크래시 리포트가 맡는다.
     */
    @Provides
    @Singleton
    @SseClient
    fun provideSseClient(
        @AuthenticatedClient client: OkHttpClient,
    ): OkHttpClient =
        client
            .newBuilder()
            .apply { interceptors().removeAll { interceptor -> interceptor is HttpLoggingInterceptor } }
            .readTimeout(SSE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(SSE_CALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .build()

    @Provides
    @Singleton
    fun provideEventSourceFactory(
        @SseClient client: OkHttpClient,
    ): EventSource.Factory = EventSources.createFactory(client)

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
    fun provideChatApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): ChatApi = retrofit(client, config, json).create(ChatApi::class.java)

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

    /** 토큰이 이만큼 끊기면 죽은 연결로 본다. */
    private const val SSE_READ_TIMEOUT_SECONDS = 60L

    /** 0 은 무제한이다. */
    private const val SSE_CALL_TIMEOUT_MILLIS = 0L
}
