package app.manyak.create.data.di

import android.content.Context
import androidx.room.Room
import app.manyak.create.data.database.MIGRATION_1_2
import app.manyak.create.data.database.MIGRATION_2_3
import app.manyak.create.data.database.ManyakDatabase
import app.manyak.create.data.database.PendingStoryCreationDao
import app.manyak.create.data.database.StoryCompletionRequestDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CreateStorageModule {
    @Provides
    @Singleton
    fun provideManyakDatabase(
        @ApplicationContext context: Context,
    ): ManyakDatabase =
        Room
            .databaseBuilder(context, ManyakDatabase::class.java, ManyakDatabase.NAME)
            // 사용자 입력과 복구 키가 든 저장소라 파괴적 폴백을 쓰지 않는다. 마이그레이션이 없는 버전
            // 간격은 앱 시작을 막는 편이 조용히 지우는 것보다 낫다.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    @Singleton
    fun providePendingStoryCreationDao(database: ManyakDatabase): PendingStoryCreationDao =
        database.pendingStoryCreationDao()

    @Provides
    @Singleton
    fun provideStoryCompletionRequestDao(database: ManyakDatabase): StoryCompletionRequestDao =
        database.storyCompletionRequestDao()
}
