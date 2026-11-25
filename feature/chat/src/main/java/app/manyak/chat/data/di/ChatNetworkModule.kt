package app.manyak.chat.data.di

import app.manyak.chat.data.api.ChatApi
import app.manyak.chat.data.api.ChatUserApi
import app.manyak.network.data.di.AuthenticatedClient
import app.manyak.network.data.di.DataLayerConfig
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

@Module
@InstallIn(SingletonComponent::class)
object ChatNetworkModule {
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

    @Provides
    @Singleton
    fun provideChatApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): ChatApi = retrofit(client, config, json).create(ChatApi::class.java)

    @Provides
    @Singleton
    fun provideChatUserApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): ChatUserApi = retrofit(client, config, json).create(ChatUserApi::class.java)

    /** 토큰이 이만큼 끊기면 죽은 연결로 본다. */
    private const val SSE_READ_TIMEOUT_SECONDS = 60L

    /** 0 은 무제한이다. */
    private const val SSE_CALL_TIMEOUT_MILLIS = 0L
}
