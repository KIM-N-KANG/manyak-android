# KNK-1668 간편 제작 임시 저장의 서버 보관과 새 제작 상한

## 목표와 기준

[KNK-1668](https://kimandkang.atlassian.net/browse/KNK-1668)의 두 번째 작업입니다. 일반 제작([계획](knk-1668-general-draft-server.md))에 이어 회원 간편 제작의 키워드 초안과 스토리라인 초안을 서버 초안(`/users/me/story-drafts`, mode `SIMPLE`)에 보관해 다른 기기와 웹에서 이어서 만들게 하고, 만들고 있는 스토리 카드가 5장이면 새 제작을 막습니다. 웹은 [KNK-1667](https://kimandkang.atlassian.net/browse/KNK-1667)에서 같은 계약으로 먼저 구현했습니다.

정본은 [공통 임시 저장 계약](../../../knk-harness/docs/domain/STORY/CLIENT/4-1-CONTRACT.md#4-1-4-공통-초안-봉투와-버전)과 [새 제작 상한](../../../knk-harness/docs/domain/STORY/CLIENT/2-2-REQUIREMENTS.md#2-2-2-탐색과-제작-목록)(둘 다 KNK-1667 하네스 브랜치), [서버 초안 계약](../../../knk-harness/docs/spec/4-backend-server-spec.md#story-drafts), [Android spec](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/AGENTS.md), [Android design](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/AGENTS.md)입니다. 결정 이유는 [A-077](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/DECISIONS.md#a-077), [A-075](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/DECISIONS.md#a-075)와 [A-076](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/DECISIONS.md#a-076)에 있습니다. API는 2026-10-17에 [dev Swagger JSON](https://dev-api.manyak.app/v3/api-docs)과 서버 `StoryDraftService`로 확인했습니다. 목록에는 `kind: CREATION_REQUEST` 항목이 섞여 오고, 상세의 `generation`은 생성 요청 복구 조회와 같은 `{stage, status, result}`입니다.

## 확정한 선택

설계를 먼저 보이고 사용자 확인을 받았습니다.

- Room v5→v6으로 `pending_story_creation`과 `story_completion_request`에 nullable `serverDraftVersion` 열을 더합니다. null은 기기 안 세션, 0은 서버 초안을 가리키지만 아직 서버에 없는 기록입니다.
- 새 제작 상한은 병합한 초안 카드를 셉니다. 완성 중 카드와 검수 제출본은 세지 않습니다.
- 완성 실패는 웹과 같이 그 초안의 추가 정보 단계 초안으로 되돌리고 실패 카드와 "다시 시도하기"를 없앴습니다(2026-10-17 후속 결정). 되돌린 초안은 상한에서도 셉니다.
- 웹도 단계 이동 자동 저장을 없애 두 플랫폼 모두 임시 저장 버튼과 앱을 떠날 때(Android 백그라운드 전환, 웹 화면 숨김)만 저장합니다. 웹 변경은 KNK-1667 브랜치와 [W-039](../../../knk-harness/docs/domain/STORY/CLIENT/WEB/DECISIONS.md#w-039)에 있습니다.
- 서버 초안을 읽는 진입 대기는 채팅방 진입과 같이 헤더를 두고 그 아래에 늦게 뜨는 가운데 스피너로 보입니다.
- 제작 탭 첫 조회는 웹과 같이 모든 카드 묶음이 준비될 때까지 한 스켈레톤으로 덮습니다.

## 구현

- 진입 판정은 `StorylineGenerationStore.ensureRestored(resume, server)`가 하고 저장 위치와 기준 버전, 처음 저장 시각은 `SimpleDraftSession`이 듭니다. 새 화면도 프로세스 복원을 위해 상세를 조회합니다(일반 제작과 같음).
- payload는 `SimpleDraftPayload`(쓰기)와 `SimpleDraftPayloadReader`(읽기)이고, 서버 저장 규칙과 두 제작 방식이 함께 쓰는 목록은 `StoryDraftRemote`로 옮겼습니다. 일반 제작의 `GeneralDraftSave`는 `StoryDraftSave`로 이름을 바꿔 함께 씁니다.
- 스토리라인 초안의 생성 요청은 화면에 보이는 결과를 만든 요청을 따로 들고 씁니다(다시 만들기가 실패해도 이전 결과의 요청).
- 저장은 버튼과 백그라운드 전환에서만 하고, 쓰는 중에 다시 부르면 앞 저장 뒤에 이어 보냅니다. 충돌은 단계 화면마다 같은 확인 다이얼로그(`DraftConflictDialog`)로 묻습니다.
- 확정된 완성 실패는 `StoryCompletionExecutor`가 `demoteToDraft`로 요청 행 삭제와 초안 쓰기를 한 트랜잭션으로 하고 `completionFailures`로 제작 탭에 알립니다. 이전 버전이 실패로 남긴 행도 새로고침에서 되돌립니다.
- 제작 탭은 `mergeSimpleDrafts`로 서버 카드를 숨기거나 따로 보이고, 따로 보이는 서버 카드는 `CreationProgressSummary.onServer`로 키, 이어서 만들기, 삭제를 구분합니다. 서버 카드로 열면 같은 ID의 기기 안 행을 새 ID로 떼어 냅니다.

## 검증 결과

| 범위 | 방법 | 결과 |
| --- | --- | --- |
| payload 읽기와 쓰기 | `SimpleDraftPayloadTest`. 웹 `toSimpleDraftRequest`와 같은 모양의 고정 JSON, null 선택 필드, 웹이 빼는 생성 요청 필드, 생성 결과 없음, 다른 스키마와 단계, Android 왕복 | 통과 |
| 카드 병합 | `SimpleDraftMergeTest`. 기준 버전 이하만 숨김, 다른 곳의 저장은 따로 보임, 아직 서버에 없는 기록, 완성 요청 연결, 서버 카드의 재개 지점과 설명 | 통과 |
| 스토어 서버 세션 | `StorylineGenerationServerDraftTest`(20건). 키워드 저장, 생성 중과 결과의 버전 연결, 결과를 다음 저장 때 옮기기, 재생성 실패 뒤 저장(STORY-DRAFT-29), 충돌 뒤 덮어쓰기와 기록 정리(32), 저장 이어 보내기(33), 진입 판정 고정, 카드 재개와 찾지 못함, 기기 안 세션 유지(26), 완성 요청의 `draftId`(25), 처음 저장 시각 승계, 따로 보이는 서버 카드 열기 | 통과 |
| 화면 | 키워드, 스토리라인, 추가 정보 ViewModel 기존 테스트를 서버 세션 기준으로 갱신 | 통과 |
| 완성 실패 되돌리기 | `ReturnedDraftTest`(제출한 명령, 화풍, 순번, 서버 초안 연결), `StoryCompletionExecutorTest`(서버가 모르는 요청은 초안으로 되돌리고 알림, 이전 실패 행도 되돌림, 조회 실패 격리), `StudioViewModelTest`(실패 안내) | 통과 |
| 제작 탭 | `StudioDraftLimitTest`(5장 안내, 4장 진입, 첫 조회 한 스켈레톤, 복귀는 덮지 않음, 같은 ID 카드 구분) | 통과 |
| 라우트 | `RouteRestorationTest`. `resume` 없이 저장된 이전 경로 복원 | 통과 |
| Room 마이그레이션 | `GeneralDraftMigrationTest`(기기, v1~v5→v6, 새 열 null), `GeneralDraftStorageTest` 등 `create` 기기 테스트 14건 | 통과 |
| 정적 검사 | 변경 모듈 ktlint, detekt, `checkModuleArchitecture` | 통과 |

`installDebug` 후 Pixel 10 API 37 에뮬레이터에서 dev 서버로 확인했습니다.

- 기존 빌드의 DB가 v6으로 올라가고 기기 안 키워드 초안 2건이 연결 없는 행으로 남았습니다.
- 웹이 만든 서버 간편 제작 키워드 초안이 제작 탭 카드로 보였고, 이어서 만들기에서 상세 200 뒤 웹이 고른 장르가 복원됐습니다.
- 카드가 5장 이상일 때 FAB가 "만들고 있는 스토리는 5개까지 둘 수 있어요"를 띄우고 제작 방식 선택을 열지 않았습니다.
- 키워드 임시 저장이 같은 ID로 PUT 200이었고 기기 안 행은 생기지 않았습니다. 스토리라인 생성 중 행과 생성 결과 행에 서버 버전 2가 붙었고 생성 결과는 PUT하지 않았습니다. 제작 탭은 같은 제작을 기기 안 카드 한 장으로 보였습니다.
- 추가 정보 단계 임시 저장이 `STORY_DRAFT` PUT 200이었고 기기 안 행이 지워졌습니다. 서버 카드는 고른 스토리라인 본문 2줄과 원래 처음 저장 시각으로 보였고, 이어서 만들기는 상세 200 뒤 추가 정보 단계로 열렸습니다.
- 처음 확인에서 두 가지를 고쳤습니다. 기기 안 생성 결과 카드의 처음 저장 시각이 서버 초안의 시각 대신 기록을 쓴 시각으로 바뀌어 서버 시각을 잇도록 했고, 기기 안 기록의 기준 버전을 낮춰 다른 곳의 저장을 흉내 내자 같은 ID의 두 카드가 같은 목록 키를 써 제작 탭이 종료돼 카드 출처를 구분하도록 했습니다. 고친 뒤 두 카드가 따로 보였습니다.
- 이전 버전이 남긴 것과 같은 "실패" 완성 요청 행을 기기 DB에 넣자, 제작 탭 새로고침이 "스토리를 완성하지 못했어요"를 띄우고 요청 행을 지운 뒤 원래 처음 저장 시각의 "추가 정보를 입력하고 있었어요" 카드로 되돌렸습니다. 이어서 만들기는 고른 스토리라인과 입력한 추가 정보로 추가 정보 단계를 열었고, 확인용 카드는 삭제로 지웠습니다(서버 연결이 없어 DELETE 없음).
- 기준 버전이 낮은 기기 안 카드에서 저장하자 실제 서버가 409를 주어 충돌 확인이 떴고, "저장하기"로 서버의 현재 버전을 기준으로 200, "임시 저장했어요", 기기 안 행 삭제를 확인했습니다.

## 확인하지 못한 것

- 제작 탭 첫 조회의 한 스켈레톤과 진입 대기 스피너: dev 응답이 빨라 화면에 머물지 않아 단위 테스트와 코드로만 확인했습니다.
- 실제 서버가 완성을 실패로 확정하는 경로(POST 상태 코드 오류 뒤 복구 조회 404, 새로고침 조회의 FAILED): 실패를 일으킬 수 없어 단위 테스트와 이전 실패 행 주입으로만 확인했습니다.
- 완성 요청의 `draftId`로 서버가 초안을 지우는 것과 그 사이 완성 요청 연결로 서버 카드를 숨기는 것: 실제 스토리를 만들어야 해서 단위 테스트로만 확인했습니다.
- 기기 안 카드 삭제가 숨긴 서버 초안을 함께 지우는 것과 따로 보이는 서버 카드 열기의 기기 안 행 분리: 단위 테스트와 코드로만 확인했습니다.
- 웹이 Android가 쓴 payload를 여는 것: dev 웹에 KNK-1667이 배포되지 않아 웹 형식 고정 JSON 테스트로만 확인했습니다.
