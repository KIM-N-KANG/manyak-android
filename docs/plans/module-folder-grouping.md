# 모듈 폴더를 core, feature로 묶고 Gradle 경로 변경

- 티켓: [KNK-2049](https://kimandkang.atlassian.net/browse/KNK-2049)
- 작성일: 2026-11-09
- 상태: 구현, 로컬 전체 검증 완료
- Android: `refactor/KNK-2049-improve-module-folder-structure`
- 하네스: `docs/KNK-2049-improve-module-folder-structure`
- 정본: [Android ADR A-093](../../../knk-harness/docs/platform/CLIENT/ANDROID/DECISIONS.md#a-093), [컴포넌트 구조](../../../knk-harness/docs/platform/CLIENT/ANDROID/1-COMPONENT-ARCHITECTURE.md#1-1-책임과-의존)

## 목표와 범위

루트에 나란히 있던 17개 런타임 모듈을 `core/`와 `feature/` 두 폴더로 묶고, Gradle 경로도 폴더와 같게 바꿉니다. 모듈 수, 소유권, 의존 규칙, Kotlin 패키지, namespace, 앱 동작은 바꾸지 않습니다.

| 그룹 | 모듈 | Gradle 경로 |
| --- | --- | --- |
| 루트 | app, build-logic | `:app`, included build |
| `core/` | common, designsystem, navigation, analytics, network, auth, report | `:core:<모듈>` |
| `feature/` | home, chat, studio, story, login, legal, my, create, notification, notice | `:feature:<모듈>` |

report는 chat, studio, story가 함께 쓰는 공유 신고 업무라 `core/`에 둡니다.

## 구현

| 영역 | 변경 |
| --- | --- |
| 폴더 | `git mv`로 모듈 폴더를 그룹 아래로 이동 |
| 등록과 의존 | `settings.gradle.kts`의 `include(":core:<모듈>")`, `include(":feature:<모듈>")`. 모든 `build.gradle.kts`의 `projects.<모듈>`을 `projects.core.<모듈>`, `projects.feature.<모듈>`로 변경 |
| 경계 검사 | `manyak.architecture.gradle.kts`가 빌드 파일이 있는 프로젝트만 모아 Gradle 경로로 의존 그래프를 넘깁니다. `:core`, `:feature` 중간 프로젝트는 제외됩니다. `ModuleArchitecture.kt`는 소스 파일의 모듈을 등록된 경로 접두사로 찾고, 모듈이 정해진 그룹 경로에 없으면 `<모듈> must be registered as :<그룹>:<모듈>`로 실패합니다. 회귀 테스트를 추가했습니다 |
| IDE | `.idea/gradle.xml` 모듈 목록에 `core`, `feature`와 하위 경로 반영 |
| 문서 | `CLAUDE.md`(`AGENTS.md`), `README.md` 폴더 구조, `DESIGN.md` 경로, `module-reorganization.md` 소스 링크, 하네스 도메인 Android 문서의 소스 링크 |

이전 기능 계획의 `:chat:testDebugUnitTest` 같은 명령은 당시 기준 실행 기록이라 그대로 둡니다.

## 검증

- `:build-logic:test`, `:build-logic:ktlintCheck` 통과. 그룹 밖 등록, 잘못된 그룹, 미등록 폴더의 소스가 실패하는 회귀 테스트를 포함합니다.
- `./gradlew check assembleDebug` 통과(1분 35초). `checkModuleArchitecture`가 18개 프로젝트(app과 런타임 17개)와 Kotlin 635개 파일을 검사했고 `:core`, `:feature` 중간 프로젝트는 대상에서 빠졌습니다.
- 변경한 문서의 상대 링크가 모두 실제 파일을 가리키는지 스크립트로 확인했습니다.
- 앱 동작 코드와 리소스를 바꾸지 않아 기기 화면 검증은 하지 않았습니다. Android Studio 싱크 결과도 확인하지 않았습니다.
