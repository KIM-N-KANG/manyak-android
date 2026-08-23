package app.manyak.core.data.di

import app.manyak.core.data.api.AuthApi
import app.manyak.core.data.api.SimpleStoryApi
import app.manyak.core.data.api.UserApi
import app.manyak.core.data.interceptor.AuthInterceptor
import app.manyak.core.data.interceptor.DeviceIdInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/** access 토큰을 붙이지 않는 클라이언트. 로그인·재발급·로그아웃이 쓴다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlainClient

/** access 토큰을 붙이고 선제·반응형 재발급을 수행하는 클라이언트. 보호 요청이 쓴다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(config: DataLayerConfig): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // 헤더에 access 토큰이 들어가므로 debug 에서도 본문·헤더를 찍지 않는다.
            level = if (config.isDebugBuild) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

    @Provides
    @Singleton
    @PlainClient
    fun providePlainClient(
        deviceIdInterceptor: DeviceIdInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(deviceIdInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

    @Provides
    @Singleton
    @AuthenticatedClient
    fun provideAuthenticatedClient(
        deviceIdInterceptor: DeviceIdInterceptor,
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(deviceIdInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(
        @PlainClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): AuthApi = retrofit(client, config, json).create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideSimpleStoryApi(
        @PlainClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): SimpleStoryApi = retrofit(client, config, json).create(SimpleStoryApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): UserApi = retrofit(client, config, json).create(UserApi::class.java)

    private fun retrofit(
        client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(config.apiBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
