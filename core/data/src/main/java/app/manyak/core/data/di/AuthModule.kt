package app.manyak.core.data.di

import app.manyak.common.domain.auth.AccountLinkRepository
import app.manyak.common.domain.invite.InviteOnboardingRepository
import app.manyak.common.domain.session.SessionRepository
import app.manyak.common.domain.session.UserScopedStore
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.core.data.database.PendingStoryCreationRoomStore
import app.manyak.core.data.datastore.InviteOnboardingStore
import app.manyak.core.data.datastore.ProfileCacheStore
import app.manyak.core.data.provider.GoogleIdTokenProvider
import app.manyak.core.data.provider.KakaoIdTokenProvider
import app.manyak.core.data.provider.SocialIdTokenProvider
import app.manyak.core.data.repository.AccountLinkRepositoryImpl
import app.manyak.core.data.repository.SessionRepositoryImpl
import app.manyak.core.data.repository.UserProfileRepositoryImpl
import app.manyak.core.data.session.SessionBootstrap
import app.manyak.core.data.session.SessionTokenManager
import app.manyak.network.domain.SessionTokenAccess
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthProviderKey(
    val value: AuthProvider,
)

/**
 * 인증 관련 바인딩을 한곳에 모은다.
 *
 * **사용자 귀속 저장소는 [UserScopedStore] 집합에 반드시 들어가야 한다** — 세션 종료 흐름이 이 집합을
 * 통째로 지우므로, 새 저장소를 만들고 여기에 넣지 않으면 정리 대상에서 빠진다. 바인딩이 한 파일에
 * 모여 있어 그 누락이 리뷰 diff 에 드러난다.
 */
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
    abstract fun bindUserProfileRepository(impl: UserProfileRepositoryImpl): UserProfileRepository

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

    @Binds
    @IntoSet
    abstract fun bindProfileCacheAsUserScoped(impl: ProfileCacheStore): UserScopedStore

    /** 간편 제작 진행 레코드 — 3-1 의 로그아웃 슬롯 전량 삭제 조항을 이 정리 계약으로 충족한다. */
    @Binds
    @IntoSet
    abstract fun bindPendingCreationAsUserScoped(impl: PendingStoryCreationRoomStore): UserScopedStore

    @Binds
    @Singleton
    abstract fun bindInviteOnboardingRepository(impl: InviteOnboardingStore): InviteOnboardingRepository

    /** 신규 가입 안내 표시 — 남으면 공용 기기의 다음 사용자에게 이전 회원의 안내가 뜬다. */
    @Binds
    @IntoSet
    abstract fun bindInviteOnboardingAsUserScoped(impl: InviteOnboardingStore): UserScopedStore
}
