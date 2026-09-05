package app.manyak.core.data.di

import app.manyak.common.domain.story.StoryCreationRepository
import app.manyak.common.domain.story.StoryRepository
import app.manyak.common.entity.story.PendingStoryCreationStore
import app.manyak.core.data.database.PendingStoryCreationRoomStore
import app.manyak.core.data.repository.StoryCreationRepositoryImpl
import app.manyak.core.data.repository.StoryRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StoryModule {
    @Binds
    @Singleton
    abstract fun bindStoryRepository(impl: StoryRepositoryImpl): StoryRepository

    @Binds
    @Singleton
    abstract fun bindStoryCreationRepository(impl: StoryCreationRepositoryImpl): StoryCreationRepository

    @Binds
    @Singleton
    abstract fun bindPendingStoryCreationStore(impl: PendingStoryCreationRoomStore): PendingStoryCreationStore
}
