package app.manyak.create.data.di

import android.content.Context
import androidx.room.Room
import app.manyak.create.data.database.ManyakDatabase
import app.manyak.create.data.database.PendingStoryCreationDao
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
            // 진행 레코드는 재생성 가능한 스냅숏이다. 스키마가 바뀌면 되살리는 것보다 버리는 쪽이
            // 안전하며, 이는 해석 불가 레코드를 없는 것으로 보는 기존 규칙과 같다.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun providePendingStoryCreationDao(database: ManyakDatabase): PendingStoryCreationDao =
        database.pendingStoryCreationDao()
}
