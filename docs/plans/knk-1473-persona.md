# KNK-1473 페르소나 선택, 관리, 채팅 표시와 주인공 이름 전송

## 목표와 기준

[KNK-1473](https://kimandkang.atlassian.net/browse/KNK-1473)은 웹 KNK-1469에서 구현한 사용자 페르소나를 Android에 같은 계약과 문구로 맞춥니다. 범위는 스토리 상세의 페르소나 선택과 생성 진입, 마이의 관리, 생성, 수정, 채팅방 헤더의 페르소나 표시, 일반 제작의 `protagonistName` 전송입니다. 앱은 로그인 필수라 게스트 분기는 적용하지 않습니다.

Android 기준은 `6b39aa01`, 하네스 기준은 `e39aef3`(KNK-1469 문서 병합)입니다. 정본은 [공통 스펙](../../../knk-harness/docs/spec/3-1-client-spec.md)의 FE-SCREEN-003 페르소나 선택, FE-SCREEN-005 플레이 중인 페르소나, FE-SCREEN-008 페르소나 생성과 관리, 일반 제작 클라이언트 선검증, [분석 스펙](../../../knk-harness/docs/spec/6-analytics.md) §6-4-2-4와 §6-4-2-17입니다. API는 2026-10-08 [dev Swagger JSON](https://dev-api.manyak.app/v3/api-docs)으로 확인했습니다.

- `GET/POST /users/me/personas`, `PATCH/DELETE /users/me/personas/{personaId}`. 응답은 `UserPersonaResponse{id, name, description, createdAt, updatedAt}`, 삭제는 204입니다.
- `POST /chats`의 `CreateChatRequest.personaId`(nullable), `ChatDetailResponse.persona{name}`(nullable).
- `CreateGeneralStoryRequest`, `UpdateStoryRequest`, `StoryEditFormResponse`의 `protagonistName`(nullable). 반려 제출본 조회는 자유 형식 payload라 같은 필드를 읽습니다.

## 확정한 선택

선택 이유는 [A-070](../../../knk-harness/docs/adr/1-3-android-adr.md#a-070-페르소나를-마이가-소유하고-상세에는-좁은-공용-계약으로-열기)에 기록합니다.

- 새 모듈 없이 `my/persona`가 페르소나를 소유하고, 상세에는 `common`의 `PersonaAccess`만 엽니다.
- 목록은 Singleton 저장소의 메모리 StateFlow이고 `UserScopedStore`로 로그아웃 정리에 참여합니다.
- 상세에서 만든 페르소나의 미리 선택은 `PersonaCreateRoute(originStoryId)`와 저장소 메모리 값으로 연결합니다.
- 채팅을 시작하면 미리 선택을 지우되 고른 주인공은 직접 고른 값으로 남깁니다. 상세 ViewModel이 백스택에 남기 때문입니다.
- 일반 제작 "인물 추가" 버튼이 두 번째 사용처(페르소나 추가)를 얻어 `designsystem`의 `ManyakAddButton`으로 올렸습니다.
- 채팅 시작 버튼 요약 줄을 위해 `{typography.label-tiny}`(11sp, 500, 16sp) 토큰을 추가했습니다.

## 검증 결과

위험 태그는 `ui-flow`, `state-restore`, `user-scoped-data`, `api-contract`입니다.

- 로컬 게이트: `designsystem`, `common`, `analytics`, `navigation`, `chat`, `story`, `my`, `create`, `app`의 `ktlintCheck`, `detekt`, `testDebugUnitTest`와 `checkModuleArchitecture`, `:app:assembleDebug`를 통과했습니다.
- 단위 테스트: 상세 페르소나 8건(기본과 페르소나 시작의 `personaId`, 분석 `persona_type`, 미리 선택과 해제, 다른 스토리 미적용, 삭제된 선택의 기본 복귀, 10개 상한, 요약 말줄임), 마이 11건(소개 글 조립과 분해, 요약, 필수 오류와 해제, 생성 요청, 409, 수정 채움과 PATCH, 없는 ID, 저장소의 미리 선택과 정리), 일반 제작에 `protagonistName` 왕복, PATCH 조건, `{username}` 선검증을 추가했습니다.
- 에뮬레이터(dev 서버, 2026-10-09): 마이의 페르소나 섹션 위치, 관리 목록의 두 줄 요약과 펼치기, 옵션 시트, 수정 화면의 기존 값 채움, 생성 화면의 필수 오류, 상세 셀렉트 목록과 동작 항목, 상세에서 만든 페르소나의 미리 선택과 요약 줄 말줄임, 회전 전후 선택 유지, 페르소나로 시작한 채팅의 헤더 보조 줄, 채팅방에서 돌아온 상세의 선택 유지를 화면으로 확인했습니다.

## 확인하지 못한 범위

- 일반 제작과 수정의 `{username}` 이름 칸 오류, 프롤로그 안내 문구는 단위 테스트로만 확인했습니다.
- 삭제 확인과 삭제 성공 토스트, 서버 409 응답은 기기에서 실행하지 않았습니다. 409는 단위 테스트로 확인했습니다.
- 생성과 수정 입력은 프로세스 종료 뒤 복원하지 않습니다(공통 계약 범위 밖).
