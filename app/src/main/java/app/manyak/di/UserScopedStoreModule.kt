package app.manyak.di

import app.manyak.common.domain.session.UserScopedStore
import app.manyak.my.invite.data.datastore.InviteOnboardingStore
import app.manyak.my.profile.data.datastore.ProfileCacheStore
import app.manyak.notification.data.PushNotificationTray
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class UserScopedStoreModule {
    @Binds
    @IntoSet
    abstract fun bindProfileCacheAsUserScoped(impl: ProfileCacheStore): UserScopedStore

    // 간편 제작 초안·완성 요청은 여기 두지 않는다 — 회원 ID 로 격리해 로그아웃 뒤 같은 회원이 돌아오면
    // 완성 중이던 요청이 이어지게 한다(create 의 Room 스토어 참고).

    /** 신규 가입 안내 표시 — 남으면 공용 기기의 다음 사용자에게 이전 회원의 안내가 뜬다. */
    @Binds
    @IntoSet
    abstract fun bindInviteOnboardingAsUserScoped(impl: InviteOnboardingStore): UserScopedStore

    /** 표시된 알림도 이전 회원의 것이다. 정리 단계에서 트레이를 비워 다음 회원에게 남기지 않는다. */
    @Binds
    @IntoSet
    abstract fun bindNotificationTrayAsUserScoped(impl: PushNotificationTray): UserScopedStore
}
