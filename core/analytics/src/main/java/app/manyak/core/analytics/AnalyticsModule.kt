package app.manyak.core.analytics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    @Singleton
    abstract fun bindAnalytics(impl: AmplitudeAnalytics): Analytics

    @Binds
    @Singleton
    abstract fun bindAnalyticsIdentity(impl: AmplitudeAnalytics): AnalyticsIdentity
}
