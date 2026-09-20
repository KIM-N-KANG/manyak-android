# 약관·개인정보 처리방침·만 14세 명시 동의 시트와 광고 알림 동의 재질문 (KNK-1347)

- 작성일: 2026-09-20
- 브랜치: Android·하네스 모두 `feat/KNK-1347-legal-consent-bottom-sheet`
- 정본: 공통 [동의 모델](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-010-서비스-이용약관개인정보-처리방침),
  [Android spec §3-3-1·§3-3-5](../../../knk-harness/docs/spec/3-3-android-spec.md),
  [Android design §1-2-5 약관 동의 게이트·§1-2-6 광고 알림 수신 동의](../../../knk-harness/docs/design/1-2-android-design.md),
  [A-044](../../../knk-harness/docs/adr/1-3-android-adr.md#a-044). API는 dev Swagger `GET·POST /users/me/consents`.
- 참고 구현: 웹 `manyak-web` `feat/KNK-1342-legal-consent-bottom-sheet`의 `consent-gate.tsx`·`consent-sheet.tsx`.

## 목표

로그인 직후 회원 그래프 위에 필수 동의 시트를 띄우고 서버 기록 성공을 완료로 판정한다. 같은 시트 가장 아래에서
광고 알림 수신 동의(선택)를 받고, 거절한 회원에게는 세 번째 재진입에 한 번 더 묻되 두 번 거절하면 다시 묻지 않는다.

## 이 레포에서 새로 내린 결정

1. **동의 게이트는 `legal/consent`가 소유한다.** 문서 화면·문구·URL이 이미 `legal`에 있고, 필요한 의존은 `auth`(로그아웃)와
   `network`뿐이다. `notification`의 `PushSettingsRepository`는 기능 간 의존 금지로 쓸 수 없어 선택 항목의 답만 상태
   (`marketingAnswer`)로 남기고 루트가 `MarketingConsentViewModel`에 넘긴다.
2. **전문은 시트 위 `Dialog`로 연다.** 모달 시트는 아래 화면을 덮어 백스택에 문서를 쌓아도 보이지 않는다(`marketing-consent.md`
   결정 5의 한계). `LegalDocumentScreen`을 문서별 ViewModel 키로 띄우고 앱바의 뒤로가기·시스템 뒤로가기가 창만 닫는다.
   창에는 나가는 길이 보여야 해서 `LegalDocumentScreen` 자체에 제목·뒤로가기 앱바를 넣었고, 백스택으로 여는 기존 진입(로그인
   화면·서비스 안내·알림 설정)도 같은 화면이라 함께 앱바가 생겼다(2026-09-20 사용자 결정 — 표현을 통일).
3. **뒤로가기만 로그아웃이다.** 웹과 같다. `ManyakBottomSheet`에 `dismissOnBackPress`를 추가해 저장·로그아웃 중에는 뒤로가기를
   창에서 막는다 — M3 시트의 뒤로가기는 `confirmValueChange`를 거치지 않고 `onDismissRequest`를 부른다.
4. **ViewModel은 세션 상태로 다시 판정한다.** 루트에서 만든 ViewModel은 액티비티 수명이라 로그아웃 뒤 다른 회원이 로그인해도
   살아 있다. 준비 플래그 대신 `sessionState`가 회원이 될 때마다 조회하고, 회원이 아니면 상태를 비운다. 광고 동의 ViewModel도 같다.
5. **질문 단계는 거절 횟수 + 재진입 횟수다.** `claimPrompt()`가 호출마다 재진입을 세고 거절 1회면 세 번째부터 참이다.
   두 번째 거절·허용은 닫힘(2)이다. 이전 "물었음" 값은 한 번 거절로 읽는다. 순수 전이는 `MarketingPromptState`로 분리해 검사한다.
6. **약관 시트에서 답한 진입은 재질문 후보로 세지 않는다.** `AnsweredInConsentSheet`가 준비 표시를 올려 같은 프로세스의
   `Prepare`를 건너뛴다. 선택 항목을 체크했으면 서비스 값을 읽어 광고만 켠 `PUT` 뒤 통지하고, 실패는 토스트만 띄운다.
7. **전체 동의는 선택 항목까지 켠다.** 국내 관례를 따르고, 제출 조건은 필수 항목만 본다. 항목 순서는 만 14세 → 약관 → 처리방침
   → 광고(선택)이다(2026-09-20 사용자 결정, 웹은 약관 → 처리방침 → 만 14세).
8. **로그인 화면의 간주 동의 문구를 문서 링크로 바꾼다.** 동의를 시트에서 명시적으로 받으므로 "간주" 고지는 사실과 어긋난다.
9. **체크박스를 `ManyakCheckbox`로 올린다.** 탈퇴 확인에 이어 두 번째 사용처다. 줄이 토글을 맡는 규칙은 `DESIGN.md` 그대로다.
10. **알림 권한을 시트보다 먼저 묻되, 선택 항목은 권한과 무관하게 항상 싣는다**(2026-09-20 사용자 결정). OS 권한과
    광고성 정보 수신 동의는 별개의 동의라 권한을 거부했어도 시트에서 받는다. 기기 알림이 꺼진 채 허용하면 서버에는 동의가
    남고 표시는 설정에서 알림을 켠 뒤부터라, 처리 결과 다이얼로그를 닫을 때 "기기 알림이 꺼져 있어 알림 설정에서 켜야 받을 수
    있어요" 토스트로 알린다.

## 구현 순서

1. `designsystem` — `ManyakCheckbox`, `ManyakBottomSheet(dismissOnBackPress)`. `my/withdrawal`이 새 체크박스를 쓴다.
2. `legal/consent` — `entity/ConsentStatus`, `domain/ConsentRepository`, `data/api·dto·repository·di`,
   `presentation/LegalConsentViewModel`·`LegalConsentSheet`(문서 `Dialog` 포함), `strings.xml`, `build.gradle.kts`(auth·network·retrofit·serialization).
3. `notification/consent` — `MarketingConsentPromptRepository`(`claimPrompt`·`markDeclined`·`markSettled`), 저장소의 `MarketingPromptState`,
   ViewModel의 `AnsweredInConsentSheet`·`Reset`·`isBusy`.
4. `login` — `LegalLinks`와 `login_legal_*` 문자열.
5. `app` — `MemberOverlays`: 알림 권한 → 동의 시트(`enabled = permissionSettled`) → 답 전달 → `onboardingReady`로 초대·재질문 순서.
6. 테스트 — `LegalConsentViewModelTest`(10), `MarketingConsentViewModelTest`(9), `MarketingPromptStateTest`(3).

## 검증

- 로컬: 변경 모듈 `compileDebugKotlin`·`ktlintCheck`·`detekt`, `:legal`·`:notification` 유닛 테스트.
- 에뮬레이터: 아래 "실행 결과" 참고.

## 실행 결과 (2026-09-20)

- 로컬: `:designsystem`·`:my`·`:legal`·`:notification`·`:login`·`:app` `ktlintCheck`·`detekt`, `:legal`(10)·`:notification`(26)·`:my` 유닛 테스트,
  `checkModuleArchitecture`, `assembleDebug` 통과.
- 에뮬레이터(Pixel_10, API 37, Google 계정 kdw34441360): 로그인 → 동의 시트 → "보기"로 약관 전문 창 → 뒤로가기로 시트 복귀 →
  시트에서 뒤로가기 → 로그아웃(로그인 화면) → 재로그인 → 전체 동의(선택 항목까지 켜짐) → 선택 해제 → 동의하기 → 홈, 시트 없음,
  `marketing-consent.preferences_pb`에 `declines=1`. 콜드 스타트 1·2회는 재질문 없음(`entries=1,2`), 3회째 광고 동의 시트 표시(`entries=3`) →
  닫기 → 콜드 스타트 4·5회 재질문 없음. 저장소를 지운 뒤 첫 질문 시트에서 허용 → `PUT` → 처리 결과 다이얼로그.
  검증 뒤 알림 설정에서 광고 알림을 다시 껐다. 캡처는 `../captures/KNK-1347/`(01~12).
- 코드로만 확인한 것: 조회 실패 시트와 다시 시도, `CONSENT_VERSION_MISMATCH` 재조회, 선택 항목 허용 시 약관 시트 직후 통지, 알림 권한 →
  시트 순서(검증 계정의 동의가 이미 기록돼 시트를 다시 띄울 수 없었다).
