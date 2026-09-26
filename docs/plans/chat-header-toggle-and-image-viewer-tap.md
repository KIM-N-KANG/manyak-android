# 채팅 헤더 탭 전환과 이미지 뷰어 그림 탭 비닫힘

- 티켓: [KNK-1432](https://kimandkang.atlassian.net/browse/KNK-1432) (상위 KNK-1428)
- 작업일: 2026-09-26
- 브랜치: Android `feat/KNK-1432-chat-header-tap-toggle-and-image-viewer-tap`, 하네스 `docs/KNK-1432-chat-header-tap-toggle`
- 분기 기준: fetch한 `origin/dev` — Android `2626910c`, 하네스 `6e6d091`
- 계약: [공통 채팅 화면 헤더 show/hide](../../../knk-harness/docs/spec/3-1-client-spec.md), [FE-SCREEN-003 썸네일 이미지 뷰어](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-003-스토리-상세), [Android 채팅 목록과 채팅방](../../../knk-harness/docs/spec/3-3-android-spec.md#채팅-목록과-채팅방)
- 참고: 웹 KNK-1427(manyak-web #211), 웹 `src/features/chats/room/utils/header-toggle-tap.ts`, `src/lib/contained-image.ts`

## 변경

1. `ChatRoomScreen`의 헤더를 `Column` 위쪽 자리에서 목록 위에 겹친 `AnimatedVisibility`로 옮겼습니다. 웹과 같이 200ms(`motion.elementEnterMillis`) 감속 곡선 페이드로 숨기고 보입니다. 숨김 여부는 `rememberSaveable`로 구성 변경에서 유지하고, 로딩·실패 상태에서는 항상 보입니다. 숨긴 헤더는 컴포지션에서 빠져 보조기술·포커스 대상에서도 제외됩니다.
2. `ChatTranscript`가 헤더 높이(`TopAppBarExpandedHeight`)만큼 목록 위 여백을 두고, 자식이 떼는 이벤트나 이동을 소비하지 않은 탭만 헤더 전환으로 올립니다. 버튼·선택지·인물 이미지(`clickable`)와 스크롤은 여기까지 오지 않습니다. 탭을 시작할 때 IME가 떠 있었으면 키보드를 닫으려는 탭으로 보고 넘깁니다. 앱 채팅에는 투어·텍스트 선택이 없어 해당 제외 조건은 두지 않았습니다.
3. 스트리밍 앵커의 패드 높이를 위 여백을 뺀 콘텐츠 시작점부터 뷰포트 끝까지(`viewportEndOffset`)로 잽니다. 전체 뷰포트로 재면 헤더 높이만큼 패드가 남습니다.
4. `FullscreenImageViewer`는 Coil이 알려 준 원본 크기로 Fit 그림 영역을 계산하고, 현재 확대·이동을 되돌린 좌표가 그 안이면 닫지 않습니다(`isOnFittedImage`). 여백 배경 탭·닫기 버튼·뒤로가기로만 닫히고 더블 탭·핀치·이동은 그대로입니다. 원본 크기를 알기 전에는 그림이 없는 것으로 보고 닫습니다. 스토리 상세 썸네일 뷰어에도 같이 적용됩니다.
5. 뷰어의 배경 탭 닫힘이 약 300ms 늦던 문제를 고쳤습니다. `detectTapGestures`에 `onDoubleTap`을 함께 주면 모든 한 번 탭이 더블 탭 대기 시간(`doubleTapTimeoutMillis`)을 채운 뒤에야 `onTap`으로 옵니다. 웹처럼 배경 탭은 떼는 즉시 닫고, 두 번째 탭 대기는 그림 위 탭에만 겁니다. 두 번째 탭이 배경이면 닫습니다.
6. 대화 맨 위 AI 생성 안내 문구의 위 여백(`spacing.passage` 20dp)을 없앴습니다. 웹처럼 목록 위 여백(헤더 높이)이 끝나는 자리에서 바로 시작합니다. 헤더 높이는 앱의 다른 헤더와 같은 M3 기본 64dp로 두어 웹(56px)보다 4dp 아래에서 시작합니다.
7. 하네스 Android spec의 "채팅 헤더는 고정" 문구와 design을 현행화했습니다.

## 검증

```bash
./gradlew --continue \
  :designsystem:ktlintCheck :designsystem:detekt :designsystem:testDebugUnitTest \
  :chat:ktlintCheck :chat:detekt :chat:testDebugUnitTest :chat:lintDebug
./gradlew installDebug
```

- 모두 통과했습니다. 그림 영역 판정(여백·확대·이동·원본 크기 미상)은 `FullscreenImageViewerTest`가 고정합니다.
- emulator-5554(dev 서버)에서 `adb exec-out screencap`으로 확인했습니다. 본문 탭마다 헤더가 숨고 다시 보였고, 숨기고 보일 때 본문 위치가 그대로였습니다. 맨 아래로 버튼·추천 "입력창에 넣어 수정"·인물 이미지 탭으로는 헤더가 바뀌지 않았습니다. 뷰어는 그림 위 탭으로 닫히지 않고 위쪽 여백 탭으로 닫혔습니다.
- 배경 탭 닫힘은 `show_touches`를 켜고 `screenrecord`로 찍어 프레임별 밝기를 쟀습니다. 탭 뒤 첫 프레임에서 이미 뷰어가 사라져 있었습니다(더블 탭 대기 없음).
- 확인 중 추천 선택지를 잘못 눌러 dev 채팅방에 턴이 하나 전송됐습니다(무료 체험 적용가 0 이프). 그 뒤 직접 조작하지 않은 사이 헤더가 한 번 숨은 상태로 바뀌어 있었으나 다시 재현하지 못했습니다. 버튼·수정 버튼 탭으로는 재현되지 않았습니다.
- 페이드 중간 프레임, 다크 테마, 키보드가 떠 있을 때의 탭 제외는 화면으로 확인하지 않았고 코드로만 판단했습니다.
- 캡처는 `/captures` 규칙상 Git에 포함하지 않습니다.

| 화면 | 캡처 |
| --- | --- |
| 헤더 | [진입 시 표시](../../captures/knk-1432/header-shown.png) · [숨긴 상태에서 본문 탭 → 표시](../../captures/knk-1432/header-shown-again.png) · [다시 본문 탭 → 숨김, 본문 위치 그대로](../../captures/knk-1432/header-hidden.png) |
| AI 생성 안내 | [헤더 바로 아래](../../captures/knk-1432/ai-notice-top.png) |
| 뷰어 그림 탭 → 여백 탭 | [그림 탭 후 유지](../../captures/knk-1432/viewer-image-tap.png) · [여백 탭 후 닫힘](../../captures/knk-1432/viewer-background-tap.png) |

## 복구

UI 동작만 바꿨고 저장 데이터·서버 변경은 없습니다. 되돌릴 때는 이 변경을 revert합니다.
