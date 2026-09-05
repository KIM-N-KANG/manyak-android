# KNK-1197 모듈·패키지 재구성 실행 계획

- 작성일: 2026-09-05
- 상태: **구현 진행 중**
- 후속 구현 요청에 따라 모듈별로 이동·검증·커밋을 진행합니다.
- 공통 설계 제안: [하네스 Android 모듈 재구성 설계](../../../knk-harness/docs/planning/android-module-architecture.md)
- 현행 정본: [Android 스펙](../../../knk-harness/docs/product-specs/3-3-android-app.md), 로컬 검증 정책: [_project.md](./_project.md)

## 1. 목표와 기준 상태

사용자가 합의한 최상위 기능·기반 모듈과 단일 common 모듈로 전환하고, chat·create·my 내부를 실제 화면/업무 경계로 나눕니다. 공통 책임·계층·의존 방향의 제안은 하네스 문서가 소유하고, 이 문서는 현재 파일의 이동 범위·순서·검증만 소유합니다.

| 대상 | 확인한 상태 |
| --- | --- |
| Android | `refactor/KNK-1197-improve-folder-structure`, 기준 HEAD `5ee07c6` |
| 하네스 | `dev`, 기준 HEAD `eaf081c`; 현행 구조 절이 해당 dev에 존재함을 확인 |
| 기존 로컬 변경 | `.idea/gradle.xml` 수정이 존재함. 이 작업에서 소유하거나 되돌리지 않음 |
| 실제 등록 모듈 | [settings.gradle.kts](../../settings.gradle.kts)의 app·core 5개·feature 8개, 총 14개 |
| CI | [android-ci.yml](../../.github/workflows/android-ci.yml)의 check·assembleDebug, 모듈 경로와 무관한 리포트 수집 |

아래 클래스·파일 이름은 현재 코드를 기준으로 합니다. 새 포트 이름은 하네스의 설계 후보이며 구현된 타입을 뜻하지 않습니다. 실행 시에는 두 레포의 현재 브랜치를 다시 확인하고 그 브랜치에서 진행합니다. 현재 브랜치를 유지하며, 사용자 요청에 따라 모듈당 로컬 커밋 하나를 작성합니다. 티켓·PR·푸시는 이 작업의 범위에 포함하지 않습니다.

완료 범위는 기존 화면·데이터·인증 동작을 보존한 구조 전환입니다. 기능 추가, 서버 계약 변경, 저장 스키마 변경, 의존성 버전 업그레이드, 디자인 변경, 새 ViewModel/스코프 도입은 제외합니다.

## 2. 현재 코드에서 확인한 이동 단위

### 2.1 화면과 테스트

| 현재 파일 묶음 | 목표 소유 위치 | 함께 옮기거나 확인할 것 |
| --- | --- | --- |
| [ChatListScreen](../../feature/chat/src/main/java/app/manyak/feature/chat/ChatListScreen.kt), ChatListViewModel·ChatCard·ChatListSkeleton·RelativeTime | `chat/list/presentation` | 목록 ViewModel·상대 시간 테스트 |
| [ChatRoomScreen](../../feature/chat/src/main/java/app/manyak/feature/chat/ChatRoomScreen.kt), ChatRoomViewModel·Transcript·TurnStream·Regenerate·AnchorPad·Status | `chat/room/presentation` | 삭제·재생성·스트림·추천·앵커 테스트 |
| chat의 composer·message·suggestion | `chat/room/presentation` 하위 패키지 | 기존 내부 참조를 같은 chat 모듈 안에서 유지; 같은 위치로 관련 테스트 이동 |
| [CreateKeywordScreen](../../feature/create/src/main/java/app/manyak/feature/create/CreateKeywordScreen.kt), Keyword 입력·캐릭터·성별 선택·선택 키워드 UI·Reducer | `create/keyword/presentation` | 키워드 상태·ViewModel 테스트; GenderSelectField는 KeywordCharacterForm에서 사용 |
| CreateStorylineScreen·ViewModel·RatingButtons | `create/storyline/presentation` | 스토리라인 ViewModel 테스트 |
| CreateAdditionalInfoScreen·ViewModel·AdditionalInfoSections | `create/additionalinfo/presentation` | 추가 정보 ViewModel 테스트 |
| [StorylineGenerationStore](../../feature/create/src/main/java/app/manyak/feature/create/StorylineGenerationStore.kt), DraftSave·CreateFunnelModule | `create/presentation/state`, `create/presentation/di` | 기존 ActivityRetained 수명·Scope와 스토어 테스트 유지 |
| CreateFunnelChrome·CreateGeneratingLoading·PreviewFixtures | `create/presentation/component`, `create/presentation/preview` | 단계 간 실제 공유만 남김 |
| MyScreen·MyViewModel·ProfileHeader·CreditCard·MenuItem·AccountLinkDialogs | `my/profile/presentation` | MyViewModelTest; 계정 연동 UI와 auth 구현을 구분 |
| CreditCharge·CreditFreeCharge·CreditHistory 파일 묶음 | `my/credit/presentation` | CreditChargeViewModelTest |
| InviteScreen·ViewModel·ShareLinkProvider·OnboardingSheet·OnboardingViewModel | `my/invite`의 domain/presentation, `presentation/onboarding` | 공유 링크 공급자는 domain, 초기 안내 표시 수명은 앱 수준 계약 유지 |
| Feedback·Withdrawal·OpenSourceLicense 파일 묶음 | `my/feedback`, `my/withdrawal`, `my/licenses` | 각각 필요한 계층만; FeedbackViewModelTest |
| MyDetailHeader·MyFormComponents·MyProviderResources | `my/presentation/component` 후보 | 실제 소비자가 하나라면 해당 하위 기능으로 배치 |
| StoryDetail 및 상세 내부 UI | `story/detail/presentation` | 상세 ViewModel 테스트·FakeRepositories |
| home·studio·login·legal 화면 | 각 모듈의 `presentation` | 테스트는 구현과 같은 패키지; LegalUrlProvider는 legal/domain |

여러 테스트가 사용하는 Fake는 해당 모듈의 `src/test` 공유 패키지로 둡니다. 테스트 편의를 위해 프로덕션 모델/구현을 common으로 이동하거나, 기능 간 테스트 의존을 추가하지 않습니다. 필요한 타입·함수 가시성은 실제 소비자와 컴파일 결과에 따라 조정합니다.

### 2.2 기반·데이터·공유 업무

| 현재 파일/책임 | 목표 소유자 | 이동 시 해소할 참조 |
| --- | --- | --- |
| [MviViewModel](../../core/ui/src/main/java/app/manyak/core/ui/mvi/MviViewModel.kt)·DomainErrorMessages | common/presentation | 공통 오류·세션 안내 값과 문자열도 함께 배치; designsystem 역참조 금지 |
| DomainResult·DomainError와 실제 공유 값/포트 | common/domain·entity | DomainError의 AuthProvider 참조를 빠뜨리지 않음 |
| ManyakTheme·토큰·기본 UI·폰트·시각 자산 | designsystem | data·analytics·기능 모델에 의존하는 업무 코드가 함께 들어가지 않도록 분리 |
| [CreditAmount](../../core/ui/src/main/java/app/manyak/core/ui/credit/CreditAmount.kt) | 정책 전달/숫자 변환은 common, 시각적 로딩 표현은 designsystem | 기존 rememberSkeletonPulseAlpha 호출 때문에 파일째 이동할 수 없음 |
| [Routes](../../core/navigation/src/main/java/app/manyak/core/navigation/Routes.kt) | navigation | 첫 이동에서 Kotlin 패키지·직렬화 타입 이름 보존 |
| [AnalyticsEvent](../../core/analytics/src/main/java/app/manyak/core/analytics/AnalyticsEvent.kt)·SDK 구현 | analytics | 기능 enum 변환을 호출부로 옮겨 분석→기능 역참조 제거 |
| [NetworkModule](../../core/data/src/main/java/app/manyak/core/data/di/NetworkModule.kt)·ApiCall·인터셉터 | network와 각 API 소유 기능의 data/di | 클라이언트/직렬화 기반과 업무 API provider를 분리; timeout·qualifier 유지 |
| [SessionTokenManager](../../core/data/src/main/java/app/manyak/core/data/session/SessionTokenManager.kt)·SessionGate·TokenStore·Crypto·소셜 SDK·AccountLink | auth | 인터셉터의 구현 참조를 토큰 접근 계약으로 교체; auth→my 금지 |
| [SessionRepositoryImpl](../../core/data/src/main/java/app/manyak/core/data/repository/SessionRepositoryImpl.kt) | auth/data | 프로필은 공통 계약, 초대 안내는 최소 writer로 사용; 기록 시점 유지 |
| [UserApi](../../core/data/src/main/java/app/manyak/core/data/api/UserApi.kt) | auth·chat·studio·my의 해당 data/api | me·withdraw·myChats·myStories/delete·credit·invite 동작별 소유자를 정해 분리 |
| StoryRepository·구현·StoryApi/DetailApi | home·studio·story·report의 소유 동작 | 목록·상세·삭제·신고 계약을 분리; 원래 Repository 전체를 common으로 이동하지 않음 |
| ChatRepository·ChatApi·SSE·ChatPreferencesStore | chat/domain·entity·data | create/story에는 채팅 생성 포트와 결과 ID만 공개 |
| StoryCreationRepository·SimpleStory/Generation/Rating/CreationRequest API | create/domain·entity·data | 관련 DTO·생성 함수·DI provider를 같은 단위로 이동 |
| [ManyakDatabase](../../core/data/src/main/java/app/manyak/core/data/database/ManyakDatabase.kt)·PendingStoryCreationRoomStore·LegacyPendingCreationFile | create/data | studio에 관찰/폐기 계약 제공; Application의 레거시 파일 정리 연결도 유지 |
| UserProfileRepositoryImpl·ProfileCacheStore·InviteOnboardingStore | my/profile·my/invite, my 공통 data 구성 | 같은 profile DataStore 파일의 단일 인스턴스 유지; 사용자 귀속 정리 등록 |
| Credit·Invite·Feedback 계약/구현 | my의 해당 하위 기능 | 공개 이프 정책만 common 계약으로 앱 루트에 제공 |
| [StoryReport](../../core/ui/src/main/java/app/manyak/core/ui/report/StoryReport.kt)·StoryReportSheet | 권장안 report | 네 화면의 상태/Scope 유지; Repository·API·분석과 함께 공유 업무로 분리 |
| DeviceIdStore·ThemePreferencesStore·공통 dispatcher 기반 | common/data 및 필요한 공통 계약 | 기존 파일·키 유지, 기기 ID의 HTTP/분석 단일 값 유지 |
| [SessionCleanupSteps](../../app/src/main/java/app/manyak/session/SessionCleanupSteps.kt)·종료 Coordinator·루트/탭 | app 유지 | 각 저장소 정리 구현의 중앙 등록·순서와 화면 진입 연결만 갱신 |

파일 이동 전 각 public 타입·top-level 함수·extension의 정의/사용처와 리소스 소비자를 대응표로 고정합니다. 전역 문자열 치환으로 패키지와 import를 일괄 처리하지 않습니다. 특히 같은 이름의 `toDomain`, 암시적 동일 패키지 참조, Compose의 getValue/setValue 연산자 import, Kotlin fully qualified 타입, XML 클래스 참조를 따로 확인합니다.

## 3. 실행 순서와 단계별 종료 조건

각 단계는 앱이 조립되는 상태에서 종료합니다. 이전 core 모듈은 전환 중 남을 수 있지만, `이전 core → 새 기능 → 이전 core` 순환을 만드는 임시 의존은 허용하지 않습니다. 되돌림 단위는 해당 이동과 그 소비자·DI·리소스 변경을 포함한 한 단계이며, 사용자 데이터 삭제나 Gradle 캐시 초기화를 되돌림 수단으로 쓰지 않습니다.

### P0. 전환 기준 고정

1. 변경 시작 시 git 상태·사용자 변경·현행 테스트 목록을 기록합니다.
2. 하네스 3-3에 제안 문서를 가리키는 전환 절과 이번에 대체할 규칙을 반영합니다. 기존 구현 상태 매트릭스는 완료로 올리지 않습니다.
3. Android AGENTS.md의 구조/문자열 규칙과 `_project.md`의 포인터를 전환 단계에 맞춥니다. 공통 설계를 중복 작성하지 않습니다.
4. 저장 식별자·Room 스키마·라우트 직렬화 이름·리소스 원문·분석 이벤트의 비교 기준을 확보합니다. 테스트 fixture에는 합성 데이터만 사용합니다.

종료 조건: 소유 파일 목록, 보존 목록, 검사할 기존 테스트와 기존 실패가 구분되어 있습니다. 현재 확인된 사용자 변경인 `.idea/gradle.xml`은 작업에 포함하지 않습니다.

### P1. 기능 모듈의 최상위 이동과 화면 패키지 구분

1. `:feature:*`를 `:chat`·`:create` 등으로 옮기고 settings와 app의 의존을 갱신합니다.
2. §2.1의 화면·테스트·내부 컴포넌트를 같은 기능 모듈 안에서 재배치합니다.
3. 이 단계에서는 데이터 계층·기존 리소스 위치를 유지하여 화면 패키지 변경과 데이터 의존 재설계를 분리합니다.

종료 조건: 모든 진입 화면이 app에서 연결되고 이동 모듈·app 컴파일, 관련 ViewModel/순수 로직 테스트, ktlint·detekt가 통과합니다. Compose 동작을 검증했다고 보고하려면 별도의 설치/기기 확인이 필요합니다.

### P2. 공통 Gradle 설정 추출

1. 버전 변경 없이 Android Library·Compose·Hilt/KSP·품질 검사 설정을 build-logic으로 추출합니다.
2. 모듈 역할에 따라 적용하고 실제 라이브러리 의존은 명시합니다. 앱 환경·서명·서비스 플러그인 설정은 app에 남깁니다.
3. included build의 검사 연결, 생성 소스 제외, configuration cache, 경로에 독립적인 리포트 수집을 확인합니다.

종료 조건: 동일 variant·SDK·검사 실패 정책을 유지하고 새 구성으로 앱이 컴파일됩니다. 전체 게이트는 공통 빌드 변경의 검증에 필요한 시점 또는 P8에서 한 번 실행하고 매 단계 반복하지 않습니다.

### P3. 최소 common·디자인 시스템·라우트·분석 경계 정리

1. common에 실제 공통 타입·포트·MVI 기반을 이동합니다. 옮기는 타입의 전이 의존까지 확인하여 common이 이전 core를 의존하지 않게 합니다.
2. 디자인 시스템을 시각적 코드만으로 추출합니다. 남은 업무 UI가 이를 소비하는 전환 의존은 가능하지만 designsystem의 역참조는 금지합니다.
3. navigation·analytics를 최상위로 이동합니다. 라우트 직렬화 이름은 보존하고 분석용 값의 변환 경계를 정리합니다.
4. 모듈 그래프와 domain/entity/presentation 경계를 확인하는 검사를 연결합니다. 명시적으로 남기는 직렬화 호환 패키지는 이유와 종료 조건을 가진 예외로 기록합니다.

종료 조건: 새 기반이 화면 기능이나 이전 core 구현을 의존하지 않습니다. DomainError 등 공통 타입 이동으로 순환이 생기지 않고, 기존 MVI 테스트·분석 값 비교·라우트 복원 기준이 유지됩니다.

### P4. network·auth의 의존과 객체 그래프 분리

1. 네트워크 기반·토큰 접근 포트와 auth 구현을 함께 배치합니다. 업무 API provider는 API의 최종 소유자와 함께 이동하거나, 해당 업무가 아직 남은 이전 data 모듈에 둡니다.
2. 프로필·초대 안내의 최소 계약을 먼저 도입하고 auth가 구현을 직접 알지 않게 합니다. 아직 이동 전인 구현은 기존 data에서 계약을 제공할 수 있습니다.
3. auth의 세션·토큰·연동·SDK·저널 코드를 이동하고 app 조립을 갱신합니다. 기존 AuthModule의 사용자 귀속 저장소 중앙 멀티바인딩은 app의 조립 파일로 옮겨, 이후 저장소 이동 때 auth가 create/my 구현을 참조하지 않게 합니다. 구현 이동과 소비자/provider 갱신은 한 단위로 처리합니다.
4. 네트워크 자체의 컴파일뿐 아니라 Hilt 앱 생성과 비인증 갱신 경로를 확인합니다.

종료 조건: auth→network→common 단방향, 인증 클라이언트→토큰 관리자→갱신 API→비인증 클라이언트의 조립이 성립합니다. 갱신 합류·세대·원자 저장·연동·종료 경로 테스트와 인증 회귀 확인이 통과해야 다음 업무 이동으로 진행합니다.

### P5. 공유 신고 분리와 기능별 데이터 소유권 이동

1. report를 분리하고 chat 목록/방·studio·story 소비자를 연결합니다. 신고 동작을 StoryRepository에서 분리하되 네 화면의 상태 수명·분석 값은 유지합니다.
2. home·studio·story의 조회/삭제, chat의 시작/목록/상세/SSE를 소유 모듈로 이동합니다. API·DTO·Repository·DI·관련 테스트를 함께 옮깁니다.
3. my/profile·credit·invite·feedback의 계약·구현·저장소를 이동합니다. API 경로별 UserApi 분리는 구현과 함께 진행합니다.
4. create의 생성 계약·API·Room·진행 모델을 이동하고 studio의 읽기/폐기 사용을 최소 포트로 교체합니다.

서로 다른 화면 코드의 이동은 독립적으로 작업할 수 있습니다. 공통 UserApi·NetworkModule·바인딩 파일의 분리와 app 조립은 충돌/누락을 피하도록 순차적으로 처리합니다. 하위 기능을 나누기 위해 같은 API 동작이나 저장 인스턴스를 복제하지 않습니다.

종료 조건: 각 업무 구현을 소유한 모듈과 직접 소비자의 검사·테스트가 통과하고, 중복 Hilt 바인딩·feature 간 직접 의존이 없습니다. 다음 단계 전에 이전 설치의 제작 진행·프로필·초대 안내·설정 데이터가 유지되는지 확인합니다.

### P6. 리소스·공통 표현의 최종 소유권 정리

1. 문자열·아이콘·폰트의 모든 소비자를 조사하여 기능·common·designsystem·app·report에 배치합니다.
2. 파일명 접두사만으로 이동하지 않고 Kotlin·XML·Preview·테스트 소비를 확인합니다.
3. 값·포맷 인자·배열 순서·qualifier가 동일한지 비교하고 각 namespace의 R 참조를 갱신합니다.

종료 조건: 리소스 링크·앱 패키징이 통과하고 문자열/배열 내용에 의도하지 않은 변경이 없습니다. 실제 화면 검증은 설치 후 screenshot과 UI 흐름으로 확인합니다.

### P7. 전환 모듈 제거와 문서 정리

1. 소비자가 없는 `:core:*`와 `:feature:*` 등록·의존을 제거합니다. 단순 치환을 위해 만들었던 임시 계약/호환 연결도 확인합니다.
2. README·DESIGN·AGENTS·계획 문서의 현재 코드 경로와 명령을 갱신합니다. 과거 결정 기록을 새로 구현된 사실처럼 소급 변경하지 않습니다.
3. 하네스 3-3의 최종 구조·문자열·의존 강제 수준과 해당 구현 상태 매트릭스를 실제 완료 상태로 갱신합니다. 이 제안의 내용을 승격하면서 두 문서에 같은 결정의 정본을 남기지 않습니다.

종료 조건: 목표 모듈 그래프가 성립하고, 명시적 직렬화 호환 예외 외에는 이전 패키지 경로 참조가 없습니다. core 모듈 제거와 Kotlin 직렬화 이름 보존을 혼동하지 않습니다.

### P8. 최종 통합 검증과 완료 보고

공통 빌드·리소스·DI가 모두 바뀐 최종 상태에서 기존 CI 게이트를 확인합니다. 실행한 검증과 미실행 항목을 구분하고, 스크린샷 없는 UI 주장은 코드 판단으로 표시합니다. 버전 업그레이드나 기능 수정은 후속 범위로 남깁니다.

## 4. 위험별 검증 계획

실제 작업 이름은 이동 후 Gradle task 목록과 빌드 파일에서 확인합니다. 아래는 실행 대상의 의미이며 아직 실행 결과가 아닙니다.

| 위험/시점 | 기존 근거 및 검증할 것 | 완료 증거 |
| --- | --- | --- |
| 패키지 이동 · P1/P3 | MviViewModelTest·각 기능 ViewModel 테스트, 이동 모듈과 app 컴파일 | 테스트 수/시나리오 유지, package/private/internal 접근과 Hilt 생성 성공 |
| build 설정 · P2/P8 | 기존 CI check·assembleDebug, included build 검사·ktlint/detekt/lint 연결 | 새 모듈 검사 누락 없음, 빌드 스크립트 검사에 생성 코드가 섞이지 않음 |
| `auth-session`, `race-condition` · P4 | SessionTokenManagerTest·TokenFreshnessEvaluatorTest·AccountLinkRepositoryImplTest | 동시 갱신 합류, 이전 세대 요청, 저장 실패, 취소, 비인증 갱신, 연동이 서버 로그인으로 오인되지 않음 |
| `session-cleanup` · P4/P5 | SessionCleanupSteps·AuthModule의 현재 등록을 소유자 목록과 비교 | 프로필·초대 안내·제작 진행의 등록 유지, 정리 실패/늦은 응답 후 다음 계정에 상태가 남지 않음 |
| `state-restore`, 저장 호환 · P5 | PendingStoryCreationEntityTest·RoomStoreTest·CreationResumePointTest | SQL/identity hash·기존 직렬화 값 비교, 이전 버전 데이터 유지 후 새 앱의 읽기·재개·폐기 성공 |
| `state-restore`, `coroutine-lifecycle` · P1/P5 | StorylineGenerationStoreTest·제작 단계 테스트 | ActivityRetained 수명·단계 교체·폴링/취소·임시 저장 의미 유지 |
| `data-retry`, 스트리밍 · P5 | ChatSseEventMapperTest·ChatSseSourceTest·ChatRoomStream/Regenerate/Delete/Suggestion 테스트 | 요청·정규화·이벤트·재시도·취소·추천 출처 의미 유지 |
| `observability` · P3/P5 | AnalyticsEvent의 기존 name/properties 값, 발화 위치 | enum 변환 전후 값 동일; 식별자 연결·분리 및 화면 노출 중복 없음 |
| 라우트·리소스 · P3/P6 | 이전 라우트 직렬화 fixture, 원문 리소스 비교 | 탭/상세/채팅/제작 재개 백스택 복원, 포맷 인자·배열·R 참조 유지 |
| UI 통합 · P6/P8 | installDebug 후 실제 흐름과 adb screenshot | 로그인→탭→상세→채팅, 제작 단계/재개/폐기, 마이 하위 화면, 신고, 로그아웃의 필요한 흐름 증거 |

기존 테스트가 위 경계를 직접 검증하지 못하면 해당 위험에 대한 회귀 테스트를 추가합니다. 이름 변경이나 이동 자체를 그대로 따라 쓰는 테스트는 만들지 않습니다. 현행 코드의 기존 실패는 기준 상태와 비교해 별도 기록하고, 기존 실패를 숨기기 위해 테스트를 제거하거나 검사를 완화하지 않습니다.

전체 게이트는 공통 빌드 변경 또는 최종 통합 확인에 필요한 시점에 실행합니다. 매 단계 전체 check/assembleDebug를 반복하지 않습니다. 기기·업그레이드 fixture가 준비되지 않으면 해당 검증을 미완료로 남기고 구조 전환 전체가 검증됐다고 보고하지 않습니다.

## 5. 계획 검토 결과와 외부 합의

가장 큰 위험과 보완된 설계는 [하네스 설계 §9](../../../knk-harness/docs/planning/android-module-architecture.md#9-계획-검토에서-반영한-보완)가 소유합니다. 특히 파일 경로를 먼저 일괄 바꾸고 나중에 순환을 해결하는 방식은 피하고, 계약·구현·provider·소비자·테스트를 이동 단위로 묶습니다.

- 현재 범위에서 필요한 새 서버·제품·디자인 합의는 없습니다. 기존 계약 보존을 전제로 하는 구조 계획입니다.
- report는 코드의 실제 공유 사용을 근거로 계획에 포함한 공유 업무 모듈입니다.
- 실제 서버 계약 변경, 저장 식별자/스키마 변경, 인증 정책 변경이 필요해지면 구조 이동을 잠시 분리하고 그 변경의 근거·범위·검증을 별도로 확정해야 합니다.
- 구현 전에 소스가 달라지면 기준 커밋과 §2 이동 목록을 갱신합니다. 이 문서가 오래됐다는 이유로 현재 사용자 변경을 되돌리지 않습니다.

## 6. 이번 문서 작업의 확인 범위

현행 하네스와 Android 소스·테스트 목록·Gradle/CI 구성을 읽어 계획을 작성했습니다. 문서의 로컬 링크·참조 경로·섹션과 diff를 확인합니다. 코드·Gradle·리소스를 변경하지 않으므로 Android 빌드·테스트·기기 검증은 이번 단계에서 실행하지 않습니다.

## 7. 모듈별 실행 기록

사용자의 후속 지시에 따라 모듈당 커밋 하나를 유지하도록 P1~P7의 세부 작업을 모듈 단위로 묶습니다. 기본 순서는 build-logic → common → designsystem → navigation → analytics → network → auth → report → 각 화면 기능 → app 최종 조립입니다. 대상 모듈을 옮길 때 소비자의 import·DI·설정 수정도 같은 커밋에 포함합니다.

- 이동 전 기준: `:app:assembleDebug` 통과. 기준 APK와 원본 소스 스냅숏은 작업 임시 디렉터리에 보관했습니다. 복원 fixture는 해당 모듈 검증에서 준비합니다.
- 전환 중 common에는 아직 이동하지 않은 기능의 기존 계약이 잠시 남을 수 있습니다. 각 기능 커밋에서 최종 소유 위치로 이동하며, common에서 다른 프로젝트 모듈로의 역참조는 허용하지 않습니다.
- 기존 `.idea/gradle.xml` 변경은 커밋 대상에서 제외합니다.

### build-logic

- Android Library·Compose·Hilt·품질 convention plugin 추출, SDK·버전·실패 정책 유지.
- `check assembleDebug :app:assembleRelease` 통과, configuration cache 저장/재사용 확인.
- included build 검사를 루트 check에 연결하고 원본 플러그인 소스만 ktlint 입력으로 지정. 기존 생성 코드 진단은 해당 ktlint 태스크 재실행으로 갱신.
