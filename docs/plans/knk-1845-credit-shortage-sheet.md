# 이프 부족 바텀 시트 (KNK-1845)

- 작성일: 2026-10-25
- 상위: KNK-1841. 웹 KNK-1844
- 근거 정본: 하네스 ADR [A-081](../../../knk-harness/docs/adr/1-3-android-adr.md#a-081),
  [공통 이프 부족 처리](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-008-로그인마이-페이지),
  [Android 계약](../../../knk-harness/docs/spec/3-3-android-spec.md), [Android 구조](../../../knk-harness/docs/design/1-2-android-design.md)

## 목표와 제외 범위

채팅 전송과 재생성(잔액 선검사, 402)의 "이프가 부족해요" 토스트를 바텀 시트로 바꾸고, 간편 제작 스토리 완성에 잔액 선검사를 더해 퍼널 안에서 같은 시트를 띄운다. 시트는 "이프 충전하기", 오늘 출석하지 않았을 때만 "출석 체크하기", "닫기"를 둔다.

**제외**: 제작 탭으로 떠난 뒤 확정된 완성 실패 안내(A-077의 "스토리를 완성하지 못했어요" 유지), 충전에서 돌아와 퍼널을 다시 여는 동작.

## 결정

1. **출석은 `common`의 `AttendanceClaimer`.** `my`의 `CreditRepository`가 상속하고 같은 구현을 바인딩한다. `AttendanceResult`도 `common`으로 옮긴다.
2. **절차는 `common`의 `CreditShortageController`.** 열기(프로필 재조회), 출석, 충전 중 잠금, 닫기를 맡고 상태는 각 ViewModel의 `creditShortage`에 싣는다. 분석 이벤트(`CreditShortageShown`, `CreditShortageChargeButtonClicked`, `CreditShortageAttendanceButtonClicked`)는 ViewModel이 보낸다.
3. **UI와 문구는 `designsystem`의 `CreditShortageSheet`.** 닫기는 다른 시트와 같은 보조색 텍스트 버튼이다.
4. **채팅 충전**은 `ChatRoomViewModel`을 백스택에 둔 채 `MyCreditChargeRoute()`를 쌓아 입력이 그대로 남는다.
5. **간편 제작 충전**은 `StorylineGenerationStore.saveDraftAndAwait()`로 버튼 저장과 같은 경로를 기다려 성공했을 때만 퍼널을 이탈 처리하고, `app`이 퍼널을 걷어 제작 탭 위에 이프 충전을 쌓는다. 실패와 충돌은 버튼 저장과 같이 안내하고 시트에 머문다.
6. **완성 선검사** `isStoryCreationCreditShort`는 프로필 잔액, 정책 `storyCreationCost`, 체험 `storyCreation`을 모두 알고 체험이 소진됐을 때만 막는다.

## 검증

- `./gradlew checkModuleArchitecture`, `common`, `designsystem`, `analytics`, `my`, `chat`, `create`, `app`의 `ktlintCheck`와 `detekt`, `chat`, `create`, `my`, `common`의 `testDebugUnitTest`, `:app:compileDebugKotlin` 통과(chat 220, create 269, my 48, common 13 테스트).
  - 채팅: 선검사와 402가 시트를 열고 입력 유지, 충전 효과와 시트 닫힘, 출석 성공 시 보상 알림과 닫힘(연타 한 번 요청), 실패 시 시트 유지.
  - 간편 제작: 선검사가 완성 요청 없이 시트를 엶, 충전이 입력을 초안으로 저장한 뒤 떠남, 저장 실패 시 머묾, 이미 출석 응답도 닫힘, 선검사 판정의 모름과 체험 잔여 조합.
- 에뮬레이터(`installDebug`): dev 계정의 잔액이 충분해 선검사 판정만 임시로 참으로 바꾼 빌드로 화면을 확인한 뒤 되돌렸다.
  - 채팅: 시트 표시, 충전 → 이프 충전 → 뒤로가기에 같은 방과 입력 유지, 시트 열린 채 회전 유지, 닫기.
  - 간편 제작: 시트 표시, 충전 → 초안 저장 뒤 이프 충전 → 뒤로가기에 제작 탭, 초안 카드 이어서 만들기로 입력과 화풍 유지.
  - 계정이 오늘 이미 출석해 출석 버튼은 기기에서 보지 못했고 단위 테스트로만 확인했다. 실제 402 경로도 기기에서는 재현하지 않았다.
