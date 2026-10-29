# KNK-1668 일반 제작 임시 저장의 서버 보관

## 목표와 기준

[KNK-1668](https://kimandkang.atlassian.net/browse/KNK-1668)은 회원 일반 제작 임시 저장본을 Room 대신 서버 초안(`/users/me/story-drafts`)에 보관해 다른 기기와 웹에서 이어서 만들게 합니다. 웹은 [KNK-1667](https://kimandkang.atlassian.net/browse/KNK-1667)에서 같은 계약으로 먼저 옮겼습니다.

정본은 [공통 임시 저장 계약](../../../knk-harness/docs/domain/STORY/CLIENT/2-2-REQUIREMENTS.md#2-2-6-일반-제작과-수정-입력)(KNK-1667 하네스 브랜치), [서버 초안 계약](../../../knk-harness/docs/spec/4-backend-server-spec.md#story-drafts), [Android spec](../../../knk-harness/docs/spec/3-3-android-spec.md), [Android design](../../../knk-harness/docs/design/1-2-android-design.md)입니다. API는 2026-10-16에 조회한 [dev Swagger JSON](https://dev-api.manyak.app/v3/api-docs)과 서버 `StoryDraftService`로 확인했습니다. 오류 본문의 `details`는 `ApiErrorResponse`의 `{field, message}` 목록입니다.

## 확정한 선택

데이터 레이어 설계를 먼저 보이고 사용자 승인을 받았습니다.

- payload는 웹 v1 스냅숏 형식이고 이미지는 객체 키만 보냅니다. 장르는 모두 `custom` 이름 항목으로 쓰고, 웹이 쓴 `tag` 항목은 제공 장르 카탈로그로 이름을 찾습니다.
- Room 스키마는 바꾸지 않습니다. 이관 전 초안과 접수 연결 행만 남습니다.
- 충돌 확인의 보조 버튼은 Android 규칙대로 "닫기"입니다.

선택 이유는 [A-074](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/DECISIONS.md#a-074)에 기록합니다.

## 검증 결과

| 범위 | 방법 | 결과 |
| --- | --- | --- |
| payload 읽기와 쓰기 | `GeneralDraftPayloadTest`. 웹 `toGeneralDraftRequest`와 같은 모양의 고정 JSON, 빠진 필드와 다른 타입, Android 폼 왕복 | 통과 |
| 화면 저장 흐름 | `GeneralStoryViewModelTest`. 버전 이어 쓰기, 충돌 뒤 덮어쓰기, 백그라운드 실패 무음, 카드 재개의 초안 없음 안내, 등록의 `draftId` | 통과 |
| 저장소 | `GeneralDraftStorageTest`(에뮬레이터, 메모리 Room과 서버 규칙을 흉내 낸 가짜 API). 이관과 상한 중단, 충돌, 지워진 초안 재생성, 접수 뒤 저장 거절, 목록 실패 시 같은 계정만 유지, 삭제 | 5개 통과 |
| 제작 탭 | `StudioViewModelTest`. 화면 표시와 새로고침의 서버 초안 조회 | 통과 |
| 정적 검사 | 변경 모듈 ktlint, detekt, `checkModuleArchitecture` | 통과 |

`installDebug` 후 Pixel 10 API 37 에뮬레이터에서 dev 서버로 확인했습니다.

- 기존 빌드(v1.5.1)로 만든 Room 초안이 새 빌드의 제작 탭 진입에서 서버로 옮겨지고 Room 행이 지워졌습니다. 목록 조회, PUT, 목록 재조회 순서였습니다.
- 처음 확인에서는 옮긴 카드가 목록 맨 앞에 붙으며 화면 위로 숨었습니다. 맨 위를 보던 중이면 맨 위에 두도록 고친 뒤 다시 옮겨 카드가 맨 위에 보이는 것을 확인했습니다.
- 서버 초안 이어서 만들기에서 제목과 소개가 복원됐고, 두 번 연속 저장이 모두 200과 "임시 저장했어요"였습니다.
- 앱 프로세스를 종료한 뒤 같은 ID의 다른 내용 Room 행을 넣고 복원된 화면에서 저장하자 실제 서버가 409를 돌려 충돌 다이얼로그가 떴습니다. "저장하기"로 200, "임시 저장했어요", Room 행 삭제를 확인했습니다.
- 서버 초안 5개에서 새 초안 저장이 400과 "임시 저장은 5개까지 할 수 있어요"였고, 같은 화면의 백그라운드 전환 저장은 400이었지만 안내가 없었습니다.
- 새로 시작한 화면은 상세 404 뒤 안내 없이 빈 폼으로 열렸습니다. 초안 카드 삭제는 DELETE 204와 카드 제거였습니다.

## 확인하지 못한 것

- 웹과 서로 연 초안: dev 웹에는 KNK-1667이 배포되지 않아 웹이 쓴 실제 payload는 고정 JSON 테스트로만 확인했습니다.
- 실제 서버에서 카드로 연 초안의 404 안내와, 열어 둔 초안이 다른 곳에서 지워진 뒤의 재생성: 두 번째 클라이언트가 없어 가짜 API 테스트로만 확인했습니다.
- 이미지가 든 초안의 서버 저장과 `imageUrls` 미리보기, 등록 요청의 `draftId`로 서버가 초안을 지우는 것: 실제 서버로는 확인하지 않았습니다.
