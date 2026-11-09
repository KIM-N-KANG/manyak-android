package app.manyak.studio.data.di

import app.manyak.common.domain.story.StoryDeletion
import app.manyak.studio.data.repository.StudioRepositoryImpl
import app.manyak.studio.domain.StudioRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StudioModule {
    @Binds
    @Singleton
    abstract fun bindStudioRepository(impl: StudioRepositoryImpl): StudioRepository

    @Binds
    @Singleton
    abstract fun bindStoryDeletion(impl: StudioRepositoryImpl): StoryDeletion
}
