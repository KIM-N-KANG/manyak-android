# 제작 초안 카드·다중 완성 진행 계획

- 티켓: [KNK-1222](https://kimandkang.atlassian.net/browse/KNK-1222)
- 작성일: 2026-09-08
- 상태: 구현·에뮬레이터 검증 완료(2026-09-08). 실패 카드 문구·동작과 완료 분석 이벤트는 잠정 구현
- Android: `feat/KNK-1222-studio-creation-cards` — fetch한 `origin/dev`의 `de51b360`
- 하네스: `docs/KNK-1222-studio-creation-cards` — fetch한 `origin/dev`의 `24b2d0b`
- 제품 정본: [하네스 변경 스펙](../../../knk-harness/docs/product-specs/3-3-android-app.md#knk-1222-제작-카드와-다중-완성-진행)

## 목표와 범위

제작 탭에서 편집 중 초안 1개와 완성 중 요청 여러 개를 내 스토리 가로 카드 형태로 표시합니다. 완성 버튼을 누르면 제작 탭으로 돌아와 새 스토리를 만들 수 있습니다. 문구·카드 상태·사용자 동작의 정본은 하네스에만 둡니다.

Android의 간편 제작·제작 목록·해당 저장 및 복구 흐름을 변경합니다. 웹 UI, 새 서버 API, 일반 제작, 새 알림 권한·설정, 상시 폴링, 별도 백그라운드 서비스는 범위에 넣지 않습니다.

## 구현 결과 (2026-09-08)

| 영역 | 구현 |
| --- | --- |
| 저장 | `ManyakDatabase` v2 — 편집 슬롯 `pending_story_creation` 유지 + `story_completion_request`(requestId PK, 명령·생성 결과·진행 JSON, `submittedAt`, `status`, `storyId`, `storyTitle`). `MIGRATION_1_2`가 v1 `STORY_COMPLETION` 행을 해석해 PENDING 요청 행으로 옮기고 원본 삭제, 해석 불가 행은 원본 유지. `fallbackToDestructiveMigration` 제거. 제출은 `StoryCompletionRequestDao.submit`(같은 `generation` JSON을 가진 초안만 삭제 + 요청 삽입) 한 트랜잭션 |
| 실행자 | `create/data/completion/StoryCompletionExecutor` — `@ApplicationScope` + `SessionGate.withAuthWork/commit`. requestId별 in-flight 합류만 하고 요청끼리 직렬화하지 않음. POST 성공→COMPLETED, Network·401/403→미확정, 그 외 상태 코드(409 포함)→복구 GET 판정, GET 404만 FAILED. 새로고침은 미확정 요청을 GET으로 조회하고 404면 같은 명령 재전송. `:create`가 `:auth`에 의존(features→infrastructure 허용) |
| 경계 | `:common` `CreationProgressAccess` 확장 — `progress`(초안), `completionRequests`, `discard`(초안만), `refreshCompletionRequests`, `retryCompletionRequest`, `deleteCompletionRequest`. `CreationStage.STORY_COMPLETION` 제거, `CompletionRequestSummary/Status` 추가 |
| 퍼널 | `StorylineGenerationStore.submitCompletion` — 임시 저장과 같은 `persistenceMutex` 안에서 제출, 성공 시 스토어 초기화, 실패 시 편집 유지·`lastCompletionCommand` 승계. `CreateAdditionalInfoViewModel`은 검증·제출만(채팅 생성·폴링·완성 로딩 제거), `ReturnToStudioAfterSubmission` 효과를 앱이 `popToMainTabs()` + 제작 탭 선택으로 처리 |
| 제작 탭 | `CreationProgressCard`(Draft/Completing/Completed/Failed) — 회색 표지 + `ic_manyak_symbol`(`textDisabled`), 제목 `textSubtle`, 40dp `sizes.controlCompact` 버튼(터치 48dp는 M3 기본 유지), 완성 중은 스피너 + 접근성 문구. 목록 키 `draft`/`completion:<id>`/`story:<id>`. 로컬 카드가 있으면 로딩·실패·빈 목록 대신 목록으로 그려 새로고침 유지, 목록 실패는 목록 끝 재시도 항목. 완료 요청은 목록에 같은 storyId가 있으면 행 삭제, 없으면 목록 1회 재조회 |
| 정리 | (2026-09-08 사용자 결정으로 변경) 두 스토어를 `UserScopedStoreModule`에서 빼고 DB v3 `ownerId` 컬럼으로 회원별 격리. 스토어가 `UserProfileRepository.profile.id`의 행만 읽고 쓰며, 실행자가 로그인 회원 변경 시 소유자 없는 이전 버전 행을 `claimUnowned`로 넘겨받음. 한계: 편집 슬롯은 기기당 한 행이라 다른 회원의 새 초안이 이전 초안을 덮음, 탈퇴 회원 행은 숨은 채 남음 |
| 분석 | `client_storyCreate_completed`는 `chat_id` 필수라 수집 중단(가짜 ID 금지). `storyCompletion_requested`·`completeError_shown(story)` 유지 |

### 잠정 구현(확인 필요)

- 실패 카드: 제목 "스토리를 완성하지 못했어요", 소개 "다시 시도하거나 삭제할 수 있어요", "다시 시도하기"(같은 requestId), 더보기 "삭제하기". 편집 복귀는 두지 않음. 402도 이 카드로 합류.
- 완료 분석 이벤트: `chat_id` 선택화 합의 전까지 미수집.
- 초안 카드 소개(2026-09-08 사용자 결정): "임시 저장한 내용부터 이어서 만들 수 있어요". 심벌 색은 `textDisabled`, 카드 글 영역 위 패딩은 두 카드 모두 제거.

### 검증 기록

- 로컬: `:common`·`:create`·`:studio`·`:designsystem`·`:app` ktlint·detekt, `:create`·`:studio`·`:common` 유닛 테스트(114건), `checkModuleArchitecture`, `:app:compileDebugKotlin`, `installDebug`.
- 에뮬레이터(API 37, dev 서버): 변경 전 APK로 비행기 모드 완성 → v1 `STORY_COMPLETION` 행 생성 → 새 빌드 설치 → `story_completion_request` PENDING 이동·완성 중 카드 → 온라인 새로고침 → GET 404 재전송 → 실제 카드 전환. 초안+완성 중+완성 스토리 동시 표시, 완성 중 2건 동시(오프라인 제출 2건 → 온라인 새로고침에서 동시 전송·각각 완료), 가로 회전·`force-stop` 재실행 후 카드 유지, 목록 실패 상태에서 카드·새로고침 유지, 새로 만들기 확인이 초안만 폐기, 초안 더보기 삭제 확인/삭제, 로그아웃 후에도 행이 남고 같은 계정 재로그인 시 완성 중 카드 유지(계정별 보존 검증은 아래 추가 기록), 완성 푸시 수신·탭 상세 진입, 다크 모드.
- 계정별 보존(2026-09-09 에뮬레이터): v2→v3 업그레이드 후 오프라인 제출 → 로그아웃 → 요청 행이 `ownerId`와 함께 남음 → 같은 Google 계정 재로그인 → 완성 중 카드 유지 → 온라인 새로고침에서 재전송·실제 카드 전환 확인. 로그아웃 상태에서는 두 저장소가 비어 보임(유닛 테스트).
- 미검증: 확정 실패(402/FAILED) 카드 실제 노출, 영속 실패 토스트(유닛 테스트만), 다른 계정으로 로그인 후 늦은 응답(게이트 유닛 테스트로 대체).

## 확인한 현재 흐름

| 위치 | 현재 동작 | 변경 이유 |
| --- | --- | --- |
| `studio/.../StudioScreen.kt`, `component/StudioCreationEntry.kt` | 배너 1개. 완성 스토리가 없는 상태는 별도 `StoriesStatus` 경로 | 초안·진행 요청만 있어도 스크롤 및 당겨서 새로고침 필요 |
| `studio/.../StudioViewModel.kt` | `pendingBanner` 1개, 새 제작에서 `CreationProgressAccess.discard()` 호출 | 완성 중 요청을 새 제작 때문에 지우면 안 됨. 기존 discard 결과도 확인해야 함 |
| `common/.../story/CreationProgressAccess.kt`, `CreationProgressSummary.kt` | 단일 진행 요약 및 폐기 계약 | 목록 요약·초안만 폐기·요청별 갱신을 기존 기능 간 경계로 전달 |
| `create/.../PendingStoryCreationRoomStore.kt`, `PendingStoryCreationEntity.kt` | 모든 단계를 Room `id=0`에 덮어씀, `clear()`는 전체 삭제 | 1개 편집 슬롯과 여러 완성 요청 분리 필요 |
| `create/.../StorylineGenerationStore.kt` | ActivityRetained 편집 상태, `beginCompletion()`이 저장 성공 여부를 호출자에게 반환하지 않음 | 영속화 실패 후 전송·화면 이동 금지. 제출한 A의 늦은 저장이 새 초안 B를 덮으면 안 됨 |
| `create/.../CreateAdditionalInfoViewModel.kt`, `CreateAdditionalInfoScreen.kt` | ViewModel에서 완성 POST, 화면 STARTED에서 복구 폴링. 성공 후 채팅 생성·진입 | 화면 제거 후에도 완성 요청 지속, 제작 탭에서 복구, 자동 채팅 제거 |
| `app/.../root/ManyakApp.kt`, `MainTabs.kt` | 퍼널·메인 탭 조립 | 퍼널 백스택 제거와 제작 탭 선택을 앱에서 처리 |
| `analytics/.../AnalyticsEvent.kt` | `StoryCreateCompleted`가 필수 `chatId` 요구 | 자동 채팅 제거에 맞는 분석 계약 정리 필요 |

현재 모듈 경계는 하네스 `android-module-architecture.md`를 따릅니다. 해당 문서의 오래된 미병합 안내와 별개로 현재 파일이 fetch한 `origin/dev`에 들어 있음을 확인했습니다.

## API 근거와 한계

2026-09-08 [dev Swagger](https://dev-api.manyak.app/swagger-ui/index.html)와 `../manyak-server` 구현을 확인했습니다.

- `POST /api/v1/stories/simple`: 성공 `201`, 오류 `400/402/404/409/502`. 완성 결과를 반환하는 기존 요청이며 접수 전용 `202` API가 아닙니다.
- `GET /api/v1/stories/simple/creation-requests/{requestId}`: `stage`, `status=PENDING|COMPLETED|FAILED`, nullable `result`. `404`는 요청 없음 또는 요청자 소유 아님입니다. 응답에 상세 실패 사유가 있다고 가정하지 않습니다.
- `GET /api/v1/users/me/stories`: 완성된 내 스토리 목록. 요청의 존재를 목록 누락만으로 판정하지 않습니다.
- 서버 `StoryCreationRequestService`와 `SimpleStoryCreationService`는 requestId 기반 COMPLETED replay, PENDING 충돌, FAILED 재시도를 처리합니다. 409 전체를 성공이나 처리 중으로 단정하지 않고 복구 GET 결과를 사용합니다.
- 진행 요청 전체 조회 API를 새로 가정하지 않습니다. 이 기기에서 시작한 요청 식별자는 로컬에서 보존합니다. 앱 삭제·데이터 초기화 후 다른 기기의 진행 요청까지 복원하는 기능은 범위 밖입니다.
- 실제 서버 동시 완성 허용 및 두 요청의 독립 결과·이프 차감은 구현 검증 단계에서 확인합니다. 정적 계약 조회를 동시 실행 검증으로 보고하지 않습니다.

## 구현 설계안

### 1. 편집 슬롯과 완성 요청 분리

기존 `pending_story_creation`을 편집 슬롯으로 유지하고, 같은 DB에 requestId를 기본 키로 하는 완성 요청 테이블 하나를 추가하는 안을 우선합니다. 기존 구조를 전부 다중 행으로 바꾸는 것보다 키워드·스토리라인 복원 변경을 줄일 수 있습니다. 정확한 Entity 필드·DDL은 구현 시 확정합니다.

- 편집 슬롯은 `KEYWORD_DRAFT`, `STORYLINE_GENERATION`, `STORY_DRAFT` 중 하나만 보유합니다. 스토리라인 생성은 편집 퍼널에 속하며 이번 다중 완성 대상과 구분합니다.
- 완성 요청에는 requestId, 제출 명령, 실패 복구에 필요한 입력·생성 결과, 요청 순서용 시각, 로컬 처리 상태, 성공 시 storyId를 보존합니다. 중첩 입력은 기존 JSON 직렬화를 재사용합니다.
- 완성 버튼 처리의 한 트랜잭션에서 요청 행 삽입과 **해당 초안** 제거를 처리합니다. 성공해야 전송을 시작하고 제작 탭으로 이동합니다. 저장 실패 시 편집 내용과 화면을 유지하고 기존 오류 경로로 안내합니다.
- 제출 전에 오래된 임시 저장을 직렬화·무효화합니다. 제출한 퍼널의 입력 미러를 분리한 다음 새 편집을 허용합니다. 완료 콜백은 requestId만 갱신하며 현재 편집 스토어 전체를 reset/clear하지 않습니다.
- 요청 삭제는 ID 단위, 초안 폐기는 초안 전용입니다. 전체 삭제는 로그아웃·탈퇴 정리에서만 호출합니다. 삭제 확인 시 캡처한 초안과 현재 초안이 다르면 새 초안을 지우지 않습니다.
- v1→v2는 명시적 보존 마이그레이션을 작성합니다. 기존 초안·키워드·스토리라인 행은 그대로 보존하고, 기존 `STORY_COMPLETION`은 요청 테이블로 이동합니다. 유효한 명령의 requestId와 모든 복구 입력을 유지합니다. 변환을 검증하기 전 원본을 삭제하지 않습니다.
- 기존 `fallbackToDestructiveMigration`을 이 사용자 입력 저장소의 업그레이드 경로로 사용하지 않습니다. 해석 불가 기존 행도 무조건 버리지 말고 원본 보존 및 복구 불가 안내를 설계합니다. 신규 설치·기존 설치 경로와 exported schema를 함께 검증합니다.

### 2. 요청 실행 수명과 복구

- 완성 전송·결과 반영은 `:create`가 소유하며 기존 `@ApplicationScope`와 `SessionGate.withAuthWork/commit`을 재사용하는 실행자를 둡니다. 네트워크·DB 구현이 common으로 이동하지 않게 합니다.
- `CreateAdditionalInfoViewModel`은 검증·제출 인계만 담당합니다. 서버 완료까지 기다리거나 화면 ViewModel의 취소가 제출된 요청을 취소하게 만들지 않습니다. 새 화면별 스코프·새 모듈·일반 작업 프레임워크는 만들지 않습니다.
- 기존 POST가 장시간 걸리는 동안에도 다른 requestId를 제출할 수 있습니다. 전체 HTTP 요청을 하나의 전역 mutex 안에서 직렬화하지 않습니다. 같은 requestId의 중복 전송·복구만 합류시킵니다.
- 프로세스 종료 시 클라이언트는 멈출 수 있습니다. 재실행/탭 복귀 때 영속 요청을 읽어 먼저 상태를 조회합니다. 서버 접수 전 프로세스가 종료된 요청은 로컬 명령을 사용해 동일 requestId로 전송을 복구합니다. 화면 이동 성공을 서버 접수 성공이라고 표시하지 않습니다.
- 전송 실패/타임아웃은 결과 미확정입니다. 요청을 삭제하거나 바로 새 초안으로 낮추지 않습니다. `404`도 소유권·접수 전 중단·전송 경합 가능성을 구분하며 다른 계정으로 재전송하지 않습니다.
- 로그아웃 시 실행 작업을 취소하고 쓰기 장벽 이후 늦은 결과를 거절합니다. 두 테이블을 기존 `UserScopedStore` 정리에 포함합니다. A 계정 요청이 B 계정의 초안이나 목록으로 되살아나면 실패입니다.
- WorkManager와 상시 폴링은 기본안에서 제외합니다. 앱을 닫은 상태에서 미전송 요청의 자동 제출까지 보장하는 별도 요구가 생길 때만 추가 검토합니다. 기존 서버 완성 작업·푸시는 유지합니다.

### 3. 제작 목록·새로고침

- `CreationProgressAccess`를 최소한으로 확장해 초안 요약, 요청별 표시 상태, 초안 삭제 결과, 요청 새로고침을 전달합니다. `:studio → :create` 직접 의존은 추가하지 않습니다.
- 목록은 초안 → 완성 요청(제출 최신순) → 기존 완성 스토리 순으로 둡니다. 키는 `draft`, `completion:<requestId>`, `story:<storyId>`처럼 충돌을 막습니다. 이 순서는 구현 기본안입니다.
- 기존 `ManyakPullToRefreshBox` 안에서 모든 카드 상태를 표시합니다. 서버 목록이 비어 있거나 실패해도 로컬 카드와 새로 만들기 FAB는 사용할 수 있습니다. 로컬 카드가 있으면 빈 목록 안내를 주 콘텐츠로 겹쳐 그리지 않습니다.
- 수동 새로고침/화면 복귀에서 요청 상태와 목록을 조회합니다. 개별 조회 실패를 격리하고 기존 카드/초안을 유지합니다. 이전 갱신 결과가 더 최신 상태를 덮지 않게 합니다.
- 완료 응답의 storyId를 먼저 보존하고 같은 ID의 일반 카드로 교체합니다. 목록 반영이 늦으면 성공 결과 또는 기존 상세 조회로 실제 데이터를 확보합니다. 정상 카드에 필요한 필드가 없다고 가짜 통계를 저장하지 않습니다.
- 결과 카드가 준비되기 전에 진행 기록을 삭제하지 않습니다. 이미 완료를 확인한 요청을 다시 '완성 중'으로 내리지 않습니다. 목록 갱신 실패로 실제 카드 전환이 늦어지는 경우에도 완료 결과는 유지하며 재조회할 수 있게 합니다.
- 완료 후 현재 화면을 강제로 채팅/상세로 바꾸지 않습니다. 다른 스토리 편집 중 완료된 A는 A의 기록만 변경합니다. 기존 알림 탭의 상세 진입을 유지합니다.

### 4. 카드와 자산

- `MyStoryCard`의 128dp·3:4 표지, 행 패딩, 오른쪽 정보 배치, 더보기 정렬을 참고해 `:studio` 내부 진행 카드를 구현합니다. 가짜 `StorySummary`를 만들어 기존 신고·상세 진입을 재사용하지 않습니다.
- 현재 `ic_logo_manyak.xml`은 캐릭터 심벌 + 워드마크 락업입니다. 파일 안의 **심벌 group**을 재사용 가능한 단독 벡터로 분리해 사용하고, 전체 로고를 넣거나 일반 `ic_image` placeholder로 대체하지 않습니다. 새 그림 생성은 필요하지 않습니다.
- 회색 배경·더 진한 회색 심벌·회색 제목은 테마 시맨틱 토큰으로 지정합니다. 완성 중에는 심벌 대신 표지 중앙에 스피너를 두는 안을 기본으로 합니다.
- 40dp 버튼은 보이는 높이이며 접근성 터치 영역은 기존 최소 기준을 유지합니다. 현재 `sizes.input=40dp`를 버튼 의미로 전용하지 말고 기존 버튼 토큰을 확인한 뒤 필요 시 전용 토큰과 `DESIGN.md`를 함께 갱신합니다.
- 초안 더보기는 삭제 1개와 기존 확인 UI만 사용합니다. 완성 중에는 더보기 자체와 길게 누르기 옵션, 카드 상세/재개 클릭을 비활성화합니다. 완성된 일반 카드의 신고·삭제는 이번 변경 범위에 포함하지 않습니다.
- 소개는 요청된 한 문장으로 표시하되 화면이 좁거나 큰 글자에서는 줄바꿈을 허용합니다. 스피너의 상태를 접근성 서비스에 전달하고 회색만으로 상태를 구별하지 않습니다.

## 구현 전에 확정할 항목 (계획 당시 기록)

아래는 계획 당시의 **제안**입니다. 구현에서 택한 잠정안은 위 "잠정 구현"을 봅니다.

| 항목 | 제안과 필요한 확인 | 막는 단계 |
| --- | --- | --- |
| A 완성 확정 실패 중 B 초안 존재 | A 입력을 실패 요청에 보존하고 스피너를 멈춘 실패 상태를 표시합니다. 같은 명령 재시도는 A의 requestId를 재사용합니다. 편집 복귀는 슬롯이 비어 있을 때만 옮기고, B가 있으면 먼저 B를 완료하거나 명시적으로 삭제하도록 안내합니다. B 자동 덮어쓰기·복수 편집 초안·실패 입력 자동 폐기를 금지합니다. 실패 카드 문구/버튼 노출은 제품 확인 후 확정합니다. | 실패 상태 UI 완료 판정 |
| 완료 분석 이벤트의 필수 chatId | `6-analytics.md`는 `client_storyCreate_completed.chat_id`를 필수로 규정합니다. Android에서 실제 스토리 완료를 수집하되 chat_id를 선택으로 바꾸는 안을 제안합니다. 분석 정본·소비 쿼리와 합의 전 임의 이벤트명·가짜 chatId를 만들지 않습니다. | 완료 분석 계약 변경 |
| 저장 DDL·업그레이드 | 위 보존 설계를 기반으로 실제 schema diff와 v1 변환 예시를 구현 전에 검토합니다. 데이터 폐기나 새 외부 계약이 필요한 변경만 별도로 승인받습니다. | 마이그레이션 적용 |

## 의존 순서와 완료 조건

1. **스펙**: 하네스 변경 절과 미구현 상태를 확인합니다. 위 미결정의 영향 범위를 구분합니다. 완료 조건은 기존 단일 슬롯·완성 후 자동 채팅 규칙과 신규 목표가 명시적으로 구별되는 것입니다.
2. **저장·경계**: 초안/요청 분리, v1 보존, 공통 요약·폐기 경계를 구현합니다. 완료 조건은 요청 A·B와 초안 C를 저장/조회하고 C 삭제 후 A·B가 남는 것입니다.
3. **제출·수명·탭 이동**: 영속화 성공 후 제출 인계·퍼널 제거·제작 탭 선택을 연결합니다. 완료 조건은 완성 POST가 진행 중인데도 새 제작이 열리고 자동 채팅 생성이 발생하지 않는 것입니다.
4. **카드·새로고침**: 단계 2 이후 카드와 요청 조회/병합 작업은 서로 독립 진행 가능합니다. 완료 조건은 하네스 상태표 전부와 완성 중 2개+초안 1개의 동시 표시, 요청별 완료 전환입니다.
5. **실패·분석·정리**: 확정된 실패 UX와 분석 계약을 반영하고 요청 ID/세션 단위 갱신을 검증합니다. 완료 조건은 실패 A·완성 B·편집 C가 서로 훼손하지 않는 것입니다.
6. **검증·문서 마감**: 아래 검증 후 하네스 매트릭스를 실제 결과만큼 갱신하고 기존 단일 슬롯/로딩/자동 채팅 설명을 구현에 맞게 정리합니다.

## 검증 계획

위험 태그: `db-migration`, `state-restore`, `durable-work`, 계정 정리, 내비게이션, UI. 실제 실행 선택은 구현 시 `android-change-verification`에 넘깁니다. 이번 문서 작성에서는 Gradle·에뮬레이터 검증을 실행하지 않습니다.

- 저장 변경 직후: 기존 `PendingStoryCreationRoomStoreTest`, `CreationStorageCompatibilityTest` 등을 확장해 각 v1 stage 보존·완성 행 이동·삽입 실패 롤백·초안 한 개 제약·요청별 삭제를 검증합니다. 실제 v1 DB 업그레이드 테스트도 필요합니다.
- 제출/복구 연결 직후: `CreateAdditionalInfoViewModelTest`, `StorylineGenerationStoreTest`, `StudioViewModelTest`를 변경해 연타 1회 제출, 화면 제거 후 요청 지속, 새 초안 중 이전 완료, 응답 유실→동일 ID 복구, 409/PENDING, 402/FAILED, 조회 부분 실패, 프로세스 재시작, 늦은 저장을 검증합니다.
- 관련 모듈 컴파일·유닛 테스트·`ktlintCheck`·`detekt`를 실행합니다. 기본 대상은 `create`, `studio`, `common`, `app`; 실제 변경 시 `designsystem`, `analytics`, `navigation`을 포함합니다. 전체 `check`/`assembleDebug`는 CI에 맡깁니다.
- UI 연결 후 `installDebug`와 에뮬레이터 `adb exec-out screencap` 전후 비교: 초안 카드, 삭제 확인/실패, 완성 중 1개/2개, 초안+진행 카드 혼재, 완성 스토리 0개에서 pull-to-refresh, A·B 각각 완료, 알림 비허용 새로고침, 뒤로가기 시 제작 탭 유지, 40dp 버튼·큰 글자·회전·다크 모드.
- 통합 시 실제 서로 다른 simpleCreationId 두 건의 완료를 확인합니다. 앱 프로세스 종료 후 복구, 알림 탭 상세, A 로그아웃→B 로그인 후 늦은 응답/푸시 격리도 확인합니다.

## 계획 검토 결과

가장 큰 위험은 **기존 단일 슬롯의 clear/reset과 지연 저장이 다른 요청·새 초안을 지우는 것**입니다. 전 호출부 확인, 원자적 제출, ID별 결과 반영과 실패 입력 보존으로 계획을 보완했습니다.

그 밖에 완성 스토리 0개에서 새로고침이 없는 기존 경로, 영속 실패를 무시하는 `beginCompletion`, 화면 제거에 따른 POST 취소, 업데이트 데이터 소실, 필수 chatId의 계약 충돌을 점검하고 단계별 완료 조건에 포함했습니다. 실제 서버 동시 처리·마이그레이션·화면 동작은 아직 실행 검증하지 않았습니다.
