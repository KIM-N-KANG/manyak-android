package app.manyak.auth.data.di

import app.manyak.auth.data.provider.GoogleIdTokenProvider
import app.manyak.auth.data.provider.KakaoIdTokenProvider
import app.manyak.auth.data.provider.SocialIdTokenProvider
import app.manyak.auth.data.repository.AccountLinkRepositoryImpl
import app.manyak.auth.data.repository.SessionRepositoryImpl
import app.manyak.auth.data.session.SessionTokenManager
import app.manyak.auth.domain.AccountLinkRepository
import app.manyak.auth.domain.SessionBootstrap
import app.manyak.auth.domain.SessionRepository
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.network.domain.SessionTokenAccess
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthProviderKey(
    val value: AuthProvider,
)

/** 인증·토큰·소셜 제공자의 바인딩. 사용자 저장소 정리 등록은 app이 소유한다. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindSessionTokenAccess(impl: SessionTokenManager): SessionTokenAccess

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindSessionBootstrap(impl: SessionRepositoryImpl): SessionBootstrap

    @Binds
    @Singleton
    abstract fun bindAccountLinkRepository(impl: AccountLinkRepositoryImpl): AccountLinkRepository

    @Binds
    @IntoMap
    @AuthProviderKey(AuthProvider.GOOGLE)
    abstract fun bindGoogleProvider(impl: GoogleIdTokenProvider): SocialIdTokenProvider

    @Binds
    @IntoMap
    @AuthProviderKey(AuthProvider.KAKAO)
    abstract fun bindKakaoProvider(impl: KakaoIdTokenProvider): SocialIdTokenProvider
}
