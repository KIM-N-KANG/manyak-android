# 기본 지침

작업을 시작하기 전에 `../knk-harness` 경로에 있는 하네스 레포지토리를 먼저 확인하세요.

## 정본 문서

| 확인할 것 | 위치                                                 |
| --- |----------------------------------------------------|
| 앱 공통 기술 결정(구조·계층·인증·내비게이션)과 화면·흐름 구현 상태 매트릭스 | `../knk-harness/docs/product-specs/3-3-android-app.md` |
| 웹·앱 공통 계약(화면·상태·사용자 흐름·API 사용) | `../knk-harness/docs/product-specs/3-1-client.md`  |
| 이 레포가 소유하는 값(빌드 주입·검증 정책) | `docs/plans/_project.md`                           |
| 색·타이포·여백·컴포넌트 규칙 | `DESIGN.md`                                        |
| 기능별 구현 순서와 그 기능에서 새로 내린 결정 | `docs/plans/<기능>.md`                               |
| **서버 API 계약(엔드포인트·요청·응답 필드·상태 코드)** | **dev 서버 Swagger** `https://dev-api.manyak.app/swagger-ui/index.html` (기계 판독은 `https://dev-api.manyak.app/v3/api-docs`) |

- 공통 결정은 하네스에 쓰고 이 레포에는 포인터만 둡니다. 같은 결정을 두 곳에 두지 않습니다.
- 하네스 문서를 읽을 때는 해당 절이 `dev`에 병합됐는지 확인하세요. 미병합 브랜치 내용을 정본으로 오인하면 잘못된 전제 위에서 작업하게 됩니다.
- **API 관련 확인은 하네스(`4-backend.md`)가 아니라 Swagger를 직접 봅니다.** 하네스의 API 절은 서버 구현보다 뒤처질 수 있어(예: `author`·`isOwner`가 구현됐는데 문서는 "계획"으로 남아 있었음) 응답 필드 유무·타입·상태 코드는 반드시 Swagger 또는 `../manyak-server` 코드로 확인합니다. 하네스와 Swagger가 다르면 Swagger가 맞고, 그 차이를 하네스에 정정합니다.

## 작업 방식

- **기능 작업은 하네스 스펙을 먼저 갱신하고 그 문서를 기반으로 구현합니다.** 구현이 끝나면 하네스의 구현 상태 매트릭스도 함께 갱신합니다.
- 지라 티켓을 새로 만들지 않습니다. 두 레포 각각의 현재 브랜치에서 작업합니다.
- 구현을 요청받았으면 중간에 되묻지 말고 판단해서 끝까지 진행합니다. 되돌리기 비싼 결정(새 API 계약·저장 스키마·인증·권한)만 확인을 받습니다.
- 문제를 찾아 달라고 한 작업은 고치지 말고 먼저 정리해 보고합니다.

## Git

- 브랜치·커밋·PR은 하네스 `.agents/skills/`의 `create-branch`·`create-commit`·`create-pr`를 **먼저 읽고** 그대로 따릅니다.
- 커밋 제목은 `[KNK-번호] 태그: 제목`이고 **명사형으로 끝냅니다** — "~ 구현", "~ 추가", "~ 교체". "~한다" 서술형은 쓰지 않습니다.
- `Co-Authored-By: Claude`, "Generated with Claude Code" 같은 귀속 문구를 커밋·PR에 넣지 않습니다.
- `git add -A` 대신 `git add -- <경로>`로 검토한 파일만 stage합니다. 본문이 있으면 메시지 파일 하나를 만들어 `git commit --file`로 커밋합니다.
- 같은 작업의 반복 조정(크기·색·자산 교체)은 커밋을 쪼개지 않고 확정된 최종 상태로 하나만 남깁니다.
- **`git push`와 PR 생성은 명시적으로 요청받기 전에 하지 않습니다.** 완료 보고에 "푸시할까요?" 같은 제안도 붙이지 않습니다.

## 코드 규칙

- 색·크기·여백·모서리는 `ManyakTheme` 접근자로만 읽습니다. 팔레트 값(`#05A66B` 등)이나 `MaterialTheme.colorScheme`·`MaterialTheme.typography`를 화면 코드에서 직접 쓰지 않습니다. 토큰에 없는 값이 필요하면 Kotlin 토큰 파일과 `DESIGN.md` 표를 함께 고칩니다(`DESIGN.md` 갱신 지침).
- 사용자에게 보이는 문자열은 전부 `core/ui/src/main/res/values/strings.xml`에 둡니다. 화면 코드에 문구를 직접 쓰지 않습니다.
- 코드 주석에 `하네스 §3-3-3`·`공통 계약`·`FE-SCREEN-008`·`검수 #4` 같은 스펙 참조를 넣지 않습니다. 코드만 보고는 알 수 없는 이유만 남기고, 결정 근거는 `docs/plans/*.md`가 소유합니다.
- 화면 ViewModel은 `:core:ui`의 `MviViewModel`을 상속합니다(Intent → 부수효과 → Event → `reduce` → State, 일회성 출력은 Effect). `reduce`는 순수 함수입니다.
- 화면 부수효과는 구성 변경(회전·다크 모드·글자 크기)에서 다시 실행되고 `remember` 값은 사라집니다. 되돌리기·초기화·저장을 하는 효과는 `configuration-changes` 스킬 기준으로 점검합니다.
- 라우트는 `:core:navigation`의 `Routes.kt` 한 곳에만 등록하고 **복원 가능한 식별자만** 싣습니다. `:feature:*` 끼리 직접 참조하지 않습니다.
- 오류는 `:core:domain`의 `DomainError`로 올리고 문구는 `:core:ui`의 `DomainErrorMessages`가 붙입니다.
- 두 번째 사용처가 생기기 전에는 공용 컴포넌트를 `:core:ui`로 올리지 않습니다.

## 모듈 구조

`app`이 모든 모듈을 조립하고 `:feature:*`는 서로 참조하지 않습니다. 화면은 모듈 루트 패키지에 두고 모듈 이름을 화면·탭 이름과 맞춥니다.

| 모듈 | 소유하는 것 |
| --- | --- |
| `:core:domain` | 순수 Kotlin. 도메인 모델 · Repository 계약 · `DomainError` |
| `:core:data` | Repository 구현 · Retrofit API · DataStore · Room · 인터셉터 · 세션 토큰 관리 · 소셜 SDK 어댑터 |
| `:core:ui` | 디자인 시스템(`ManyakTheme`) · 공용 컴포저블 · `MviViewModel` · 문자열 리소스 전량 |
| `:core:navigation` | 타입 안전 라우트의 단일 등록처 |
| `:feature:login` `legal` `home` `chat` `studio` `my` `create` | 화면 단위 |
| `app` | Navigation 3 백스택 · 메인 탭 셸 · 세션 부트스트랩·종료 조율 · DI 조립 |

화면별·흐름별 구현 진척은 하네스 `3-3-android-app.md`의 매트릭스가 정본입니다.

## 명령어

```bash
./gradlew check          # ktlint · detekt · Android lint · testDebugUnitTest
./gradlew assembleDebug  # 리소스·매니페스트까지 확인
./gradlew installDebug   # 연결된 기기에 설치
```

- 포맷 규칙은 ktlint(`.editorconfig`), 코드 스멜은 detekt(`config/detekt/detekt.yml`)가 봅니다. 둘 다 `ignoreFailures = false`입니다.
- 서버 주소와 소셜 로그인 키는 `local.properties`에서 읽습니다. `cp local.properties.example local.properties` 후 채우고, 값이 없어도 빌드는 되며 해당 제공자 로그인만 실패합니다.
- Gradle 데몬 toolchain은 JDK 25(`gradle/gradle-daemon-jvm.properties`)이고 CI도 같은 값을 씁니다.

## 검증

- CI(`.github/workflows/android-ci.yml`)가 PR에서 `check`와 `assembleDebug`를 돌립니다. **같은 전체 작업을 로컬에서 자동으로 반복하지 않습니다.**
- 로컬에서는 변경 모듈의 컴파일과 직접 관련된 테스트를 먼저 돌립니다. 전체 게이트는 사용자가 요청했거나 공통 빌드 로직을 바꿨을 때만 실행합니다.
- 로컬 검증에 변경 모듈의 `ktlintCheck`·`detekt`도 포함합니다. 몇 초면 끝나고, CI까지 끌고 갈 종류가 아닙니다.
- UI 동작에 대한 주장은 `installDebug` 후 에뮬레이터에서 `adb exec-out screencap`으로 전후를 비교해 확인합니다. 확인하지 못했으면 코드로만 판단했다고 밝힙니다.
