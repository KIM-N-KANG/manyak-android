package app.manyak.my.persona.data.di

import app.manyak.common.domain.persona.PersonaAccess
import app.manyak.my.persona.data.api.PersonaApi
import app.manyak.my.persona.data.repository.PersonaRepositoryImpl
import app.manyak.my.persona.domain.PersonaRepository
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
abstract class PersonaModule {
    @Binds
    @Singleton
    abstract fun bindPersonaRepository(impl: PersonaRepositoryImpl): PersonaRepository

    /** 스토리 상세는 같은 저장소를 좁힌 계약으로 본다. */
    @Binds
    @Singleton
    abstract fun bindPersonaAccess(impl: PersonaRepositoryImpl): PersonaAccess

    companion object {
        @Provides
        @Singleton
        fun providePersonaApi(
            @AuthenticatedClient client: OkHttpClient,
            config: DataLayerConfig,
            json: Json,
        ): PersonaApi = retrofit(client, config, json).create(PersonaApi::class.java)
    }
}
