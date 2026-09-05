package app.manyak.my.credit.data.di

import app.manyak.my.credit.data.api.CreditApi
import app.manyak.my.credit.data.api.CreditPolicyApi
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
object CreditNetworkModule {
    @Provides
    @Singleton
    fun provideCreditApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): CreditApi = retrofit(client, config, json).create(CreditApi::class.java)

    /** 이프 수치는 인증이 필요 없는 공개 조회라 토큰 없는 클라이언트로 부른다. */
    @Provides
    @Singleton
    fun provideCreditPolicyApi(
        @PlainClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): CreditPolicyApi = retrofit(client, config, json).create(CreditPolicyApi::class.java)
}
