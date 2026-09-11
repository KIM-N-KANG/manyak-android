# 광고 알림 수신 동의 시트와 처리 결과 통지 (KNK-1254)

- 작성일: 2026-09-11
- 근거 정본: 하네스 [Android Spec의 알림 계약](../../../knk-harness/docs/spec/3-3-android-spec.md#3-3-5-알림) ·
  `design/1-2-android-design.md §1-2-6 알림 진입과 권한`, `spec/4-backend-server-spec.md §4-3-5 푸시 수신 동의`,
  웹 `src/features/legal/content/privacy-content.ts`의 광고성 정보 수신 동의 절

## 목표와 제외 범위

회원 그래프 첫 진입에서 알림 권한 응답과 초대 코드 안내가 끝난 뒤 광고 알림 수신 동의를 바텀 시트로 한 번
묻고(야간은 시트에서 받지 않고 알림 설정에 맡긴다), 동의·철회를 처리할 때마다 전송자·일시·처리 내용을 다이얼로그로
통지한다(정보통신망법 제50조).
알림 설정의 광고 행에서 개인정보 처리방침을 열 수 있게 한다.

**제외** — 2년 주기 재확인 발송과 광고 페이로드의 전송자 연락처·수신 거부 문구(서버), 앱 안 권한 재요청(두지
않기로 확정, `push-token.md`), 시트에서 문서 열기·고지(아래 결정 5).

## 이 레포에서 새로 내린 결정

1. **"물었다" 는 사용자 귀속 DataStore(`marketing-consent`) 에 두고 로그아웃 정리 대상에 넣는다.** 동의의 정본은
   서버의 동의 시각이고 이 값은 묻는 차례가 지났는가만 뜻한다. 서버 `marketingPush` 가 이미 참이면 묻지 않는다.
   재로그인하면 거절했던 회원에게 한 번 더 묻는다 — 서버에 "물었음" 을 두면 막을 수 있지만 API 계약이 늘어난다.
   `ponytail:` 반복 노출이 문제되면 그때 서버 필드로 올린다.
2. **기록은 답한 뒤에 남긴다.** 시트가 뜬 채 프로세스가 죽으면 다음 실행에서 다시 뜬다. 회전은 ViewModel 이 살아
   있어 두 번 묻지 않는다. 저장 실패는 시트를 유지하고 기록하지 않는다.
3. **순서는 루트가 정한다.** `NotificationPermissionRequest(onSettled)` 가 "더 물을 것이 없다" 를 알리고, 루트
   ViewModel 이 초대 코드 안내의 `pending` 을 읽어 둘 다 끝났을 때만 `MarketingConsentSheet(enabled = true)` 다.
   `pending` 의 초기값은 참이다 — 읽기 전에 거짓이면 안내 두 장이 한 프레임에 겹칠 수 있다.
4. **기기 알림이 꺼져 있으면 묻지 않고 기록도 남기지 않는다.** 동의해도 표시되지 않으니, 시스템에서 켠 뒤 첫
   실행에서 묻는 편이 낫다. 판정은 알림 설정과 같은 `areNotificationsEnabled` 다.
5. **시트에는 고지를 싣지 않는다.** 모달 시트 위로는 문서 화면을 열 수 없고(시트가 덮는다), 전송자·이용 항목·보유
   기간의 정본은 개인정보 처리방침이라 시트는 제목·한 줄 설명·버튼만 둔다(2026-09-11 사용자 결정 — 처음엔 네 줄
   고지와 야간 스위치를 실었다가 뺐다. 야간은 별도 동의라 설정 화면에서만 켠다). 처리방침 링크는 알림 설정의 광고 행 옆 외부 링크 아이콘이 맡는다.
6. **통지 일시는 사용자가 의사를 표시한 기기 시각이다.** 서버 응답에 동의 시각이 없고, 법의 기산점도 의사 표시
   시점이다. `java.time` 없이 `SimpleDateFormat` 으로 그린다(minSdk 24, 디슈가링 없음).
7. **통지는 광고·야간 동의를 바꿀 때만 띄운다.** 서비스 알림은 동의가 아니고, 시트의 "받지 않기" 는 동의한 적이
   없어 통지 대상이 아니다("닫기"). 광고 끄기는 야간도 함께 지우므로 통지 한 장이다.
8. **알림 설정의 토글은 줄이 아니라 스위치가 받는다**(2026-09-11 사용자 결정, `notification-settings.md` 결정과 `DESIGN.md`
   `switch` 항목을 대체). 라벨 옆에 처리방침 아이콘 버튼이 생겨 줄 전체 토글과 겹치기 때문이다. **야간 광고 허용 줄은 광고가
   꺼져 있으면 비활성 대신 숨긴다**(같은 날 사용자 결정) — 스위치의 비활성 색은 함께 지웠다. 처리 결과 다이얼로그는 `ManyakDialog` 창에 파괴적 확인과 같은 배치(제목·본문·오른쪽 버튼)다.

## 구현 순서

1. `notification/consent` — `entity/ConsentNotice`, `domain/MarketingConsentPromptRepository`,
   `data/MarketingConsentPromptStore`, `presentation/MarketingConsentViewModel`·`Sheet`·`ConsentNoticeDialog`,
   `res/values/strings.xml`, `NotificationModule` 바인딩.
2. `notification/settings` — ViewModel 에 `notice` 상태·`DismissNotice`, 화면에 다이얼로그·처리방침 링크·`onOpenPrivacyPolicy`.
3. `notification/presentation/NotificationPermissionRequest` 에 `onSettled`.
4. `app` — `RootViewModel.inviteOnboardingPending`, `ManyakApp` 회원 분기 순서 배선과 알림 설정 라우트의 처리방침 콜백,
   `UserScopedStoreModule` 바인딩.
5. `MarketingConsentViewModelTest` — 띄우는 조건 셋, 허용 요청 모양·기록·통지, 실패 유지, 받지 않기.
   `NotificationSettingsViewModelTest` — 철회·야간 통지, 서비스·실패 미통지.

## 검증

- 로컬: `:notification` ktlint·detekt·유닛 테스트(24개), `:app` ktlint·detekt, `assembleDebug`.
- 에뮬레이터(API 37, 2026-09-11 실행 결과): 서버가 이미 동의 상태면 콜드 스타트에서 시트 없음(`GET` 200) → 알림 설정 광고
  끄기 → `PUT` 200 → 철회 통지 다이얼로그 → 링크 아이콘 → 처리방침 WebView → 콜드 스타트 → 시트 → 허용 → `PUT` 200 → "동의 완료" 통지 → `marketing-consent.preferences_pb` 생성 → 콜드 스타트 → 시트 없음.
  캡처는 `../captures/KNK-1254/`(01~08). 권한 다이얼로그 순서와 기기 알림 끔 분기는 코드·유닛 테스트로만 확인했다.
