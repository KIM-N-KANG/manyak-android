package app.manyak.core.data.di

import javax.inject.Qualifier

/** IO 디스패처. 암복호화·저장소 접근처럼 무거운 작업은 호출된 쪽이 이 디스패처로 옮긴다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** 앱 수명 코루틴 스코프. 화면 이탈로 중단되면 안 되는 작업(로그아웃 등)이 쓴다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** 인증 토큰 전용 DataStore. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthTokenDataStore

/** 기기 귀속 값(디바이스 ID·종료 저널) 전용 DataStore. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceDataStore
