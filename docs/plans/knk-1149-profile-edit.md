# KNK-1149 프로필 수정 화면

## 목표와 기준

[KNK-1149](https://kimandkang.atlassian.net/browse/KNK-1149)는 상위 [KNK-1121](https://kimandkang.atlassian.net/browse/KNK-1121)의 Android 작업입니다. 회원이 마이에서 닉네임과 프로필 이미지(프리셋)를 바꿉니다. 규칙은 [KNK-1146](https://kimandkang.atlassian.net/browse/KNK-1146), API는 [KNK-1147](https://kimandkang.atlassian.net/browse/KNK-1147)이고, 웹은 [KNK-1148](https://kimandkang.atlassian.net/browse/KNK-1148)에서 같은 계약으로 먼저 구현했습니다.

정본은 [공통 프로필 수정](../../../knk-harness/docs/client/3-1-INFORMATION-ARCHITECTURE.md#3-1-1-화면과-진입점)과 [분석 §6-4-2-19](../../../knk-harness/docs/spec/6-analytics.md)(KNK-1148 하네스 브랜치), [계정 백엔드 계약](../../../knk-harness/docs/domain/ACCOUNT/BACKEND/4-2-CONTRACT.md) 4-2-14, 4-2-15, [Android spec](../../../knk-harness/docs/client/ANDROID/3-3-LAYOUT.md#3-3-1-셸과-조작), [Android design](../../../knk-harness/docs/client/ANDROID/AGENTS.md)입니다. 닉네임 규칙은 Jira KNK-1146 본문의 "공백 허용"이 아니라 Swagger의 최신 규칙(입력값 그대로 2~20자, 완성형 한글, 영문, 숫자, 공백 불가)을 따릅니다.

## 확정한 선택

- 진입, 프리셋 4열 시트, 고르면 화면 이미지만 바꾸고 저장은 "저장하기"에서 하는 흐름, 문구, 분석 이벤트는 웹과 같습니다.
- 경로는 인자 없는 `ProfileEditRoute`이고 저장하거나 나가면 마이로 돌아갑니다.
- 저장 응답을 기존 프로필 캐시(`ProfileCacheStore`)에 바로 씁니다. 공용 `UserProfileRepository`는 바꾸지 않고 `my` 안의 `ProfileEditRepository`가 맡습니다.
- 홈 목록은 내 닉네임이 바뀌면 첫 페이지를 조용히 다시 읽습니다(실패해도 알리지 않음). 스토리 상세는 열 때마다 서버에서 읽어 따로 처리하지 않습니다.
- 저장 중에는 뒤로가기를 막습니다. 나가면 ViewModel과 함께 요청이 취소돼 결과를 알 수 없어서입니다.

## 구현

- 데이터: `ProfileApi.updateProfile`(`PATCH users/me`, null 필드는 직렬화에서 빠짐), `ProfileApi.presets`, `ProfileEditRepositoryImpl`(세션 세대가 같을 때만 캐시에 씀).
- 규칙: `my/profile/domain/NicknameViolation.kt`의 `validateNickname`. 허용 문자를 길이보다 먼저 봅니다.
- 화면: `ProfileEditViewModel`이 입력, 고른 프리셋, 시트, 이탈 확인을 상태로 둡니다. `ProfileEditScreen`, `ProfilePresetSheet`, `ProfileImage`(썸네일을 먼저 깐 원형 이미지).
- 진입: `ProfileHeader` 행 전체 클릭과 닉네임 바로 뒤 꺾쇠(웹과 같은 자리). 프리셋 격자는 화면 높이의 70%로 묶어 시트가 꼭대기까지 차오르지 않게 했습니다. `:my`에 `activity-compose`(`BackHandler`)를 더했습니다.
- 분석: `AnalyticsEvent.ProfileEditViewed`, `ProfileEditCompleted(nicknameChanged, imageChanged)`.

## 검증 결과

| 범위 | 방법 | 결과 |
| --- | --- | --- |
| 화면 상태 | `ProfileEditViewModelTest` 6건. 규칙 위반(공백, 1자, 자모) 미전송과 오류 지움, 바뀐 필드만 전송, 원래 이미지와 같은 프리셋은 바뀐 내용 아님, 409와 400의 칸 아래 사유와 그 밖 실패의 토스트와 입력 유지, 바뀐 내용이 있을 때만 이탈 확인, 캐시가 비었을 때 조회 실패와 재시도 | 통과 |
| 홈 갱신 | `HomeViewModelTest`에 닉네임이 바뀔 때만 다시 읽고 실패를 알리지 않는 경우 추가 | 통과 |
| 분석 | `AnalyticsEventContractTest`에 두 이벤트의 이름과 프로퍼티 추가 | 통과 |
| 정적 검사 | `:my`, `:home`, `:analytics`, `:navigation`, `:app`의 ktlint, detekt, `:my:lintDebug`, `:app:compileDebugKotlin`, `checkModuleArchitecture` | 통과 |
| 기기 | 에뮬레이터(dev 서버): 마이 프로필 영역의 꺾쇠, 수정 화면(현재 닉네임과 이미지, 저장 비활성), 시트에서 현재 이미지 프리셋의 선택 테두리, 다른 프리셋을 고르면 시트가 닫히고 이미지만 바뀜, 공백 닉네임의 허용 문자 오류, 바뀐 내용이 있을 때 헤더와 기기 뒤로가기의 확인 다이얼로그, 저장 요청이 `profileImagePreset` 하나만 실은 37바이트 본문으로 나감 | 통과 |
| 저장 성공 | 에뮬레이터(dev 서버): 프리셋만 바꿔 저장하면 `PATCH /users/me` 200 뒤 "프로필을 저장했어요" 토스트와 함께 마이로 돌아오고 마이 아바타가 바로 바뀜. 앱을 강제 종료하고 다시 켜도 새 이미지가 유지됨. 확인 뒤 원래 프리셋으로 되돌림. 첫 시도 때 dev 서버가 모든 요청에 503을 돌려 저장 실패 토스트와 입력 유지도 함께 확인함 | 통과 |
| 미확인 | 닉네임을 바꿔 저장하는 경우와 409, 400은 기기에서 보내지 않았습니다(adb 입력은 한글 IME가 바꿔 원래 닉네임으로 되돌릴 수 없음). 단위 테스트로만 확인했습니다. 홈 목록의 작성자 닉네임 갱신도 단위 테스트로만 확인했습니다 | 코드와 테스트로 판단 |
