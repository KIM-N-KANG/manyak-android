# 디자인 시스템

디자인 토큰의 용도와 공용 컴포넌트 규칙입니다. 값의 기준은 `core/designsystem`의 Kotlin 토큰 파일이고, 화면별 배치는 하네스 도메인의 ANDROID `3-3-LAYOUT.md`가 정합니다([화면별 배치](#화면별-배치)).

## 개요

마냑 앱의 시각 언어는 **초록 하나와 무채색 두 단계**입니다. 표면은 거의 흰색과 거의 검정 둘뿐이고, 그 위에서 색이 있는 것은 브랜드 초록과 상태 색(오류, 경고, 안내)뿐입니다. 브랜드 초록 `colors.brand`는 로고 그린과 같은 값이고 팔레트 단계가 이 값에 맞춰져 있습니다.

색, 크기, 여백, 모서리, 모션은 모두 토큰에서 나옵니다. 토큰은 팔레트와 시맨틱 두 층이고 화면이 읽을 수 있는 것은 시맨틱뿐입니다. 팔레트는 Kotlin에서 `private`이라 참조할 수 없습니다.

서체는 조작하는 화면의 Pretendard와 읽는 글의 MaruBuri 두 벌입니다. 같은 16sp라도 `typography.bodyLarge`는 행간 24sp로 촘촘하고 `typography.bodyReading`은 28sp로 벌어집니다. UI는 훑어보는 글이고 스토리는 읽는 글이기 때문입니다.

- 상호작용은 브랜드 초록 하나로 말합니다. 두 번째 강조색은 없습니다.
- 그림자를 쓰지 않습니다. 층은 `colors.surface`와 `colors.surfaceRaised`의 표면 색 차이로 나눕니다.
- 성공 상태에 별도 초록을 두지 않고 브랜드 초록을 다시 씁니다.
- 라이트와 다크는 같은 토큰 이름으로 대응하고 화면 코드에는 분기가 없습니다.
- 굵기는 Regular(400), Medium(500), Bold(700) 셋뿐입니다.
- 기기 배경화면에서 색을 가져오는 dynamic color를 쓰지 않습니다.

## 코드 대응

이 문서의 토큰은 `ManyakTheme.` 뒤의 경로로 적습니다. `colors.textSubtle`은 코드의 `ManyakTheme.colors.textSubtle`입니다. 화면이 토큰을 읽는 통로는 `colors`, `typography`, `spacing`, `shapes`, `sizes`, `motion` 여섯입니다.

`core/designsystem/src/main/java/app/manyak/designsystem/`

| 위치 | 내용 |
| --- | --- |
| `theme/Color.kt` | 팔레트(private)와 시맨틱 색, 라이트와 다크 인스턴스 |
| `theme/Type.kt` | Pretendard와 MaruBuri FontFamily, 타이포 롤 |
| `theme/ManyakSpacing.kt`, `ManyakShapes.kt`, `ManyakSizes.kt`, `ManyakMotion.kt` | 여백, 모서리, 크기, 모션 시간 |
| `theme/Theme.kt` | `ManyakTheme` 컴포저블과 접근자, M3 슬롯 파생, 리플 설정 |
| `component/` | 공용 컴포저블과 Modifier. 아래 표 |
| `credit/` | 이프 수치(`CreditAmountText`), 내 이프 카드(`CreditBalanceCard`), 이프 내역 행과 목록 끝 감지(`CreditHistory`), 이프 부족 시트(`CreditShortageSheet`) |
| `mascot/` | 마스코트 안무의 공용 재료(`MascotChoreography`)와 무대, 소품, 붓질 그리기(`MascotDrawing`) |
| `text/` | 스토리 마크업 파싱(`parseTextSegments`, `parsePassageSegments`), 큰 수 줄임(`formatCompactCount`) |

| 분류 | `component/`의 공개 API |
| --- | --- |
| 버튼 | `ManyakNeutralButton`, `ManyakTextButton`, `ManyakIconButton`, `ManyakMoreButton`, `ManyakAddButton`, `ManyakInfoTooltipButton` |
| 선택과 입력 | `ManyakCheckbox`, `ManyakSwitch`, `ManyakTextField`, `ManyakMultilineTextField`, `ManyakInputCounter`, `ManyakSelectField`, `ManyakSelectOption`, `ManyakSelectAction`, `Modifier.selectionRing`, `Modifier.keepKeyboardOnTap`, `Modifier.clearFocusOnTap`, `FocusScrollMargin` |
| 시트, 창, 팝업 | `ManyakBottomSheet`, `ManyakOptionsSheet`, `ManyakOptionItem`, `ManyakDialog`, `ManyakConfirmDialog`, `ManyakDestructiveDialog`, `ManyakPopup`, `CreateTutorialDialog` |
| 셸 | `ManyakBrandHeader`, `ManyakSectionHeader`, `ManyakDetailHeader`, `ManyakNavigationBar`, `ManyakNavigationItem`, `ManyakPullToRefreshBox`, `SystemBarIconRequests`, `BusinessInfoFooter`(홈과 마이 하단 사업자 정보) |
| 스토리 표시 | `StoryCover`, `StoryCoverBadge`, `StoryThumbnail`, `StoryBadgeScale`, `StoryGenreBadge`, `StoryGenreBadges`, `MetaChip`, `Modifier.coverBackdrop`, `CharacterImage`, `FullscreenImageViewer` |
| 상태와 목록 | `ManyakProgressIndicator`, `rememberDelayedProgressVisibility`, `SkeletonPlaceholder`, `LoadFailedContent`, `LoadMoreFooter`, `ScrollEdgeFade`, `CyclingPhrases`, `rememberTextShimmerBrush` |
| 배치와 모션 | `withScreenMargins`, `withRowListMargins`, `RowRevealTransition`, `Modifier.swipeBetweenTabs` |
| 그림 | `ManyakLogo`, `DrawScope.drawManyakMascot` |

## 색

값은 `Color.kt`가 기준입니다. 값 자체는 웹과 공유하는 디자인 토큰에서 왔지만 생성기가 이 저장소 밖에 있어 여기서 다시 만들 수 없습니다. 값을 바꾸면 `Color.kt`와 이 절을 같은 커밋에서 고치고, 웹과 공유하는 값이면 PR에 웹 변경이 필요하다고 적습니다. ([A-101](../knk-harness/docs/platform/CLIENT/ANDROID/DECISIONS.md#a-101))

### 브랜드

- `colors.brand`: 로고와 일러스트 같은 큰 그래픽, 그리고 **주 버튼의 배경**입니다. 버튼에 프라이머리 색을 넣을 때는 이 색을 씁니다. 흰 글자 대비가 3.15로 AA에 못 미치지만 웹 primary와 같은 값을 우선했습니다. ([A-099](../knk-harness/docs/platform/CLIENT/ANDROID/DECISIONS.md#a-099))
- `colors.backgroundBrandBold`: 한 단계 어두운 초록. 주 버튼의 **눌림 상태**에만 씁니다. 라이트와 다크가 같은 값입니다.
- `colors.textBrand`: 링크와 강조 글자. 다크에서는 밝은 초록이라 어두운 바탕에서도 읽힙니다.
- `colors.backgroundBrandSubtle`, `colors.borderBrand`: 선택된 칩과 배지의 채움과 경계.

### 표면

| 토큰 | 용도 |
| --- | --- |
| `colors.surface` | 앱 바탕, 카드, 목록. 화면의 기본 바닥 |
| `colors.surfaceRaised` | 팝오버, 바텀시트, 다이얼로그처럼 떠 있는 판. 라이트에서 순백을 쓰는 유일한 자리 |
| `colors.backgroundNeutral` | 입력창과 비강조 채움. 눌림은 `colors.backgroundNeutralPressed` |
| `colors.backgroundNeutralRaised` | `colors.surfaceRaised` 위에 까는 회색 카드. 다크에서는 `backgroundNeutral`이 시트 바탕과 같은 값이라 한 단계 밝게 둡니다. 웹 시트 위 `bg-muted`와 같은 관계 |
| `colors.overlayPressed` | 리플 색. 알파가 곧 눌림 농도라 채움 색이 아니라 반투명입니다(라이트 검정 6%, 다크 흰색 8%) |
| `colors.backgroundDisabled` | 비활성 컨트롤의 바닥 |
| `colors.tourScrim` | 안내 투어와 안내 창의 딤(검정 50%). 이미지 뷰어보다 옅은 것은 뒤 화면이 어디인지 읽혀야 안내가 성립하기 때문 |
| `colors.imageViewerScrim` | 전체 화면 이미지 뷰어의 바탕(검정 92%) |
| `colors.imageCaption`, `colors.textOnImage`, `colors.textOnImageSubtle`, `colors.backgroundOnImage` | 표지에 바로 붙는 검은 글 영역과 그 위 글자, 장르 뱃지 바탕. 그림과 이어지는 자리라 테마와 무관하게 같은 값 |

### 텍스트

| 토큰 | 용도 |
| --- | --- |
| `colors.text` | 본문과 제목 기본 |
| `colors.textSubtle` | 보조 설명과 메타 정보 |
| `colors.textSubtlest` | 약한 보조. 본문 크기로는 `colors.surface`와 `colors.surfaceRaised` 위에서만 |
| `colors.textNarration` | 서사의 상황 묘사(`*…*`)와 상황 블록 입력 |
| `colors.textDisabled` | 비활성과 장식 전용. 읽어야 하는 글자에 쓰지 않음 |
| `colors.textInverse` | 진한 채움 위 글자 |
| `colors.textDanger`, `colors.textWarning`, `colors.textInformation` | 오류, 경고, 안내 문구 |

`colors.textNarration`은 라이트에서 `colors.textSubtlest`와 같은 값이지만 따로 둡니다. 상황 묘사는 대사와 나란히 읽는 본문이라 약한 보조 글자보다 밝아야 하고, 다크에서 그 차이가 드러납니다.

### 경계

| 토큰 | 용도 |
| --- | --- |
| `colors.border` | 구분선과 카드 테두리. 대비 요구 대상이 아님. 두께는 `sizes.borderWidth`, 입력 모양은 `sizes.inputBorderWidth` |
| `colors.borderStrong` | **점선 전용** 장식 경계. 점선은 잉크가 절반만 닿아 같은 색이어도 실선보다 연하게 읽혀 한 단계 진하게 둡니다. 실선에 쓰면 진한 테두리가 될 뿐입니다. 경계 밖에서는 `colors.border` 골격 면 위의 글 자리 막대에만 씁니다 |
| `colors.borderInput` | 경계가 유일한 식별 수단일 때. 표면이 아니라 자기 채움색 기준으로 정한 값 |
| `colors.borderFocused` | 포커스 링. `colors.borderBrand`와 같은 값 |
| `colors.borderOverlay` | 바탕과 같은 표면으로 딤 위에 뜨는 카드가 다크에서 묻히지 않게 두르는 1dp 경계. 라이트는 투명 |
| `colors.borderDanger`, `colors.borderWarning`, `colors.borderInformation` | 상태 배너와 입력창 |

### 쓰면 안 되는 조합

색 자체의 한계라 값을 바꾸지 않는 한 해소되지 않습니다. 대비는 2026-11-24의 `Color.kt` 값으로 계산했습니다. 이 저장소에서 추가하거나 바꾼 색은 웹 토큰의 대비 검증을 거치지 않으므로, 색을 바꾸면 직접 재서 이 표를 고칩니다.

| 조합 | 대비 | 대신 |
| --- | --- | --- |
| `colors.textSubtlest` + `backgroundNeutral`, `backgroundNeutralRaised`, `backgroundNeutralPressed` | 라이트 4.03~4.29, 다크 `backgroundNeutralRaised` 4.21, 다크 `backgroundNeutralPressed` 2.18 | `colors.textSubtle` |
| `colors.textDisabled` + 읽어야 하는 글자 | 라이트 2.55~2.96, 다크 1.78~4.58 | `colors.textSubtle` |
| `colors.textSubtle`, `colors.textNarration` + 다크 `backgroundNeutralPressed` | 3.68 | 눌림 채움 위에는 `colors.text` |
| `colors.brand` 채움 + `colors.textInverse` | 3.15 | 주 버튼에만 허용([A-099](../knk-harness/docs/platform/CLIENT/ANDROID/DECISIONS.md#a-099)) |

### 그라디언트

장식용 그라디언트를 토큰으로 두지 않습니다. 깊이는 표면 색 차이로만 만듭니다. 표지 아래 덮개처럼 그림과 글 영역을 잇는 자리는 그 화면이 정합니다.

## 타이포그래피

### 서체

- **Pretendard**: UI 전반. Regular, Medium, Bold를 정적 TTF로 번들합니다(`res/font/pretendard_*.ttf`).
- **MaruBuri**: 서사 글. Regular와 Bold를 **OTF로** 번들합니다(`res/font/maru_buri_*.otf`). 배포된 TTF는 TrueType 힌팅 때문에 작은 크기에서 획이 픽셀 격자에 붙어 글자마다 굵기가 갈립니다.
- 둘 다 SIL OFL 1.1이고 원문은 `assets/licenses/`에 있습니다.

### 위계

| 토큰 | 크기 | 굵기 | 행간 | 용도 |
| --- | --- | --- | --- | --- |
| `typography.headlineSmall` | 24sp | 700 | 30sp | 온보딩과 랜딩 헤드라인 |
| `typography.titleLarge` | 20sp | 700 | 27sp | 화면 제목, 탭 헤더, 시트 제목 |
| `typography.titleMediumStrong` | 18sp | 700 | 26sp | 목록 구역 제목 |
| `typography.titleMedium` | 18sp | 500 | 26sp | 섹션과 카드 제목 |
| `typography.bodyLargeStrong` | 16sp | 500 | 24sp | 카드 제목, 하위 화면 헤더 제목 |
| `typography.bodyLarge` | 16sp | 400 | 24sp | 강조 본문과 입력 |
| `typography.bodyReading` | 16sp | 400 | 28sp | 스토리 본문(MaruBuri, 자간 -2%) |
| `typography.bodyReadingSmall` | 14sp | 400 | 24.5sp | 짧은 서사 문장과 추천 입력(MaruBuri, 자간 -2%) |
| `typography.bodyMediumStrong` | 14sp | 700 | 20sp | 라벨 옆에 세우는 값(비용 행의 이프 수치), 탐색 타일 제목 |
| `typography.bodyMedium` | 14sp | 400 | 20sp | 본문 기본 |
| `typography.labelLarge` | 14sp | 500 | 20sp | 버튼과 탭 라벨 |
| `typography.bodySmall` | 12sp | 400 | 16sp | 메타 정보와 보조 설명 |
| `typography.labelSmall` | 12sp | 500 | 16sp | 타임스탬프, 최소 보조 문구, 표지 위 뱃지 |
| `typography.labelTiny` | 11sp | 500 | 14sp | 탐색 타일 장르 뱃지 |

### 원칙

- **행간은 배수가 아니라 sp 절대값입니다.** Compose `TextStyle`이 절대값만 받아 배수로 두면 플랫폼마다 반올림이 갈립니다.
- **UI 롤의 행간은 1.25~1.5이고, 서사 롤 둘만 1.75로 벌리고 자간을 -2% 좁힙니다.** 바탕 계열의 기본 자간이 장문에서 벌어져 보이기 때문입니다. 두 롤은 줄 상자를 자르지 않고 가운데 정렬합니다(`LineHeightStyle` Center, `Trim.None`).
- **MaruBuri는 `typography.bodyReading`, `typography.bodyReadingSmall`로만 씁니다.** 스토리 본문, 채팅 메시지, 추천 입력, 퍼널의 스토리라인과 추가 정보 문장이 그 자리입니다. 버튼, 라벨, 제목에 쓰지 않습니다. 제작 방식 그림 속 글자만 그림의 일부라 서체를 직접 지정합니다.
- **굵기 사다리는 400 / 500 / 700입니다.** 번들에 없는 SemiBold(600)를 요구하면 Bold로 대체 렌더되어 의도보다 두꺼워집니다.
- **`Strong`은 크기가 아니라 굵기로 갈리는 롤입니다.** 같은 이름의 기본 롤과 크기와 행간이 같고 굵기만 다릅니다(`titleMediumStrong`, `bodyMediumStrong`은 700, `bodyLargeStrong`은 500). 크기 이름이 굵기 차이를 뜻하게 되면 스케일이 거짓말을 하기 때문입니다.
- 화면 기본 글자 스타일은 `typography.bodyMedium`, 기본 색은 `colors.text`이고 `ManyakTheme`이 `LocalTextStyle`, `LocalContentColor`로 내립니다.

## 레이아웃

### 여백

이름은 크기가 아니라 **상황**으로 붙입니다. `space.200`은 값이 바뀌면 의미를 잃지만 `spacing.gutter`는 그대로 유효합니다. 2dp 격자이고 시맨틱 이름이 붙은 단계만 둡니다. 안 쓰는 중간 단계는 언젠가 이름 없이 쓰여 시맨틱 층을 무의미하게 만듭니다.

| 토큰 | 값 | 용도 |
| --- | --- | --- |
| `spacing.hairline` | 2dp | 아이콘과 라벨 사이 |
| `spacing.inline` | 4dp | 인접한 인라인 요소 |
| `spacing.dense` | 6dp | 촘촘한 요소 사이 |
| `spacing.compact` | 8dp | 목록 항목 간격, 버튼 내부 세로 |
| `spacing.controlVertical` | 10dp | 입력, 칩, 메뉴 항목의 세로 여백 |
| `spacing.component` | 12dp | 컴포넌트 내부 기본 |
| `spacing.controlHorizontal` | 14dp | 입력, 칩, 메뉴 항목의 가로 여백 |
| `spacing.gutter` | 16dp | 화면 좌우 여백 |
| `spacing.passage` | 20dp | 읽는 본문 블록의 세로 여백과 블록 안 조각 사이 |
| `spacing.section` | 24dp | 섹션 사이 |
| `spacing.block` | 32dp | 성격이 다른 큰 구획 사이 |
| `spacing.screenTop` | 64dp | 헤더 없이 시작하는 전체 화면(로그인)의 위쪽 여백 |
| `spacing.screenBottom` | 32dp | 스크롤 영역 하단 여유 |

### 화면 구성

- 화면 좌우는 `spacing.gutter`, 스크롤 영역 하단은 `spacing.screenBottom`입니다. 셸이 넘긴 콘텐츠 여백에 이 값을 더할 때는 `withScreenMargins`를 씁니다.
- **하단 탭 셸을 두르는 목록은 `spacing.screenBottom` 대신 그 목록의 리듬에 맞춥니다**(`withRowListMargins`). 홈과 제작 그리드는 행 사이 간격과 같은 `spacing.gutter`, 채팅 목록은 카드의 세로 여백과 같은 `spacing.compact`입니다. 늘 떠 있는 탭 바가 이미 끝을 알리기 때문입니다. 셸이 없는 전체 화면(채팅방, 제작 퍼널)은 `spacing.screenBottom`을 그대로 씁니다.
- 섹션 사이는 `spacing.section`, 성격이 다른 큰 구획 사이는 `spacing.block`입니다.
- 시스템 인셋은 `enableEdgeToEdge()`와 `Scaffold`의 `innerPadding`으로 처리하고 화면 여백은 그 안쪽에 얹습니다.

## 표면과 깊이

| 층 | 처리 | 용도 |
| --- | --- | --- |
| 바닥 | `colors.surface` | 앱 바탕, 카드, 목록 |
| 떠 있음 | `colors.surfaceRaised` | 팝오버, 바텀시트, 다이얼로그 |
| 채움 | `colors.backgroundNeutral` | 입력창과 비강조 채움 |
| 경계 | `colors.border`, `sizes.borderWidth` | 구분선과 카드 테두리 |

**그림자를 쓰지 않습니다.** 깊이는 표면 색 차이와 경계선으로만 만듭니다. M3의 tonal elevation도 끕니다. `surfaceTint`를 투명으로 파생해 표면이 고도에 따라 브랜드 색으로 물드는 것을 막습니다.

예외는 떠 있는 판이 같은 밝기의 바탕과 겹쳐 경계만으로 층이 드러나지 않는 자리뿐입니다. 셀렉트 메뉴, 장르 검색 메뉴, 툴팁, 비용 안내 판은 4dp 그림자(`sizes.selectMenuElevation`)를 쓰고, 신규 가입 온보딩 캐러셀 카드는 아주 옅게 넓힌 그림자를 씁니다([STORY 3-3-2](../knk-harness/docs/spec/STORY/CLIENT/ANDROID/3-3-LAYOUT.md#3-3-2-가입-온보딩)).

## 크기

| 토큰 | 값 | 용도 |
| --- | --- | --- |
| `sizes.control` | 48dp | 버튼과 탭처럼 누르는 일반 컨트롤의 높이. 안드로이드 최소 터치 타깃 |
| `sizes.input` | 40dp | 입력창, 칩, 셀렉트 앵커의 최소 높이 |
| `sizes.controlCompact` | 40dp | 카드 안 라벨 버튼의 보이는 높이. 터치 영역은 48dp 유지 |
| `sizes.controlSmall` | 32dp | 작은 보조 버튼. 최소 터치 타깃보다 작아 주된 동작에 쓰지 않음 |
| `sizes.controlTiny` | 28dp | 제목 줄 높이 안에 드는 원형 보조 버튼 |
| `sizes.iconTiny` | 12dp | 12sp 라벨 옆 아이콘 |
| `sizes.iconSmall` | 16dp | 밀도 높은 컨트롤 안 아이콘 |
| `sizes.icon` | 20dp | 라벨 옆 아이콘, 제공자 로고 |
| `sizes.tabIcon` | 24dp | 하단 탭 아이콘. 라벨 옆에 붙는 아이콘이 아니라 탭의 주된 시각 요소라 더 큼(웹도 24px) |
| `sizes.logo` | 24dp | 로고 락업 높이. 폭은 원본 비율(89:32) |
| `sizes.startupSymbol` | 64dp | 시작 화면 로고 심벌 |
| `sizes.selectionBorderWidth` | 2dp | 미선택 체크박스 경계, 선택 테두리. M3 라디오 버튼과 같음 |
| `sizes.borderWidth` | 1dp | 카드, 버튼, 칩, 이미지 테두리와 구분선 |
| `sizes.inputBorderWidth` | 1dp | 입력 모양 경계(입력창, 셀렉트 앵커와 메뉴, 검색 메뉴, 채팅 입력 상자) |
| `sizes.selectMenuElevation` | 4dp | 그림자 예외 판의 그림자 |
| `sizes.shimmerBandHalfWidth` | 60dp | 글자 시머 띠의 반폭 |
| `sizes.generationDotGap`, `sizes.generationDotRadius` | 10dp, 1dp | 마스코트 무대 바탕 점의 간격과 반지름 |
| `sizes.tourCardWidth` | 288dp | 안내 투어와 이미지 안내 카드 폭. 좁으면 좌우 `spacing.gutter`를 남기고 줄어듦 |
| `sizes.tourStepDot` | 6dp | 안내 투어 스텝 점 |
| `sizes.mascotAvatar` | 48dp | 간편 제작 첫 진입 안내의 마스코트 프로필 |
| `sizes.studioCoverWidth` | 128dp | 제작 탭 카드 표지 폭 |
| `sizes.unreadDot`, `sizes.unreadDotInline` | 8dp, 6dp | 헤더 공지 버튼과 목록 제목 끝의 안 읽음 점 |
| `sizes.menuMaxHeight` | 240dp | 셀렉트와 장르 검색 메뉴 최대 높이. 40dp 항목과 4dp 간격으로 5줄 반이 보여 더 있다는 것이 드러남 |
| `sizes.choiceListMaxHeight` | 284dp | 시트 안 선택 목록 최대 높이. 48dp 항목으로 5줄 반 |

높이 토큰은 웹 디자인 토큰에 없어 이 저장소가 정합니다. 밀집하는 입력, 칩, 셀렉트 앵커는 40dp로 낮추고 터치 타깃 미달을 알고 받아들입니다.

## 모션

| 토큰 | 값 | 쓰임 |
| --- | --- | --- |
| `motion.screenTransitionMillis` | 150ms | 탭이 바뀔 때 교차 페이드, 선택 테두리 |
| `motion.screenSlideMillis` | 250ms | 스택에 쌓이고 걷히는 화면의 밀기 |
| `motion.elementEnterMillis` | 200ms | 화면 안 작은 요소가 나타날 때 |
| `motion.elementExitMillis` | 150ms | 화면 안 작은 요소가 사라질 때 |
| `motion.listItemEnterMillis` | 300ms | 차례로 드러나는 목록의 항목 하나 |
| `motion.listItemStaggerMillis` | 80ms | 그 항목들이 시작하는 간격 |
| `motion.nudgeDimEnterMillis`, `nudgeCardDelayMillis`, `nudgeCardEnterMillis`, `nudgeExitMillis` | 550, 200, 450, 300ms | 실시간 이미지 안내의 딤 등장, 카드 시작 지연, 카드 등장, 퇴장 |

모션도 웹 토큰에 없어 이 저장소가 정합니다. 시간만 정하고 무엇을 움직일지는 쓰는 쪽이 정합니다. 마스코트 안무, 시작 화면 점프처럼 성격이 다른 움직임은 그 자리에서 고정합니다.

- **전환은 짧습니다.** 내비게이션 라이브러리 기본값 700ms는 자주 오가는 탭 전환에서 뒤늦게 따라오는 느낌을 줍니다. 150ms는 전환이 있었다는 것만 알리고 비켜섭니다.
- **밀기는 페이드보다 깁니다.** 쌓이고 걷히는 화면은 폭 전체를 움직여 150ms로는 어느 쪽에서 왔는지 눈이 따라가지 못합니다. 움직이는 화면은 불투명하고, 덮이는 화면은 폭의 1/4만 물러나며 60%로 어두워집니다. 제작 퍼널도 별도 덮개 전환 없이 같은 밀기를 씁니다. 예측형 뒤로가기는 같은 변환을 손가락 진행도로 되감으므로 시간 토큰을 쓰지 않습니다.
- **등장은 퇴장보다 깁니다.** 나타나는 요소는 눈이 따라갈 시간이 필요하지만 사라지는 요소는 이미 볼 일이 끝났습니다. 방향도 함께 씁니다. 목록 끝으로 보내는 버튼은 아래에서 올라오고 아래로 내려가 사라집니다.
- **사용자가 늘리고 줄이는 목록의 칸은 아래 변을 붙잡고 자라고 접힙니다**(`RowRevealTransition`). 위를 붙잡으면 손대지 않은 위쪽 칸까지 밀렸다 당겨져 어느 칸이 바뀌었는지 읽히지 않습니다. 지운 칸은 접힘이 끝난 뒤 목록에서 뺍니다.
- **차례로 드러나는 목록은 항목 하나가 더 깁니다.** 300ms는 뒤 항목이 80ms씩 늦게 출발해도 앞 항목이 아직 움직여 목록 전체가 한 흐름으로 읽히게 합니다. 값은 웹과 같습니다.
- **앵커 아래 팝업은 스프링으로 자리 잡습니다**(`ManyakPopup`). 열 때 판이 앵커 쪽 위 변에서 94% 크기, 6dp 위에서 튕김 없는 스프링(`Spring.StiffnessMediumLow`, 약 0.3초)으로 자리 잡고 투명도는 150ms 감속 곡선으로 먼저 찹니다. 앵커 위에 뜨는 판은 `slideFromBelow`로 6dp 아래에서 올라옵니다. 닫을 때는 120ms 가속 곡선으로 흐려지며 97%로만 줄어듭니다. 셀렉트 꺾쇠도 같은 스프링으로 반 바퀴 돕니다. 웹(100ms 페이드와 95% 확대)보다 조금 긴 것은 팝업 창의 첫 프레임이 늦어도 남은 움직임이 끊겨 보이지 않게 하려는 것입니다. 항목이 많은 목록은 보이는 항목만 그립니다.
- **보는 중에 목록 순서가 바뀌면 카드가 새 자리로 미끄러집니다.** `animateItem`의 기본 배치 스프링으로 옮기고, 생기거나 빠지는 카드는 `motion.elementEnterMillis`, `motion.elementExitMillis`로 페이드합니다. 위 카드일수록 앞에 쌓고(`zIndex`) `colors.surface`를 칠해 오르는 카드가 내려가는 카드를 덮고 지나가며 글자가 겹치지 않습니다. 처음 그리는 목록은 움직이지 않고, 기기 애니메이션을 끄면 바로 바뀝니다.
- **눌림은 리플 하나로 말합니다.** `ManyakTheme`이 `LocalRippleConfiguration`으로 `colors.overlayPressed`의 색과 농도를 내리고 호버, 포커스, 드래그 농도는 0입니다. `clickable`을 쓰는 카드, 행, 메뉴 항목과 M3 버튼이 같은 리플을 받습니다. 홈 스토리 카드는 둥글게 클립하지 않아 리플이 사각형으로 돕니다. 카드 곡률로 깎으면 맨 아래 줄이, 표지 곡률로 깎으면 표지 테두리가 클립 경계와 겹치기 때문입니다. 눌림 표시를 축소 같은 다른 애니메이션으로 더하지 않습니다. 길게 누르기는 열리는 순간 `LongPress` 햅틱을 한 번 울립니다. ([A-100](../knk-harness/docs/platform/CLIENT/ANDROID/DECISIONS.md#a-100))
- **선택 표시가 피드백을 맡는 자리는 리플을 두지 않습니다.** 라디오, 체크박스, 행 전체로 토글하는 스위치가 있는 행은 `selectable`, `toggleable`에 `interactionSource = null`, `indication = null`을 주되 행 전체 터치 영역, 접근성 역할, 선택 상태는 유지합니다. 컨트롤 자체에는 클릭을 따로 연결하지 않아 한 번만 토글되고 읽힙니다. 하단 탭, 밑줄 탭, 이미지 뷰어 닫기 영역도 리플을 끕니다.

## 모양

| 토큰 | 값 | 용도 |
| --- | --- | --- |
| `shapes.checkbox` | 6dp | 한 변이 20dp 남짓인 작은 네모 |
| `shapes.menuItem` | 10dp | 셀렉트 메뉴 항목, 콘텐츠 안 작은 아이콘 버튼 |
| `shapes.thumbnail` | 12dp | 작은 썸네일과 작은 아이콘 컨테이너 |
| `shapes.control` | 14dp | 버튼, 입력창, 칩 |
| `shapes.card` | 16dp | 카드와 목록 카드 표지 |
| `shapes.overlay` | 20dp | 다이얼로그, 큰 인물 이미지와 그 카드 |
| `shapes.featureCard` | 24dp | 화면의 주인공으로 크게 놓이는 카드 |
| `shapes.sheet` | 위쪽 두 모서리 20dp | 바텀시트 |
| `shapes.pill` | `CircleShape` | 배지, 칩, 아바타 |

`shapes.pill`은 큰 dp가 아니라 `CircleShape`입니다. 값으로 두면 큰 요소에서 모서리가 잘못 그려집니다. 그래서 `ManyakShapes`는 dp가 아니라 `Shape`를 내립니다. 테두리를 두른 요소 안쪽의 곡률은 `insetForBorder`로 바깥 곡률에서 테두리 두께를 뺍니다.

## 컴포넌트

공용 컴포넌트는 두 번째 사용처가 생길 때 `:core:designsystem`으로 올립니다. 한 기능만 쓰는 헤더, 단계 표시, FAB 같은 요소의 규격은 그 도메인의 화면 배치 문서에 있습니다([화면별 배치](#화면별-배치)).

### 버튼

- **주 버튼**: 배경 `colors.brand`, 글자 `colors.textInverse`와 `typography.labelLarge`, 모서리 `shapes.control`, 여백 세로 `spacing.compact`와 가로 `spacing.component`. 눌림은 배경만 `colors.backgroundBrandBold`. M3 슬롯 파생(primary = `backgroundBrandBold`)은 안전망이므로 기본 `Button` 색에 기대지 말고 색을 명시합니다. 주 버튼 공용 컴포저블은 아직 없고 마이와 제작 퍼널이 각자 둡니다.
- **파괴 버튼**: 탈퇴와 삭제. 같은 모양에 배경만 `colors.backgroundDangerBold`, 눌림 `colors.backgroundDangerBoldPressed`.
- **중립 버튼**(`ManyakNeutralButton`): 보조 동작. 배경 `colors.backgroundNeutral`, 글자 `colors.text`, `colors.border` 1dp 경계, 눌림 `colors.backgroundNeutralPressed`. 기본 여백은 M3 버튼과 같고, `compact = true`면 `sizes.controlCompact` 높이와 `spacing.controlHorizontal` 좌우 여백입니다. 퍼널의 이전과 다시 만들기, **조회 실패 자리의 다시 시도**가 씁니다. 실패로 멈춘 자리에 브랜드 색 버튼을 두면 새로 시작하라는 권유처럼 읽힙니다.
- **글자 버튼**(`ManyakTextButton`): 다이얼로그와 시트의 닫기, 배너의 보조 동작, 인라인 재시도. 배경 없음, `typography.labelLarge`, 색은 자리가 정합니다(닫기 `colors.textSubtle`, 진입과 재시도 브랜드 색). 모서리는 `shapes.control`입니다. M3 `TextButton` 기본은 알약이라 리플이 채움 버튼과 다른 모양으로 돕니다. 제출 중에는 비활성 `colors.textDisabled`입니다.
- **비활성**: 배경 `colors.backgroundDisabled`, 글자 `colors.textDisabled`. 색만으로 전달하지 않고 상태 안내를 함께 둡니다.
- **아이콘 버튼**(`ManyakIconButton`): 배경 없음. 앱바의 뒤로가기와 닫기(`sizes.control`)는 안드로이드 관례대로 원(`shapes.pill`)입니다. 콘텐츠 안 작은 버튼(입력 옆 삭제, 카드 제목 줄 더보기, 컴포저 아이콘, `sizes.controlSmall` 이하)과 낮은 글자 토글은 `shapes.menuItem`입니다. 버튼과 나란히 놓이는 큰 아이콘 버튼(상세 하단의 좋아요)은 옆 버튼과 같은 `shapes.control`이라 짝으로 읽힙니다. M3 `IconButton`은 모양을 고를 수 없어 쓰지 않습니다.
- **더보기**(`ManyakMoreButton`): 헤더와 카드의 메뉴는 채워진 가로 점 셋 `ic_more_horizontal`입니다.
- **추가 버튼**(`ManyakAddButton`): "키워드 추가", "인물 추가", "페르소나 추가" 같은 목록 끝 추가. 칩 모양에 `colors.backgroundNeutral` 채움과 `colors.border` 경계, 16dp `+` 아이콘이고 폭을 채우지 않을 때는 가운데에 둡니다.
- **정보 툴팁 버튼**(`ManyakInfoTooltipButton`): 제목 옆 도움말. 누르면 설명 판이 뜨고 판은 그림자 예외입니다.

### 선택 컨트롤

- **체크박스**(`ManyakCheckbox`): `sizes.icon` 정사각, `shapes.checkbox`. 기본은 `colors.surface` 채움과 `sizes.selectionBorderWidth` 두께의 `colors.border` 경계, 체크되면 `colors.brand` 채움과 같은 색 경계, 16dp `colors.textInverse` 체크입니다. 체크박스는 누르는 대상이 아닙니다. 줄 전체가 토글을 맡아 문구를 눌러도 켜지고 터치 타깃도 줄이 확보하며, 접근성 이름도 줄이 읽힙니다.
- **스위치**(`ManyakSwitch`): M3 `Switch`의 크기와 모양을 그대로 두고 색만 얹습니다. 꺼짐은 `colors.backgroundNeutral` 트랙, `colors.borderStrong` 경계, `colors.textSubtlest` 손잡이이고 켜짐은 `colors.brand` 트랙에 `colors.textInverse` 손잡이입니다.
  - 켤 수 없는 줄은 흐리게 두지 않고 숨깁니다. 비활성은 값이 살아 있는데 지금 손댈 수 없을 때만 씁니다(기기 알림이 꺼진 동안의 알림 설정). 위치는 유지한 채 켜짐은 `colors.textDisabled` 트랙, 꺼짐은 `colors.backgroundDisabled` 트랙과 경계, 손잡이 `colors.textDisabled`이고 같은 행의 라벨과 설명도 `colors.textDisabled`로 누릅니다.
  - 설정 화면의 스위치는 스위치 자신이 누르는 대상입니다. 라벨 옆에 문서를 여는 아이콘 버튼이 함께 놓여 줄 전체 토글과 겹치기 때문입니다. 접근성 이름은 줄의 라벨을 붙입니다. 옆에 다른 버튼이 없는 시트(채팅 설정)는 행 전체를 `Role.Switch`로 토글하고 스위치는 `onCheckedChange = null`로 표시만 맡습니다.
  - 불러오기 전에는 같은 크기의 골격이 자리를 지킵니다.
- **선택 테두리**(`Modifier.selectionRing`): 고른 칸 바깥쪽의 `sizes.selectionBorderWidth` 테두리. 색은 자리가 정하고(`colors.borderBrand` 또는 `colors.text`) `motion.screenTransitionMillis` 동안 채웁니다. 선택 변화 자체가 반응이라 리플을 그리지 않습니다.

### 입력

- **텍스트 필드**(`ManyakTextField`, `ManyakMultilineTextField`): 배경 `colors.surfaceRaised`, `colors.border` 1dp 경계, `typography.bodyMedium`, `shapes.control`, 여백 세로 `spacing.controlVertical`과 가로 `spacing.controlHorizontal`, 최소 높이 `sizes.input`. 포커스는 경계만 `colors.borderInput`으로 진하게 바꾸고 브랜드 색을 쓰지 않습니다. 입력 경계는 무채색 단계로만 말합니다. 오류는 경계 `colors.borderDanger`와 아래 `colors.textDanger` + `typography.bodySmall` 문구이고 색만으로 알리지 않습니다. 글자 수는 `ManyakInputCounter`로 오른쪽 아래에 둡니다.
- **포커스와 키보드**: 입력란 사이 이동과 같은 입력란 재터치(라벨과 여백 포함)에서는 키보드를 유지합니다. 입력란 밖 빈 영역, 버튼, 칩을 누르면 포커스와 키보드를 닫고 버튼과 칩의 동작도 그대로 실행합니다. 스크롤, 드래그, 다중 터치는 포커스를 풀지 않습니다. 앱 루트, 공통 시트, 입력 다이얼로그에 `clearFocusOnTap`을 걸고, 공통 입력과 직접 쓰는 `BasicTextField`, 키보드를 유지해야 하는 버튼(채팅 작성, 추가 정보 편집)에 `keepKeyboardOnTap`을 겁니다. 두 Modifier는 터치 이벤트를 소비하지 않습니다. ([CHAT 4-4](../knk-harness/docs/spec/CHAT/CLIENT/ANDROID/4-4-EXECUTION.md), [STORY 3-2](../knk-harness/docs/spec/STORY/CLIENT/ANDROID/3-2-INTERACTION-FLOWS.md))
- **셀렉트**(`ManyakSelectField`): 앵커는 텍스트 필드와 같은 모양이고 미선택(랜덤) 값은 `colors.textDisabled`로 낮춥니다. 메뉴는 앵커와 같은 폭으로 항상 앵커 아래 `spacing.inline`에 열리고, `colors.surfaceRaised`, `colors.border`, 그림자 예외의 옅은 그림자를 씁니다. 판 안쪽 여백과 항목 사이는 `spacing.inline`, 항목(`ManyakSelectOption`)은 `shapes.menuItem`, 세로 `spacing.controlVertical`, 가로 `spacing.controlHorizontal`, 최소 `sizes.input`입니다. 고른 항목은 `colors.backgroundNeutral` 채움과 체크로 드러냅니다. 값 항목이 `sizes.menuMaxHeight`를 넘으면 메뉴 안에서 스크롤합니다.
  - 값 아래에 값이 아닌 동작 항목(`ManyakSelectAction`, 상세의 "페르소나 생성하기")을 둘 수 있습니다. `colors.border` 1dp 구분선 아래 스크롤 밖에 고정하고 `sizes.iconSmall` 더하기 아이콘과 라벨을 `colors.textSubtle`로 흐리게 두며 선택 채움과 체크는 없습니다. 누르면 메뉴를 닫고 동작을 실행합니다.

### 컨테이너

- **카드**: 배경 `colors.surface`, `colors.border` 1dp, `shapes.card`, 안쪽 `spacing.component`. 제목 `typography.titleMedium`, 본문 `typography.bodyMedium`, 메타 `typography.bodySmall` + `colors.textSubtle`.
- **다이얼로그**(`ManyakDialog`): 배경 `colors.surfaceRaised`, `shapes.overlay`, 안쪽 `spacing.gutter`. 확인 다이얼로그(`ManyakConfirmDialog`)는 제목과 설명 아래 오른쪽 정렬 버튼 줄에 `colors.textSubtle` 글자 버튼과 확정 버튼을 둡니다. 확정 버튼은 `shapes.control`, `typography.labelLarge`, 기본 `colors.brand` 채움이고, 무언가를 잃는 확인(`ManyakDestructiveDialog`)만 `colors.backgroundDangerSubtle` + `colors.textDanger`입니다.
- **바텀시트**(`ManyakBottomSheet`): 배경 `colors.surfaceRaised`, `shapes.sheet`로 위쪽만 깎습니다. 아래를 깎으면 그 틈으로 스크림이 비칩니다. 안쪽 여백은 좌, 우, 아래 `spacing.gutter`이고 위쪽은 드래그 핸들의 자체 여백이 맡습니다. 하단 안전 영역과 키보드 높이는 시트가 직접 낍니다.
  - **닫기 버튼은 위에 주 동작 버튼이 있을 때만 그 짝으로 둡니다.** 전체 폭, 최소 높이 `sizes.control`, `typography.labelLarge`, `colors.textSubtle`의 글자 버튼이고 주 버튼과 `spacing.compact` 띕니다. 주 동작이 없는 시트(읽기 전용, 옵션 시트)는 스크림, 끌어내리기, 뒤로가기로만 닫습니다. 닫기 하나뿐인 줄은 자리만 차지합니다.
  - **닫힘을 막은 시트는 핸들 드래그도 잠급니다.** 끌리기만 하고 닫히지 않는 튕김이 닫을 수 있다는 신호가 되기 때문입니다. 끌어내리기가 아예 없는 시트(필수 동의)는 핸들 없이 위쪽 여백을 `spacing.gutter`로 채우고, 전송 중처럼 잠깐 잠그는 시트는 핸들을 그대로 둡니다.
  - 본문에 스크롤 목록이 있어 목록을 끌다 닫히기 쉬운 시트는 `dismissOnContentDrag = false`로 본문 끌기를 시트에 넘기지 않습니다.
- **옵션 시트**(`ManyakOptionsSheet`, `ManyakOptionItem`): 카드 옵션, 상세 옵션, 채팅 메뉴. 머리글(종류를 `typography.bodyMedium` + `colors.textSubtle`로 작게, 제목을 `typography.titleLarge` 한 줄) 아래 `spacing.gutter`에 항목을 쌓고 닫기 버튼과 카드 축소판은 두지 않습니다. 항목은 아이콘 `sizes.icon`, 아이콘과 라벨 사이 `spacing.component`, 최소 `sizes.control`, 가로 `spacing.compact`, `shapes.menuItem`, 라벨 `typography.bodyLarge`입니다. 파괴 항목은 아이콘과 라벨을 `colors.textDanger`로 칠해 맨 아래에 두고, 진행 중 항목은 오른쪽에 `sizes.icon` 스피너를 두고 탭을 막습니다.
- **메뉴 행**: 마이 메뉴와 채팅 설정 시트의 행. 왼쪽 아이콘 `sizes.icon`, 아이콘과 라벨 사이 `spacing.component`, 오른쪽 이동 아이콘 `sizes.iconSmall`, 최소 `sizes.control`.
- **배너**: 배경은 상태별 `subtle`, 글자는 같은 계열의 글자 색, `shapes.control`, 안쪽 `spacing.component`. 색만으로 구분되지 않게 문구를 적습니다.

### 뱃지와 표시

- **배지**: 배경 `colors.backgroundBrandSubtle`, 글자 `colors.textBrand` + `typography.bodySmall`, `shapes.pill`, 여백 세로 `spacing.hairline`과 가로 `spacing.compact`. 상태가 아니라 정보인 배지는 `colors.backgroundNeutral` + `colors.textSubtle`, 들어가 보라는 신호(마이의 "새 공지")는 `colors.backgroundDangerSubtle` + `colors.textDanger`이고 모양과 여백은 같습니다.
- **메타 칩**(`MetaChip`): 아이콘 하나에 값 하나를 붙인 메타 표시(채팅 목록 카드, 제작 탭 카드). 아이콘에 이름을 붙이지 않고 칩 전체를 "턴 15회"처럼 무엇인지까지 담은 한 문장으로 읽힙니다.
- **이프 수치**(`CreditAmountText`): `res/drawable-nodpi/if_credit_mark.png` 마크를 수치 앞 `spacing.inline`에 붙입니다. 마크 크기는 옆 글자의 `fontSize`를 dp로 바꾼 값이라 큰 글자에서 같이 커지고, 장식이라 접근성 이름이 없습니다. 체험으로 깎인 비용은 정가에 취소선을 긋고 오른쪽에 적용가를 둡니다. 정책을 받기 전에는 자리표시 숫자에 골격 맥박을 얹습니다.
- **내 이프 카드**(`CreditBalanceCard`): 라벨, 잔액, 충전 버튼. 마이와 채팅 메뉴 시트가 같이 쓰고 카드 바탕은 `colors.backgroundNeutralRaised`입니다. 강조색은 충전만 씁니다.

### 탭

- **밑줄 탭**: M3 `SecondaryTabRow` 기본을 씁니다(`TabRow`는 deprecated). 컨테이너 `colors.surface`, 선택 라벨 `colors.text`, 비선택 `colors.textSubtle`. 표시선은 탭 폭에 맞는 `colors.text` 1.5dp 선이고 초록을 쓰지 않습니다. 선택 표시는 상태이지 다음 동작이 아니기 때문입니다. 탭을 누르면 라벨 색과 표시선이 곧바로 바뀌므로 리플을 끕니다.
- **밀어서 넘기기**(`Modifier.swipeBetweenTabs`): 왼쪽으로 밀면 다음 탭, 오른쪽이면 이전 탭입니다. 내용은 손가락을 반쯤 따라 밀리고 폭의 1/4을 넘기거나 400dp/s보다 빠르게 튕기면 넘어가며, 새 탭은 민 쪽 반대편에서 폭의 15%만큼 `motion.elementEnterMillis` 동안 들어옵니다. 첫 탭과 마지막 탭 너머, 잠긴 탭 쪽으로는 10%만 밀리고 돌아옵니다. 탭 줄이 목록 안에 고정된 화면은 목록 전체에서 밀림을 받아 아래 빈 자리에서도 밀 수 있고, 안쪽의 가로 스크롤 줄이 먼저 끌림을 가져갑니다.

### 셸

셸 컴포넌트는 **M3 컴포넌트 위에 색만 얹어** 만듭니다. 인셋, 높이, 터치 타깃, 시맨틱을 직접 계산하지 않기 위해서이고 이 시스템에 없는 요소만 지웁니다.

- **브랜드 헤더와 섹션 헤더**(`ManyakBrandHeader`, `ManyakSectionHeader`): 메인 탭 상단. 홈은 로고만, 나머지 탭은 제목(`typography.titleLarge`)만 둡니다. `TopAppBar` 위에 배경 `colors.surface`와 제목 색 `colors.text`를 얹고, 좌우 여백은 앱 바 기본값 16dp라 `spacing.gutter`와 같습니다. 높이는 최소 64dp이고 제목이 커지면 함께 늘어나며, 구분선과 그림자는 없습니다.
- **하위 화면 헤더**(`ManyakDetailHeader`): 셸 밖 하위 목적지(마이 하위 화면, 이프 충전)의 앱 바. 뒤로가기와 제목(`typography.bodyLargeStrong`), 제목 옆 요소를 둡니다. 섹션 헤더보다 한 단 작은 것은 화면 이름이 아니라 지금 보고 있는 것의 이름이라 길어질 수 있기 때문이고, 한 줄로 자릅니다. 스토리 상세와 채팅방은 각자 헤더를 그립니다.
- **하단 탭 바**(`ManyakNavigationBar`): 홈, 채팅, 제작, 탐색, 마이 고정 5탭([공통 UI 요구사항](../knk-harness/docs/ui/2-2-REQUIREMENTS.md#2-2-1-적용-범위)). 배경 `colors.surface`, 위쪽 `colors.border` 1dp. 아이콘(`sizes.tabIcon`) 아래 이름 `typography.labelSmall`. 선택은 filled 아이콘 + `colors.text`, 비선택은 outline 아이콘 + `colors.textSubtle`이고 아이콘과 라벨이 같은 색입니다.
- **사업자 정보 푸터**(`BusinessInfoFooter`): 홈 섹션 목록과 마이 메뉴 바로 아래에 붙는 같은 푸터. 위 내용과 48dp(`spacing.block` + `spacing.gutter`)를 푸터가 갖고, 좌우 `spacing.gutter` 안쪽에 `colors.border` 1dp 가로 구분선, 그 아래 `spacing.gutter`에 라벨과 값 쌍을 `typography.bodySmall` + `colors.textSubtle`로 쌍 단위 줄 바꿈합니다. 아래 문서 링크 세 개는 최소 `sizes.control` 높이 버튼이고 좁으면 다음 줄로 넘기고 세로선은 같은 줄의 링크 사이에만 둡니다. 바탕은 화면과 같고 화면 맨 아래로 밀지 않으며, 홈의 첫 로딩, 빈 상태, 오류 상태에서는 안내 아래 화면 끝에 둡니다.
  - 라벨이 이름을 맡으므로 아이콘은 장식으로 두고 접근성 이름을 붙이지 않습니다. 둘 다 붙이면 같은 이름을 두 번 읽습니다.
  - 위쪽 경계선은 바 배경이 화면 바탕과 같고 콘텐츠가 바 아래로 흘러 들어가기 때문에 둡니다. 그림자를 쓰지 않으니 경계선이 유일한 수단입니다. 상단 헤더는 위로 흐르는 것이 없어 두지 않습니다.
  - `NavigationBar` 위에서 알약 선택 표시자(투명)와 리플을 지웁니다. 아이콘 모양과 색이 곧바로 바뀌는 것이 반응입니다.
  - 선택에 브랜드 색을 쓰지 않습니다. 이 시스템에서 초록은 지금 누를 것을 뜻하는데, 늘 떠 있는 바에 초록을 띄우면 화면 안 주 동작과 겹칩니다.
- **당겨서 새로고침**(`ManyakPullToRefreshBox`): M3 `PullToRefreshBox` 기본 표시자에 배경 `colors.surfaceRaised`와 스피너 `colors.progressIndicator`를 얹습니다. 표시자는 셸이 넘긴 콘텐츠 여백의 상단만큼 내려 헤더 뒤에서 나옵니다. 발동 거리는 M3 기본 80dp 대신 56dp이고(M3는 손가락 이동의 절반만 세어 112dp를 끌면 새로고침), 표시자도 56dp까지만 내려와 멈춥니다. 응답이 빨라도 스피너가 최소 300ms 돌고, 목록이 그려진 상태에만 둡니다(골격, 조회 실패, 빈 목록에는 없음).
- **시스템 바 아이콘**(`SystemBarIconRequests`): 앱 테마와 화면의 밝은 아이콘 요청을 합쳐 루트 한 곳에서 시스템 바 아이콘 밝기를 적용합니다. edge-to-edge 기본값은 시스템 다크 모드를 따르므로 앱 테마와 어긋나면 상태 바 글자가 배경에 묻힙니다. 화면은 컨트롤러를 직접 바꾸지 않고 요청만 올립니다.

### 스토리 표시

- **표지**(`StoryCover`): 표지가 없으면 초안 카드와 같은 `ic_manyak_symbol`을 `colors.backgroundNeutral` 위 `colors.textDisabled` 32dp로 둡니다. 모서리는 웹과 같이 홈, 제작 탭, 이미지 스타일 표지와 그 골격이 `shapes.card`, 채팅 목록 표지가 `shapes.thumbnail`이고 화면 폭을 채우는 상세 히어로와 탐색 타일은 각집니다. ORIGINAL 태그의 좌상단은 `shapes.card` 표지의 테두리 안쪽 곡률입니다.
- **표지 위 뱃지**(`StoryCoverBadge`, `Modifier.coverBackdrop`): 좋아요 수와 턴 수, ORIGINAL 태그, 화풍 이름, 인물 카드 이전과 다음 버튼은 웹의 `backdrop-blur-md`처럼 뒤 그림을 흐려 바탕에 깝니다. 흐림은 표준편차 12dp이고 그 위에 검정 20%, ORIGINAL 태그는 도안 색 #111414 20%를 덮습니다. 흐림을 그릴 수 없는 API 31 미만은 #111414 70%입니다. 웹 상세는 흐림 없는 검정 70%라 상세 히어로만 웹과 다릅니다. 턴 수 아이콘은 웹과 같은 말풍선입니다.
- **스토리 뱃지 크기**(`StoryBadgeScale`): 웹 CSS처럼 한 줄 글자의 줄 상자를 자르지 않습니다(`LineHeightStyle.Trim.None`). Compose 기본 `Trim.Both`는 글꼴 높이로 잘라 뱃지가 3dp 낮아집니다. 접힌 장르는 `+N` 글자 뱃지입니다.

| 크기 | 글자 | 여백 | 높이 | 자리 |
| --- | --- | --- | --- | --- |
| `Large` | `typography.labelLarge` | 가로 `spacing.component`, 세로 `spacing.inline` | 28dp | 상세 히어로의 좋아요와 턴 수, 상세 장르와 본 엔딩, 제작 카드 장르 |
| `Compact` | `typography.labelSmall` | 가로 `spacing.compact`, 세로 `spacing.hairline` | 20dp | 홈 카드 장르, 목록 카드 표지 뱃지 |
| `Tiny` | `typography.labelTiny` | 가로 `spacing.dense`, 세로 `spacing.hairline` | 18dp | 탐색 타일 장르 |

- **스토리 본문**: 배경 `colors.surface`, `colors.text` + `typography.bodyReading`, 좌우 `spacing.gutter`. 마크업은 웹과 같은 규칙으로 파싱합니다(`parseTextSegments`, `parsePassageSegments`). `**…**`는 볼드, 단일 `*…*`(내레이션, 속마음)는 `colors.textNarration`입니다.
- **골격**(`SkeletonPlaceholder`): 바탕 `colors.border`이고 한 화면의 조각이 함께 불투명도 100%와 50% 사이를 700ms씩 오가며 맥동합니다. `colors.backgroundNeutral`은 화면 바탕과 거의 같아 맥동으로 옅어지면 빈 화면과 구분되지 않습니다. 웹은 `bg-muted`라 Android 골격이 한 단계 진합니다. 탐색 칸 골격은 조각을 따로 놓지 않고 칸 전체를 `colors.border` 한 면으로 채운 뒤 글 자리 막대를 `colors.borderStrong`으로 얹고, 투명도는 면에만 걸어 막대가 함께 맥동합니다. 실제 목록 끝에 골격을 잇는 곳은 `rememberSkeletonPulse`의 상태를 그리기 단계에서만 읽어 목록을 프레임마다 다시 구성하지 않습니다.
- **글자 시머**(`rememberTextShimmerBrush`): 대기 문구 위를 지나가는 띠. 반폭 `sizes.shimmerBandHalfWidth`.
- **인물 이미지**(`CharacterImage`): 장면과 채팅 인물 이미지. 4:3 원본 전체 표시, `shapes.overlay`, 실패하면 자리를 지웁니다. 버튼 접근성 이름은 "{이름} 인물 이미지 크게 보기"입니다.
- **전체 화면 이미지 뷰어**(`FullscreenImageViewer`): 상세 썸네일, 주변 인물과 채팅 인물 이미지가 같이 씁니다. 바탕 `colors.imageViewerScrim`, 닫기 아이콘 `colors.textInverse`. 핀치 1~5배, 팬, 더블탭 2.5배 토글이고 X, 이미지 밖 배경 탭, 시스템 뒤로가기로 닫습니다. 그림 위 탭으로 닫지 않고 배경 탭은 떼는 즉시 닫으며 두 번째 탭 대기는 그림 위 탭에만 둡니다. ([공통 UI Android 3-3](../knk-harness/docs/ui/ANDROID/3-3-LAYOUT.md#3-3-1-셸과-조작))

### 마스코트와 로고

- **마스코트**(`drawManyakMascot`): 로고 심벌을 눈이 움직이는 마스코트로 그리는 `DrawScope` 함수. 동그란 눈 외에 힘주는, 웃는, 졸린, 반짝이는, 윙크, 어지러운 눈이 있습니다. 시작 화면, 제작 탭 완성 중 표지, 채팅 실시간 이미지 대기, 간편 제작 첫 진입 안내가 씁니다. 안무는 웹과 같은 값을 `mascot/`의 재료로 조립하고 장면별 연출은 하네스 도메인 문서와 결정 기록에 있습니다. 소품은 다크에서도 바탕과 갈리도록 `colors.surfaceRaised` 위 `colors.borderStrong` 테두리를 두르고, 소품 묶음은 불투명하게 그린 뒤 한 번에 투명도를 줘 겹친 붓질이 진해지지 않게 합니다. 기기 애니메이션을 끄면 정해 둔 장면에 멈춥니다.
- **마냑 로고**(`ManyakLogo`): 로고 락업. 높이 `sizes.logo`, 폭은 원본 비율.
- **구글 로고**: `res/drawable/ic_logo_google.xml`. 공식 4색 G를 그대로 쓰고 tint, 변형, 재색칠하지 않습니다.
- **카카오 로고**: `res/drawable/ic_logo_kakao.xml`. 로그인 버튼에서는 카카오가 정한 노란 컨테이너 `#FEE500` 위에 검정으로 올리고, 이 노랑은 팔레트에 넣지 않습니다. 컨테이너 없이 놓을 때는 `colors.text` 단색으로 칠합니다. 검정 그대로면 다크에서 묻히고, 카카오는 단색 사용을 허용합니다.

### 화면별 배치

한 도메인 화면에만 쓰는 배치는 하네스 도메인의 ANDROID `3-3-LAYOUT.md`가 정합니다. 화면을 고칠 때는 이 문서의 공용 규칙과 함께 읽습니다.

| 화면 | 문서 |
| --- | --- |
| 홈, 탐색, 검색, 제작 탭, 스토리 상세, 채팅 시작 시트, 가입 온보딩, 간편 제작 안내, 제작 퍼널 | [STORY](../knk-harness/docs/spec/STORY/CLIENT/ANDROID/3-3-LAYOUT.md) |
| 채팅방, 채팅 목록 카드, 안내 투어, 실시간 이미지 안내, 채팅 메뉴의 이프 | [CHAT](../knk-harness/docs/spec/CHAT/CLIENT/ANDROID/3-3-LAYOUT.md) |
| 시작 화면, 로그인, 프로필 헤더와 수정, 연동 계정 칩, 동의 시트 | [ACCOUNT](../knk-harness/docs/spec/ACCOUNT/CLIENT/ANDROID/3-3-LAYOUT.md) |
| 이프 충전과 내역, 부족 시트 | [PAYMENT](../knk-harness/docs/spec/PAYMENT/CLIENT/ANDROID/3-3-LAYOUT.md) |
| 알림함, 공지, 안 읽음 점, 알림 설정 | [NOTIFICATION](../knk-harness/docs/spec/NOTIFICATION/CLIENT/ANDROID/3-3-LAYOUT.md) |
| 셸, 시트 닫기, 옵션 시트, 이미지 뷰어 | [공통 UI](../knk-harness/docs/ui/ANDROID/3-3-LAYOUT.md) |

## 지킬 것

- 색, 크기, 여백, 모서리, 모션은 `ManyakTheme` 접근자로만 읽습니다. 팔레트 값(`#05A66B` 등)을 화면 코드에 적지 않습니다.
- `MaterialTheme.colorScheme`, `MaterialTheme.typography`를 직접 읽지 않습니다. `ManyakTheme`이 토큰에서 파생해 채우지만 M3 기본값(보라색, Roboto)이 새는 것을 막는 안전망일 뿐입니다.
- 주 동작 버튼은 `colors.brand`, 파괴 동작은 `colors.backgroundDangerBold`입니다. `colors.backgroundBrandBold`는 눌림 전용이고, `colors.brand`를 글자 바탕으로 쓰지 않습니다.
- 보조 설명은 `colors.textSubtle`, 장식만 `colors.textDisabled`입니다. 상태는 색과 문구를 함께 씁니다.
- 그림자로 층을 만들지 않고, `shapes.pill`을 큰 dp로 대신하지 않고, SemiBold(600)를 요구하지 않고, 성공 상태에 새 초록을 만들지 않습니다.
- dynamic color를 켜지 않습니다. 브랜드 색이 기기 배경화면에 덮입니다.

## 화면 대응과 알려진 공백

- **큰 글자**: 모든 글자 크기와 행간이 sp라 시스템 글자 크기를 따릅니다. 고정 dp 높이에 글자를 가두지 않습니다.
- **라이트와 다크**: `ManyakTheme(darkTheme = isSystemInDarkTheme())`로 정합니다. Compose 첫 프레임 전 창 배경은 `values`와 `values-night`의 `@color/surface`가 맡아 다크에서 흰 화면이 번쩍이지 않습니다.
- **창 크기**: `enableEdgeToEdge()`와 `Scaffold` 인셋까지만 정해져 있습니다. 하네스 공통 UI 요구사항은 큰 화면에서 폭을 제한한 한 열 스크롤을 요구하지만 콘텐츠 최대 폭 제한은 아직 구현되지 않았습니다. ([공통 UI Android 2-2](../knk-harness/docs/ui/ANDROID/2-2-REQUIREMENTS.md))
- **M3 확장 슬롯**(`*Fixed` 색, `*Emphasized` 타이포 등)은 파생하지 않습니다. 그 슬롯을 읽는 컴포넌트를 쓰게 되면 파생을 넓힙니다.
- **폰트**: 번들 원본이 약 9MB입니다. 줄여야 하면 Pretendard 공식 subset 빌드로 바꿉니다.
- **웹과 값 비교**: 생성기가 저장소 밖에 있고 웹은 자체 CSS 변수를 써서 두 클라이언트의 값이 갈리는지 사람이 확인해야 합니다. 다크 보조 글자 두 단계(`textSubtlest`, `textDisabled`)는 앱이 웹보다 밝습니다.
- **모션**: 눌림은 색 변화로만 정의되어 있고 골격 맥동 같은 나머지 모션은 그 컴포넌트에 있습니다. 여러 곳이 같은 시간을 쓰게 되면 `motion`에 단계를 더합니다.
- **주 버튼**: 공용 컴포저블이 없어 마이와 제작 퍼널이 각자 둡니다.

## 갱신 지침

1. 값을 바꾸면 Kotlin 토큰 파일과 이 문서의 표를 같은 커밋에서 고칩니다. 값이 다르면 Kotlin이 기준입니다.
2. Kotlin 파일은 사람이 옮겨 적고 생성기는 두지 않습니다. ([A-101](../knk-harness/docs/platform/CLIENT/ANDROID/DECISIONS.md#a-101))
3. 토큰에 없는 값이 필요하면 화면에서 임시로 만들지 말고 토큰에 단계를 더합니다(Kotlin과 이 문서). 웹과 공유하는 층이면 디자인 토큰에도 같은 단계가 필요하다고 PR에 적습니다.
4. 공용 컴포넌트를 올리거나 바꾸면 [코드 대응](#코드-대응) 표와 해당 컴포넌트 규칙을 함께 고칩니다. 규칙에는 값 대신 토큰 이름을 적습니다.
5. 한 화면에만 쓰는 배치는 이 문서가 아니라 하네스 도메인의 ANDROID `3-3-LAYOUT.md`에 적습니다.
6. 색을 더하거나 바꾸면 [쓰면 안 되는 조합](#쓰면-안-되는-조합)의 대비를 다시 계산합니다.
