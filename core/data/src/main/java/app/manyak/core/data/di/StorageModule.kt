package app.manyak.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    @DeviceDataStore
    fun provideDeviceDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(DEVICE_STORE_NAME) }

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
    @PendingCreationDataStore
    fun providePendingCreationDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(PENDING_CREATION_STORE_NAME) }

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /** 화면 이탈로 중단되면 안 되는 작업(재발급·로그아웃 정리)이 쓰는 스코프. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideProcessAnchorState(): ProcessAnchorState = ProcessAnchorState()

    private const val AUTH_TOKEN_STORE_NAME = "auth_tokens"
    private const val DEVICE_STORE_NAME = "device"
    private const val PROFILE_STORE_NAME = "profile"
    private const val SESSION_JOURNAL_STORE_NAME = "session_journal"
    private const val PENDING_CREATION_STORE_NAME = "pending_creation"
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
