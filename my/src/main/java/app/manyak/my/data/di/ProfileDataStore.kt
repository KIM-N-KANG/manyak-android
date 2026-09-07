package app.manyak.my.data.di

import javax.inject.Qualifier

/** 프로필 캐시 전용 DataStore. 사용자 귀속 데이터라 세션 종료 정리 대상이다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProfileDataStore
