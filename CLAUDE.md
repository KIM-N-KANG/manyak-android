# 기본 지침

작업을 시작하기 전에 `../knk-harness` 경로에 있는 하네스 레포지토리를 먼저 확인하세요.

## 정본 문서

[도메인 목록](../knk-harness/docs/spec/AGENTS.md)에서 관련 도메인을 선택하고 `CLIENT/` 공통 문서와 `CLIENT/ANDROID/`를 단계마다 함께 확인합니다. 사용자 목표와 ID는 공통 문서가 소유하며 플랫폼 구현과 검증은 ANDROID에 둡니다.

| 확인할 것 | 위치 |
| --- | --- |
| 공용 사용자 계약과 셸 | [공통 UI](../knk-harness/docs/ui/AGENTS.md) |
| Android 기반과 공용 검증 | [ANDROID 기반](../knk-harness/docs/platform/CLIENT/ANDROID/AGENTS.md) |
| 모듈 소유권과 의존 방향 | [컴포넌트 구조](../knk-harness/docs/platform/CLIENT/ANDROID/1-COMPONENT-ARCHITECTURE.md) |
| 결정 당시 맥락과 이유 | 해당 CLIENT와 ANDROID의 `DECISIONS.md` |
| 릴리스와 배포 | [Android 배포](../knk-harness/docs/platform/CLIENT/ANDROID/5-DEPLOYMENT.md) |
| 기술 스택과 로컬 준비 | [README](README.md) |
| 디자인 토큰과 컴포넌트 | [DESIGN](DESIGN.md) |
| 기능별 계획과 실행 결과 | 해당 기능의 PR 본문 |
| 서버 API의 실제 필드와 상태 코드 | [dev Swagger](https://dev-api.manyak.app/swagger-ui/index.html), [OpenAPI JSON](https://dev-api.manyak.app/v3/api-docs) |

- 공통 결정은 하네스에 쓰고 이 레포에는 포인터만 둡니다. 같은 결정을 두 곳에 두지 않습니다.
- 하네스 문서를 읽을 때는 해당 절이 `dev`에 병합됐는지 확인하세요. 미병합 브랜치 내용을 정본으로 오인하면 잘못된 전제 위에서 작업하게 됩니다.
- **API 구현 확인은 Swagger를 직접 봅니다.** 승인된 계약은 해당 도메인의 BACKEND 문서에서 확인합니다. 하네스의 API 절은 서버 구현보다 뒤처질 수 있어(예: `author`, `isOwner`가 구현됐는데 문서는 "계획"으로 남아 있었음) 응답 필드 유무, 타입, 상태 코드는 반드시 Swagger 또는 `../manyak-server` 코드로 확인합니다. Swagger와 코드는 구현 사실의 근거입니다. 승인된 제품/API 계약과 다르면 차이를 기록하고 실제 오류인지 승인된 변경인지 확인한 뒤 해당 정본을 정정합니다.

## 작업 방식

- 기능 작업은 관련 spec과 design을 전체로 읽고 목표, 계약, 적용 범위를 확인합니다. 승인된 계약은 spec, 중요한 선택 이유는 ADR에 둡니다. 변경 순서, 검증, 복구 방법은 저장소에 계획 문서로 두지 않고 PR 본문에 남깁니다. 구현 후 현재 design을 맞춥니다. spec에는 Phase, 구현 상태, Jira, 작업 순서를 넣지 않습니다.
- 지라 티켓을 임의로 새로 만들지 않습니다. 브랜치 준비를 요청받으면 하네스와 구현 저장소에 같은 Jira 키를 사용합니다. 별도 브랜치 요청이 없으면 각 저장소의 현재 브랜치에서 작업합니다.
- 구현을 요청받았으면 중간에 되묻지 말고 판단해서 끝까지 진행합니다. 되돌리기 비싼 결정(새 API 계약, 저장 스키마, 인증, 권한)만 확인을 받습니다.
- 문제를 찾아 달라고 한 작업은 고치지 말고 먼저 정리해 보고합니다.

## Git

- 브랜치, 커밋, PR, 머지는 레포 스킬 `create-branch`, `create-commit`, `create-pr`, `merge-pr`로 합니다. 앞의 셋은 하네스 공통 스킬을 읽어 따르는 연결 스킬이고, 커밋 제목 형식(명사형), 필수 검증, PR 전 리뷰처럼 이 저장소에서 더하는 규칙을 담습니다. 하네스 공통 스킬은 이 저장소에서 고치지 않습니다.
- `Co-Authored-By: Claude`, "Generated with Claude Code" 같은 귀속 문구를 커밋과 PR에 넣지 않습니다.
- **`git push`와 PR 생성은 명시적으로 요청받기 전에 하지 않습니다.** 완료 보고에 "푸시할까요?" 같은 제안도 붙이지 않습니다.

## 에이전트 스킬

- 스킬 원본은 `.agents/skills/`에 두고 `.claude/skills/`에는 같은 이름의 심볼릭 링크만 둡니다.
- 공식 Android 스킬은 `npx skills add android/skills --skill <이름> -a claude-code -a codex -y`로 받고 `npx skills update -p`로 갱신합니다. 받은 스킬은 `skills-lock.json`의 해시와 어긋나지 않도록 직접 고치지 않습니다.

## 코드 규칙

- 색, 크기, 여백, 모서리는 `ManyakTheme` 접근자로만 읽습니다. 팔레트 값(`#05A66B` 등)이나 `MaterialTheme.colorScheme`, `MaterialTheme.typography`를 화면 코드에서 직접 쓰지 않습니다. 토큰에 없는 값이 필요하면 Kotlin 토큰 파일과 `DESIGN.md` 표를 함께 고칩니다(`DESIGN.md` 갱신 지침).
- 사용자에게 보이는 문자열은 소유 모듈의 `src/main/res/values/strings.xml`에 둡니다. 기능 전용, 디자인 공용, 공통 오류, 앱 셸 문구의 소유 기준은 하네스 모듈 아키텍처를 따릅니다. 화면 코드에 문구를 직접 쓰지 않습니다.
- 코드 주석에 `하네스 §1-2-4`, `공통 계약`, `FE-SCREEN-008`, `검수 #4` 같은 스펙 참조를 넣지 않습니다. 코드만 보고는 알 수 없는 이유만 남기고, 핵심 결정 근거는 하네스의 영역별 ADR 한 파일에 결정 단위로 추가합니다. 기존 확정 기록은 보존하고 후속 결정에 대체 ID와 범위를 남깁니다.
- 화면 ViewModel은 `:core:common`의 `MviViewModel`을 상속합니다(Intent → 부수효과 → Event → `reduce` → State, 일회성 출력은 Effect). `reduce`는 순수 함수입니다.
- 화면 부수효과는 구성 변경(회전, 다크 모드, 글자 크기)에서 다시 실행되고 `remember` 값은 사라집니다. 되돌리기, 초기화, 저장을 하는 효과는 `configuration-changes` 스킬 기준으로 점검합니다.
- 라우트는 `:core:navigation`의 `Routes.kt` 한 곳에만 등록하고 **복원 가능한 식별자만** 싣습니다. 화면 기능 모듈끼리 직접 참조하지 않습니다.
- 오류는 `:core:common`의 `DomainError`로 올리고 문구는 `:core:common`의 `DomainErrorMessages`가 붙입니다.
- 두 번째 사용처가 생기기 전에는 공용 컴포넌트를 `:core:designsystem`으로 올리지 않습니다.

## 모듈 구조

모듈 소유권, 계층과 의존 규칙은 하네스 [공용 Android 컴포넌트 구조](../knk-harness/docs/platform/CLIENT/ANDROID/1-COMPONENT-ARCHITECTURE.md)를 따릅니다. 실제 모듈 등록은 `settings.gradle.kts`를 확인하세요.

`app`이 `core/`의 기반 모듈과 `feature/`의 화면 기능 모듈을 조립합니다. Gradle 경로도 폴더를 따라 `:core:<모듈>`, `:feature:<모듈>`이고 의존은 `projects.core.common`, `projects.feature.home`처럼 씁니다. 기능 간 공유 업무인 report는 `core/`에 둡니다. `core/testing`은 테스트 소스 세트 전용으로, 여러 모듈이 같이 쓰는 `MainDispatcherRule`과 core 계약의 Fake만 둡니다. 프로덕션 의존은 `checkModuleArchitecture`가 막고, 한 모듈만 쓰는 Fake는 그 모듈의 `src/test`에 둡니다. 기능은 필요한 entity, domain, data, presentation 패키지를 소유하고, chat, create, my의 하위 기능도 패키지로 구분합니다. `checkModuleArchitecture`가 프로젝트 의존과 Kotlin PSI 기반 계층 경계를 검사하며 루트 `check`에 연결되어 있습니다. navigation의 기존 Kotlin 패키지는 직렬화 호환을 위해 유지합니다.

- 모듈을 추가, 삭제, 이름 변경하면 `settings.gradle.kts`와 함께 `.idea/gradle.xml`의 `<option value="$PROJECT_DIR$/<core|feature>/<모듈>" />` 목록도 같은 커밋에서 갱신합니다(알파벳 순). 이 파일은 추적 대상이라, 빠뜨리면 사용자가 IDE 싱크할 때 diff가 남습니다. `build-logic`의 `ModuleArchitecture.kt` 모듈 집합도 함께 고치고(모듈이 정해진 그룹 경로에 등록됐는지도 이 집합으로 검사합니다) `:build-logic:ktlintCheck`까지 로컬 검증에 포함합니다.
- 새 모듈은 역할에 맞는 정적 검사, lint, 테스트가 루트 `check`에 연결되고 리포트를 모듈 경로와 무관하게 수집할 수 있어야 합니다.

## 명령어

```bash
./gradlew check          # 모듈과 계층 검사, ktlint, detekt, Android lint, 유닛 테스트
./gradlew assembleDebug  # 리소스와 매니페스트까지 확인
./gradlew installDebug   # 연결된 기기에 설치
```

- 포맷 규칙은 ktlint(`.editorconfig`), 코드 스멜은 detekt(`config/detekt/detekt.yml`)가 봅니다. 둘 다 `ignoreFailures = false`입니다.
- 서버 주소와 소셜 로그인 키는 `local.properties`에서 읽습니다. `cp local.properties.example local.properties` 후 채우고, 값이 없어도 빌드는 되며 해당 제공자 로그인만 실패합니다.
- 서버 주소는 빌드 타입별 `BuildConfig.BASE_URL`입니다. debug는 dev 서버(`https://dev-api.manyak.app/api/v1/`)가 기본이고 `local.properties`의 `BASE_URL`로 덮어쓰며, release는 운영 서버(`https://api.manyak.app/api/v1/`)입니다. 평문 통신은 debug 소스 세트의 `network_security_config.xml`에서 에뮬레이터 호스트와 로컬 주소에만 허용하고 release에는 들어가지 않습니다.
- Gradle 데몬 toolchain은 JDK 25(`gradle/gradle-daemon-jvm.properties`)이고 CI도 같은 값을 씁니다. 데몬 힙은 4GB(`gradle.properties`)입니다. 2GB에서는 전체 검사 중 GC 과부하로 데몬이 종료됐습니다. `checkModuleArchitecture`의 Kotlin 파서는 별도 512MB worker 프로세스에서 돕니다.

## 검증

- CI(`.github/workflows/android-ci.yml`)가 PR에서 `check`와 `assembleDebug`를 돌립니다. **같은 전체 작업을 로컬에서 자동으로 반복하지 않습니다.**
- 로컬에서는 변경 모듈의 컴파일과 직접 관련된 테스트를 먼저 돌립니다. 전체 게이트는 사용자가 요청했거나 공통 빌드 로직을 바꿨을 때만 실행합니다.
- 로컬 검증에 변경 모듈의 `ktlintCheck`, `detekt`도 포함합니다. 몇 초면 끝나고, CI까지 끌고 갈 종류가 아닙니다.

  ```bash
  ./gradlew :feature:<모듈>:ktlintCheck :feature:<모듈>:detekt :feature:<모듈>:testDebugUnitTest
  ```

  기반 모듈은 `:core:<모듈>`, 앱은 `:app`입니다.
- PR 전 리뷰: `create-pr` 스킬이 PR을 만들기 전에 pr-review 스킬(Claude Code는 `pr-reviewer` 서브에이전트)로 diff를 리뷰하고, UI를 바꿨으면 ui-review 스킬(`ui-reviewer`)로 에뮬레이터 화면을 검사합니다. 둘은 동시에 실행할 수 있고, 남은 지적은 PR 본문 "리뷰 참고"에, 화면 검사 결과는 화면 절에 남깁니다.
- UI 동작에 대한 주장은 ui-review 스킬 절차(`installDebug`, `android screen capture`, `android layout`)로 에뮬레이터에서 확인합니다. 확인하지 못했으면 코드로만 판단했다고 밝힙니다.
