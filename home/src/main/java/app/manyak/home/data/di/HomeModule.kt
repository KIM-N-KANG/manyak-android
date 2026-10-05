package app.manyak.home.data.di

import app.manyak.common.domain.story.StoryLikeUpdates
import app.manyak.home.data.repository.HomeRepositoryImpl
import app.manyak.home.domain.HomeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    abstract fun bindStoryLikeUpdates(impl: HomeRepositoryImpl): StoryLikeUpdates

    @Binds
    @Singleton
    abstract fun bindHomeRepository(impl: HomeRepositoryImpl): HomeRepository
}
