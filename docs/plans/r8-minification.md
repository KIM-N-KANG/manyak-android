# release 빌드 R8 축소·난독화 적용

- 티켓: [KNK-1408](https://kimandkang.atlassian.net/browse/KNK-1408)
- 작성일: 2026-09-26
- 상태: 구현·에뮬레이터 검증 완료, Play Console 확인은 다음 업로드 뒤
- Android: `chore/KNK-1408-enable-r8-minification-for-release` — fetch한 `origin/dev`의 `74a10daa`
- 하네스: `docs/KNK-1408-enable-r8-minification-for-release` — fetch한 `origin/dev`의 `ab5f483`
- 정본: [Android ADR A-047](../../../knk-harness/docs/adr/1-3-android-adr.md#a-047), [배포 Design](../../../knk-harness/docs/design/4-deployment.md#manyak-android-ci)

## 목표와 범위

Play Console의 "DEX 코드 최적화가 기준점 미만"(v1.1.1, 난독화 1%)을 해소합니다. release에 R8 코드·리소스 축소와 난독화를 켜고, 깨지는 곳만 keep 규칙으로 막고, Crashlytics가 역난독할 매핑을 받게 합니다. debug는 바꾸지 않습니다.

## 구현

| 영역 | 구현 |
| --- | --- |
| R8 | `app/build.gradle.kts` release의 `optimization { enable = true }`와 `keepRules { files.add(file("proguard-rules.pro")) }`. AGP 9 DSL이라 `isMinifyEnabled`·`isShrinkResources`를 따로 두지 않고, 리소스 축소도 함께 켜짐(`convertShrunkResourcesToBinaryRelease` 실행 확인) |
| Crashlytics 매핑 | 플러그인이 `variant.isMinifyEnabled`로 업로드 여부를 정하는데 `optimization` DSL을 반영하지 않아 `uploadCrashlyticsMappingFileRelease`가 생기지 않았음. release에 `mappingFileUploadEnabled = true`를 명시해 `bundleRelease`에 연결 |
| AAB 매핑 | `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`에 자동 포함(Play 역난독용) |
| keep: `DomainError` | `LoginOauthErrorShown`·`ReportFailed`가 `error::class.simpleName`을 보냄. 이름만 남기면 부족하고 R8이 바깥 클래스 이름까지 있어야 `InnerClass` 속성을 남김. dex에서 `name="ProviderFailed"` 등 확인 |
| keep: 카카오 SDK | `ClientError` 생성자가 `ClientErrorCause`의 필드를 `getField(reason.name)`으로 읽는데 AAR에 규칙이 없음. 없으면 카카오 토큰이 없는 상태에서 시작 직후 `NoSuchFieldException: TokenNotFound`로 크래시. 카카오 공식 규칙 `-keep class com.kakao.sdk.**.model.* { <fields>; }` |
| consumer rules로 충분 | kotlinx.serialization, Retrofit 3, Room, Hilt, Credential Manager·Google ID, Navigation3, Firebase, Amplitude, Coil이 자체 규칙을 실음. enum `.name` 저장값(DataStore·Room)은 R8이 이름 문자열을 바꾸지 않아 영향 없음. `ProviderFailed.diagnostic`에 실리는 라이브러리 예외 이름은 읽는 곳이 없어 두지 않음 |

## 검증

로컬 업로드 키로 서명한 release는 운영 로그인이 되지 않으므로([crashlytics.md](./crashlytics.md)), 로그인 뒤 흐름은 debug에 같은 설정(`isDebuggable = false` + 같은 `optimization`·keep 규칙)을 임시로 켜 dev 서버에서 확인하고 되돌렸습니다.

- 빌드: `:app:assembleRelease`·`:app:bundleRelease`(업로드 제외) 성공, `bundleRelease --dry-run`에 `uploadCrashlyticsMappingFileRelease` 포함, debug에는 업로드 태스크 없음. `:app:ktlintCheck`·`ktlintKotlinScriptCheck` 통과.
- 에뮬레이터, R8 debug(dev 서버): Google 로그인 → 홈 목록 → 스토리 상세 → 회전·`am kill` 뒤 상세 라우트 복원 → 채팅 목록 → 채팅방 추천 입력 전송과 스트리밍 응답·인물 이미지 → 제작 키워드 태그 로딩·임시 저장·초안 카드·재개 시 선택 복원·삭제 → 마이·크레딧 충전·내역 → 오픈소스 라이선스(raw 리소스) → 로그아웃 → 카카오 로그인 시작 후 취소(오류 안내 없음) → 푸시 진입 extra로 스토리 상세(deepLink)·크레딧 충전(type) 이동. 카카오 규칙 추가 전에는 첫 실행에서 위 크래시가 재현됐고, 추가 뒤에는 크래시 없음.
- 에뮬레이터, 서명된 release: 설치 후 로그인 화면까지 크래시 없음, Crashlytics·Amplitude 초기화 로그 확인.
- 미검증: 토큰 갱신 경로(만료를 만들 수 없음), Amplitude 실제 전송(SDK가 로그를 남기지 않음), Crashlytics 매핑 업로드와 역난독 리포트(다음 `bundleRelease`에서 업로드됨), Play Console의 DEX 최적화 문제 해소(업로드 뒤 확인).
