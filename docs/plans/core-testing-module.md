# 테스트 전용 core/testing 모듈 추가

- 티켓: [KNK-2049](https://kimandkang.atlassian.net/browse/KNK-2049)
- 작성일: 2026-11-09
- 상태: 구현, 로컬 검증 완료
- Android: `refactor/KNK-2049-improve-module-folder-structure`
- 하네스: `docs/KNK-2049-improve-module-folder-structure`
- 정본: [Android ADR A-094](../../../knk-harness/docs/platform/CLIENT/ANDROID/DECISIONS.md#a-094), [컴포넌트 구조](../../../knk-harness/docs/platform/CLIENT/ANDROID/1-COMPONENT-ARCHITECTURE.md#1-1-책임과-의존)

## 목표와 범위

여러 모듈의 테스트에 반복되던 메인 디스패처 설정과, 같은 동작의 core 계약 Fake를 테스트 전용 `:core:testing`으로 모읍니다. 프로덕션 코드와 테스트 동작은 바꾸지 않습니다.

| 모은 것 | 이전 위치 | 비고 |
| --- | --- | --- |
| `MainDispatcherRule` | 11개 모듈, 39개 테스트의 `@Before`/`@After` 안 `Dispatchers.setMain`, `resetMain` | 기본은 `StandardTestDispatcher`. `dispatcher`를 `runTest` 등 다른 곳에서도 쓰는 31개는 그 디스패처를 `MainDispatcherRule(dispatcher)`로 넘겨 동작을 유지 |
| `FakeCreditPolicyRepository` | chat, create | 패키지 선언만 달랐음 |
| `FakeTrialsRepository` | chat, create | create의 `initial` 인자 버전으로 통일. 기본값이 chat 버전과 같음 |
| `FakeAttendanceClaimer` | chat, create | 패키지 선언만 달랐음 |

모듈마다 동작이 다른 `FakeUserProfileRepository`(chat, create, my)와 `FakeSessionRepository`(my, notification)는 테스트 전제가 달라 각 모듈에 남겼습니다. 이름만 같은 `FakeStoryRepository`(home, story, studio)는 구현하는 계약이 달라 대상이 아닙니다.

## 구현

| 영역 | 변경 |
| --- | --- |
| 모듈 | `core/testing`(Android Library, `app.manyak.testing`). `core:common`에만 의존하고 JUnit, coroutines-test를 `api`로 노출 |
| 경계 검사 | `manyak.architecture.gradle.kts`가 테스트 설정(`test*`, `androidTest*`, 이름에 `Test` 포함)의 프로젝트 의존을 별도 그래프로 넘깁니다. testing은 테스트 그래프에서만 허용되고 프로덕션 의존, 프로덕션 코드의 참조, 기능 간 테스트 의존은 실패합니다. testing은 계층 패키지 규칙에서 제외합니다 |
| 등록 | `settings.gradle.kts`, `.idea/gradle.xml`, 사용하는 11개 모듈의 `testImplementation(projects.core.testing)` |
| 문서 | `CLAUDE.md`(`AGENTS.md`), `README.md` 폴더 구조, 하네스 컴포넌트 구조와 ADR A-094 |

## 검증

- `:build-logic:test`, `:build-logic:ktlintCheck` 통과. testing의 프로덕션 의존, 프로덕션 코드 참조, 기능 간 테스트 의존이 실패하고 테스트 의존은 통과하는 회귀 테스트를 포함합니다.
- 영향받은 12개 모듈의 `ktlintCheck`, `detekt`, `testDebugUnitTest`와 `checkModuleArchitecture` 통과. 단위 테스트 797개 전부 통과, 경계 검사 19개 프로젝트와 Kotlin 639개 파일.
- 공통 빌드 로직을 바꿔 `./gradlew check assembleDebug`도 실행했고 통과했습니다(52초).
- 테스트 코드만 정리해 기기 검증은 하지 않았습니다.
