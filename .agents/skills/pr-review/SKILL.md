---
name: pr-review
description: manyak-android의 변경을 PR 전에 코드 리뷰해 달라는 요청에 사용합니다. 예를 들어 "PR 올리기 전에 리뷰해줘", "코드 리뷰해줘", "보안 점검해줘"에 적용합니다. diff를 읽어 정확성 버그, 보안과 개인정보, CLAUDE.md 규칙, 모듈 경계, 테스트와 하네스 문서 동기화를 점검하고 PR 본문에 넣을 보고서를 만듭니다. 에뮬레이터에서 화면을 실행해 보는 검사는 ui-review 스킬이 맡습니다. 코드는 고치지 않고 보고만 합니다.
---

# PR 전 코드 리뷰 (manyak-android)

diff를 읽는 리뷰입니다. 에뮬레이터에서 화면을 띄워 보는 검사는 ui-review 스킬이 맡고, 머지를 막는 관문은 PR CI(`./gradlew check`, `./gradlew assembleDebug`)입니다. ktlint, detekt, Android lint, `checkModuleArchitecture`가 잡는 문제는 다시 지적하지 않습니다.

## 원칙

- 보고만 합니다. 코드 수정은 사용자가 보고서를 보고 요청한 뒤 합니다.
- 근거 없는 추측은 문제로 올리지 않습니다. 실패하는 입력이나 상태, 코드 위치(`파일:줄`)를 댈 수 있는 것만 문제로 쓰고, 확신이 낮으면 "확인 필요"로 따로 둡니다.
- 바뀐 줄과 그 줄이 영향을 주는 코드만 봅니다. 기존 코드의 문제는 이번 변경이 악화시킨 경우에만 올리고, 나머지는 "기존 문제"로 짧게 적습니다.

## 1. 범위 정하기

- PR 전 브랜치는 `git fetch origin dev` 뒤 `git diff origin/dev...HEAD`와 커밋하지 않은 변경(`git diff`, `git status --short`)을 읽습니다. 특정 커밋은 `git show <sha>`, 이미 올린 PR은 `gh pr diff <번호>`로 읽습니다. 통계나 파일 목록만 보고 판단하지 않습니다.
- Room 스키마 JSON(`feature/*/schemas/`)과 외부 스킬(`skills-lock.json`에 있는 `.agents/skills/` 폴더)은 생성되거나 받은 파일이라 내용을 리뷰하지 않고, 바뀐 이유만 확인합니다. 읽을 때 `':!feature/*/schemas'`처럼 빼고 봅니다.
- 브랜치 이름의 Jira 키로 작업 목적을 확인합니다. PR이 있으면 `gh pr view <번호> --json body`로 본문을 읽습니다. 호출한 쪽이 Jira 설명이나 슬랙 요청 같은 요청 맥락을 넘겨주면 그것을 기준으로 삼습니다.
- 요청받은 그대로 구현한 동작은 문제로 올리지 않습니다. 요청 자체에 위험이 있어 보이면 "확인 필요"에 요청 내용과 함께 적습니다.

## 2. 일반 리뷰

- Skill 도구에 `code-review`가 있으면 effort `medium`으로 실행합니다. 인자는 PR 전이면 `medium`(현재 diff), PR이 있으면 `medium <PR 번호>`입니다. `max`와 `ultra`는 사용자가 요청할 때만 씁니다. 영어로 돌아온 지적은 한국어로 옮기고 아래 점검과 겹치면 하나로 합칩니다. 없으면 정확성 항목을 직접 봅니다.
- 내장 `security-review`는 부르지 않습니다. 인자 없이 현재 브랜치 전체 diff를 대상으로 삼고 하위 에이전트를 띄우는 절차라 범위와 도구가 맞지 않습니다. 보안은 3번을 직접 봅니다. 사용자가 원하면 메인 세션에서 `/security-review`를 따로 실행할 수 있다고 보고서에 적습니다.
- 정확성: 조건 분기와 경계값, 코루틴 취소와 경쟁 상태(연타, 응답 순서 역전), `CancellationException`을 일반 실패로 삼키는 곳, Flow 수집 수명과 중복 구독, 실패와 빈 응답 처리, 로딩과 오류 상태 누락
- 이 앱에서 자주 틀리는 곳
  - 회원과 게스트가 같은 화면을 쓰면 세션 분기와 회원 데이터 갱신이 함께 바뀌었는지 봅니다.
  - 목록을 바꾸는 요청 뒤에 그 목록을 쓰는 다른 화면의 상태도 갱신되는지 봅니다.
  - 바텀 시트와 다이얼로그는 열린 뒤 잠긴 상태에서도 뒤로가기가 의도대로 막히는지 봅니다(`dismissOnBackPress`).

## 3. 보안과 개인정보

- 토큰과 세션: `core/auth`의 저장(`datastore`, `crypto`)과 갱신(`session`), `core/network`의 인증 헤더와 재시도. 평문 저장, 로그 출력, 동시 갱신, 만료 뒤 처리
- 외부 진입: `AndroidManifest.xml`의 `exported` 컴포넌트, 딥링크와 앱링크 필터, 알림 extra, `PendingIntent` 플래그, 받은 Intent의 extra를 검증 없이 라우트나 다른 Intent로 넘기는 곳. 매니페스트나 Intent 처리를 바꿨으면 android-intent-security 스킬로 확인합니다.
- 권한: 새 권한 선언, 런타임 요청 흐름, 서비스와 리시버 보호. 권한이나 IPC를 바꿨으면 android-permissions-security 스킬로 확인합니다.
- 분석과 모니터링: Amplitude(`core/analytics`), Crashlytics, `Log`로 사용자 입력 원문이나 개인정보를 보내지 않는지. 기준은 하네스 `docs/reference/ANALYTICS.md`의 수집 제한입니다.
- 로그아웃, 탈퇴, 계정 전환: 새로 사용자 데이터를 저장하는 곳이 기존 정리 경로에 참여하는지(session-data-cleanup 스킬 기준)
- 비밀값과 로컬 파일: `local.properties`, 키스토어(`*.jks`, `*.keystore`), 실제 키가 커밋에 섞이지 않았는지. debug 전용 평문 통신 설정이 release에 들어가지 않는지

## 4. 프로젝트 규칙

CLAUDE.md 규칙 중 정적 검사로 막지 못하는 것을 봅니다.

- 디자인 토큰: 색, 크기, 여백, 모서리를 `ManyakTheme` 접근자(`core/designsystem`) 대신 팔레트 값(`#05A66B` 등), `Color(...)`, `MaterialTheme.colorScheme`, `MaterialTheme.typography`로 직접 쓴 곳. 토큰을 추가했으면 Kotlin 토큰 파일과 `DESIGN.md` 표가 함께 바뀌었는지
- 문구: 화면 코드의 하드코딩 문자열, 소유 모듈이 아닌 `strings.xml`에 둔 문구
- MVI: 화면 ViewModel이 `:core:common`의 `MviViewModel`을 상속하는지, `reduce`가 순수 함수인지(부수효과, 시간, 난수 없음), 일회성 출력이 State가 아니라 Effect인지
- 내비게이션: 라우트가 `:core:navigation`의 `Routes.kt`에만 등록되는지, 복원 가능한 식별자만 싣는지(객체, 콜백, 큰 목록 금지), 기능 모듈끼리 직접 참조하지 않는지
- 오류: `:core:common`의 `DomainError`로 올리고 문구는 `DomainErrorMessages`가 붙이는지
- 구성 변경: 되돌리기, 초기화, 저장을 하는 효과가 회전, 다크 모드, 글자 크기 변경으로 다시 실행되지 않는지, 지켜야 할 값을 `remember`에만 두지 않았는지(configuration-changes 스킬 기준)
- 모듈: 두 번째 사용처 전에 `:core:designsystem`으로 올린 컴포넌트, 모듈을 추가, 삭제, 이름 변경했을 때 `settings.gradle.kts`, `.idea/gradle.xml`, `build-logic/src/main/kotlin/architecture/ModuleArchitecture.kt`가 함께 바뀌었는지, `core/testing`을 프로덕션 의존으로 쓰지 않는지
- Room: 엔티티를 바꿨으면 DB 버전, 마이그레이션, 내보낸 스키마 JSON, 마이그레이션 테스트가 같은 변경에 있는지(room-migration 스킬 기준)
- Compose 성능: 목록 key, 불안정한 파라미터, 컴포지션 중 무거운 계산이 바뀐 화면에 있으면 jetpack-compose-performance 스킬로 확인합니다. import나 이름만 바뀌었으면 건너뛰고 그 사실을 적습니다.
- 주석: 스펙 참조(`하네스 §1-2-4`, `FE-SCREEN-008`, `검수 #4` 등), 코드만 보고 알 수 있는 내용을 되풀이한 주석

## 5. 테스트와 하네스 문서

- `reduce`, 순수 로직, 데이터 변환을 바꿨으면 같은 모듈 `src/test`에 테스트가 있거나 바뀌었는지 봅니다. 여러 모듈이 쓰는 Fake는 `core/testing`, 한 모듈만 쓰는 Fake는 그 모듈 `src/test`에 있는지 봅니다.
- 동작 변경은 하네스 문서도 같은 작업에서 갱신합니다. 화면과 기능은 `../knk-harness/docs/spec/<도메인>/CLIENT/`(공통과 `ANDROID/`), 앱 셸은 `docs/ui/ANDROID/`, 공통 기술 규칙은 `docs/platform/CLIENT/ANDROID/`에서 시작합니다. 디자인 토큰과 컴포넌트를 바꿨으면 `DESIGN.md`도 봅니다. 갱신 대상이 없다고 판단하면 이유를 적습니다.
- 같은 Jira 키의 하네스 브랜치는 `git -C ../knk-harness branch -a --list '*KNK-<번호>*'`와 `git -C ../knk-harness worktree list`로 찾고, 있으면 그 diff와 대조합니다.
- 백엔드, AI, 웹 소유 문서와 계약에 걸리는 변경은 고칠 대상이 아니라 알릴 대상으로 적습니다. API 필드와 상태 코드는 하네스가 아니라 dev Swagger나 `../manyak-server` 코드로 확인합니다.

## 6. 보고

```markdown
## PR 리뷰 결과

- 대상: 기준(`origin/dev`) 또는 대상 커밋과 PR, 커밋 수, 바뀐 파일 수, 커밋하지 않은 변경 포함 여부, 실행한 단계
- 문제: 심각도별로 위치(`파일:줄`), 근거(실패하는 입력이나 상태), 제안
  - 높음: 머지 전에 고쳐야 함(버그, 보안, 개인정보, 계약 위반, 데이터 손실)
  - 보통: 이번 PR에서 고치는 게 좋음(규칙 위반, 테스트 누락, 문서 미갱신)
  - 낮음: 선택(개선 제안)
- 확인 필요: 확신이 낮거나 다른 팀 확인이 필요한 것
- 기존 문제: 이번 변경과 무관하게 발견한 것
- 확인하지 못한 것: 건너뛴 단계와 이유
```

- 문제가 없으면 무엇을 확인했는지만 짧게 적습니다. 확인하지 않은 항목을 통과로 쓰지 않습니다.
- PR 본문의 "리뷰 참고"에는 남은 문제와 확인 필요 항목만 옮깁니다.
