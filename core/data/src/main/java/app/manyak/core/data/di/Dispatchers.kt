package app.manyak.core.data.di

import javax.inject.Qualifier

/** 인증 토큰 전용 DataStore. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthTokenDataStore

/** 프로필 캐시 전용 DataStore. 사용자 귀속 데이터라 세션 종료 정리 대상이다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProfileDataStore

/** 세션 종료 정리 저널 전용 DataStore. 기기 귀속이라 로그아웃해도 파일 자체는 남는다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionJournalDataStore
