# 채팅 입력 수정, 포크와 재생성 확인 (KNK-2006)

- 작성일: 2026-11-04
- 티켓: [KNK-2006](https://kimandkang.atlassian.net/browse/KNK-2006) (상위 KNK-2002), 웹 짝 KNK-2005
- 브랜치: Android `feat/KNK-2006-chat-turn-edit-and-fork`(fetch한 `origin/dev` `7ea996a5`에서 분기), 하네스 같은 이름(`origin/feat/KNK-2005-chat-turn-edit-and-fork` `4e3bbce` 기준, 아직 dev 미병합)
- 계약: 하네스 CHAT [공통 요구 2-2-8](../../../knk-harness/docs/domain/CHAT/CLIENT/2-2-REQUIREMENTS.md#2-2-8-입력-수정과-포크), [공통 배치 3-3-7](../../../knk-harness/docs/domain/CHAT/CLIENT/3-3-LAYOUT.md#3-3-7-재생성-입력-수정과-포크), [공통 연동 4-1](../../../knk-harness/docs/domain/CHAT/CLIENT/4-1-CONTRACT.md), 결정 C-002, C-003, C-004. 서버는 BACKEND 4-2-19 `POST /chats/{chatId}/turns/edit/stream`, 4-2-20 `POST /chats/{chatId}/fork`(dev Swagger의 `EditChatRequest`, `ForkChatRequest`, `GeneratingTurnResponse.type`의 `EDIT`과 대조). 분석은 `client_chat_editButton_clicked`, `client_chat_forkButton_clicked`(`chat_id`, `turn_number`). Android 결정 A-091.
- 참고 구현: 웹 `src/features/chats/room/`의 `hooks/use-chat-stream.ts`, `components/chat-room.tsx`, `components/messages/chat-turn-item.tsx`, `chat-turn-editor.tsx`, `chat-messages.tsx`, `constants.ts`(manyak-web `875905b6`, `c1f6a8c0`)

## 변경

1. 데이터: 입력 수정 SSE(`ChatSseSource.editTurn`, 본문 `turnId`, `userInput`, `userSource: typed`, `realtimeImage`)와 포크 API(`ChatApi.forkChat`)를 더했습니다. 포크 201은 `myChatsChanges`로 목록을 갱신합니다. 상세 `generatingTurn`은 `targetTurnId`와 `isEdit`으로 옮기고, 대상 턴이 없는 `EDIT`은 이어쓰기처럼 목록 끝에 그립니다.
2. ViewModel: 재생성과 입력 수정이 대상 턴 ID(`replacingTurnId`, 기존 `regeneratingTurnId`)를 함께 쓰고 입력 수정은 `hidesLaterTurns`로 뒤 턴을 숨깁니다. 편집(`TurnEditing`), 확인 창(`TurnConfirm`의 재생성, 입력 수정, 포크)과 포크 잠금을 상태로 두어 구성 변경에서 남깁니다. 완료면 편집기를 닫고, 실패, 거절과 이프 부족이면 숨긴 턴과 고치던 입력을 그대로 둡니다. 끝내 받지 못하면 대상 턴이 상세에서 사라졌을 때만 완료입니다. 실시간 이미지 안내의 완료 횟수에 입력 수정은 넣지 않습니다.
3. 확인 창: 다시 생성과 입력 수정은 생성 중이 아니고 아는 잔액이 모자라지 않을 때 열고, 모자라면 확인 없이 이프 부족 시트를 엽니다. 클릭 이벤트는 확인 전 버튼 시점에 보냅니다. 포크 성공은 `OpenChat` 효과로 app이 원래 방 위에 채팅방을 쌓고, 효과 뒤 잠금을 풉니다(A-091).
4. 화면: 사용자 입력 배경 안 오른쪽 아래 입력 수정 버튼, AI 응답 아래 마지막 턴의 다시 생성과 모든 턴의 포크(32dp 고스트 아이콘)를 두었습니다. 편집기는 컴포저와 같은 입력칸에 "고칠 입력" 이름, "취소"와 "보내기"이고, 고치는 턴의 응답, 뒤 턴과 추천을 40%로 흐립니다. 편집 중에는 키보드 높이에 맞춘 읽던 위치 유지를 멈추고 IME 높이가 바뀔 때 편집기 전체를 목록 안으로 당깁니다.
5. 공용: 확정 버튼 색만 다른 `ManyakConfirmDialog`를 기존 `ManyakDestructiveDialog`의 틀로 더하고 `DESIGN.md` `overlay`에 규칙을 적었습니다. 사용자가 준 SVG를 `ic_pen`, `ic_split` 벡터 드로어블로 옮겼습니다.
6. 정리: `ChatRegenerate.kt`를 `ChatTurnActions.kt`로, 턴 그리기를 `ChatTurnBlock.kt`로 나눴고 고정 문구 토스트 매핑을 `fixedToastRes`로 분리해 detekt 기준을 지켰습니다.

웹과 다른 점: Android는 로그인 필수라 포크의 게스트 로그인 안내가 없습니다. 확인 창의 머무르는 버튼은 Android 공통 UI 규칙대로 세 창 모두 "닫기"입니다(공통 문구 "그대로 두기" 대신). 포크 확인의 확정 버튼은 사용자 요청으로 "시작하기"입니다(공통 문구 "새 채팅 시작하기" 대신, 2026-11-04). 웹의 Enter, Escape 단축키 대신 키보드 줄바꿈은 줄바꿈이고 보내기는 버튼으로만 합니다. 웹 줄바꿈 변경(W-044, W-045)은 해당하지 않습니다.

## 검증

```bash
./gradlew :chat:ktlintCheck :chat:detekt :designsystem:ktlintCheck :designsystem:detekt \
  :analytics:ktlintCheck :analytics:detekt :app:ktlintCheck :app:detekt \
  :chat:testDebugUnitTest :analytics:testDebugUnitTest :chat:lintDebug :designsystem:lintDebug
./gradlew :app:installDebug
```

- 모두 통과했습니다. 새 테스트는 `ChatRoomEditForkTest` 14건(편집기 열기와 클릭 턴 번호, 같은 입력과 공백, 취소, 뒤 턴 수와 진행 중 숨김, 완료 교체와 편집기 닫기, error, 402, 끊김 뒤 대상 유무 2건, 마지막 턴 확인, `EDIT` 재진입, 포크 성공과 실패, 생성 중 잠금), `ChatRoomRegenerateTest`의 확인 창과 이프 부족 2건, `GeneratingTurnDtoTest` 2건, 분석 계약 1건입니다. 기존 재생성 테스트는 확인 창을 거치도록 고쳤습니다.
- emulator-5554(dev 서버, 회원 계정)에서 `adb exec-out screencap`으로 확인했습니다. 턴 버튼 배치, 입력 수정 버튼, 편집기와 응답 흐림, 같은 입력의 보내기 비활성, 마지막 턴 확인 창(기본색), 닫기 뒤 편집기 복귀와 취소, 재생성 확인 창, 포크 확인 창과 201 뒤 새 방(`turnId` 요청과 `turn_number=2` 이벤트 로그), 포크한 방의 첫 턴 수정 확인 창(위험색, "1턴"), 진행 중 대상 턴만 보이고 뒤 턴 숨김, 완료 뒤 새 턴 하나와 편집기 닫힘, 목록의 포크 채팅 맨 위와 원래 채팅 2턴 유지, 포크한 방에서 뒤로가기로 원래 방 복귀와 포크 버튼 잠금 해제, 다크 모드 전환 뒤 고치던 입력과 초점 유지를 봤습니다.
- 기기에서 처음 편집기를 열었을 때 키보드가 편집기를 화면 위로 밀어내고, 화면 아래쪽 편집기는 버튼이 키보드에 가리는 것을 찾아 위 4번의 처리로 고친 뒤 다시 확인했습니다.
- 입력 수정의 error, 402, 끊김과 `EDIT` 재진입, 포크 실패는 유닛 테스트로만 확인했습니다. 가로 화면은 키보드가 목록 자리를 다 차지해 편집기 유지를 화면으로 확인하지 못했고, TalkBack 낭독도 듣지 않았습니다.
- 확인하며 dev 서버에 포크 채팅 두 개(`0dc9329c…`, `fc449caa…`)를 만들었고 첫 번째는 입력 수정으로 1턴이 됐습니다. 원래 채팅은 바꾸지 않았습니다.
- 캡처는 `/captures` 규칙상 Git에 포함하지 않습니다.

| 화면 | 캡처 |
| --- | --- |
| 턴 버튼 | 보관 경로: `../../captures/knk-2006/room-initial.png`, `../../captures/knk-2006/room-band.png` |
| 편집기 | 보관 경로: `../../captures/knk-2006/editor-open.png`, `../../captures/knk-2006/edit-first-turn.png` |
| 확인 창 | 보관 경로: `../../captures/knk-2006/edit-confirm-last.png`, `../../captures/knk-2006/edit-confirm-later.png`, `../../captures/knk-2006/regenerate-confirm.png`, `../../captures/knk-2006/fork-confirm.png` |
| 입력 수정 진행과 완료 | 보관 경로: `../../captures/knk-2006/edit-streaming.png`, `../../captures/knk-2006/edit-completed.png` |
| 포크 뒤 목록과 복귀 | 보관 경로: `../../captures/knk-2006/chat-list-after-fork.png`, `../../captures/knk-2006/back-to-origin.png` |
| 다크 모드 편집기 | 보관 경로: `../../captures/knk-2006/dark-editor.png` |

## 동의 조회 실패 문구

- 웹 KNK-2005가 바꾼 동의 조회 실패 문구를 사용자 요청으로 Android에도 맞췄습니다. 제목 "약관 동의 내역을 불러오지 못했어요", 설명 "확인이 끝나야 서비스를 이용할 수 있어요", 버튼 "다시 시도하기"이고 하네스 ACCOUNT ANDROID 3-3에 적었습니다. 다른 조회 실패의 "다시 시도"는 그대로입니다.
- emulator-5554에서 비행기 모드로 앱을 새로 열어 시작 화면의 실패 안내를 확인했습니다. 실행 중 시트(로그아웃 포함)는 같은 구성 요소라 따로 재현하지 않았습니다.

## 복구

저장 스키마와 라우트 변경은 없습니다. 서버 v0.14.0 이전으로 되돌린 환경에서는 입력 수정과 포크 요청이 404로 실패 안내만 보입니다. 되돌릴 때는 이 변경을 revert합니다.
