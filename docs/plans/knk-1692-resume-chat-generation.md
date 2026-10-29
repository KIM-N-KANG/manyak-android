# KNK-1692 채팅 응답 생성 중 나갔다 와도 이어서 받기

## 목표와 기준

[KNK-1692](https://kimandkang.atlassian.net/browse/KNK-1692)는 상위 [KNK-1690](https://kimandkang.atlassian.net/browse/KNK-1690)의 Android 작업입니다. 서버가 연결이 끊겨도 생성을 계속하고 채팅마다 생성을 하나만 돌리게 되어(KNK-1677, KNK-1678), 채팅방이 끊긴 생성에 다시 붙고 나갔다 들어와도 생성 중 화면을 보이며 이어 받게 합니다. 웹은 [KNK-1691](https://kimandkang.atlassian.net/browse/KNK-1691)에서 같은 계약으로 먼저 구현했습니다.

정본은 [생성 이어받기](../../../knk-harness/docs/domain/CHAT/CLIENT/2-2-REQUIREMENTS.md#2-2-3-생성-실패와-복구)와 채팅 예외 처리 표(KNK-1691 하네스 브랜치), [채팅 백엔드 계약](../../../knk-harness/docs/domain/CHAT/BACKEND/4-2-CONTRACT.md) 4-2-4, 4-2-6, 4-2-7, 4-2-8, 4-2-13, [Android spec](../../../knk-harness/docs/domain/CHAT/CLIENT/ANDROID/AGENTS.md), [Android design](../../../knk-harness/docs/client/ANDROID/AGENTS.md)입니다. 결정 이유는 [A-078](../../../knk-harness/docs/domain/CHAT/CLIENT/ANDROID/DECISIONS.md#a-078)에 있습니다. 웹 참고 구현은 `manyak-web`의 `follow-chat-generation.ts`, `use-chat-stream.ts`입니다.

## 확정한 선택

- 409는 응답 본문의 `code`로 나누지 않습니다. 전송이면 보낸 입력을 되돌리고, 상세를 다시 읽어 `generatingTurn`이 있으면 이어받고 없으면 최신 턴을 보이며 안내는 띄우지 않습니다. `CHAT_TURN_IN_PROGRESS`, `CHAT_TURN_STALE`, 재생성의 `CHAT_ENDED`가 모두 이 판정으로 계약대로 갈리고 웹도 같습니다.
- `error` 사건의 `code`는 쓰는 곳이 없어 파싱하지 않습니다.
- 재연결은 웹과 같은 4번, 실패 간격 1, 2, 4초입니다.
- 앱이 백그라운드로 가면(구성 변경 제외) 수신을 끊고, 돌아오면 상세로 이어받거나 저장된 턴을 보입니다. 백그라운드에서 재연결이 헛돌아 돌아온 화면에 실패 안내가 뜨고 새 턴이 보이지 않던 문제를 막습니다. 포그라운드 서비스로 연결을 붙잡는 방식은 쓰지 않습니다.

## 구현

- 데이터: `ChatTurnStreamRequestDto.lastTurnId`(`JsonElement`, 첫 턴은 `JsonNull`), `ChatDetailResponseDto.generatingTurn`, `ChatStreamEvent.Started(generationId)`, `ChatSseSource.resumeGeneration`(GET), `ChatRepository.resumeGeneration`. 연결이 열린 뒤의 실패는 `Interrupted`로 올립니다.
- 루프: `room/presentation/ChatGenerationFollow.kt`의 `followGeneration`이 연결 하나를 종료 사건까지 읽고, 끊기면 생성 ID로 다시 붙습니다. `started` 전 실패는 `Rejected`, 뒤 실패는 `Failed`, 시도를 다 쓰면 `Lost`입니다.
- 화면: `ChatRoomViewModel.follow`가 결과마다 상세를 다시 읽어 확정하고(`completeTurn`, `failTurn`, `settleRejected`, `settleLost`), 다른 생성이 진행 중이면 같은 작업 안에서 이어받습니다. 진입 때 `generatingTurn`이 있으면 바로 이어받고, 없으면 마지막 턴의 선택지를 만듭니다. `GenerationStarted`가 재생성과 이어받기의 진행 블록을 열고, `StreamRestarted`가 다시 붙을 때 조각을 비웁니다. `ChatRoomForegroundEffects`가 Activity `ON_STOP`, `ON_START`를 `Backgrounded`, `Foregrounded`로 올립니다.

## 검증 결과

| 범위 | 방법 | 결과 |
| --- | --- | --- |
| SSE와 요청 본문 | `ChatSseEventMapperTest`(`generationId`), `ChatSseSourceTest`(첫 턴 `lastTurnId: null`, 재연결 GET 경로, 열린 뒤 끊김은 `Interrupted`) | 통과 |
| 이어받기 | `ChatRoomResumeTest` 12건. 백그라운드에서 수신 멈춤과 복귀 뒤 이어받기, 복귀 뒤 저장된 턴 표시, 받는 중이 아닐 때 무동작. `lastTurnId`, 끊김 뒤 같은 생성 재연결과 본문 재구성, 재연결 실패와 상세 조회 실패 뒤 다시 찾기(CHAT-SEND-21), 새 턴 저장 시 안내 없는 완료(CHAT-SEND-08), 시도를 다 쓴 생성 미추적(CHAT-SEND-24), 이어쓰기와 재생성 재진입(CHAT-SEND-20, CHAT-REGEN-09), 409 입력 되돌림과 이어받기(CHAT-SEND-22), 503 실패 안내(CHAT-SEND-23) | 통과 |
| 기존 동작 | `ChatRoomStreamTest`, `ChatRoomRegenerateTest`(error 사건 뒤 상세 재조회, 409 무안내), `ChatRoomSuggestionTest`(진입 선택지 생성, CHAT-INPUT-22) 등 `:chat` 단위 테스트 209건 | 통과 |
| 정적 검사 | `:chat:ktlintCheck`, `:chat:detekt` | 통과 |
| 기기 재진입 | 에뮬레이터(dev 서버)에서 추천 입력 랜덤 전송 2초 뒤 채팅방을 나갔다가 다시 들어옴. 보낸 입력 버블과 "다음 내용 준비 중" 로딩, 이어서 쌓이는 본문, 전송 버튼 스피너(잠금)를 보인 뒤 완료되어 인물 이미지가 든 확정 턴과 풀린 입력, 실패 안내 없음 | 통과 |
| 기기 백그라운드 | 에뮬레이터(dev 서버)에서 보낸 지 2초 뒤 홈으로 나감. 3초 뒤 돌아오면 상세를 다시 읽어 받던 생성을 이어받아 완료, 40초 뒤 돌아오면 상세 한 번으로 저장된 턴과 선택지를 보이고 실패 안내 없음. 받는 중 가로, 세로 회전은 상세 재조회 없이 그대로 이어 받음(OkHttp 로그로 확인) | 통과 |

## 배포 조건

서버 KNK-1677, KNK-1678은 dev에만 있고 운영 서버는 끊기면 생성을 취소합니다. 출시는 서버 운영 배포 뒤입니다. 운영 서버는 `started`에 생성 ID를 싣지 않고 상세에 `generatingTurn`이 없어, 순서가 어긋나도 끊긴 생성은 재연결 없이 상세로 판단해 끝나고 모르는 `lastTurnId`는 Spring 기본 설정(`FAIL_ON_UNKNOWN_PROPERTIES=false`)으로 무시될 것으로 봅니다(운영 설정은 확인하지 않았습니다).
