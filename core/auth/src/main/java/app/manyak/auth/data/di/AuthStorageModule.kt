package app.manyak.auth.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.manyak.auth.data.session.ProcessAnchorState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthStorageModule {
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
    @SessionJournalDataStore
    fun provideSessionJournalDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SESSION_JOURNAL_STORE_NAME) }

    @Provides
    @Singleton
    fun provideProcessAnchorState(): ProcessAnchorState = ProcessAnchorState()

    private const val AUTH_TOKEN_STORE_NAME = "auth_tokens"
    private const val SESSION_JOURNAL_STORE_NAME = "session_journal"
}
