# 구성 변경 대응 (KNK-1009)

## 목표

방향·글자 크기·다크 모드·로케일·창 크기가 바뀌어 Activity 가 재생성돼도 사용자가 하던 일을 잃지 않게 하고,
바뀐 리소스가 레이아웃을 깨지 않게 합니다.

## 제외 범위

- 대화면 전용 레이아웃(2-pane·리스트/디테일). 정보 구조가 창 크기로 달라지지 않습니다.
- 다국어 추가. 지금은 한국어 리소스만 있고 로케일 변경은 재생성만 일으킵니다.
- 접힘·펼침 기기의 posture 대응.
- R8 축소 시 라우트 키 이름 보존. 현재 release 에 축소가 켜져 있지 않아 `release-ops` 가 켤 때 함께 정합니다.

## 새로 내리는 결정

### 1. 재생성을 그대로 수용합니다 — `configChanges` 를 선언하지 않습니다

리소스 재적용(다크 팔레트·글자 크기·로케일)과 `enableEdgeToEdge` 의 시스템 바 아이콘 색 결정을
프레임워크가 하게 두고, 우리는 **상태 복원만** 책임집니다. 직접 처리하면 그 재적용을 손으로 재현해야 하고,
빠뜨린 축은 조용히 옛 리소스를 씁니다.

### 2. 방향을 잠그지 않습니다 — `screenOrientation` 을 선언하지 않습니다

`targetSdk` 37 은 Android 16 의 대화면 방향·크기 제한 대상이라 **최소 폭 600dp 이상 화면에서 잠금이 무시됩니다.**
잠금을 선언하면 폰에서만 듣는 반쪽 계약이 되고, 대화면에는 검증한 적 없는 가로 레이아웃이 그대로 나옵니다.
잠그는 대신 어떤 창 크기에서도 깨지지 않게 만듭니다.

### 3. 레이아웃 분기 대신 두 가지만 보장합니다

한 열 구성을 유지하고, 화면마다 (a) 세로로 넘치면 스크롤되고 (b) 넓은 창에서 본문 폭에 상한이 있는지만 봅니다.
창 크기별 분기는 정보 구조가 실제로 달라질 때만 넣습니다.

### 4. 상태 수명 — `SavedStateHandle` 을 도입하지 않습니다

ViewModel 상태는 **구성 변경까지만** 보장합니다. 프로세스 종료 복원은 이미 있는 두 경로가 맡습니다 —
라우트 키에 실린 식별자로 다시 조회하고, 제작 퍼널 입력은 DataStore 임시 저장이 복구합니다.
화면 로컬 상태의 기본은 `remember` 이고, **잃으면 사용자가 다시 입력해야 하는 값만** `rememberSaveable` 로 올립니다.

### 5. Activity 수명 신호에서 구성 변경을 걸러냅니다

`ON_STOP` 은 백그라운드 진입과 구성 변경을 구분하지 못합니다. Activity 수명을 백그라운드 신호로 쓰는 곳은
`isChangingConfigurations` 로 재생성을 제외합니다.

## 확인된 위반

구성 변경은 Activity를 재생성하므로 **컴포지션이 통째로 다시 만들어집니다.** 그래서 깨지는 자리는 네 갈래입니다 —
부수효과가 다시 실행되거나, `remember` 값이 사라지거나, 뷰 시스템 객체가 새로 만들어지거나, Activity 참조가 죽습니다.
아래 표의 "확인"은 에뮬레이터(API 36)에서 다크 모드 토글·회전으로 실제 재현한 것입니다.

### A. 부수효과가 재생성마다 다시 실행됨

| # | 위치 | 증상 | 확인 |
| --- | --- | --- | --- |
| A1 | `CreateStorylineScreen.kt:169` | `LaunchedEffect(state.activeIndex) { scrollToItem(0) }`가 복원된 스크롤을 덮어씁니다. 본문을 읽다가 화면이 바뀌면 맨 위로 튑니다 | 재현 |
| A2 | `CreateFunnelChrome.kt:233` | `ON_STOP` 임시 저장이 백그라운드와 구성 변경을 구분하지 못합니다. 저장하지 않고 나가려던 편집이 회전 한 번으로 디스크에 들어갑니다 | 재현 |
| A3 | `StoryDetailScreen.kt:100` | `ON_START`의 `ScreenShown`이 `ChatStartReset`을 보냅니다. 채팅 생성이 진행 중이면 **버튼 잠금과 스피너가 풀려** 진행 중임을 알 수 없고, 다시 눌러도 in-flight 가드에 막혀 반응이 없습니다. 읽지 않은 실패 안내도 함께 지워집니다 | 코드 |
| A4 | `StoryDetailScreen.kt:100`, `StudioScreen.kt:89` | 같은 `ScreenShown`이 상세·목록을 다시 조회합니다. 화면은 그대로지만 구성 변경마다 요청이 한 번씩 더 나갑니다 | 코드 |
| A5 | `CreateGeneratingLoading.kt:152` | `revealedCount`가 `remember`이고 `LaunchedEffect(hints)`가 0초부터 다시 셉니다. 생성 50초째에 화면을 바꾸면 쌓인 "오래 걸리고 있어요" 안내가 사라지고 15초를 다시 기다립니다 | 코드 |
| A6 | `ProgressIndicator.kt:41` | `rememberDelayedProgressVisibility`의 `shown`이 false로 돌아가고 300ms 지연이 다시 시작됩니다. 로딩·로그아웃 정리 중에 스피너가 사라졌다 다시 나타납니다 | 코드 |
| A7 | `CreateGeneratingLoading.kt:120`, `LoginBackground.kt:61`, `Skeleton.kt:37` | 타자기 문구·배경 크로스페이드·스켈레톤 펄스가 처음부터 다시 시작합니다. 사용자가 잃는 정보는 없습니다 | 코드 |

`KeywordCharacterForm.kt:257`은 같은 구조인데 안전합니다 — `previousCharacterCount`를 **현재 값으로** 초기화해
재생성 직후에는 `characterWasAdded`가 false가 됩니다. A1이 따라야 할 형태입니다.

### B. `remember` 로컬 상태가 사라짐

| # | 위치 | 증상 |
| --- | --- | --- |
| B1 | `CreateKeywordScreen.kt:100` | 키워드 추가 시트가 닫히고, 그 안에 입력하던 값(`KeywordInputs.kt:253`의 `rememberSaveable`)도 함께 버려집니다 |
| B2 | `ManyakSelectField.kt:58` | 열려 있던 셀렉트 메뉴가 닫힙니다 — 퍼널의 성별, 스토리 상세의 시작 상황 두 곳 |
| B3 | `MyStoryCard.kt:61` | 카드 더보기 메뉴가 닫힙니다 |
| B4 | `StoryEndingInfo.kt:48` | `rememberTooltipState`가 초기화되어 "엔딩은 시작 상황마다 달라져요" 말풍선이 닫힙니다. 스스로 사라지지 않게(`isPersistent`) 만든 안내라 의도와 어긋납니다 |
| B5 | `StoryDetailScreen.kt:123-127` | 앱바 계산에 쓰는 측정값이 0·∞로 돌아갑니다. 재측정 전 첫 프레임에 앱바 배경과 시스템 바 아이콘 색이 한 번 틀리게 나옵니다 |

`ManyakSelectField.kt:59`의 앵커 폭과 `StoryDetailScreen.kt:257`의 CTA 높이는 재측정으로 곧 채워져 문제가 없습니다.

### C. 뷰 시스템 객체가 새로 만들어짐

| # | 위치 | 증상 |
| --- | --- | --- |
| C1 | `LegalDocumentScreen.kt` | `AndroidView`의 `factory`가 새 `WebView`를 만들고, 새 인스턴스의 `tag`가 `reloadToken`과 달라 `loadUrl`이 다시 실행됩니다. **약관·개인정보 문서가 처음부터 다시 로드되고 스크롤이 맨 위로 갑니다.** 재생성마다 네트워크 요청이 한 번씩 더 나갑니다 |

### D. Activity 참조가 죽음

| # | 위치 | 상태 |
| --- | --- | --- |
| D1 | `GoogleIdTokenProvider.kt:48`, `KakaoIdTokenProvider.kt:45` + `CurrentActivityProvider.kt` | 제공자 창이 뜬 채 재생성되면 호출 시점에 잡아 둔 Activity가 죽습니다. 다만 **잠금이 영구히 남을 경로는 코드에 없습니다** — 두 제공자 모두 continuation을 Activity가 아니라 SDK·프레임워크 콜백에 물려 두고 `continuation.isActive`로 이중 재개를 막으며, `inProgress`는 `finally`에서 풀립니다. 재현하려면 로그아웃이 필요해 미확인으로 둡니다 |
| D2 | 퍼널·상세의 모든 `BasicTextField` | 기기에서 확인했습니다. 입력 **텍스트는 유지되고 포커스와 키보드만 사라집니다** |

## 확인한 결과 안전한 것

기기에서 다크 모드 토글·회전으로 확인했습니다. 아래는 손대지 않습니다.

- **ViewModel 상태 전부** — Nav3 데코레이터가 ViewModelStore를 Activity 스토어에 물려 둡니다. 퍼널 세 화면의 입력·선택·활성 탭이 모두 유지됐습니다.
- **`StorylineGenerationStore`** — `@ActivityRetainedScoped`라 생성 결과와 진행 상태가 살아남습니다.
- **백스택 넷과 선택 탭** — `rememberNavBackStack`·`rememberSaveable`.
- **목록·본문 스크롤 위치** — A1의 스토리라인 화면만 예외입니다. 홈·제작 그리드, 추가 정보, 상세 본문은 유지됩니다.
- **다이얼로그 셋** — 이어서 만들기·삭제 확인·퍼널 이탈 경고는 모두 ViewModel 상태입니다.
- **스토리 이미지 뷰어** — ViewModel 상태라 구성 변경에는 유지되고 프로세스 종료에는 닫힙니다(하네스 검수 항목이 정한 동작).

"활동 유지 안 함"을 켜면 위 전부가 사라지지만 그건 구성 변경이 아니라 프로세스 종료입니다. 별개 축으로 검증합니다.

## 구현 단계

### 1단계 — 하네스 §3-3-5 에 지원 정책을 씁니다 (선행)

`3-3-android-app.md` §3-3-5 의 "디바이스 지원 작성 예정" 자리에 위 결정 1~4 와 검수 항목을 채웁니다.
KNK-1009 용 하네스 브랜치가 따로 필요합니다(현재 브랜치는 KNK-1015).

완료 조건: 방향·창 크기 지원 범위, 상태 수명 표, 구성 변경 검수 항목이 하네스에 있습니다.

### 2단계 — 재생성에서 사용자가 잃는 것 (1단계 이후, 3단계와 병렬)

우선순위는 사용자가 되돌릴 수 없거나 오해하게 되는 순서입니다.

1. **A3** — `ScreenShown` 이 채팅 시작 잠금을 풀지 않게 합니다. 진행 중이면 잠금과 스피너를 유지하고,
   읽지 않은 실패 안내도 남깁니다. 지금은 화면이 "안 누른 상태"라고 거짓말합니다.
2. **A2** — `SaveDraftWhenBackgrounded` 가 `isChangingConfigurations` 인 `ON_STOP` 을 무시합니다.
3. **A1** — 스토리라인 스크롤 되돌리기를 탭이 **실제로 바뀔 때만** 실행합니다.
   `KeywordCharacterForm.kt:257` 의 이전 값 비교 방식을 그대로 씁니다.
4. **C1** — 법적 문서 `WebView` 가 재생성에서 다시 로드되지 않게 합니다.
5. **A5** — 생성 지연 안내의 경과 시간을 화면 로컬이 아닌 곳에서 셉니다.
6. **B1** — `addKeywordTarget` 을 `rememberSaveable` 로 올립니다(`KeywordTarget` 이 직렬화 가능해야 합니다).

**D1은 재현 없이 인증 코드를 바꾸지 않습니다.** "Activity가 죽으면 실패 처리" 같은 방어 코드를 넣으면
완료된 제공자 인증을 버리는 새 문제를 만듭니다. 잠금이 실제로 남는 것을 본 뒤에 다룹니다.

**D2는 포커스 복원을 넣지 않습니다.** 필드마다 포커스를 저장했다 되돌리려면 `LazyColumn` 안에서
스크롤로 사라졌다 살아나는 필드가 포커스를 뺏는 더 나쁜 동작이 생깁니다. 값은 이미 유지되므로,
제대로 된 해법인 `BasicTextField(state: TextFieldState)` 이전까지 현재 동작을 수용합니다.

**손대지 않는 것**: B2·B3·B4 의 메뉴·말풍선 닫힘과 A6·A7 의 애니메이션 리셋은 사용자가 다시 열면 그만입니다.
A4 의 중복 조회도 `ScreenShown` 계약 자체가 그렇게 설계된 것이라 이번 범위에서 바꾸지 않습니다.

완료 조건: 퍼널을 편집하며 화면을 바꿔도 임시 저장이 실행되지 않고, 스토리라인 본문의 읽던 위치가 유지되며,
채팅 시작 중에는 잠금이 풀리지 않고, 약관 문서가 처음부터 다시 로드되지 않습니다.

### 3단계 — 창 크기·글자 크기 (1단계 이후, 2단계와 병렬)

1. `CreateFunnelChrome.kt:180`·`KeywordCharacterForm.kt:230` 의 고정 높이를 `heightIn(min=)` 으로 바꿉니다.
2. 스크롤이 없는 전체 화면 — `ManyakApp` 의 `CleanupFailed`, `LoadFailedContent`, `GeneratingLoadingContent` — 에 스크롤을 둡니다.
   `CleanupFailed` 가 가장 급합니다. 다시 시도 버튼이 유일한 출구인데 잘리면 앱을 벗어날 수 없습니다.
3. 홈·제작 목록의 `GridCells.Fixed(2)` 를 폭 기준으로 바꿉니다. 가로·대화면에서 카드 한 장이 뷰포트보다 커집니다.
4. 넓은 창의 본문 폭 상한을 셸과 전체 화면 목적지에 한 번씩 적용합니다.

완료 조건: 최대 글자 크기에서 두 버튼의 문구가 잘리지 않고, 가로에서 어떤 화면도 조작 수단이 잘린 채 멈추지 않습니다.

### 4단계 — 하네스 구현 상태 매트릭스 갱신

## 검증

`android-change-verification` 에 넘길 위험 태그입니다. 이 계획에서 실행하지 않습니다.

- `state-restore` — 구성 변경과 프로세스 종료를 **각각** 확인합니다. 회전은 임시 저장 미실행과 시트 유지를,
  "활동 유지 안 함"은 백스택·라우트 복원을 봅니다.
- `adaptive-ui` — 최대 글자 크기, 가로 방향 두 축만 봅니다. 전체 기기 매트릭스는 릴리스 QA 몫입니다.
