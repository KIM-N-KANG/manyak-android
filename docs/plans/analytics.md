# Amplitude 이벤트 배선

> **KNK-1197 구조 이전 안내 (2026-09-05)** — 아래 모듈 경로·코드 예시·검증 명령은 작성 당시 기록입니다. 현재 소유 위치와 검증 결과는 [모듈 재구성 기록](./module-reorganization.md), 현재 계층·의존 규칙은 [하네스 모듈 아키텍처](../../../knk-harness/docs/planning/android-module-architecture.md)를 따릅니다.

- 작성일: 2026-09-04
- 근거 정본: 하네스 `6-analytics.md §6-4-2`(카탈로그)·`§6-4-2-15`(앱 보강)·`§6-4-2-16`(플랫폼 적용 범위), `3-3-android-app.md §3-3-6`(화면별 발화 지점)

## 목표와 제외 범위

웹과 같은 이벤트 이름·프로퍼티로 앱의 사용자 행동을 Amplitude 에 보낸다. 웹 카탈로그 92개 중 앱에 해당하는
59개와 앱 보강 이벤트 18개를 배선하고, `device_id`·`user_id` 를 API 헤더·세션과 같은 값으로 묶는다.

**제외** — Crashlytics breadcrumb(도입 전), 웹 전용 24개(인앱 브라우저·게스트 한도·온보딩·투어·공유),
보류 항목(테마 전환·라이선스·pull-to-refresh·다이얼로그 취소·키워드 단계 세부 입력).

## 새로 내린 결정

1. **`:core:analytics` 한 모듈에 계약·카탈로그·SDK 배선·노출 추적을 둔다.** 화면 모듈은 `Analytics` 와
   `AnalyticsEvent` 만 보고 SDK 를 모른다. 이벤트 프로퍼티가 도메인 enum(`AuthProvider`·`StoryTagCategory` 등)을
   그대로 받으므로 `:core:domain` 을 `api` 로 노출한다.
2. **`device_id` 주입 전에는 이벤트를 내보내지 않는다.** `AmplitudeAnalytics` 가 그때까지의 이벤트를
   프로퍼티째 쌓았다가 `setDeviceId` 직후 순서대로 보낸다. SDK 큐에 먼저 넣으면 SDK 생성 값이 식별자로
   굳어 API 헤더와 갈라진다. 로그아웃 재발급은 `SessionCleanupSteps.rotateDeviceId` 가 저장 확인 뒤 같은
   함수로 넘긴다.
3. **사용자 식별자는 프로필 캐시를 따른다.** `AnalyticsSessionBinder` 가 `UserProfileRepository.profile` 의
   `id` 를 `setUser`/`clearUser` 로 옮긴다. 세션 상태를 따로 구독하지 않는 이유는 프로필이 회원 상태에서만
   존재하고 종료 정리가 그것을 비우기 때문이다. 로그아웃 클릭·탈퇴 완료는 그보다 앞서 발행돼 옛 사용자에게
   귀속된다.
4. **`viewed` 는 ViewModel `init` 에서 보낸다.** ViewModel 수명이 곧 목적지 수명이라 구성 변경에는 한 번, 재진입에는
   다시 센다. `ScreenShown` Intent 는 채팅방에서 돌아온 복귀에도 오므로 진입 계측으로 쓰지 않는다.
5. **ViewModel 을 거치지 않는 탭은 화면이 `LocalAnalytics` 로 보낸다.** 카드 탭·노출, 마이의 충전 진입, 초대
   복사·공유, 충전 탭 전환이 여기 해당한다. 추적만을 위해 Intent 를 늘리지 않는다 — 웹도 컴포넌트에서
   `track()` 을 직접 부른다.
6. **스튜디오 탭은 `client_storyList_*` 를 `section=created` 로 재사용한다.** 웹 `/studio` 가 이미 그렇게
   하고 있어 `client_studio_*` 를 새로 만들면 플랫폼 비교가 끊긴다. `client_storyList_viewed` 에만 앱이
   `section` 을 선택 프로퍼티로 더한다(홈·스튜디오가 별도 화면이라 진입을 갈라야 한다).
7. **신고는 `StoryReportController` 가 `source` 를 받아 발화한다.** 네 화면이 같은 컨트롤러를 쓰므로
   이벤트도 한 곳에서 나가고, `screen_name` 은 모두 `report` 다. `target_type` 은 신고 API 가 스토리
   하나뿐이라 항상 `story` 다.
8. **노출은 `Modifier.trackImpression` 이 §6-4-3 기준(면적 50%·1초·30초 중복 제거)을 구현한다.** 중복 제거
   장부(`ImpressionTracker`)는 목록 컴포저블이 들어 카드가 스크롤로 버려져도 남는다.
9. **API 키는 `local.properties` 의 `AMPLITUDE_API_KEY_DEBUG`/`_RELEASE` 에서 읽는다.** 비어 있으면 SDK 를
   만들지 않고 debug 빌드는 Logcat(`Analytics` 태그)에만 남긴다 — 키 없이도 배선을 검증할 수 있다.

## 웹에 되돌려야 하는 것

앱 보강 이벤트 중 웹에 같은 화면이 있는 항목은 하네스 `6-analytics.md §6-4-2-16` 의 표가 소유한다. 이 레포는
그 표를 다시 적지 않는다.

## 외부 합의가 필요한 항목

- Amplitude 프로젝트 API 키(dev·prod) 발급과 CI 시크릿 등록.
- Amplitude Android SDK 의 `platform`·`os_name` 자동 수집 값 확인 후 하네스 `§6-3-2` 에 기록(첫 이벤트
  수신 뒤).
