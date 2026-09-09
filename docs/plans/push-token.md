# 푸시 토큰 등록 (KNK-1133)

- 작성일: 2026-09-08
- 근거 정본: 하네스 `3-7-android-design.md §3-3-4 푸시 토큰 등록`, `docs/planning/android-module-architecture.md §3·§5`

## 목표와 제외 범위

FCM 등록 토큰을 회원 세션마다 서버에 맡기고(`PUT /users/me/push-tokens`), 사용자 로그아웃 때 이 기기의
토큰 삭제를 한 번 시도하며(`DELETE`), Android 13+ 에서 알림 권한을 한 번 묻는다.

**제외** — 메시지 수신·표시·채널·탭 진입(KNK-1134), 수신 동의 API·설정 화면(KNK-1135), 서버 페이로드의
수신자 식별자 합의(KNK-1134 선행), prod FCM 서비스 계정 배선(서버).

## 이 레포에서 새로 내린 결정

1. **새 모듈 `notification`은 화면 기능 모듈로 등록한다.** `ModuleArchitecture.kt`의 `features` 집합에
   넣어 다른 화면 기능과 같은 의존 상한을 받는다. Firebase 의존은 Crashlytics 와 같은 BOM 을 쓰고,
   google-services 플러그인은 `app`에 남긴다.
2. **`FirebaseMessaging.getToken()` 의 Task 는 `suspendCancellableCoroutine` 으로 기다린다.** 호출이 한
   곳뿐이라 `kotlinx-coroutines-play-services` 를 추가하지 않는다. 실패는 "토큰 없음" 으로 보고 건너뛴다.
3. **등록기는 잠금 하나로 등록·삭제를 직렬화하고, 닫힘 플래그와 "잠금을 쥔 등록 Job" 만 들고 있다.**
   대기 중인 등록은 잠금을 얻은 뒤 닫힘을 보고 빠지므로 목록으로 관리하지 않는다. 닫힘은 세션 상태가
   `미확정`이 아닌 값으로 발행될 때 풀린다 — 정리가 끝나 `미로그인`이 되거나, 저널 기록 실패로 로그아웃이
   취소돼 `회원`으로 돌아오는 두 경우를 한 규칙으로 덮는다.
4. **종료 조정자는 저널을 장벽 앞에서 한 번 읽는다.** 저널이 없고 사유가 `USER_REQUESTED` 일 때만 등록기의
   닫기·삭제를 부른다. 재개·재시도·서버 강제 종료는 세션이 이미 닫혀 시도하지 않는다.
5. **알림 권한 요청 여부는 기기 DataStore 플래그로 기억해 설치당 한 번만 묻는다**(2026-09-08, KNK-1135 에서
   변경 — 원래는 프로세스 수명 변수였다). 프로세스 변수만으로는 앱을 재실행할 때마다 시스템이 자동 거부하기
   전까지 다시 물어, 사용자가 내린 결정을 되묻는 셈이었다. 플래그는 테마 설정과 같은 기기 귀속 파일에 두어
   로그아웃 정리 대상이 아니고, 이미 로그인된 채 업데이트를 받은 회원도 첫 실행에서 한 번은 묻는다.
   읽기·쓰기 실패는 "아직 묻지 않음" 으로 본다 — 한 번 더 묻는 쪽이 영영 묻지 않는 쪽보다 낫다.

`ponytail:` 탈퇴는 `signOut()` 을 거쳐 `USER_REQUESTED` 로 들어오므로 삭제 호출이 한 번 나가 401 로 빠진다
(서버가 탈퇴 시 토큰을 전부 지우므로 결과는 같다). 사유를 나누는 값이 필요해지면 그때 분리한다.

## 구현 순서

1. `settings.gradle.kts`·버전 카탈로그(`firebase-messaging`)·`ModuleArchitecture.kt` 에 모듈 등록.
2. `notification` 모듈 — 매니페스트(`POST_NOTIFICATIONS`·서비스), `domain/PushTokenRegistrar`,
   `data/api/PushTokenApi`·DTO, `data/PushTokenRegistrarImpl`, `data/ManyakFirebaseMessagingService`,
   `data/di/NotificationModule`, `presentation/NotificationPermissionRequest`.
3. `app` — 의존 추가, `ManyakApplication.onCreate` 에서 `start()`, `SessionTerminationCoordinator.start` 에
   장벽 앞 닫기·삭제, `ManyakApp` 회원 분기에 권한 요청 컴포저블.
4. `PushTokenRegistrarImplTest` — 회원 전이 등록, 갱신은 회원일 때만, 403 → 정지 신호, 닫기 → 취소·삭제·갱신 무시.

## 검증

- 로컬: `:notification` ktlint·detekt·유닛 테스트, `checkModuleArchitecture`, `:app` ktlint·detekt, `assembleDebug`.
- 에뮬레이터(Play 서비스 이미지, API 37): 로그인 직후 `PUT` 발신, 앱 재시작 시 재발신, 로그아웃 시 `DELETE`
  발신, 권한 거부 뒤 화면·등록 정상 — OkHttp BASIC 로그로 확인.
- 하네스 매트릭스는 구현 완료 뒤 `Phase 3 · 구현`·FLOW-010 상태로 갱신한다.
