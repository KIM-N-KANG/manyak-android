# 신규 가입 온보딩을 onboarding 기능 모듈로 분리

- 티켓: [KNK-2049](https://kimandkang.atlassian.net/browse/KNK-2049)
- 작성일: 2026-11-09
- 상태: 구현, 로컬 전체 검증과 에뮬레이터 확인 완료
- Android: `refactor/KNK-2049-improve-module-folder-structure`
- 하네스: `docs/KNK-2049-improve-module-folder-structure`
- 정본: [STORY Android ADR A-095](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/DECISIONS.md#a-095), [STORY 컴포넌트 구조](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/4-2-COMPONENT-ARCHITECTURE.md#4-2-1-소유권)

## 목표와 범위

home의 `home/onboarding` 패키지를 `:feature:onboarding`으로 옮깁니다. 온보딩 동작, 저장 형식, 라우트, 분석 이벤트는 바꾸지 않습니다.

## 구현

| 영역 | 변경 |
| --- | --- |
| 공용 포트 | `core:common`의 `PopularOriginalStories.firstPage()`. 채팅이 많은 오리지널의 첫 페이지만 공개 |
| home | `PopularOriginalStoriesImpl`이 기존 `publicStories(ORIGINAL, CHATS)`로 포트를 구현하고 `HomeModule`이 바인딩. 온보딩 바인딩과 문구 8개 제거 |
| onboarding | 화면, 캐러셀, ViewModel, `StoryOnboardingStore`, `StoryOnboardingRepository`를 `app.manyak.onboarding`으로 이동. `OnboardingModule`이 저장소와 가입 기록기(`@IntoSet`)를 바인딩. 문구 8개 이동, 로딩 문구는 `story_onboarding_loading`으로 자기 소유 |
| app | 의존 추가, `RootViewModel`, `StoryOnboardingEntry`, `UserScopedStoreModule`의 import 변경 |
| 등록 | `settings.gradle.kts`, `.idea/gradle.xml`, `ModuleArchitecture.kt` 기능 모듈 목록 |

그대로 둔 것은 DataStore 파일 `story-onboarding`과 키 `story_onboarding_pending`, `:core:navigation`의 `StoryOnboardingRoute`, 분석 이벤트입니다.

## 검증

- `:feature:onboarding`, `:feature:home`, `:core:common`, `:app`의 `ktlintCheck`, `detekt`와 onboarding, home 단위 테스트, `:app:compileDebugKotlin`, `checkModuleArchitecture`, `:build-logic:test` 통과.
- 온보딩 ViewModel 테스트는 포트 Fake로 옮겼고, 조회 조건 검증은 home의 `PopularOriginalStoriesImplTest`로 옮겼습니다.
- `./gradlew check assembleDebug` 통과(1분 14초). `checkModuleArchitecture`는 20개 프로젝트와 Kotlin 642개 파일을 검사했습니다.
- Pixel_10 에뮬레이터(로그인된 dev 계정)에 `installDebug` 후 `story-onboarding` 파일에 `story_onboarding_pending=true`를 써서 실행했습니다. 온보딩이 메인 백스택의 루트로 열리고, home이 구현한 포트로 서버의 오리지널 목록을 받아 캐러셀과 문구가 표시됐습니다. 변경 전 빌드의 화면과 나란히 비교하지는 않았습니다. 확인 뒤 파일을 원래의 빈 상태로 되돌렸고, 작업을 비운 새 실행에서 홈 탭이 열렸습니다.
- 표시 파일을 비운 직후 같은 작업으로 다시 실행하면 저장된 백스택이 복원돼 온보딩이 한 번 더 보였습니다. 완료 없이 표시만 지운 이 확인 절차에서만 생기는 경우로, A-083의 루트 교체 동작과 같고 이번 변경과는 관계없습니다.
- 채팅 시작과 간편 제작 진입은 서버에 채팅이나 초안을 만들기 때문에 기기에서 누르지 않았고, ViewModel 단위 테스트로만 확인했습니다.
