package app.manyak.my.invite.data.di

import app.manyak.my.invite.data.api.InviteApi
import app.manyak.network.data.di.AuthenticatedClient
import app.manyak.network.data.di.DataLayerConfig
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
object InviteNetworkModule {
    @Provides
    @Singleton
    fun provideInviteApi(
        @AuthenticatedClient client: OkHttpClient,
        config: DataLayerConfig,
        json: Json,
    ): InviteApi = retrofit(client, config, json).create(InviteApi::class.java)
}
