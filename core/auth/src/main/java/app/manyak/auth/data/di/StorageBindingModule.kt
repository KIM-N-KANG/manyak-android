package app.manyak.auth.data.di

import app.manyak.auth.data.datastore.AuthTokenStore
import app.manyak.auth.data.session.AndroidSessionClock
import app.manyak.auth.data.session.SessionClock
import app.manyak.auth.data.session.TokenStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageBindingModule {
    @Binds
    @Singleton
    abstract fun bindSessionClock(impl: AndroidSessionClock): SessionClock

    @Binds
    @Singleton
    abstract fun bindTokenStorage(impl: AuthTokenStore): TokenStorage
}
