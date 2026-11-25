package app.manyak.analytics.data.di

import app.manyak.analytics.data.AmplitudeAnalytics
import app.manyak.analytics.data.FirebaseCrashReporter
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.domain.AnalyticsIdentity
import app.manyak.analytics.domain.CrashReporter
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

    @Binds
    @Singleton
    abstract fun bindCrashReporter(impl: FirebaseCrashReporter): CrashReporter
}
