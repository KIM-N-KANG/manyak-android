# 알림 설정 화면 (KNK-1135)

- 작성일: 2026-09-08
- 근거 정본: 하네스 `3-3-android-app.md §3-3-3 마이 · 알림 설정`, `4-backend.md §4-3-5 푸시 수신 동의`,
  `docs/planning/android-module-architecture.md §5`

## 목표와 제외 범위

`GET`·`PUT /users/me/push-settings` 로 서비스·광고·야간 세 동의를 보고 바꾸는 화면을 마이 탭에 붙이고,
기기 알림이 꺼져 있으면 시스템 설정으로 보낸다.

**제외** — 알림 수신·표시·탭 진입(KNK-1134), 앱 안의 권한 재요청(두지 않기로 확정, `push-token.md`).

## 이 레포에서 새로 내린 결정

1. **저장 요청을 Intent 큐 안에서 기다린다.** `MviViewModel` 은 Intent 를 하나씩 처리하므로 `PUT` 을 `handleIntent`
   안에서 기다리면 앞 요청이 끝나기 전의 탭이 순서대로 뒤에 붙는다. 진행 플래그·병렬 Job·마지막 확인값 변수를
   따로 두지 않아도 "요청 직전 상태 = 마지막 서버 확인값" 이 성립해 실패 되돌리기가 한 이벤트로 끝난다.
   `ponytail:` 앞 요청 동안 다음 탭의 화면 반영이 그만큼 늦다. 체감되면 낙관 반영을 즉시 하고 저장만 직렬화한다.
2. **광고 끄기는 야간을 같은 요청에서 함께 내리고, 야간 토글은 광고 꺼짐이면 무시한다.** 서버가 400 으로 거절할
   조합을 만들지 않는다. 그래도 `NIGHT_PUSH_REQUIRES_MARKETING` 이 오면 들고 있던 값이 낡은 것이라 되돌린 뒤 재조회한다.
3. **기기 알림 여부는 ViewModel 상태가 아니라 화면이 `ON_RESUME` 마다 OS 에서 읽는다.** 정본이 OS 라 복제할 이유가
   없고, 설정에서 돌아오는 복귀가 곧 재판정 시점이다. 판정은 `areNotificationsEnabled` 하나로 API 33+ 권한과 그 이하의
   앱 알림 끔을 함께 본다.
4. **스위치는 `notification` 모듈 안의 M3 `Switch` 색 지정으로 두고 `:designsystem` 으로 올리지 않는다.** 사용처가 이
   화면 하나다. 색 규칙은 `DESIGN.md` `switch` 항목이 소유한다.
5. **상세 헤더는 화면이 직접 그린다.** `my` 의 `MyDetailHeader` 는 그 모듈 내부 컴포넌트이고, 스토리 상세·채팅방도
   각자 `TopAppBar` 를 그린다. 공용으로 올리는 일은 이 티켓 범위가 아니다.

## 구현 순서

1. `notification/settings` — `entity/PushSettings`, `domain/PushSettingsRepository`, `data/api`·`dto`·`repository`·`di`,
   `presentation/NotificationSettingsViewModel`·`Screen`, `res/values/strings.xml`.
2. `:navigation` `NotificationSettingsRoute`, `:designsystem` `ic_bell`, `DESIGN.md` `switch`.
3. `my` 마이 탭에 알림 섹션·"알림 설정" 항목과 콜백, `app` 의 `MainTabs`·`ManyakApp` 배선.
4. `NotificationSettingsViewModelTest` — 광고 끄기 동반 하강, 광고 꺼짐 시 야간 무시, 실패 되돌리기·효과, 야간 400 재조회,
   성공 시 서버값 교체.

## 검증

- 로컬: `:notification` ktlint·detekt·유닛 테스트, `:app`·`:my` ktlint·detekt, `:build-logic:ktlintCheck`, `assembleDebug`.
- 에뮬레이터: 조회 200 → 토글 `PUT` 200, 프록시로 실패 유도 시 되돌리기·토스트, 골격, 실패 자리, 알림 권한 끔 배너 →
  시스템 설정 이동 → 복귀 시 배너 소멸, 회전 후 토글 값 유지.
