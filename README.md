# manyak-android

## 기술 스택

| 분류       | 사용 기술                                                        |
| ---------- | ---------------------------------------------------------------- |
| 언어·빌드  | Kotlin 2.3.21, AGP 9.3.1, Gradle 9.5, JDK 25                     |
| UI         | Jetpack Compose (BOM 2026.02.01), Material 3, 자체 디자인 시스템 |
| 내비게이션 | Navigation 3                                                     |
| DI         | Hilt                                                             |
| 네트워크   | Retrofit 3 + kotlinx.serialization, OkHttp 5, SSE(채팅 스트리밍) |
| 로컬 저장  | DataStore(세션·설정), Room(제작 진행 등 로컬 데이터)             |
| 이미지     | Coil 3                                                           |
| 인증       | Credential Manager + Google ID, Kakao SDK                        |
| 정적 검사  | ktlint, detekt, Android Lint                                     |

## 폴더 구조

```text
manyak-android/
├── build-logic/           # 공통 Gradle convention plugin · 모듈/계층 검사
├── gradle/                # Wrapper · 버전 카탈로그
├── app/                   # Navigation 3 백스택 · 메인 탭 · 세션 조율 · DI 조립
├── auth/                  # 인증 · 토큰 · 세션 · 계정 연동
├── network/               # HTTP · 직렬화 · 공통 통신 오류 변환
├── analytics/             # 이벤트 계약 · 분석/크래시 SDK
├── navigation/            # 라우트 단일 등록처
├── designsystem/          # ManyakTheme · 시각적 공용 컴포넌트
├── common/                # 실제 공통 entity/domain/data/presentation
├── report/                # 공유 신고 업무
├── home/, studio/, story/ # 오리지널 목록 · 내 목록 · 상세
├── chat/                  # list/presentation · room/presentation
├── create/                # keyword · storyline · additionalinfo
├── my/                    # profile · credit · invite · feedback · withdrawal · licenses
└── login/, legal/         # 로그인 · 웹 문서 화면
```

런타임 모듈은 16개이며 내부 계층·하위 기능은 Kotlin 패키지입니다. 구조·계층·공통 코드의 정본은 [하네스 Android 스펙](../knk-harness/docs/product-specs/3-3-android-app.md), 이동·검증 내역은 [모듈 재구성 기록](docs/plans/module-reorganization.md)을 확인하세요. 디자인 규칙은 [DESIGN.md](DESIGN.md)에 있습니다.

## 실행하기

### 사전 요구사항

- **JDK 25** — Gradle 데몬 toolchain(`gradle/gradle-daemon-jvm.properties`)이며 CI도 같은 값을 씁니다.
- **Android Studio** 최신 안정 버전, **Android SDK 37** (`compileSdk`/`targetSdk` 37, `minSdk` 24)
- 백엔드 서버는 따로 띄우지 않아도 됩니다. 기본값이 공용 개발 서버(`https://dev-api.manyak.app`)입니다.

### 설치 및 실행

```bash
# 로컬 설정 파일 생성
cp local.properties.example local.properties

# 연결된 기기·에뮬레이터에 디버그 빌드 설치
./gradlew installDebug
```

`local.properties`에 소셜 로그인 키를 채우지 않으면 빌드·실행은 되지만 로그인이 시작 단계에서 실패합니다. 키는 아래 두 개를 팀에서 받아 채웁니다.

| 키                              | 설명                                                       |
| ------------------------------- | ---------------------------------------------------------- |
| `GOOGLE_SERVER_CLIENT_ID_DEBUG` | 구글 **서버(웹) 클라이언트 ID**. Android 클라이언트 ID가 아닙니다 |
| `KAKAO_NATIVE_APP_KEY_DEBUG`    | 카카오 **네이티브 앱 키**. REST API 키가 아닙니다          |

로컬 서버에 붙이려면 `BASE_URL`을 채웁니다(끝에 `/`가 있어야 합니다). 나머지 항목의 설명은 `local.properties.example`에 있습니다. 값이 든 `local.properties`는 커밋하지 않습니다.

## 주요 명령

| 명령                           | 설명                                         |
| ------------------------------ | -------------------------------------------- |
| `./gradlew check`              | 모듈/계층 검사 · ktlint · detekt · Android Lint · 유닛 테스트 |
| `./gradlew assembleDebug`      | 디버그 APK 빌드                              |
| `./gradlew installDebug`       | 연결된 기기에 설치                           |
| `./gradlew ktlintFormat`       | 포맷 자동 수정                               |
| `./gradlew bundleRelease`      | 릴리스 AAB 빌드 (Play Console 업로드용)      |
| `./gradlew :app:signingReport` | 빌드 타입별 서명 구성 확인                   |

PR에서는 CI(`.github/workflows/android-ci.yml`)가 `check`와 `assembleDebug`를 실행합니다.

## 문서

| 문서                                                   | 내용                                        |
| ------------------------------------------------------ | ------------------------------------------- |
| `DESIGN.md`                                            | 색·타이포·여백·컴포넌트 규칙                |
| `CLAUDE.md`                                            | 이 저장소의 작업 규칙                       |
| `docs/plans/`                                          | 기능별 구현 순서와 그 기능에서 내린 결정    |
| `../knk-harness/docs/product-specs/3-3-android-app.md` | 앱 공통 기술 결정과 화면 구현 상태 매트릭스 |
