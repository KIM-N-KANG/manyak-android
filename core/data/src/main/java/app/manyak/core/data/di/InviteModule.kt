package app.manyak.core.data.di

import app.manyak.common.domain.invite.InviteRepository
import app.manyak.core.data.repository.InviteRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 신규 가입 안내 표시(`InviteOnboardingStore`)는 사용자 귀속 저장소라
 * [AuthModule] 의 `UserScopedStore` 집합 옆에 둔다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InviteModule {
    @Binds
    @Singleton
    abstract fun bindInviteRepository(impl: InviteRepositoryImpl): InviteRepository
}
