# 간편 제작 완성 직후 채팅 진입

- 작성일: 2026-08-25
- 근거 정본: 하네스 `3-1-client.md §3-1-4 완성(finalize)`·`§3-1-5 턴 모델`, `4-backend.md §4-3-3 POST /chats`, `3-3-android-app.md §3-3-3`

## 목표와 제외 범위

완성 성공 경로를 3-1 계약대로 교체한다 — `POST /stories/simple` 성공 → `POST /chats {storyId}` → 채팅방
화면 진입. 채팅방 화면(FE-SCREEN-005)이 미착수라 이번 작업이 채팅 기능의 1단계를 함께 연다.

**이번 범위(1단계)** — 채팅 생성·상세 조회 API 배선, 채팅방 목적지·라우트, 채팅방 조회 렌더(제목·프롤로그·
턴 이력 표시), 퍼널 완성 연결, 하네스 기록 갱신.

**제외(후속 단계)** — 2단계: 입력 컴포저·SSE 턴 진행(okhttp-sse)·추천 입력 표시(전송 수단이 없는 동안에는
누를 수 없는 후보만 남아 컴포저와 함께 붙인다). 3단계 이후: 선택지 생성 토글·응답 재생성·
삭제·공유·채팅 목록(FE-SCREEN-004 카드)·첫 진입 투어·이미지·엔딩 표시. 백그라운드 복귀·임시 저장은 §3-3-5
결정과 함께 별도 진행.

## 새로 내린 결정

1. **채팅 기능을 단계로 나눈다.** 완성 직후 진입이 채팅방 목적지·조회 배선에만 걸려 있으므로, FE-SCREEN-005
   전체(SSE·컴포저)를 기다리지 않고 조회 렌더까지를 1단계로 잘라 진입을 먼저 연결한다. 매트릭스에는 005를
   `부분 구현`으로 기록하고 잔여를 명시한다.
2. **`ChatRoomRoute(chatId: String)`** — 라우트에 식별자만 싣는 규칙 그대로. `POST /chats` 응답의
   `prologue`·`suggestedInputs`는 라우트로 넘기지 않고 화면이 `GET /chats/{chatId}`로 다시 얻는다
   (웹의 상세 prefetch 대응 — 앱은 진입 시 조회가 그 역할).
3. **완성 성공 백스택** — 퍼널 단계를 모두 걷어내고 `ChatRoomRoute`를 push해 `[MainTabs, ChatRoom]`을
   만든다. 웹의 채팅 화면 `replace` 대응이며, 뒤로가기는 홈 복귀다. 웹 §3-1-5의 "헤더 back은 /chats"는
   히스토리 없는 웹 진입 대비책이므로 앱은 백스택 pop이 그 역할을 대신한다(진입 경로가 남는다).
4. **완성 재시도 분기(3-1 계약)** — 스토리 완성 성공 후 채팅 생성이 실패하면 ViewModel 이 `storyId`를
   보관하고, 재시도는 스토리 완성을 건너뛰고 채팅 생성만 재호출한다. 실패 문구는 기존 완성 실패
   인라인(GENERAL)을 재사용한다 — 웹도 두 실패를 같은 오류 상태로 묶으며 앱이 새 문구를 만들지 않는다.
5. **`ChatApi`는 인증 클라이언트 기본 타임아웃** — 채팅 생성·상세 조회는 AI 동기 호출이 없는 경로다.
6. **완성 토스트 유지** — "스토리가 완성되었어요" 토스트는 채팅 생성 성공 시점(진입 직전)으로 옮긴다.
   채팅 생성까지가 3-1의 완성 흐름이므로 로딩(`isCompletingStory`)도 채팅 생성이 끝날 때까지 유지한다.

## 외부 합의가 필요한 항목

없음 — 서버 계약(`POST /chats`·`GET /chats/{chatId}`)은 MVP 확정·구현 상태다.

## 구현 순서

1. `:core:domain` — `chat` 패키지: `CreatedChat`·`ChatDetail`(턴 포함) 모델, `ChatRepository`(생성·상세).
2. `:core:data` — `ChatApi`(POST /chats·GET /chats/{chatId})·DTO·`ChatRepositoryImpl`·DI 배선. (1 이후)
3. `:core:navigation` — `ChatRoomRoute(chatId)`. (1·2와 병렬 가능)
4. `:feature:chat` — `ChatRoomViewModel`(조회·로딩·실패·재시도)·`ChatRoomScreen`(조회 렌더). (2·3 이후)
5. `:feature:create` — `CreateAdditionalInfoViewModel`: 완성 성공 → 채팅 생성 → `EnterChat(chatId)` 효과,
   `storyId` 보관·재시도 분기. 화면·`ManyakApp` 배선 교체. (2·3 이후, 4와 병렬 가능)
6. 테스트 — `FakeChatRepository`, 완성→채팅 생성 성공·실패·재시도 멱등 ViewModel 테스트, 채팅방 ViewModel 테스트.
7. 하네스 갱신 — §3-3-3 보류 문단을 구현 기록으로 교체(백스택 결정 포함), 매트릭스 FE-SCREEN-002·005,
   FLOW-002 행 갱신.

## 검증

- 위험 태그: 화면 배선·기존 오류 정책을 따르는 API 추가 — 로컬은 변경 모듈 테스트와 컴파일
  (`:feature:create`·`:feature:chat` test, `assembleDebug`는 CI 소유).
- 수동 확인 시점: 퍼널 완성 → 채팅방 진입 → 뒤로가기 홈 복귀 E2E는 에뮬레이터 수동 검증 항목으로 남긴다
  (기존 퍼널 수동 검증 체크리스트에 추가).
