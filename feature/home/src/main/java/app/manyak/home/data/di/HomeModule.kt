package app.manyak.home.data.di

import app.manyak.common.domain.story.PopularOriginalStories
import app.manyak.home.data.repository.HomeRepositoryImpl
import app.manyak.home.data.repository.PopularOriginalStoriesImpl
import app.manyak.home.domain.HomeRepository
import app.manyak.home.search.data.RecentSearchStore
import app.manyak.home.search.domain.RecentSearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    @Singleton
    abstract fun bindHomeRepository(impl: HomeRepositoryImpl): HomeRepository

    /** 신규 가입 온보딩이 이 목록으로 첫 스토리를 고르게 한다. */
    @Binds
    abstract fun bindPopularOriginalStories(impl: PopularOriginalStoriesImpl): PopularOriginalStories

    @Binds
    @Singleton
    abstract fun bindRecentSearchRepository(impl: RecentSearchStore): RecentSearchRepository
}
