package app.manyak.chat.data.di

import app.manyak.chat.data.datastore.ChatPreferencesStore
import app.manyak.chat.data.repository.ChatRepositoryImpl
import app.manyak.chat.domain.ChatPreferencesRepository
import app.manyak.chat.domain.ChatRepository
import app.manyak.common.domain.chat.ChatStarter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {
    @Binds
    @Singleton
    abstract fun bindChatStarter(impl: ChatRepositoryImpl): ChatStarter

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    /**
     * 기기 귀속 설정이라 `UserScopedStore` 집합에는 넣지 않는다. 로그아웃 정리에서 누락된 것이
     * 아니라 대상이 아니다.
     */
    @Binds
    @Singleton
    abstract fun bindChatPreferencesRepository(impl: ChatPreferencesStore): ChatPreferencesRepository
}
