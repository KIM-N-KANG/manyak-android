# 알림 수신과 딥링크 진입 (KNK-1134)

- 작성일: 2026-09-08
- 근거 정본: 하네스 `design/1-2-android-design.md §1-2-6 알림 수신·탭 진입`, `spec/4-backend-server-spec.md §4-3-5 푸시 발송 모듈`(공통 키 `recipientId`, KNK-1220)

## 목표와 제외 범위

서버의 data 전용 메시지를 받아 현재 회원이 수신자일 때만 시스템 알림으로 띄우고, 탭하면 `type` 에 맞는
화면으로 들어가며, 미로그인이면 로그인 뒤 그 화면으로 이어진다. 세션이 끝나면 표시된 알림을 전부 걷는다.

**제외** — App Link·공유 URL 열기(FE-SCREEN-012 결정과 함께), 앱 안 알림 목록, prod FCM 서비스 계정 배선(서버
인프라 — 릴리스 전 필요). 서버의 `recipientId` 는 KNK-1220 으로 반영됐다(`FcmPushSender.sendToUser`).

## 이 레포에서 새로 내린 결정

1. **라우트 매핑은 `:navigation` 의 `PushEntry` 하나가 소유한다.** `type`·대상 식별자·`recipientId` 세 문자열만
   담는 `java.io.Serializable` 값이라 알림 인텐트 extra 와 `SavedStateHandle` 을 그대로 오간다. 클래스 직렬화
   대신 문자열 extra 셋으로 싣는 이유는 앱 업데이트 전에 만든 PendingIntent 도 읽혀야 해서다. `routeFor` 는
   홈을 `MainTabsRoute` 로 돌려줘 호출부가 "보류 없음(null)" 과 구분하지 않아도 된다.
2. **보류 진입은 루트 ViewModel 의 `SavedStateHandle` 에 둔다.** `MainActivity` 는 첫 생성(`savedInstanceState`
   가 null 일 때만)과 `onNewIntent` 에서 extra 를 읽어 넣고, 메인 그래프가 `LaunchedEffect` 로 소비한다. 메인
   그래프는 회원일 때만 그려지므로 미로그인 보류는 자연히 로그인 뒤에 소비된다. 재생성이 돌려주는 원래
   인텐트를 다시 해석하지 않는 이유는 백스택과 보류가 이미 저장 상태에 있어서다 — 다시 읽으면 상세가 두 번
   쌓인다. 도착지 해석(`resolveEntryDestination`)은 순수 함수로 빼 `app` 유닛 테스트가 덮는다.
3. **알림 인텐트는 런처 컴포넌트로 만든 명시 인텐트다.** `:notification` 이 `MainActivity` 를 모르므로
   `getLaunchIntentForPackage` 의 컴포넌트만 빌려 쓰고 ACTION_MAIN·LAUNCHER 카테고리는 붙이지 않는다 — 런처
   인텐트 그대로면 실행 중인 태스크를 앞으로 가져올 뿐 extra 가 전달되지 않는다. `MainActivity` 는 `singleTop`
   이고 `CLEAR_TOP|SINGLE_TOP` 플래그라 실행 중이면 `onNewIntent`, 없으면 새로 만들어 같은 보류 경로로 들어간다.
4. **표시된 알림의 정리는 `UserScopedStore` 등록으로 한다.** `PushNotificationTray` 가 `clearUserData` 에서
   `cancelAll` 하고 `app` 의 `UserScopedStoreModule` 이 바인딩한다. 로그아웃·탈퇴·서버 강제 종료·재개·재시도가
   전부 기존 사용자 데이터 삭제 단계를 지나므로 `notification/domain` 에 정리 계약을 따로 두지 않았다.
5. **수신자 판정(`PushRecipientGate`)은 세션 확정과 프로필 도착을 기다린다.** 콜드 스타트에서는 세션이
   `미확정`, 로그인 직후에는 프로필 캐시가 비어 있을 수 있어서다. 대기는 트레이가 5초로 묶고, 넘기면 버린다.
   판정을 통과한 뒤 `notify` 직전에 `회원` 인지 한 번 더 본다.
6. **`onMessageReceived` 는 `runBlocking` 으로 끝까지 기다린다.** FCM 콜백은 이미 백그라운드 스레드이고 콜백이
   돌아가면 프로세스가 죽을 수 있다. 앱 스코프에 던지면 표시 전에 프로세스가 사라진다.
7. **알림 아이콘은 디자인 시스템 `ic_bell` 이다.** 단색 브랜드 마크가 없어서다. 생기면 트레이 한 곳만 바꾼다.

`ponytail:` `notify` 직전 검사와 로그아웃 `cancelAll` 사이의 밀리초 창은 막지 않는다. 그 창에 뜬 알림은
정리 단계의 `cancelAll` 이 걷고, 탭해도 수신자 재검증이 홈으로 보낸다.

## 구현 순서

1. `:navigation` — `PushEntry`(매핑·인텐트 extra 읽기/쓰기) + `PushEntryTest`.
2. `:notification` — `entity/PushMessage`(페이로드 파싱), `domain/PushRecipientGate`, `data/PushNotificationTray`
   (채널·표시·정리), `ManyakFirebaseMessagingService.onMessageReceived`, 매니페스트 기본 채널 메타데이터,
   스토리 완성 제목 문자열, `:navigation` 의존 추가. `PushRecipientGateTest`.
3. `:app` — `RootViewModel` 보류 진입·도착지 흐름, `MainActivity` 인텐트 수용·`singleTop`, `ManyakApp` 소비,
   `ManyakApplication.ensureChannels`, `UserScopedStoreModule` 트레이 바인딩. `ExternalEntryTest`.

## 검증 (2026-09-08, emulator-5554 · API 37 Play 이미지 · dev 서버)

- 로컬: `:navigation`·`:notification`·`:app` ktlint·detekt·유닛 테스트(11건), `checkModuleArchitecture`,
  `:build-logic:ktlintCheck`, `assembleDebug` 통과.
- 채널: `dumpsys notification` 에 `service`(중요도 4)·`marketing`(중요도 3) 생성 확인.
- 탭 진입(`am start` extra 주입): 실행 중(`onNewIntent`)·종료 상태 모두 스토리 상세 진입, 수신자 불일치·알 수
  없는 `type`·식별자 누락은 홈.
- 표시(data 경로): 서버 `recipientId` 반영(KNK-1220) 뒤 dev 서버에서 간편 제작을 완성 요청하고 앱을 백그라운드로 보낸 채
  약 60~70초 뒤 스토리 완성 푸시 수신 — `service` 채널·PRIVATE·제목 "스토리가 완성됐어요"·본문 스토리 제목으로 표시.
  프로세스를 `am kill` 로 없앤 뒤 탭 → 새 스토리 상세 진입. (`am force-stop` 뒤에는 시스템이 PendingIntent 를
  무효화해 탭이 무시되는데, 이는 강제 중지 상태의 OS 동작이지 앱 경로 문제가 아니다.) 앞서 Firebase 콘솔 테스트
  메시지에 맞춤 데이터를 실어 포그라운드 수신·백그라운드 탭도 확인했다. **서비스 알림 토글이 꺼져 있으면 서버가
  발송 자체를 건너뛰므로** 검증 전 알림 설정을 확인해야 한다.
- 정리: 알림 2건이 떠 있는 상태에서 로그아웃 → 0건.
- 계정 격리: A 로그아웃 뒤 A 대상 메시지 수신 → 미표시(수신은 FCM 로그로 확인). A 대상 보류 진입을 둔 채 B 로
  로그인 → 홈. B 로그인 상태에서 A 대상 메시지 수신 → 미표시. A 로그아웃 → A 대상 보류 진입 → A 로그인 → 상세.
