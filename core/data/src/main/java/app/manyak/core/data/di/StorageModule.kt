package app.manyak.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.manyak.core.data.database.ManyakDatabase
import app.manyak.core.data.database.PendingStoryCreationDao
import app.manyak.core.data.datastore.AuthTokenStore
import app.manyak.core.data.session.AndroidSessionClock
import app.manyak.core.data.session.ProcessAnchorState
import app.manyak.core.data.session.SessionClock
import app.manyak.core.data.session.TokenStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    /** 파일 이름은 백업·기기 이전 제외 규칙(`backup_rules.xml`·`data_extraction_rules.xml`)과 짝이다. 바꾸면 함께 고친다. */
    @Provides
    @Singleton
    @AuthTokenDataStore
    fun provideAuthTokenDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(AUTH_TOKEN_STORE_NAME) }

    @Provides
    @Singleton
    @ProfileDataStore
    fun provideProfileDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(PROFILE_STORE_NAME) }

    @Provides
    @Singleton
    @SessionJournalDataStore
    fun provideSessionJournalDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SESSION_JOURNAL_STORE_NAME) }

    @Provides
    @Singleton
    fun provideProcessAnchorState(): ProcessAnchorState = ProcessAnchorState()

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

    private const val AUTH_TOKEN_STORE_NAME = "auth_tokens"
    private const val PROFILE_STORE_NAME = "profile"
    private const val SESSION_JOURNAL_STORE_NAME = "session_journal"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageBindingModule {
    @Binds
    @Singleton
    abstract fun bindSessionClock(impl: AndroidSessionClock): SessionClock

    @Binds
    @Singleton
    abstract fun bindTokenStorage(impl: AuthTokenStore): TokenStorage
}
