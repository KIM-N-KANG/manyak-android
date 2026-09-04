# Firebase Crashlytics 등록

- 작성일: 2026-09-04
- 근거 정본: 하네스 `3-3-android-app.md §3-3-2`(크래시 도구 결정 기록), `6-analytics.md §6-6-4`(수집 기준)

## 목표와 제외 범위

release 빌드의 처리되지 않은 crash·API 30+ ANR 을 Crashlytics 로 받고, 리포트에 화면 이름과 직전
행동 이벤트 이름, 로그인 사용자 식별자를 붙인다.

**제외** — API 5xx·네트워크·파싱 오류의 `recordException` 배선(아래 결정 5), Firebase Analytics(제품
분석 정본은 Amplitude), NDK 심볼 업로드(앱이 네이티브 코드를 소유하지 않는다).

## 새로 내린 결정

1. **`google-services.json` 을 레포에 커밋한다.** CI 에 주입할 시크릿이 하나도 없는데 PR 마다
   `assembleDebug` 를 돌리므로, 파일이 없으면 Google Services 플러그인이 모든 PR 을 세운다. 파일의 값은
   어차피 APK 에 실려 나가고 보호는 Firebase 보안 규칙과 API 키 제한이 맡는다. 값을 숨겨서 얻는 것보다
   빌드가 파일 하나에 막히지 않는 편이 크다.
2. **Firebase 프로젝트는 서버 FCM 과 같은 하나를 쓰고 환경별로 나누지 않는다.** 서버 푸시 발송 모듈이
   앱과 같은 프로젝트의 서비스 계정을 전제로 하고, `applicationId` 도 빌드 타입 간 같아 json 하나가 두
   타입을 모두 덮는다. debug 는 애초에 수집하지 않으므로(결정 3) 개발 리포트가 섞일 경로가 없다.
3. **debug 수집 차단은 `src/debug/AndroidManifest.xml` 의 `firebase_crashlytics_collection_enabled` 로
   한다.** 코드 분기가 없어 빌드 타입만 보고 켜짐 여부를 알 수 있고, 런타임 토글을 만들면 그 토글이
   release 에서 잘못 꺼지는 경로가 생긴다.
4. **breadcrumb 과 사용자 식별자는 분석 퍼널이 붙인다.** 화면이 이미 모든 P0 행동을 `Analytics.track` 으로
   보내고 세션 조율자가 `AnalyticsIdentity` 로 사용자를 옮기므로, `AmplitudeAnalytics` 가 그 두 지점에서
   `CrashReporter` 를 함께 부른다. 화면에 크래시용 호출을 따로 심으면 둘 중 하나는 반드시 빠진다.
   breadcrumb 은 `device_id` 대기열을 타지 않는다 — 식별자가 붙기 전에 죽어도 직전 행동은 남아야 한다.
   지속 custom key 는 `screen_name` 하나만 두고 `story_id`·`chat_id`·`creation_id` 는 breadcrumb 줄에만
   싣는다. key 로 두면 스토리를 떠난 뒤 난 크래시에도 옛 값이 붙어 무관한 리포트를 그 스토리 탓으로
   읽게 된다. `feature` 는 호출부를 새로 만들어야 하므로 실제로 리포트를 읽다가 필요해질 때 붙인다.
5. **API 오류의 `recordException` 은 이번 범위에 넣지 않는다.** 유일한 오류 변환 지점인 `apiCall` 이
   최상위 함수라 주입 지점이 없고, OkHttp 인터셉터로 옮기면 5xx 를 어떤 예외로 만들어 묶을지(Crashlytics
   는 스택 트레이스로 이슈를 묶는다)와 취소된 호출을 어떻게 거를지를 함께 정해야 한다. 등록과 섞으면
   둘 다 대충 된다. `DomainError.Server` 는 이미 `requestId` 를 들고 있어 나중에 붙일 자리는 남아 있다.

## 검증

- `:core:analytics` 컴파일·ktlint·detekt 통과.
- release 빌드는 `optimization { enable = false }` 라 난독화가 없다. 매핑 업로드 태스크는 지금 올릴 것이
  없고, R8 을 켜는 시점에 실제 업로드와 역난독 리포트를 함께 확인한다.
- 서명된 release 빌드로 에뮬레이터 검수 완료(2026-09-04). 의도적 crash 가 콘솔에 올라오고
  custom key `screen_name=login`, breadcrumb `client_login_viewed` 가 함께 보인다. 같은 빌드의
  logcat 은 수집 켜짐(`ENABLED by global Firebase setting`), debug 빌드는 꺼짐
  (`DISABLED by firebase_crashlytics_collection_enabled manifest flag`)을 찍는다.
- 로그인 상태는 debug 빌드의 수집 플래그를 임시로 켜서 확인했다(2026-09-04). release 빌드로는
  확인할 수 없었다 — 운영 서버가 release 빌드의 구글 로그인에 401을 돌려준다(아래).
  크래시 리포트의 사용자 ID 는 `1a1a3fc6-…`(프로필 `id`)이고 같은 기기의 `device_id`(`381520db-…`)
  와 다르다. `screen_name` 은 마지막으로 본 화면(`chatList`)으로 갱신됐고 breadcrumb 은
  `client_chatList_chatCard_impressed chat_id=…` 형태로 남았다.
- **로컬에서 업로드 키로 서명한 release 빌드는 운영 로그인이 원래 안 된다.** 앱은 서버(웹)
  client-id 를 audience 로 요청하므로 구글이 `azp` 에 그 앱의 **Android** client-id 를 싣고,
  서버는 `azp` 가 허용 목록에 있어야 통과시킨다. Android client-id 는 (패키지명, 서명 인증서 지문)
  으로 갈리는데 Play 는 앱 서명 키로 다시 서명하므로 스토어 배포본과 로컬 빌드의 지문이 다르다.
  로컬 업로드 키 지문은 `97:EB:44:...:6E:B2` 다. 그래서 검수는 debug 빌드로 돌렸고, 스토어
  비공개 테스트 배포본에서는 운영 로그인이 정상 동작한다. 로그인 client-id 는 `BuildConfig`
  에서만 읽으므로 `google-services.json` 추가와는 무관하다.
