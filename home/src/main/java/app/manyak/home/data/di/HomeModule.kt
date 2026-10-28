package app.manyak.home.data.di

import app.manyak.common.domain.invite.SignupOnboardingWriter
import app.manyak.home.data.repository.HomeRepositoryImpl
import app.manyak.home.domain.HomeRepository
import app.manyak.home.onboarding.data.StoryOnboardingStore
import app.manyak.home.onboarding.domain.StoryOnboardingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    @Singleton
    abstract fun bindHomeRepository(impl: HomeRepositoryImpl): HomeRepository

    @Binds
    @Singleton
    abstract fun bindStoryOnboardingRepository(impl: StoryOnboardingStore): StoryOnboardingRepository

    /** 신규 가입이면 인증이 이 표시를 남긴다. 회원 화면은 홈 대신 온보딩으로 열린다. */
    @Binds
    @IntoSet
    abstract fun bindStoryOnboardingWriter(impl: StoryOnboardingStore): SignupOnboardingWriter
}
