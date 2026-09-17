# 이프 마크와 체험 잔여 기반 비용 표기

- 티켓: [KNK-1319](https://kimandkang.atlassian.net/browse/KNK-1319) (부모 KNK-1315)
- 작업일: 2026-09-17
- 상태: 구현·로컬 집중 검증 완료. 원격 PR·CI 결과는 연결된 Jira와 PR에서 확인합니다.
- 브랜치: `feat/KNK-1319-credit-mark-and-trial-cost-display`
- 분기 기준: fetch한 `origin/dev` `cf424f8d`
- 계약: [채팅 턴 체험·이프 비용](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-005-채팅-화면), [채팅 설정 시트](../../../knk-harness/docs/spec/3-1-client-spec.md#채팅-설정-시트와-메뉴-시트), [추가 정보 진입](../../../knk-harness/docs/spec/3-1-client-spec.md#추가-정보-진입스토리라인-재선택), [이프 정책 수치 표시](../../../knk-harness/docs/spec/3-1-client-spec.md#이프-정책-수치-표시)
- 서버 근거: dev Swagger `GET /api/v1/users/me/trials` → `TrialsResponse { chatTurn, chatImage, storyCreation, storylineGeneration }`(각 `TrialUsage { used, limit: int64|null }`), `CreditPolicyResponse.chatImageCost`

## 변경

1. `:common` — `CreditPolicy.chatImageCost`, `Trials`·`TrialUsage`(`isFree` = `limit == null || limit - used > 0`), `TrialsRepository` 계약, `LocalTrials` CompositionLocal.
2. `:my` — `CreditPolicyResponseDto.chatImageCost`, `TrialsApi`(인증 클라이언트)·`TrialsResponseDto`·`TrialsRepositoryImpl`(메모리 `StateFlow`, `UserScopedStore` 참여). DI 바인딩은 `CreditModule`·`CreditNetworkModule`, 정리 집합 등록은 `:app`의 `UserScopedStoreModule`.
3. `:app` — `RootViewModel`이 세션이 `Member`가 될 때마다 `refresh()`하고 `LocalTrials`로 내립니다.
4. `:designsystem` — `drawable-nodpi/if_credit_mark.png`(웹 원본 128px)와 `CreditAmountText`(마크 + 취소선 정가(선택) + 수치, 마크 크기는 글자 `fontSize`, `pending`이면 골격 맥박).
5. `:chat` — `chatTurnCost()` 순수 함수로 정가·적용가 계산, `ComposerToolbar` 배지 교체(두 입력 모드·모든 버튼 상태 같은 자리), 설정 시트 실시간 이미지 배지(`backgroundNeutral` 알약) + 체험 소진 시 안내 버튼·팝오버, `ChatRoomViewModel`이 `Completed` 뒤 잔여 재조회.
6. `:create` — 완성 비용 행이 `storyCreation` 체험이 남으면 취소선 + "0 이프". `StoryCompletionExecutor`가 완성 확정(`markCompleted`) 뒤 잔여를 재조회합니다 — 티켓 범위 밖이지만 없으면 다음 제작의 완성 비용이 낡은 잔여로 그려집니다.
7. `MyCreditCard`를 "내 이프" 라벨 위·잔액 아래 세로 배치로 바꾸고 잔액(마이·이프 충전)에 마크를 붙였습니다.

게스트 402 유도·결제 탭은 제외입니다. Android는 로그인 필수라 `isMember` 분기 없이 체험 소진 시 항상 안내 버튼을 둡니다.

## 검증

```bash
./gradlew :common:ktlintCheck :common:detekt :common:testDebugUnitTest \
  :designsystem:ktlintCheck :designsystem:detekt \
  :my:ktlintCheck :my:detekt :my:testDebugUnitTest \
  :chat:ktlintCheck :chat:detekt :chat:testDebugUnitTest \
  :create:ktlintCheck :create:detekt :create:testDebugUnitTest \
  :app:ktlintCheck :app:detekt :app:compileDebugKotlin installDebug
```

- 전부 통과. `ChatTurnCostTest` 7건(스펙 예시: 턴·이미지 체험 남고 켬 ~~80~~ 0, 끔 ~~20~~ 0, 이미지만 남고 켬 ~~80~~ 20, 끔 20, 무제한, 잔여 미도착, 이미지 정책값 없음), `ChatRoomStreamTest`에 `Completed` 뒤 재조회 1건 추가. `StoryCompletionExecutorTest` 7건 통과.
- Pixel 에뮬레이터(회원 계정)에서 `adb exec-out screencap -p` 캡처. 로컬 `captures/knk-1319/`(`.gitignore` 대상).

| 화면 | 캡처 | 확인 |
| --- | --- | --- |
| 마이 이프 카드 | `my-card.png` | 라벨 위·잔액 아래 세로 배치, 마크 |
| 이프 충전 잔액 | `charge-balance.png` | 마크 |
| 채팅 블럭 모드·이미지 켬 | `chat-block-image-on.png` | "80 이프"(20 + 60) |
| 채팅 일반 모드·이미지 끔 | `chat-plain-image-off.png` | "20 이프", 같은 자리 |
| 채팅 설정 시트 | `settings-badge-popover.png` | 배지 "60 이프", 안내 버튼, 팝오버 문구 |
| 추가 정보 완성 비용 | `additional-info-cost.png` | 마크 + "250 이프" |

- 검증 계정은 채팅·이미지·제작 체험을 모두 소진한 상태라 **취소선 + 적용가 상태와 안내 버튼 미표시 상태는 기기에서 찍지 못했습니다.** 그 분기는 `ChatTurnCostTest`와 `CreditAmountText` Preview로만 확인했습니다. 다크 테마·큰 글자는 수동 실행하지 않았습니다.
- 로그아웃 정리: `TrialsRepositoryImpl.clearUserData()`가 메모리 상태를 비우며 `UserScopedStore` 집합에 등록했습니다. 두 계정 회귀는 실행하지 않았습니다.

## 복구

저장 스키마·서버 변경 없음. 되돌리려면 이 브랜치의 UI diff를 복원하면 됩니다.
