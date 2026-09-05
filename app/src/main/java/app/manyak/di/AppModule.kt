package app.manyak.di

import app.manyak.BuildConfig
import app.manyak.analytics.data.di.AnalyticsConfig
import app.manyak.auth.data.di.SocialAuthConfig
import app.manyak.auth.data.provider.ActivityProvider
import app.manyak.auth.domain.SessionEndSignal
import app.manyak.my.invite.domain.InviteShareLinkProvider
import app.manyak.network.data.di.DataLayerConfig
import app.manyak.session.CurrentActivityProvider
import app.manyak.session.SessionTerminationCoordinator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** `BuildConfig` 는 `:app` 만 갖고 있으므로 빌드별 값 주입은 여기서 한다. */
@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    @Provides
    @Singleton
    fun provideDataLayerConfig(): DataLayerConfig =
        DataLayerConfig(
            apiBaseUrl = BuildConfig.BASE_URL,
            isDebugBuild = BuildConfig.DEBUG,
            appVersion = BuildConfig.VERSION_NAME,
        )

    /** 초대 공유가 싣는 웹 주소. 아직 스토어 링크가 없어 웹 홈으로 보낸다. */
    @Provides
    @Singleton
    fun provideInviteShareLinkProvider(): InviteShareLinkProvider =
        object : InviteShareLinkProvider {
            override fun shareUrl(): String = BuildConfig.WEB_BASE_URL
        }

    @Provides
    @Singleton
    fun provideAnalyticsConfig(): AnalyticsConfig =
        AnalyticsConfig(
            apiKey = BuildConfig.AMPLITUDE_API_KEY,
            isDebugBuild = BuildConfig.DEBUG,
        )

    @Provides
    @Singleton
    fun provideSocialAuthConfig(): SocialAuthConfig =
        SocialAuthConfig(
            googleServerClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID,
            kakaoNativeAppKey = BuildConfig.KAKAO_NATIVE_APP_KEY,
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingModule {
    @Binds
    @Singleton
    abstract fun bindActivityProvider(impl: CurrentActivityProvider): ActivityProvider

    @Binds
    @Singleton
    abstract fun bindSessionEndSignal(impl: SessionTerminationCoordinator): SessionEndSignal
}
