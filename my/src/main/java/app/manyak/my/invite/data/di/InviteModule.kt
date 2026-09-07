package app.manyak.my.invite.data.di

import app.manyak.common.domain.invite.SignupOnboardingWriter
import app.manyak.my.invite.data.datastore.InviteOnboardingStore
import app.manyak.my.invite.data.repository.InviteRepositoryImpl
import app.manyak.my.invite.domain.InviteOnboardingRepository
import app.manyak.my.invite.domain.InviteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InviteModule {
    @Binds
    @Singleton
    abstract fun bindInviteRepository(impl: InviteRepositoryImpl): InviteRepository

    @Binds
    @Singleton
    abstract fun bindInviteOnboardingRepository(impl: InviteOnboardingStore): InviteOnboardingRepository

    @Binds
    @Singleton
    abstract fun bindSignupOnboardingWriter(impl: InviteOnboardingStore): SignupOnboardingWriter
}
