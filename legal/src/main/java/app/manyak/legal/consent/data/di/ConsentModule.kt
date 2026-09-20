package app.manyak.legal.consent.data.di

import app.manyak.legal.consent.data.api.ConsentApi
import app.manyak.legal.consent.data.repository.ConsentRepositoryImpl
import app.manyak.legal.consent.domain.ConsentRepository
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConsentModule {
    @Binds
    @Singleton
    abstract fun bindConsentRepository(impl: ConsentRepositoryImpl): ConsentRepository

    companion object {
        @Provides
        @Singleton
        fun provideConsentApi(
            @AuthenticatedClient client: OkHttpClient,
            config: DataLayerConfig,
            json: Json,
        ): ConsentApi = retrofit(client, config, json).create(ConsentApi::class.java)
    }
}
