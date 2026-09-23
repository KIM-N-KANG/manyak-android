# 스토리 제작 초안 다중 보관

- 티켓: [KNK-1395](https://kimandkang.atlassian.net/browse/KNK-1395) (웹 패리티 [KNK-1394](https://kimandkang.atlassian.net/browse/KNK-1394))
- 작성일: 2026-09-23
- 상태: 구현·에뮬레이터 검증 완료
- Android: `feat/KNK-1395-support-multiple-story-drafts` — fetch한 `origin/dev`의 `709ec3e1`
- 하네스: `docs/KNK-1395-support-multiple-story-drafts` — fetch한 `origin/dev`의 `53358c3`
- 제품 정본: [공통 §3-1-4](../../../knk-harness/docs/spec/3-1-client-spec.md), [Android Spec §3-3-3](../../../knk-harness/docs/spec/3-3-android-spec.md#제작과-데이터-보존), [Android Design §1-2-7](../../../knk-harness/docs/design/1-2-android-design.md#제작-카드와-다중-완성-진행), [A-046](../../../knk-harness/docs/adr/1-3-android-adr.md#a-046)

## 목표와 범위

편집 초안(키워드 초안·스토리라인 생성·스토리 초안)을 완성 요청처럼 여러 건 보관합니다. 새 제작은 확인 없이 새 초안으로 시작하고, 재개는 제작 탭 초안 카드에서만 합니다. 초안 카드 설명을 멈춘 단계별로 나누고, 진행 카드에 처음 임시 저장 시각을 표시하며, 그 시각 최신순으로 정렬합니다.

웹의 완성 실패 강등은 Android에 없습니다. Android는 실패 요청을 실패 카드로 남기므로 시각도 그 카드가 이어받습니다. 제작 탭의 스토리라인 생성 폴링은 두지 않고, 생성 중 초안은 그 카드로 재개한 퍼널이 3초 복구 조회로 이어받습니다(기존 Android 계약 유지).

## 구현

| 영역 | 구현 |
| --- | --- |
| 저장 | `ManyakDatabase` v4. `pending_story_creation` 기본 키를 `draftId`(퍼널 세션 UUID)로 바꾸고 `createdAt` 추가, `story_completion_request`에도 `createdAt` 추가. `MIGRATION_3_4`가 편집 테이블을 다시 만들어 남은 행을 `legacy-{id}`·`createdAt NULL`로 옮김 |
| 처음 저장 시각 | `PendingStoryCreationDao.save`가 행이 없을 때만 지금 시각을 쓰고 있으면 기존 값(null 포함) 유지. 완성 제출 트랜잭션(`StoryCompletionRequestDao.submit`)이 그 초안의 값을 요청으로 옮기고 그 초안만 삭제 |
| 정렬 | DAO가 `createdAt IS NULL, createdAt DESC`(요청은 이어서 `submittedAt DESC`)로 정렬. 제작 탭은 초안 → 완성 요청 → 스토리 순으로 그대로 그림 |
| 라우트 | `CreateKeywordRoute`·`CreateStorylineRoute`·`CreateAdditionalInfoRoute`가 `draftId`를 실음. FAB은 앱이 UUID를 만들고, 초안 카드는 그 ID로 재개 체인을 쌓음 |
| 퍼널 | 세 단계 ViewModel이 assisted `draftId`를 받아 `StorylineGenerationStore.bind`. 다른 초안을 맡으면 앞 세션 실행을 끊고 메모리를 비움. 모든 영속·복원·제출이 그 ID의 행만 다룸 |
| 경계 | `CreationProgressAccess.drafts`(목록)·`discard(draftId)`. `CreationProgressSummary`에 `draftId`·`createdAt`, `CompletionRequestSummary`에 `createdAt` |
| 제작 탭 | 새로 만들기 확인 다이얼로그·문구 제거, 초안 카드 여러 개(`draft:<id>` 키), 단계별 설명 4종, `MetaChip` 달력 줄(KST `yyyy-MM-dd HH:mm`, 초안·실패는 버튼 위, 완성 중은 본문 아래) |
| 분석 | `client_storyCreate_resumeDialog_*` 제거(분석 스펙 KNK-1394와 일치). `continueBanner_shown`은 초안별 단계가 처음 보일 때 한 번 |

## 검증

- 로컬: `:create`·`:studio`·`:navigation`·`:app`·`:common`·`:analytics` ktlint·detekt, `:create`·`:studio`·`:navigation` 유닛 테스트, `checkModuleArchitecture`, `:create:compileDebugAndroidTestKotlin`, `:app:installDebug`.
- 에뮬레이터(dev 서버): 이전 빌드(DB v3)에 스토리 초안 1건이 있는 상태에서 새 빌드 설치 → v4로 올라가 `legacy-0`·`createdAt NULL` 보존, 카드는 "스토리라인을 선택하고 있었어요"·날짜 없음. FAB은 다이얼로그 없이 빈 키워드로 시작 → 저장·이탈 뒤 초안 2개(새 초안이 날짜와 함께 위). 각 카드 재개가 자기 초안만 복원. 키워드 초안 → 생성 → 선택 → 추가 정보 → 완성 제출까지 같은 행이 단계만 바뀌고 `createdAt` 유지, 완성 중 카드가 같은 시각을 본문 아래에 표시하고 다른 초안은 남음. 비행기 모드 생성 뒤 이탈한 카드가 "스토리라인을 만들고 있어요". 더보기 삭제가 그 카드만 지움. 완성 뒤 실제 스토리 카드로 전환. 기존 초안을 스토리라인 단계로 재개한 채 `am kill` 뒤 재실행해도 같은 초안이 복원됨.
- 미검증: 실패 카드의 날짜 줄 실제 노출(402·FAILED 재현 없이 코드·프리뷰로만 확인).
