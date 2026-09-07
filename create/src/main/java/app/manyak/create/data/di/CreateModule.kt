package app.manyak.create.data.di

import app.manyak.common.domain.story.CreationProgressAccess
import app.manyak.create.data.database.PendingStoryCreationRoomStore
import app.manyak.create.data.repository.StoryCreationRepositoryImpl
import app.manyak.create.domain.PendingStoryCreationStore
import app.manyak.create.domain.StoryCreationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CreateModule {
    @Binds
    @Singleton
    abstract fun bindStoryCreationRepository(impl: StoryCreationRepositoryImpl): StoryCreationRepository

    @Binds
    @Singleton
    abstract fun bindPendingStoryCreationStore(impl: PendingStoryCreationRoomStore): PendingStoryCreationStore

    @Binds
    @Singleton
    abstract fun bindCreationProgressAccess(impl: PendingStoryCreationRoomStore): CreationProgressAccess
}
