# 신규 가입 오리지널 스토리 온보딩 (KNK-1892)

- 작성일: 2026-10-28
- 근거 정본: 하네스 [Android 내비게이션·화면 계약](../../../knk-harness/docs/domain/APP/CLIENT/ANDROID/3-1-INFORMATION-ARCHITECTURE.md#3-1-1-셸과-진입), [Android 내비게이션과 화면](../../../knk-harness/docs/platform/ANDROID/4-4-EXECUTION.md#4-4-1-요청과-취소), [분석 Spec](../../../knk-harness/docs/reference/ANALYTICS.md). 결정 이유는 Android ADR A-083에 남긴다.
- 서버 계약: dev Swagger `GET /stories?filter=original&sort=chats`(인증 없음, 커서 페이지). 2026-10-28 기준 오리지널 14편이고 첫 페이지(기본 20개)에 모두 들어온다. 썸네일은 525×700 이상이라 큰 카드에 그대로 쓴다.

## 목표와 제외 범위

처음 회원가입을 마친 사용자는 홈 탭 대신 오리지널 스토리를 넘겨 보며 바로 채팅을 시작하는 온보딩 화면에 들어온다. 채팅을 시작하거나 스토리 간편 제작으로 이동해야 이 단계를 벗어난다.

**제외**: 웹 온보딩, 서버 쪽 온보딩 완료 기록(기기 안 플래그로 충분하다), 오리지널이 한 페이지(20편)를 넘는 경우의 다음 페이지 조회.

## 결정

### 진입과 완료 판정

1. **신규 가입 신호**는 기존 `SignupOnboardingWriter.markPending()`(토큰 발급 응답의 `isNewUser`)을 그대로 쓴다. 이 계약을 Hilt `@IntoSet` 묶음으로 바꿔 `auth`가 등록된 기록기 모두를 부르고, 초대 코드 안내(`my`)와 스토리 온보딩(`home`)이 각자 기록한다.
2. **플래그 저장**: `home`의 `StoryOnboardingStore`가 자기 DataStore 파일(`story-onboarding`)에 `pending` 하나를 둔다. 회원 귀속이라 `UserScopedStore`로 로그아웃 정리에 참여한다. 읽기 실패는 "온보딩 없음"으로 흡수한다(앱 진입을 막지 않는다).
3. **완료**는 채팅 생성 성공(캐러셀 CTA 또는 온보딩 위에서 연 상세) 또는 간편 제작 진입이다. 루트(`RootViewModel`)가 완료 이벤트를 보내고 플래그를 지우며, 저장 실패에 대비해 현재 프로세스의 완료를 메모리에도 둬 같은 실행에서 온보딩으로 되돌리지 않는다.

### 내비게이션

4. 메인 백스택의 루트를 상황에 따라 고른다: 플래그가 참이면 `[StoryOnboardingRoute]`, 아니면 `[MainTabsRoute]`. 플래그를 읽기 전에는 메인 그래프를 그리지 않는다(시작 화면과 동의 확인이 그보다 길어 실제로 기다리는 일은 없다). 그래프를 그린 뒤 플래그가 참으로 바뀌면(가입 기록이 동의 확인보다 늦은 경우) 루트를 온보딩으로 바꾼다.
5. 온보딩이 루트이므로 셸과 탭이 없고, 시스템 뒤로가기는 Activity 기본 동작(앱 종료)이다. 다시 열면 플래그가 남아 같은 화면으로 온다. 건너뛰기와 헤더 뒤로가기는 두지 않는다.
6. 상세 보기는 `[StoryOnboarding, StoryDetail]`로 쌓아 뒤로가기가 온보딩으로 돌아온다.
7. 완료 시 루트 키를 `MainTabsRoute`로 바꿔 끼운다.
   - 캐러셀 CTA: `[MainTabs(홈), ChatRoom]` — 채팅방 뒤로가기는 홈 탭.
   - 온보딩 위 상세에서 채팅 시작: `[MainTabs(홈), StoryDetail, ChatRoom]` — 기존 "상세에서 시작한 채팅은 상세로, 다시 뒤로가면 탭으로" 규칙과 같다.
   - 직접 만들기: `[MainTabs(제작), CreateKeyword(새 UUID)]` — 퍼널 이탈과 완성 제출이 모두 제작 탭으로 돌아온다.
8. 알림·웹 링크 진입(`openExternalDestination`)은 루트까지 걷어낸 뒤 쌓으므로 온보딩 위에 열리고 돌아오면 온보딩이다. 탭 목적지는 탭 선택만 바꾸고 온보딩에 머문다. 회원 화면 위 안내(알림 권한, 초대 코드, 광고 재질문)는 온보딩 위에 그대로 뜬다.

### 화면

9. `home/onboarding`이 화면과 ViewModel을 소유한다. 목록은 기존 `HomeRepository.publicStories(ORIGINAL, CHATS)` 첫 페이지, 채팅 생성은 `common`의 `ChatStarter`(시작 설정과 주인공은 서버 기본값).
10. 구성: 상단 헤드라인(`headline-small`) "마음에 드는 스토리를 골라\n바로 채팅을 시작해보세요" → 안내 줄 "마음에 드는 스토리가 없나요?"(`text-subtle`)와 오른쪽 "직접 만들기"(`text-brand` 글자 버튼) → 가운데 캐러셀 → 하단 "채팅 시작하기" 주 버튼. 안내 줄은 좁은 화면과 큰 글자에서 버튼이 다음 줄로 내려간다.
11. 캐러셀: `HorizontalPager`에 큰 페이지 수를 주고 가운데에서 시작해 양방향 무한 순환한다(오리지널이 1편이면 순환하지 않는다). 카드 폭은 화면 폭의 76%와 남은 높이로 정한 3:4 중 작은 쪽이라 작은 화면에서도 CTA를 밀어내지 않는다. 양옆 카드는 페이지 간격만큼 보이고, 가운데에서 멀어질수록 88%로 줄고 불투명도 40%로 내려간다(스와이프 진행도에 따라 연속 보간). 옆 카드를 누르면 그 카드가 가운데로 온다.
12. 카드: 스토리 상세 주변 인물 카드와 같은 틀(`surface-raised`, 테두리, `feature-card` 모서리 24dp)로 위에 3:4 `StoryCover`, 구분선 아래 제목(`body-large-strong`, 1줄)과 오른쪽 "상세 보기"(꺾쇠, `text-subtlest`), 한 줄 소개(`body-medium`, `text-subtle`, 2줄 고정 말줄임)를 둔다. 가운데 카드 전체 탭도 상세를 연다. 카드 폭은 글자 영역 높이를 뺀 남은 높이로도 제한한다. 카드 아래에는 높이 16dp, 그림자 색 30%의 아주 옅은 그림자를 페이지 층의 높이로 깐다(카드 안 `shadow`는 옆 카드의 반투명 층이 잘라냄). 사용자 검토를 거치며 표지 위 흐림 정보 판과 그늘 토큰, 로그인 같은 표면색 잠김, 표지 아래 그라데이션, 진한 그림자, 가운데 카드 선택 테두리를 차례로 시도하고 걷어냈다.
13. CTA는 "채팅 시작하기" 한 줄 주 버튼이고 가운데 스토리로 시작한다(처음에는 라벨 아래 스토리 제목을 두었으나 사용자 검토로 뺐다). 생성 중에는 라벨 자리를 유지한 채 스피너로 바꾸고 잠근다. 목록이 없으면 비활성.
14. 상태: 첫 조회 중은 같은 자리의 카드 골격 세 장, 실패는 캐러셀 자리에 "스토리를 불러오지 못했어요"와 다시 시도, 0편은 "지금은 보여드릴 스토리가 없어요". 두 경우 모두 "직접 만들기"는 살아 있어 갇히지 않는다. 채팅 생성 실패는 토스트 "채팅을 시작하지 못했어요".
15. 가운데 스토리 순번은 ViewModel 상태이고 페이저 위치는 `rememberPagerState`(저장 가능)라 회전, 다크 모드, 글자 크기 변경에서 같은 카드가 남는다.

### 분석 이벤트

`screen_name`은 `storyOnboarding`. 공통 규칙(§6-3-1)대로 값은 프로퍼티로 보낸다. `position`은 오리지널 목록 안의 0부터 센 순번(홈 카드와 같음), `browsed_count`는 이번 진입에서 1초 이상 가운데 둔 서로 다른 스토리 수다.

| 이벤트 | 우선순위 | 발생 시점 | 고유 프로퍼티 |
| --- | --- | --- | --- |
| `client_storyOnboarding_viewed` | P0 | 온보딩 진입. 상세 복귀와 구성 변경은 다시 세지 않음 | 없음 |
| `client_storyOnboarding_storyCard_impressed` | P1 | 가운데 카드로 1초 이상 머문 스토리. 진입당 스토리별 1회(첫 카드 포함) | `story_id`, `position` |
| `client_storyOnboarding_detailButton_clicked` | P1 | "상세 보기" 또는 가운데 카드 탭 | `story_id`, `position`, `source`(`button`/`card`) |
| `client_storyOnboarding_chatStartButton_clicked` | P0 | "채팅 시작하기" 클릭(생성 요청 전) | `story_id`, `position`, `browsed_count` |
| `client_storyOnboarding_chatStartError_shown` | P1 | 채팅 생성 실패 토스트 | `story_id` |
| `client_storyOnboarding_createButton_clicked` | P0 | "직접 만들기" 클릭 | `browsed_count` |
| `client_storyOnboarding_loadError_shown` | P1 | 오리지널 목록 조회 실패 화면 | 없음 |
| `client_storyOnboarding_completed` | P0 | 온보딩을 벗어남: 채팅 생성 성공 또는 간편 제작 진입 | `action`(`chat`/`create`), `source`(`carousel`/`storyDetail`, chat만), `story_id`·`chat_id`(chat만) |

버튼 클릭(`chatStartButton_clicked`)과 실제 시작(`completed` + `action=chat`)을 나눠 생성 실패율을 본다. 상세에서 시작한 채팅은 상세의 기존 `client_storyDetail_chatStartButton_clicked`가 클릭을, `completed`(`source=storyDetail`)가 실제 시작을 맡는다. 퍼널: `viewed` → `storyCard_impressed` → `chatStartButton_clicked` → `completed(chat)` → `client_chat_messageInput_submitted`(`turn_number=1`, `chat_id`) → `server_chat_aiMessage_processed_succeeded`.

## 변경 파일

- `common`: `SignupOnboardingWriter` 설명(여러 기록기).
- `auth`: `SessionRepositoryImpl`이 기록기 묶음을 부름, 테스트 갱신.
- `my`: `InviteModule`의 기록기 바인딩을 `@IntoSet`으로.
- `navigation`: `StoryOnboardingRoute`.
- `home`: `onboarding/domain`·`data`(저장소), `onboarding/presentation`(ViewModel, 화면), 문자열, DataStore 의존, 바인딩.
- `analytics`: 이벤트 8종.
- `designsystem`: `featureCard` 모서리 토큰(24dp)과 `DESIGN.md`의 토큰 표, 온보딩 캐러셀 컴포넌트 규격.
- `app`: `RootViewModel` 플래그·완료, `ManyakApp` 루트 선택과 완료 처리, `UserScopedStoreModule`.

## 검증

- `home` 단위 테스트: 순환 순번 계산, 1초 머문 카드만 노출 이벤트, 채팅 시작 single-flight와 실패 시 해제, 완료 효과.
- `auth` 단위 테스트: 기록기 묶음으로 바꾼 뒤 기존 신규 가입 기록 테스트 유지.
- `:home`, `:auth`, `:app`, `:analytics`, `:designsystem`, `:navigation`, `:my`의 `ktlintCheck`·`detekt`, 관련 `testDebugUnitTest`, `:app:assembleDebug`.
- 에뮬레이터: 신규 가입 계정으로 진입 화면, 스와이프와 순환, 상세 왕복, 뒤로가기(앱 종료 후 재진입), 채팅 시작 후 뒤로가기가 홈, 직접 만들기 후 퍼널 이탈이 제작 탭, 회전과 다크 모드. 결과는 아래에 기록한다.

## 실행 결과

- `:home:testDebugUnitTest`(온보딩 7건 포함 24건), `:auth:testDebugUnitTest`, `:my:testDebugUnitTest` 통과. 변경 모듈의 `ktlintCheck`·`detekt`, `checkModuleArchitecture`, `:app:assembleDebug` 통과.
- 에뮬레이터(dev 서버, 기존 회원 계정에 온보딩 표시 파일을 `run-as`로 넣어 신규 가입 상태를 재현). 실제 신규 가입 계정으로는 확인하지 않았다.
  - 첫 화면이 온보딩이고 하단 탭과 뒤로가기 버튼이 없다. 오리지널 14편을 14번 넘기면 첫 스토리로 돌아오고, CTA 아래 제목이 가운데 카드를 따라 바뀐다. 옆 카드를 누르면 가운데로 온다.
  - 가운데 카드와 상세 보기는 상세를 열고 뒤로가기가 온보딩으로 돌아온다. 온보딩의 뒤로가기는 런처로 나가고, 다시 열거나 강제 종료 뒤 열어도 온보딩이다.
  - 회전과 다크 모드에서 가운데 스토리가 유지된다. 가로 화면은 처음에 카드가 남은 높이에 맞춰 엄지손톱만큼 줄어, 창 높이 600dp 미만이면 캐러셀 높이를 420dp로 고정하고 화면을 세로 스크롤하도록 고쳤다. 카드 폭 비율은 위아래 여백이 135dp씩 비어 70%에서 76%로 키웠다.
  - 채팅 시작하기 → 채팅방, 뒤로가기는 홈 탭. 상세의 채팅 시작 → 채팅방 → 상세 → 홈 탭. 직접 만들기 → 간편 제작, 뒤로가기는 제작 탭. 완료 뒤 강제 종료하고 다시 열면 홈이다.
  - logcat의 분석 이벤트로 `viewed`, `storyCard_impressed`, `detailButton_clicked`(`button`/`card`), `chatStartButton_clicked`(`browsed_count`), `createButton_clicked`, `completed`(`carousel`/`storyDetail`/`create`)의 프로퍼티를 확인했다. 목록 조회 실패와 채팅 생성 실패 화면은 단위 테스트로만 확인했다.
