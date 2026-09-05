package app.manyak.core.data.di

import app.manyak.common.domain.invite.InviteOnboardingRepository
import app.manyak.common.domain.invite.SignupOnboardingWriter
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.core.data.datastore.InviteOnboardingStore
import app.manyak.core.data.repository.UserProfileRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {
    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(impl: UserProfileRepositoryImpl): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindInviteOnboardingRepository(impl: InviteOnboardingStore): InviteOnboardingRepository

    @Binds
    @Singleton
    abstract fun bindSignupOnboardingWriter(impl: InviteOnboardingStore): SignupOnboardingWriter
}
