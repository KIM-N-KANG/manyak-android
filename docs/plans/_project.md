# 프로젝트 문서와 구현 기준

제품·기술 문서의 정본은 하네스입니다. 이 문서는 포인터와 이 저장소의 코드·CI가 소유하는 값만 둡니다.

| 확인할 것 | 정본 |
| --- | --- |
| 공통 화면·동작·API 사용 계약 | [공통 spec](../../../knk-harness/docs/product-specs/3-1-client-spec.md) |
| Android 사용자 모델·플랫폼 예외·수용 기준 | [Android spec](../../../knk-harness/docs/product-specs/3-3-android-spec.md) |
| 스택·MVI·내비게이션·세션·저장·라이프사이클·관측 구조 | [Android design](../../../knk-harness/docs/product-specs/3-3-android-design.md) |
| 모듈·계층·의존 방향 | [모듈 아키텍처](../../../knk-harness/docs/planning/android-module-architecture.md) |
| 중요한 선택 이유·대체 관계 | [공통 ADR](../../../knk-harness/docs/product-specs/3-1-client-adr.md) · [Android ADR](../../../knk-harness/docs/product-specs/3-3-android-adr.md) |
| 승인·적용·구현·검증·배포·Jira 연결 | [클라이언트 추적](../../../knk-harness/docs/planning/client-tracking.md) |
| Phase·일정 | [로드맵](../../../knk-harness/docs/planning/roadmap.md) |

기능 계획은 변경 순서·검증·복구를 소유합니다. 완료한 계획은 당시 실행 기록으로 보관하고 현재 문서의 사본으로 갱신하지 않습니다. 이전 계획의 모듈·명령은 당시 기준이며 현행 규칙은 위 정본을 확인합니다. 모듈 이전의 실행 기록은 [module-reorganization.md](./module-reorganization.md)에 있습니다.

## 이 레포가 소유하는 결정

하네스가 다루지 않고 이 레포의 코드·CI에 근거가 있는 값입니다.

| 영역 | 결정 | 근거 |
| --- | --- | --- |
| 빌드 버전 | JDK, SDK, 플러그인 버전은 실제 Gradle·CI 파일을 정본으로 사용합니다. 문서에 사본을 두지 않습니다. | `gradle/libs.versions.toml`, `gradle/gradle-daemon-jvm.properties` |
| 디자인 토큰·테마 | 색·타이포·여백·모서리는 디자인 토큰에서 파생합니다. 사용 규칙과 갱신 절차는 `DESIGN.md`에 있습니다. | `DESIGN.md` |
| 서버 base URL 주입 | 빌드 타입별 `BuildConfig.BASE_URL`로 주입합니다. debug는 dev 서버 `https://dev-api.manyak.app/api/v1/`가 기본이고 `local.properties`의 `BASE_URL`로 덮어씁니다. release는 `https://api.manyak.app/api/v1/`입니다. | `app/build.gradle.kts` |
| 평문 통신 | debug 소스 세트의 network security config에서 로컬 호스트에만 허용합니다. release 매니페스트에는 포함되지 않습니다. | `app/src/debug/res/xml/network_security_config.xml` |
| 모듈 품질 게이트 | 새 모듈은 역할에 필요한 정적 검사·lint·테스트가 루트 CI 게이트에 연결되고, 리포트를 모듈 경로와 무관하게 수집할 수 있어야 합니다. | `.github/workflows/android-ci.yml` |
| 모듈·계층 검사 | `checkModuleArchitecture`가 프로젝트 의존과 Kotlin PSI의 import·전체 경로·별칭·typealias 참조를 검사합니다. 파서는 Gradle 작업의 별도 worker 프로세스에서 실행합니다. | `build-logic/src/main/kotlin/architecture/`, 루트 `check` |
| Gradle 힙 | 16개 Android 모듈의 전체 검사에서 기존 2GB 데몬이 GC thrashing으로 종료되어 4GB로 설정했습니다. Kotlin 경계 검사는 별도 512MB worker 프로세스에서 실행합니다. | `gradle.properties`, `CheckModuleArchitecture.kt` |
| 로컬 검증 | 변경된 모듈의 관련 테스트 또는 가장 작은 컴파일 작업을 우선합니다. | 빠른 피드백 유지 |
| PR 검증 | CI의 `./gradlew check`와 `./gradlew assembleDebug`가 전체 기본 게이트입니다. 로컬에서 자동으로 중복 실행하지 않습니다. | `.github/workflows/android-ci.yml` |
| 수동 검증 | 자동화할 수 없는 고위험 경로만 기능 완료 시 확인합니다. 전체 기기·언어·버전 매트릭스는 릴리스 또는 명시적 QA에서 수행합니다. | 검증 비용을 변경 위험에 맞추기 위함 |
| 서명·단계적 배포 | 하네스 `7-deployment.md`가 소유합니다 — 빌드·서명키 보관은 §7-5, `main` 기준 릴리스 절차와 버전 규칙은 §7-7, 검수·단계적 출시·중단 기준·롤백은 §7-9. 이 레포에는 사본을 두지 않습니다. | `../knk-harness/docs/product-specs/7-deployment.md` |

## 갱신 규칙

- **공통 기술 결정은 하네스에 쓰고 이 문서에는 포인터만 둡니다.** 여기에 결정을 복사하지 않습니다.
- 한 기능의 선택도 핵심 구조·트레이드오프·계약에 영향을 주면 영역별 ADR에 남깁니다. 단순 구현 세부는 코드, 일회성 변경 방법은 기능 계획이 소유합니다.
- 이 레포가 소유하는 값은 근거 파일이 실제로 바뀔 때만 갱신합니다.
- 구현을 요청받은 작업은 이미 변경 권한이 있는 것으로 보고, 스킬이 같은 범위의 승인을 다시 요구하지 않습니다.
- 설계·감사·리뷰만 요청받은 작업은 코드나 외부 시스템을 변경하지 않습니다.
