# KNK-1518 동의 상태와 문서 WebView 정합성

## 목표와 범위

서버가 필수 동의 완료를 확인하기 전에는 회원 분석 식별과 FCM 토큰 등록을 시작하지 않는다. 동의 조회와 저장의 403에는 안내와 로그아웃을 제공한다. 문서 WebView의 외부 웹 링크는 브라우저로 열고 같은 호스트의 문서 외 새 페이지 로드를 차단한다. API 요청에는 가능한 경우 Amplitude 세션 ID를 전달한다.

웹 본문과 로고 링크 제거는 KNK-1534의 완료 범위다. 앱 전용 문서, 진입 쿼리, User-Agent 구분은 추가하지 않는다.

추가 승인 범위는 로그인 화면에서 필수 동의를 완료한 뒤 회원 화면에 진입하도록 하는 Android 변경이다. 서버 로그인 API와 계정 생성 시점은 유지한다. 서버의 가입 전 동의 계약 변경은 서버 담당자에게 별도로 전달했으며, 이 변경만으로 동의 전 서버 계정 저장이 차단되지는 않는다.

## 구현 결정

- `legal`의 `ConsentRepositoryImpl`이 서버에서 확인한 메모리 동의 상태를 소유한다. `common`의 읽기 전용 `MemberConsent` 계약으로 공유하고 앱의 `UserScopedStore` 정리에 참여한다. 영속 완료 플래그는 두지 않는다.
- 조회 및 기록 응답은 기존 `SessionGate` 작업과 commit 경계에서 반영한다. 누락된 응답 항목은 완료로 간주하지 않는다. 앱 재시작은 새 조회 후 기존 동의 회원을 즉시 통과시킨다.
- 분석 식별은 회원 세션, 프로필, 필수 동의가 모두 준비됐을 때만 수행한다. FCM 토큰 갱신에도 같은 조건을 적용하며 같은 세션의 성공한 동일 토큰 등록은 반복하지 않는다.
- `network`는 SDK에 직접 의존하지 않고 `SessionIdAccess` 포트를 사용한다. `app`이 분석 어댑터에 연결한다. 매 요청의 현재 값이 없거나 음수이면 기존 헤더도 제거한다.
- 문서 새 페이지 로드는 HTTPS의 `/terms`, `/privacy`, `/about`으로 제한한다. 외부 HTTP(S)는 ACTION_VIEW로 열고 실행 가능한 다른 스킴은 차단한다. 클라이언트 라우팅 링크 제거는 웹이 담당한다.
- 루트가 동의 ViewModel을 소유하고 저장소 및 화면의 완료 상태를 확인한 뒤에만 회원 그래프를 구성한다. 확인 전에는 로그인 화면에 동의 시트를 표시하고 소셜 로그인 버튼을 잠근다. 조회 중 뒤로가기도 기존 로그아웃을 호출한다.
- 로그인 및 세션 복원의 프로필 자동 조회와 루트의 체험 잔여 자동 조회를 앱 수명의 `MemberDataLoader`로 모은다. 회원 세션과 필수 동의가 모두 준비된 경우에만 조회하며 조건이 해제되면 진행 중인 조회를 취소한다.
- 알림 권한은 필수 동의 후 회원 화면에서 요청한다. 권한 응답 뒤 시트에서 선택한 광고 동의 답을 처리하고 신규 가입 안내 및 광고 재질문으로 연결한다. 관련 결정은 하네스 A-052에 기록한다.

## 구현 순서와 완료 조건

1. 동의 상태 공유, 종료 정리, 분석 식별 및 푸시 등록 조건과 회귀 테스트.
2. 403 시트와 외부 링크 처리, 세션 헤더 및 회귀 테스트.
3. 하네스 spec/design/ADR 정정과 기기 검증 결과 기록.

동의 전 등록 0회, 서버 확인 후 동일 토큰 등록 1회, 계정 종료 뒤 늦은 응답 무효화, 403에서 재시도 대신 로그아웃 표시, 내부 문서 이동 및 외부 브라우저 실행이 완료 조건이다.

## 검증

위험 태그: `auth-session`, `session-cleanup`, `push-registration`, `race-condition`, `state-restore`, `observability`, `external-entry`.

변경 모듈 컴파일, ktlint, detekt, 단위 테스트와 모듈 경계를 확인한다. 설치 후 문서와 403 화면을 확인하고 다크 모드 재생성 전후를 비교한다. WebView의 웹 분석 중복 발화 여부는 별도로 관찰하고 근거에 따라 후속 처리를 결정한다.

## 실행 결과

2026-10-04 기준 다음 변경과 검증을 완료했다.

- `9e322630`: 필수 동의 상태 공유, 세션 종료 정리, 분석 식별 및 푸시 등록 조건, 세션 헤더와 회귀 테스트.
- `e1786a27`: 동의 API 403 안내와 로그아웃, 문서 탐색 정책, 단위 및 기기 UI 테스트.
- 하네스 Android spec/design/ADR A-051에 계약과 결정 근거를 반영했다.

### 자동 검증

| 검증 | 결과 |
| --- | --- |
| 변경 모듈 단위 테스트 | app 5, legal 17, notification 33, network 7, common 13, analytics 3. 총 78개 통과 |
| 정적 검사 | 위 6개 모듈의 `ktlintCheck`, `detekt` 통과 |
| 앱 컴파일 | `:app:compileDebugKotlin` 및 Hilt 생성 통과 |
| Android lint | app/legal/notification의 `lintDebug` 통과. Error/Fatal 0건, Warning 각각 25/1/2건 |
| 모듈 경계 | `checkModuleArchitecture` 통과. 모듈 17개, 운영 Kotlin 파일 429개 검사 |
| 설치 | `:app:installDebug` 성공. Pixel_10, Android 17/API 37, emulator-5554 |
| 기기 UI | `LegalConsentUiTest`, `LegalDocumentUiTest` 총 2개 통과 |

기기 UI 검증 명령은 `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.manyak.LegalConsentUiTest,app.manyak.LegalDocumentUiTest`다. 403 테스트는 제한 안내와 재시도 부재, 로그아웃 호출을 확인했다. 문서 테스트는 같은 호스트의 문서 외 페이지 로드 차단과 외부 링크의 `ACTION_VIEW`, `CATEGORY_BROWSABLE`, 대상 URI 전달을 확인했다. 외부 Activity 실행은 테스트의 ActivityMonitor가 가로채므로 실제 브라우저 페이지 렌더링까지 확인한 결과는 아니다.

최초 문서 UI 테스트는 `loadDataWithBaseURL`의 URL을 잘못 가정해 실패했다. 실제 초기 URL과 DOM 준비 상태를 기준으로 고친 뒤 두 UI 테스트가 통과했다. 이후 임시 스크린샷 캡처 코드만 제거하고 `:app:compileDebugAndroidTestKotlin`, app/legal의 `ktlintCheck`, `detekt`를 다시 통과했다. 전체 루트 `check`와 `assembleDebug`는 별도로 반복하지 않았다.

### 화면 관찰과 남은 확인

- 실제 앱의 마이에서 서비스 안내를 열고 밝은 모드, 다크 모드, 밝은 모드 복귀 순서로 비교했다. Activity 재생성 후 문서가 유지됐으며 테스트 종료 시 밝은 모드로 복구했다. 기기의 기존 계정에서 로그아웃하거나 계정을 변경하지 않았다.
- 운영 `https://manyak.app/about`에는 아직 브라우저 전용 게스트 안내 문구가 없고 홈 로고와 피드백 링크가 남아 있었다. 웹 `dev`의 KNK-1534 병합 코드와 운영 화면이 다르므로 웹 배포 후 본문 및 클라이언트 라우팅 탈출 경로를 다시 확인해야 한다. 앱의 새 페이지 로드 차단은 웹 클라이언트 라우팅 링크 제거를 대신하지 않는다.
- 앱의 문서 조회 분석 이벤트와 웹의 `useTrackOnView` 경로가 함께 존재하고 WebView의 JavaScript 및 DOM storage가 활성화되어 있다. 실제 WebView에서 웹 Amplitude 이벤트가 수신되는지는 네트워크 또는 분석 수집 결과로 확인하지 못했다. 따라서 중복 수신 여부를 확정하지 않았고 임의로 웹 분석을 차단하지 않았다.
- 동의 전 등록 차단, 서버 확인 뒤 등록, 토큰 갱신, 종료 후 늦은 응답 무효화는 가짜 저장소 및 API를 사용하는 단위 테스트로 확인했다. 실제 미동의 계정의 FCM 요청 전후 추적은 수행하지 않았다. 화면 재생성은 다크 모드 전환으로 확인했으며 회전과 글자 크기 변경을 각각 실행하지는 않았다.
- 로컬 증거: `/tmp/knk1518-forbidden.png`, `/tmp/knk1518-about-light.png`, `/tmp/knk1518-about-dark.png`, `/tmp/knk1518-about-restored.png`. 기기 UI 최종 실행 로그는 `/tmp/knk1518-ui.log`, 마지막 정적 검사 및 테스트 소스 컴파일 로그는 `/tmp/knk1518-final-check.log`다. 임시 파일은 장기 보관을 보장하지 않는다.

## 복구

변경을 되돌릴 때는 두 구현 커밋을 역순으로 revert하고 하네스의 현재 설계 설명을 함께 맞춘다. 영속 저장소 스키마 변경은 없으므로 별도 데이터 마이그레이션은 필요하지 않다. 기존 확정 ADR은 삭제하지 않고 후속 결정에 대체 범위를 기록한다.

## 로그인 화면 동의 게이트 후속 검증

2026-10-04, Android 기존 인증 API를 유지한 상태에서 다음을 확인했다.

- app/auth/login/legal 단위 테스트 총 50개 통과: 각각 9/23/1/17개. 동의 전 자동 조회 0회, 완료 후 조회, 다음 계정의 재확인, 동의 초기화 및 세션 종료 시 요청 취소, 정지 계정 종료 전달, 동의 대기 중 새 소셜 로그인 거부를 포함한다.
- app/auth/login/legal의 ktlint와 detekt 통과. app/login/legal의 Android lint는 Error/Fatal 0건이며 Warning은 각각 25/5/1건이다.
- `checkModuleArchitecture` 통과: 모듈 17개와 운영 Kotlin 파일 431개. 앱 Hilt 생성 및 컴파일과 `:app:installDebug` 성공.
- Pixel_10 Android 17/API 37에서 `MemberConsentGateUiTest` 4개와 기존 동의 및 문서 테스트 2개, 총 6개 통과. 조회 대기, 조회 실패, 403, 저장 실패, 뒤로가기 및 상태 복원 동안 회원 화면 구성 0회를 확인했다. 이미 동의한 회원과 저장 성공 뒤에는 회원 화면을 구성한다.
- `StateRestorationTester`로 저장 상태 복원을 재현한 전후에 실제 로그인 화면과 동의 시트를 `adb exec-out screencap -p`로 캡처했다. 전체 체크와 제출 버튼 상태, 로그인 배경이 유지됨을 확인했다. 경로는 `/tmp/knk1518-login-consent-before.png`, `/tmp/knk1518-login-consent-restored.png`다.
- 캡처는 가짜 동의 저장소를 사용한 기기 테스트다. 실제 미동의 계정의 가입/동의 제출은 하지 않았고, 이번 후속 검증에서 시스템 회전과 다크 모드 변경으로 Activity 재생성을 별도로 실행하지 않았다. 시스템 다크 모드는 no, 활동 유지 안 함은 null임을 확인했으며 기기 설정은 변경하지 않았다.
- 캡처용 임시 로그와 대기 코드는 제거했다. 최종 검증 로그는 `/tmp/knk1518-gate-final.log`, 설치/모듈/정적 검사/lint 로그는 `/tmp/knk1518-gate-check2.log`다. 루트 전체 `check`와 `assembleDebug`는 집중 검증 정책에 따라 반복하지 않았다.

후속 변경 복구 시에는 로그인 화면 동의 게이트 커밋을 먼저 revert하고 하네스 A-052의 대체 범위를 후속 결정에 기록한다. 서버의 동의 전 계정 생성과 운영 웹 본문 반영 및 WebView 분석 수신 확인은 앞 절의 미완료 범위로 유지한다.
