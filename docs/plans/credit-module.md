# 이프 충전을 credit 기능 모듈로 분리

- 티켓: [KNK-2049](https://kimandkang.atlassian.net/browse/KNK-2049)
- 작성일: 2026-11-09
- 상태: 구현, 로컬 전체 검증과 에뮬레이터 확인 완료
- Android: `refactor/KNK-2049-improve-module-folder-structure`
- 하네스: `docs/KNK-2049-improve-module-folder-structure`
- 정본: [PAYMENT Android ADR A-096](../../../knk-harness/docs/domain/PAYMENT/CLIENT/ANDROID/DECISIONS.md#a-096), [PAYMENT 컴포넌트 구조](../../../knk-harness/docs/domain/PAYMENT/CLIENT/ANDROID/4-2-COMPONENT-ARCHITECTURE.md)

## 목표와 범위

my의 `my/credit` 패키지를 `:feature:credit`으로 옮깁니다. 충전 화면 동작, API, 라우트, 딥링크, 분석 이벤트는 바꾸지 않습니다.

## 구현

| 영역 | 변경 |
| --- | --- |
| credit | 화면, ViewModel, API, DTO, 저장소 구현, `CreditModule`, `CreditNetworkModule`을 `app.manyak.credit`으로 이동. 테스트 2개 이동 |
| 문구 | 충전 화면 문구 13개를 credit으로 옮기고 `my_` 대신 `credit_` 접두어로 이름 변경(`my_credit_history`는 designsystem의 `credit_history_*`와 헷갈리지 않게 `credit_charge_history`). 쓰이지 않던 `my_attendance_in_progress`는 my에 그대로 둠 |
| 공용 헤더 | `MyDetailHeader`를 `designsystem`의 `ManyakDetailHeader`로 올림. 뒤로가기 설명은 designsystem 문구 `detail_header_back`. my 하위 화면 8곳 갱신, `DESIGN.md` 컴포넌트 표에 추가 |
| 친구 초대 줄 | `CreditChargeScreen`과 무료 충전 탭이 `onOpenInvite` 대신 `inviteEntry` 슬롯을 받고, `app`이 my의 `InviteMenuItem`(공개로 변경)을 넘김 |
| 등록 | `settings.gradle.kts`, `.idea/gradle.xml`, `app` 의존, `ModuleArchitecture.kt` 기능 모듈 목록, `UserScopedStoreModule`, `ManyakApp`의 import |

## 검증

- `:feature:credit`, `:feature:my`, `:core:designsystem`, `:app`의 `ktlintCheck`, `detekt`와 credit, my, designsystem 단위 테스트, `:app:compileDebugKotlin`, `checkModuleArchitecture`, `:build-logic:test` 통과.
- `./gradlew check assembleDebug` 통과(58초). `checkModuleArchitecture`는 21개 프로젝트와 Kotlin 642개 파일을 검사했습니다.
- Pixel_10 에뮬레이터(로그인된 dev 계정)에 `installDebug` 후 출석 알림과 같은 푸시 진입 extra(`deepLink=https://manyak.app/my/credits?tab=free`, 현재 회원 `recipientId`)로 열었습니다. 이프 충전 헤더(뒤로가기 설명 "뒤로", 구매 안내 버튼), 잔액, 무료 충전 탭의 옮긴 문구와 앱이 넘긴 친구 초대 줄(보상 2,000 이프)이 표시됐습니다. 친구 초대 줄을 누르면 마이의 친구 초대 화면이 `ManyakDetailHeader`로 열렸고, 뒤로가기 뒤 내역 탭에서 서버 내역 목록이 불러와졌습니다.
- 출석 하기와 구매는 서버 상태를 바꾸므로 누르지 않았고 ViewModel 단위 테스트로만 확인했습니다. 변경 전 빌드 화면과 나란히 비교하지는 않았습니다.
