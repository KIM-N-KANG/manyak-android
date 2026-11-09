package app.manyak.auth.data.di

import app.manyak.auth.data.api.AccountApi
import app.manyak.auth.data.api.AccountLinkApi
import app.manyak.auth.data.api.AuthApi
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthNetworkModule {
    @Provides
    @Singleton
    fun provideAuthApi(
        @PlainClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): AuthApi = retrofit(client, config, json).create(AuthApi::class.java)

    /** 연동은 로그인된 세션 위에서만 성립하므로 로그인과 달리 인증 클라이언트를 쓴다. */
    @Provides
    @Singleton
    fun provideAccountLinkApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): AccountLinkApi = retrofit(client, config, json).create(AccountLinkApi::class.java)

    @Provides
    @Singleton
    fun provideAccountApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): AccountApi = retrofit(client, config, json).create(AccountApi::class.java)
}
