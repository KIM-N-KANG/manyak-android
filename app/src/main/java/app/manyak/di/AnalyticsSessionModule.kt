package app.manyak.di

import app.manyak.analytics.domain.AnalyticsIdentity
import app.manyak.network.domain.SessionIdAccess
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsSessionModule {
    @Provides
    fun provideSessionIdAccess(identity: AnalyticsIdentity): SessionIdAccess =
        SessionIdAccess(identity::currentSessionId)
}
