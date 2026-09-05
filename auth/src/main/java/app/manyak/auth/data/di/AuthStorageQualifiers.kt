package app.manyak.auth.data.di

import javax.inject.Qualifier

/** 인증 토큰 전용 DataStore. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthTokenDataStore

/** 세션 종료 정리 저널 전용 DataStore. 기기 귀속이라 로그아웃해도 파일 자체는 남는다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionJournalDataStore
