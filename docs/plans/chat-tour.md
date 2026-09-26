# 채팅 첫 진입 안내 투어와 투어 분석 이벤트

- 티켓: [KNK-1431](https://kimandkang.atlassian.net/browse/KNK-1431) (상위 KNK-1428)
- 작업일: 2026-09-26
- 브랜치: Android `feat/KNK-1431-add-chat-tour`, 하네스 `docs/KNK-1431-add-chat-tour`
- 분기 기준: fetch한 `origin/dev`. Android `746af162`, 하네스 `3fd9b0f`(KNK-1434 병합 뒤로 다시 올림)
- 계약: [공통 첫 진입 안내](../../../knk-harness/docs/spec/3-1-client-spec.md#첫-진입-안내), [분석 §6-4-2-6](../../../knk-harness/docs/spec/6-analytics.md), [Android 채팅 목록과 채팅방](../../../knk-harness/docs/spec/3-3-android-spec.md#채팅-목록과-채팅방), 결정 [A-048](../../../knk-harness/docs/adr/1-3-android-adr.md#a-048)
- 참고: 웹 `src/features/chats/room/components/tour/`, `hooks/use-chat-tour.ts`, `utils/chat-tour-gate.ts`, `utils/tour-geometry.ts`

## 변경

1. `ChatRoomViewModel`이 노출을 판정합니다. 상세를 읽어 턴이 0개이고 기기에 열람 기록(`chat_tour_seen`)이 없으면 200ms 뒤 스트리밍 중이 아니고 여전히 턴 0개인지 다시 보고 엽니다. 여는 순간 `client_chat_tour_shown`을 보내고 기록을 남기며, 그 사이 전송이 나갔으면 기록 없이 건너뜁니다(다음 새 채팅에서 노출).
2. 지금 스텝(`tourStep`)은 ViewModel 상태입니다. 화면이 대상이 그려진 스텝을 골라 `TourStepShown`으로 올리면 `client_chat_tourStep_viewed`(0부터 센 `step_number`, `step_id`)를 보내고, 완료·건너뛰기는 `client_chat_tour_completed`·`client_chat_tourSkipButton_clicked`를 보낸 뒤 닫습니다. 닫힌 뒤 늦게 온 조작은 버립니다.
3. 스텝은 웹과 같은 3개이고 첫 스텝만 모드에 따라 갈립니다(블럭 `add-blocks`는 상황·대사 추가 두 버튼, 일반 `add-emphasis`는 상황 추가). 문구도 웹과 같습니다.
4. `ComposerToolbar`의 네 버튼이 `chatTourTarget`으로 `boundsInRoot`를 `ChatTourTargets`에 올립니다. 버튼이 다시 배치될 때마다 갱신돼 추천 입력 등장으로 컴포저가 움직여도 하이라이트가 따라갑니다(웹의 1초 재측정 대신).
5. `ChatTourOverlay`는 채팅방 컴포지션 맨 위 상자입니다. 딤은 대상 영역을 `spacing.dense`만큼 넓혀 `shapes.control` 모양으로 뚫고 스텝이 바뀌면 구멍이 옮겨 갑니다. 카드는 아래 공간이 모자라면 위에 두고, 가로 배치는 웹 `resolveTourCardLeft`를 그대로 옮겼습니다. 넓은 쪽 남은 높이를 최대 높이로 주고 넘치면 카드 안에서 스크롤합니다.
6. 딤 위 탭은 뒤 화면에 닿지 않고, 뒤 화면은 보조기술에서도 가립니다. 시스템 뒤로가기는 건너뛰기와 같게 닫습니다(A-048). 투어 중에는 숨겨 둔 헤더도 보입니다.
7. 토큰 `colors.tourScrim`(검정 50%)·`sizes.tourCardWidth`(288dp)·`sizes.tourStepDot`(6dp)을 더하고 `DESIGN.md`에 `chat-tour-card`를 적었습니다. 그림자는 앱 규칙대로 두지 않았습니다.
8. 하네스 Android spec·design·분석 적용 범위(앱 비적용 18 → 14)를 현행화하고 A-048을 추가했습니다.

웹과 다른 점: 게스트 동의 시트 조건은 앱이 로그인 필수라 없습니다. 웹은 대상 존재만으로 "다음/완료"를 정하지만 앱은 화면 안에 있는지까지 봅니다.

## 검증

```bash
./gradlew --continue \
  :analytics:ktlintCheck :analytics:testDebugUnitTest \
  :designsystem:ktlintCheck :designsystem:detekt \
  :chat:ktlintCheck :chat:detekt :chat:testDebugUnitTest :chat:lintDebug
./gradlew installDebug
```

- 모두 통과했습니다. `ChatRoomTourTest`(노출 판정·기록·전송 경합·이벤트·늦은 조작, 5건), `ChatTourStepsTest`(모드별 스텝·합집합·건너뛰기·카드 위치, 5건), `AnalyticsEventContractTest`의 투어 4종이 새로 들어갔습니다.
- emulator-5554(dev 서버, 일반 입력 모드)에서 `adb exec-out screencap`으로 확인했습니다. 새 채팅 진입 직후 1단계 "상황 추가"가 떴고, 다음으로 채팅 설정, 랜덤 전송(버튼 "완료")까지 진행했습니다. 3단계에서 가로로 돌려도 같은 스텝이 유지되고 하이라이트가 전송 버튼을 따라갔습니다. 완료 뒤 같은 스토리로 새 채팅을 하나 더 열었을 때 투어가 다시 뜨지 않았습니다.
- 확인 중 dev 서버에 턴 0개 채팅 2개가 생겼습니다(「그건 반칙이지 말입니다」).
- 블럭 모드의 두 버튼 합집합 하이라이트, 뒤로가기 건너뛰기, 다크 테마, 큰 글자에서의 카드 스크롤은 기기에서 보지 않았고 코드와 유닛 테스트로만 판단했습니다. 열람 기록을 지우려면 기기 DataStore(`device_id`와 같은 파일)를 건드려야 해 다시 띄우지 않았습니다.
- 캡처는 `/captures` 규칙상 Git에 포함하지 않습니다.

| 화면 | 캡처 |
| --- | --- |
| 스텝 | [1 상황 추가](../../captures/knk-1431/step1-plain.png) · [2 채팅 설정](../../captures/knk-1431/step2-plain.png) · [3 랜덤 전송](../../captures/knk-1431/step3-plain.png) |
| 구성 변경 | [3단계에서 가로 회전](../../captures/knk-1431/step3-landscape.png) |
| 재노출 없음 | [완료 뒤 새 채팅](../../captures/knk-1431/reentry-no-tour.png) |

## 복구

기기 설정 키 `chat_tour_seen` 하나를 더했고 서버 변경은 없습니다. 되돌릴 때는 이 변경을 revert하며, 남은 키는 읽는 곳이 없어 무해합니다.
