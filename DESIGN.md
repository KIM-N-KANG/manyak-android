---
version: alpha
name: manyak-android-design
description: 초록 하나로 모든 상호작용을 말하는 무채색 인터페이스. 표면은 거의 흰색(#FCFCFC)과 거의 검정(#131313) 두 축뿐이고, 그림자 없이 표면 색 차이로만 층을 나눈다. UI는 Pretendard가, 스토리 본문은 MaruBuri가 맡아 "읽는 화면"과 "조작하는 화면"이 서체로 갈린다. 모든 색 조합은 토큰 빌드가 명도 대비를 재서 통과시킨 것만 남았다.

colors:
  brand: "#05A66B"
  text: "#131313"
  text-subtle: "#575757"
  text-subtlest: "#747474"
  text-narration: "#747474"
  text-disabled: "#969696"
  step-indicator-active: "#9F9F9F"
  progress-indicator: "#8D8D8D"
  text-inverse: "#FFFFFF"
  text-brand: "#00804B"
  text-danger: "#C1191C"
  text-warning: "#9A5700"
  text-information: "#186AB7"
  surface: "#FCFCFC"
  surface-raised: "#FFFFFF"
  background-neutral: "#F5F5F5"
  background-neutral-pressed: "#EEEEEE"
  background-brand-bold: "#00804B"
  background-brand-bold-pressed: "#006034"
  background-brand-subtle: "#E8F8EE"
  background-danger-bold: "#C1191C"
  background-danger-bold-pressed: "#95000A"
  background-danger-subtle: "#FFECE8"
  background-warning-subtle: "#FDF2E2"
  background-information-subtle: "#E9F5FF"
  background-disabled: "#EEEEEE"
  border: "#EEEEEE"
  border-strong: "#B9B9B9"
  border-input: "#8D8D8D"
  border-brand: "#00995F"
  border-danger: "#E23531"
  border-warning: "#B66E00"
  border-information: "#2F82D6"
  border-focused: "#00995F"

colors-dark:
  brand: "#05A66B"
  text: "#FCFCFC"
  text-subtle: "#B9B9B9"
  text-subtlest: "#8D8D8D"
  text-narration: "#B9B9B9"
  text-disabled: "#7E7E7E"
  step-indicator-active: "#666666"
  progress-indicator: "#8D8D8D"
  text-inverse: "#FFFFFF"
  text-brand: "#58C58F"
  text-danger: "#FF7669"
  text-warning: "#E09E32"
  text-information: "#6BB1FD"
  surface: "#131313"
  surface-raised: "#1F1F1F"
  background-neutral: "#1F1F1F"
  background-neutral-pressed: "#575757"
  background-brand-bold: "#00804B"
  background-brand-bold-pressed: "#006034"
  background-brand-subtle: "#00411F"
  background-danger-bold: "#C1191C"
  background-danger-bold-pressed: "#95000A"
  background-danger-subtle: "#6A0000"
  background-warning-subtle: "#512600"
  background-information-subtle: "#003364"
  background-disabled: "#3A3A3A"
  border: "#2C2C2C"
  border-strong: "#3A3A3A"
  border-input: "#666666"
  border-brand: "#58C58F"
  border-danger: "#FF7669"
  border-warning: "#E09E32"
  border-information: "#6BB1FD"
  border-focused: "#58C58F"

typography:
  headline-small:
    fontFamily: "Pretendard"
    fontSize: 24sp
    fontWeight: 700
    lineHeight: 30sp
  title-large:
    fontFamily: "Pretendard"
    fontSize: 20sp
    fontWeight: 700
    lineHeight: 27sp
  title-medium-strong:
    fontFamily: "Pretendard"
    fontSize: 18sp
    fontWeight: 700
    lineHeight: 26sp
  title-medium:
    fontFamily: "Pretendard"
    fontSize: 18sp
    fontWeight: 500
    lineHeight: 26sp
  body-large-strong:
    fontFamily: "Pretendard"
    fontSize: 16sp
    fontWeight: 500
    lineHeight: 24sp
  body-large:
    fontFamily: "Pretendard"
    fontSize: 16sp
    fontWeight: 400
    lineHeight: 24sp
  body-reading:
    fontFamily: "MaruBuri"
    fontSize: 16sp
    fontWeight: 400
    lineHeight: 28sp
  body-reading-small:
    fontFamily: "MaruBuri"
    fontSize: 14sp
    fontWeight: 400
    lineHeight: 24.5sp
  body-medium:
    fontFamily: "Pretendard"
    fontSize: 14sp
    fontWeight: 400
    lineHeight: 20sp
  label-large:
    fontFamily: "Pretendard"
    fontSize: 14sp
    fontWeight: 500
    lineHeight: 20sp
  body-small:
    fontFamily: "Pretendard"
    fontSize: 12sp
    fontWeight: 400
    lineHeight: 16sp
  label-small:
    fontFamily: "Pretendard"
    fontSize: 12sp
    fontWeight: 500
    lineHeight: 16sp

rounded:
  checkbox: 6dp
  menu-item: 10dp
  thumbnail: 12dp
  control: 14dp
  card: 16dp
  overlay: 20dp
  sheet: 20dp (위쪽 두 모서리)
  pill: CircleShape

sizes:
  input: 40dp
  control-small: 32dp
  control: 48dp
  icon-small: 16dp
  icon: 20dp
  tab-icon: 24dp
  logo: 24dp

spacing:
  hairline: 2dp
  inline: 4dp
  dense: 6dp
  compact: 8dp
  control-vertical: 10dp
  component: 12dp
  control-horizontal: 14dp
  gutter: 16dp
  passage: 20dp
  section: 24dp
  block: 32dp
  screen-bottom: 32dp

components:
  screen:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.body-medium}"
    padding: "{spacing.gutter}"
  button-primary:
    backgroundColor: "{colors.brand}"
    textColor: "{colors.text-inverse}"
    typography: "{typography.label-large}"
    rounded: "{rounded.control}"
    height: "{sizes.control}"
    padding: "{spacing.compact} {spacing.component}"
  button-primary-pressed:
    backgroundColor: "{colors.background-brand-bold}"
    textColor: "{colors.text-inverse}"
    rounded: "{rounded.control}"
  checkbox:
    size: "{sizes.icon}"
    backgroundColor: "{colors.surface}"
    borderColor: "{colors.border}"
    borderWidth: 1dp
    rounded: "{rounded.checkbox}"
  checkbox-checked:
    backgroundColor: "{colors.brand}"
    borderColor: "{colors.brand}"
    iconColor: "{colors.text-inverse}"
  button-danger:
    backgroundColor: "{colors.background-danger-bold}"
    textColor: "{colors.text-inverse}"
    typography: "{typography.label-large}"
    rounded: "{rounded.control}"
    padding: "{spacing.compact} {spacing.component}"
  button-danger-pressed:
    backgroundColor: "{colors.background-danger-bold-pressed}"
    textColor: "{colors.text-inverse}"
    rounded: "{rounded.control}"
  button-neutral:
    backgroundColor: "{colors.background-neutral}"
    textColor: "{colors.text}"
    typography: "{typography.label-large}"
    rounded: "{rounded.control}"
    padding: "{spacing.compact} {spacing.component}"
  button-neutral-pressed:
    backgroundColor: "{colors.background-neutral-pressed}"
    textColor: "{colors.text}"
    rounded: "{rounded.control}"
  button-disabled:
    backgroundColor: "{colors.background-disabled}"
    textColor: "{colors.text-disabled}"
    typography: "{typography.label-large}"
    rounded: "{rounded.control}"
  text-field:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.text}"
    typography: "{typography.body-medium}"
    borderColor: "{colors.border}"
    rounded: "{rounded.control}"
    minHeight: "{sizes.input}"
    padding: "{spacing.control-vertical} {spacing.control-horizontal}"
  text-field-focused:
    backgroundColor: "{colors.surface-raised}"
    borderColor: "{colors.border-input}"
    rounded: "{rounded.control}"
  text-field-error:
    backgroundColor: "{colors.surface-raised}"
    borderColor: "{colors.border-danger}"
    rounded: "{rounded.control}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.title-medium}"
    borderColor: "{colors.border}"
    rounded: "{rounded.card}"
    padding: "{spacing.component}"
  overlay:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.text}"
    rounded: "{rounded.overlay}"
    padding: "{spacing.gutter}"
  badge:
    backgroundColor: "{colors.background-brand-subtle}"
    textColor: "{colors.text-brand}"
    typography: "{typography.body-small}"
    rounded: "{rounded.pill}"
    padding: "{spacing.hairline} {spacing.compact}"
  banner-danger:
    backgroundColor: "{colors.background-danger-subtle}"
    textColor: "{colors.text-danger}"
    typography: "{typography.body-small}"
    borderColor: "{colors.border-danger}"
    rounded: "{rounded.control}"
    padding: "{spacing.component}"
  banner-warning:
    backgroundColor: "{colors.background-warning-subtle}"
    textColor: "{colors.text-warning}"
    typography: "{typography.body-small}"
    borderColor: "{colors.border-warning}"
    rounded: "{rounded.control}"
    padding: "{spacing.component}"
  banner-information:
    backgroundColor: "{colors.background-information-subtle}"
    textColor: "{colors.text-information}"
    typography: "{typography.body-small}"
    borderColor: "{colors.border-information}"
    rounded: "{rounded.control}"
    padding: "{spacing.component}"
  story-body:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.body-reading}"
    padding: "{spacing.gutter}"
  section-header:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.title-large}"
    minHeight: 64dp
    padding: "{spacing.gutter}"
  tab-bar:
    backgroundColor: "{colors.surface}"
    borderTopColor: "{colors.border}"
    borderTopWidth: 1dp
    minHeight: 80dp
  tab-bar-item-selected:
    iconColor: "{colors.text}"
    iconSize: "{sizes.tab-icon}"
    textColor: "{colors.text}"
    typography: "{typography.label-small}"
  tab-bar-item-unselected:
    iconColor: "{colors.text-subtle}"
    iconSize: "{sizes.tab-icon}"
    textColor: "{colors.text-subtle}"
    typography: "{typography.label-small}"
  fab:
    backgroundColor: "{colors.brand}"
    iconColor: "{colors.text-inverse}"
    rounded: "{rounded.pill}"
    size: 56dp
  funnel-header:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.title-large}"
    minHeight: 64dp
  detail-header:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.body-large-strong}"
    minHeight: 64dp
  step-indicator:
    completedColor: "{colors.text-disabled}"
    activeColor: "{colors.step-indicator-active}"
    inactiveColor: "{colors.border}"
    height: 3dp
    rounded: "{rounded.pill}"
  chip:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.text}"
    borderColor: "{colors.border}"
    typography: "{typography.body-medium}"
    rounded: "{rounded.control}"
    minHeight: "{sizes.input}"
    padding: "{spacing.control-vertical} {spacing.control-horizontal}"
  chip-selected:
    backgroundColor: "{colors.background-brand-subtle}"
    textColor: "{colors.text-brand}"
    borderColor: "{colors.border-brand}"
    rounded: "{rounded.control}"
  provider-chip:
    textColor: "{colors.text-subtle}"
    kakaoLogoColor: "{colors.text}"
    typography: "{typography.label-small}"
    borderColor: "{colors.border}"
    borderWidth: 1dp
    rounded: "{rounded.pill}"
    height: 24dp
    logoSize: 12dp
    gap: "{spacing.dense}"
    padding: "0 {spacing.compact}"
  provider-chip-link:
    borderColor: "{colors.border-strong}"
    borderStyle: "dashed 3dp/3dp"
  logo-manyak:
    asset: "res/drawable/ic_logo_manyak.xml"
    color: "{colors.brand}"
    height: "{sizes.logo}"
  logo-google:
    asset: "res/drawable/ic_logo_google.xml"
    size: 24dp
  logo-kakao:
    asset: "res/drawable/ic_logo_kakao.xml"
    backgroundColor: "#FEE500"
    size: 24dp
    monoColor: "{colors.text}"
---

## 개요

마냑 앱의 시각 언어는 **초록 하나와 무채색 두 축**으로 이뤄집니다. 표면은 거의 흰색 `{colors.surface}`(#FCFCFC)와 거의 검정(다크 #131313) 둘뿐이고, 그 위에서 색이 있는 것은 브랜드 초록과 상태 색(오류·경고·안내) 넷뿐입니다. 브랜드 초록 `{colors.brand}`(#05A66B)는 로고 그린과 **정확히 같은 값**이며, 팔레트 사다리 전체가 이 값에 맞춰 세워졌습니다.

색·크기·여백·모서리는 전부 **디자인 토큰**에서 나옵니다. 화면 코드는 토큰 이름만 쓰고 값을 직접 적지 않습니다. 토큰은 Primitive(팔레트) → Semantic(의도) 두 층이며, 화면이 만질 수 있는 것은 Semantic뿐입니다 — 팔레트는 Kotlin에서 `private`이라 참조 자체가 불가능합니다.

서체가 두 벌인 것이 이 시스템의 성격을 가장 잘 드러냅니다. 조작하는 화면은 Pretendard가, 읽는 화면(스토리 본문)은 MaruBuri가 맡습니다. 같은 16sp라도 `{typography.body-large}`는 행간 24sp로 촘촘하고 `{typography.body-reading}`은 28sp로 벌어져 있습니다 — UI는 스캔하는 것이고 스토리는 읽는 것이기 때문입니다.

**핵심 특징**

- 상호작용은 전부 브랜드 초록 하나. 두 번째 강조색은 없습니다.
- 그림자를 쓰지 않습니다. 층은 `{colors.surface}` ↔ `{colors.surface-raised}` 표면 색 차이로만 나눕니다.
- 성공 상태에 별도 초록을 두지 않고 브랜드 초록을 재사용합니다. 브랜드 자체가 초록이라 둘을 나누면 구분되지 않습니다.
- 라이트·다크가 같은 이름으로 대응합니다. 화면 코드에는 분기가 없습니다.
- 굵기는 Regular(400)·Medium(500)·Bold(700) 셋뿐입니다.
- 텍스트·경계 조합은 토큰을 만들 때 명도 대비 검증(30건)을 통과한 것만 남았습니다. **이 레포에서 값을 고치면 그 검증이 다시 돌지 않으므로** 바꾼 조합의 대비는 직접 재서 아래 "쓰면 안 되는 조합"을 갱신합니다.
- 기기 배경화면에서 색을 가져오는 dynamic color를 쓰지 않습니다.

## 코드 대응

`core/ui/src/main/java/app/manyak/core/ui/theme/`

| 파일 | 내용 |
| --- | --- |
| `Color.kt` | 팔레트(private)와 시맨틱 색 30종, 라이트·다크 인스턴스 |
| `Type.kt` | `Pretendard`·`MaruBuri` FontFamily와 타이포 롤 13종 |
| `ManyakSpacing.kt` | 시맨틱 여백 |
| `ManyakShapes.kt` | 시맨틱 모서리 |
| `ManyakSizes.kt` | 크기 5종 |
| `ManyakMotion.kt` | 전환 시간 1종 |
| `Theme.kt` | `ManyakTheme` 컴포저블·접근자, M3 슬롯 파생 |

`core/ui/src/main/java/app/manyak/core/ui/component/`

| 파일 | 내용 |
| --- | --- |
| `ManyakLogo.kt` | 로고 락업. 높이는 토큰, 폭은 원본 비율 |
| `ManyakSectionHeader.kt` | 메인 탭 상단 헤더 |
| `ManyakNavigationBar.kt` · `ManyakNavigationItem.kt` | 하단 탭 바와 그 항목 |
| `ProgressIndicator.kt` | 로딩 스피너와 지연 표시 헬퍼 |
| `PullToRefresh.kt` | 당겨서 새로고침 컨테이너. 표시자를 셸 헤더 아래로 내린다 |

이름은 세 표기가 1:1로 대응합니다 — 이 문서 `{colors.text-subtle}` ↔ Kotlin `ManyakTheme.colors.textSubtle` ↔ 토큰 JSON `color.text.subtle`.

화면 코드에서 토큰을 읽는 통로는 `ManyakTheme.colors` · `typography` · `spacing` · `shapes` 넷뿐입니다.

## 색

> **정본:** 아래 표와 `Color.kt`입니다. 값 자체는 웹과 공유하는 디자인 토큰에서 왔지만 **생성기가 이 레포 밖에 있어 여기서는 재생성할 수 없습니다** — 예전에 두었던 `design/design-tokens.json` 사본은 아무도 읽지 않고 손으로만 맞추다 어긋나서 지웠습니다(2026-08-29). 값을 바꿀 때는 `Color.kt`와 이 표를 같은 커밋에서 고치고, 웹과 공유하는 값이면 웹에도 같은 변경이 필요하다는 것을 PR에 적습니다.

### 브랜드

- **브랜드 초록** (`{colors.brand}` — #05A66B): 로고·일러스트 등 큰 그래픽과 **주 버튼의 배경**입니다. **버튼에 프라이머리 색을 넣을 때는 반드시 이 기본 프라이머리(브랜드 원색)를 씁니다**(2026-08-24 결정) — 한 단계 어두운 `{colors.background-brand-bold}`를 버튼 기본 배경으로 쓰지 않습니다. 웹 primary 와 같은 값으로 맞추는 것을, 흰 텍스트와의 대비 3.15(AA 미달)보다 우선한 결정입니다.
- **짙은 브랜드 초록** (`{colors.background-brand-bold}` — #00804B): 한 단계 어두운 초록. 주 버튼의 **눌림 상태**에 씁니다. 라이트·다크가 같은 값입니다.
- **브랜드 텍스트** (`{colors.text-brand}`): 링크·강조 텍스트. 라이트 #00804B, 다크 #58C58F — 다크에서 어두운 초록은 읽히지 않기 때문입니다.

### 표면

- **표면** (`{colors.surface}` — 라이트 #FCFCFC / 다크 #131313): 앱 바탕·카드·시트. 화면의 기본 바닥입니다.
- **떠 있는 표면** (`{colors.surface-raised}` — 라이트 #FFFFFF / 다크 #1F1F1F): 팝오버·플로팅. **순백을 쓰는 유일한 자리**입니다.
- **보조 배경** (`{colors.background-neutral}` — 라이트 #F5F5F5 / 다크 #191919): 입력창·비강조 채움. 눌림은 `{colors.background-neutral-pressed}`.
- **비활성 채움** (`{colors.background-disabled}`): 비활성 컨트롤의 바닥.

### 텍스트

| 토큰 | 라이트 | 다크 | 용도 |
| --- | --- | --- | --- |
| `{colors.text}` | #131313 | #FCFCFC | 본문·제목 기본 |
| `{colors.text-subtle}` | #575757 | #B9B9B9 | 보조 설명·메타 정보 |
| `{colors.text-subtlest}` | #747474 | #8D8D8D | 약한 보조. `{colors.surface}` 위에서만 본문 크기로 |
| `{colors.text-narration}` | #747474 | #B9B9B9 | 서사의 상황 묘사(`*…*`)와 상황 블럭 입력 |
| `{colors.text-disabled}` | #969696 | #7E7E7E | 비활성·장식 전용. 읽어야 하는 텍스트에 쓰지 않음 |
| `{colors.text-inverse}` | #FFFFFF | #FFFFFF | bold 배경 위 텍스트 |
| `{colors.text-danger}` | #C1191C | #FF7669 | 오류 메시지 |
| `{colors.text-warning}` | #9A5700 | #E09E32 | 경고 메시지 |
| `{colors.text-information}` | #186AB7 | #6BB1FD | 안내 메시지 |

`{colors.text-narration}`은 라이트에서 `{colors.text-subtlest}`와 같은 값이지만 **따로 둡니다** — 상황 묘사는 대사와 나란히 읽는 본문이라 약한 보조 텍스트보다 밝아야 하고, 다크에서 그 차이가 드러납니다. `{colors.background-neutral}` 위 대비는 라이트에서 `{colors.text-subtlest}`와 같은 4.29라 아래 제약을 함께 받습니다.

### 경계

- **장식 경계** (`{colors.border}`): 구분선·카드 테두리. 대비 요구 대상이 아닙니다.
- **점선 경계** (`{colors.border-strong}` — 라이트 #B9B9B9 / 다크 #3A3A3A): 점선은 잉크가 절반만 닿아 같은 색이어도 실선보다 한참 연하게 읽힙니다. 실선 이웃과 같은 무게로 보이게 하는 한 단계 진한 장식 경계이며, **점선에만** 씁니다 — 실선에 쓰면 그냥 진한 테두리가 됩니다.
- **입력 경계** (`{colors.border-input}` — 라이트 #8D8D8D / 다크 #666666): 경계가 유일한 식별 수단일 때. **표면이 아니라 자기 채움색 기준으로** 역산한 값입니다 — 표면 기준으로 잡았더니 `{colors.background-neutral}` 위에서 2.85로 미달했기 때문입니다.
- **포커스 링** (`{colors.border-focused}`): 선택된 테두리 `{colors.border-brand}`와 같은 값입니다.
- 상태 경계 `{colors.border-danger}` · `{colors.border-warning}` · `{colors.border-information}`는 각 배너·입력창에 씁니다.

### 쓰면 안 되는 조합

지정된 색 자체의 한계라 값을 바꾸지 않는 한 해소되지 않습니다.

| 조합 | 대비 | 대신 |
| --- | --- | --- |
| `{colors.text-subtlest}` + `{colors.background-neutral}`(및 pressed) | 라이트 4.03–4.29 | `{colors.text-subtle}` |
| `{colors.text-disabled}` + 읽어야 하는 텍스트 | 라이트 2.71–2.88 | `{colors.text-subtle}` |

다크는 2026-08-29에 `{colors.text-subtlest}`를 #8D8D8D, `{colors.text-disabled}`를 #7E7E7E로 한 단계
올려 위 두 조합이 모두 4.5를 넘습니다(각각 4.97–5.60, 4.33–4.58). **그래도 같은 규칙을 지킵니다** —
라이트에서 미달인 조합이고, 비활성 색은 읽어야 하는 텍스트의 자리가 아닙니다.

### 그라디언트

**없습니다.** 장식용 그라디언트를 토큰으로 두지 않습니다. 깊이는 표면 색 차이로만 만듭니다.

## 타이포그래피

### 서체

- **Pretendard** — UI 전반. Regular·Medium·Bold 세 웨이트를 정적 TTF로 번들합니다(`res/font/pretendard_*.ttf`).
- **MaruBuri** — 스토리 본문 전용. Regular·Bold를 **OTF로** 번들합니다(`res/font/maru_buri_*.otf`). 배포된 TTF에는 TrueType 힌팅이 들어 있어 작은 크기에서 획이 픽셀 격자에 스냅되며 글자마다 굵기가 갈립니다.
- 둘 다 SIL OFL 1.1이며 원문은 `assets/licenses/`에 있습니다.

### 위계

| 토큰 | 크기 | 굵기 | 행간 | 용도 |
| --- | --- | --- | --- | --- |
| `{typography.headline-small}` | 24sp | 700 | 30sp | 온보딩·랜딩 헤드라인 |
| `{typography.title-large}` | 20sp | 700 | 27sp | 화면 제목 · 탭 헤더 |
| `{typography.title-medium-strong}` | 18sp | 700 | 26sp | 목록 섹션 제목 |
| `{typography.title-medium}` | 18sp | 500 | 26sp | 섹션·카드 제목 |
| `{typography.body-large}` | 16sp | 400 | 24sp | 강조 본문·입력 필드 |
| `{typography.body-reading}` | 16sp | 400 | 28sp | 스토리 본문 (MaruBuri · 자간 −2%) |
| `{typography.body-reading-small}` | 14sp | 400 | 24.5sp | 짧은 서사 문장 — 추천 입력 (MaruBuri · 자간 −2%) |
| `{typography.body-medium-strong}` | 14sp | 700 | 20sp | 라벨 옆에 세우는 값 — 비용 행의 이프 수치 |
| `{typography.body-medium}` | 14sp | 400 | 20sp | 본문 기본 |
| `{typography.label-large}` | 14sp | 500 | 20sp | 버튼·탭 라벨 |
| `{typography.body-small}` | 12sp | 400 | 16sp | 메타 정보·보조 설명 |
| `{typography.label-small}` | 12sp | 500 | 16sp | 타임스탬프·최소 보조 문구 · 표지 위 뱃지 |

### 원칙

- **행간은 배수가 아니라 sp 절대값입니다.** Compose `TextStyle`이 절대값만 받으므로, 배수로 두면 플랫폼마다 반올림이 갈립니다.
- **UI 롤의 행간은 1.25~1.5입니다.** `{typography.body-reading}`만 1.75로 벌립니다 — 스토리 본문은 한 화면을 채우는 장문이라 UI 기준 그대로는 답답합니다.
- **자간은 `{typography.body-reading}`만 −2%로 좁힙니다.** 바탕 계열의 기본 자간이 장문에서 벌어져 보여 본문 롤에만 보정하고, UI 롤은 기본 자간을 유지합니다.
- **MaruBuri는 `{typography.body-reading}`·`{typography.body-reading-small}` 전용입니다.** 버튼·라벨·제목에 쓰지 않습니다.
- **굵기 사다리는 400 / 500 / 700입니다.** SemiBold(600)를 쓰지 않는 이유는 번들에 없는 굵기를 요구하면 Bold로 대체 렌더되어 의도보다 두꺼워지기 때문입니다.
- **`{typography.title-medium-strong}`·`{typography.body-medium-strong}`은 크기가 아니라 굵기로 갈리는 롤입니다.** 각각 `{typography.title-medium}`·`{typography.body-medium}`과 크기·행간이 같고 굵기만 700입니다. 이름에 `strong`을 붙인 것은 small/medium 같은 크기 이름이 굵기 차이를 뜻하게 되면 스케일이 거짓말을 하기 때문입니다. 스크롤되는 목록 위에 붙박이로 남는 섹션 제목처럼, 같은 크기에서 무게로만 위계를 세워야 하는 자리에 씁니다.
- 화면 기본 텍스트 스타일은 `{typography.body-medium}`이고 기본 색은 `{colors.text}`입니다. `ManyakTheme`이 `LocalTextStyle`·`LocalContentColor`로 내립니다.

## 레이아웃

### 여백

이름은 크기가 아니라 **상황**으로 붙입니다. `space.200`은 값이 바뀌면 의미를 잃지만 `{spacing.gutter}`는 그대로 유효하기 때문입니다.

| 토큰 | 값 | 용도 |
| --- | --- | --- |
| `{spacing.hairline}` | 2dp | 아이콘과 라벨 사이 |
| `{spacing.inline}` | 4dp | 인접한 인라인 요소 |
| `{spacing.dense}` | 6dp | 촘촘한 요소 사이 |
| `{spacing.compact}` | 8dp | 리스트 항목 간격·버튼 내부 세로 |
| `{spacing.control-vertical}` | 10dp | 입력·칩·메뉴 항목의 세로 패딩 |
| `{spacing.component}` | 12dp | 컴포넌트 내부 기본 |
| `{spacing.control-horizontal}` | 14dp | 입력·칩·메뉴 항목의 가로 패딩 |
| `{spacing.gutter}` | 16dp | 화면 좌우 여백 |
| `{spacing.passage}` | 20dp | 읽는 본문 블록의 세로 여백과 블록 안 조각 사이 |
| `{spacing.section}` | 24dp | 섹션 사이 |
| `{spacing.block}` | 32dp | 큰 구획 사이 |
| `{spacing.screen-bottom}` | 32dp | 스크롤 영역 하단 여유 |

2dp 격자이며, **시맨틱 이름이 붙은 단계만 둡니다.** 안 쓰는 중간 단계가 있으면 언젠가 이름 없이 쓰이고, 그러면 시맨틱 층이 무의미해집니다.

### 화면 구성

- 화면 좌우는 `{spacing.gutter}`, 스크롤 영역 하단은 `{spacing.screen-bottom}`.
- **하단 탭 셸을 두르는 목록은 `{spacing.screen-bottom}` 대신 그 목록의 리듬에 맞춥니다.** 홈·제작 그리드는 행 사이 간격과 같은 `{spacing.gutter}`, 채팅 목록은 카드가 스스로 갖는 세로 여백과 같은 `{spacing.compact}`입니다. 아래에 늘 떠 있는 탭 바가 이미 끝을 알리므로 전체 화면만큼의 여유가 필요하지 않습니다. 셸이 없는 전체 화면(채팅방·제작 퍼널)에는 `{spacing.screen-bottom}`을 그대로 적용합니다.
- 섹션 사이는 `{spacing.section}`, 성격이 다른 큰 구획 사이는 `{spacing.block}`.
- 시스템 인셋은 `enableEdgeToEdge()`와 `Scaffold`의 `innerPadding`으로 처리하고, 화면 여백은 그 안쪽에 얹습니다.

## 표면과 깊이

| 층 | 처리 | 용도 |
| --- | --- | --- |
| 바닥 | `{colors.surface}` | 앱 바탕·카드·시트 |
| 떠 있음 | `{colors.surface-raised}` | 팝오버·바텀시트·다이얼로그 |
| 채움 | `{colors.background-neutral}` | 입력창·비강조 채움 |
| 경계 | `{colors.border}` 1dp | 구분선·카드 테두리 |

**그림자를 쓰지 않습니다.** 깊이는 (a) 표면 색 차이와 (b) 경계선으로만 만듭니다. M3의 tonal elevation도 끕니다 — `surfaceTint`를 투명으로 파생시켜 표면이 고도에 따라 브랜드 색으로 물드는 것을 막습니다.

## 크기

| 토큰 | 값 | 용도 |
| --- | --- | --- |
| `{sizes.control-small}` | 32dp | 라벨 없이 아이콘만 있는 보조 버튼 |
| `{sizes.input}` | 40dp | 입력창·칩·셀렉트 앵커의 최소 높이 |
| `{sizes.control}` | 48dp | 버튼·탭처럼 탭 가능한 일반 컨트롤의 높이 |
| `{sizes.icon-small}` | 16dp | 밀도 높은 컨트롤 안의 작은 아이콘 |
| `{sizes.icon}` | 20dp | 라벨 옆 아이콘·제공자 로고 |
| `{sizes.tab-icon}` | 24dp | 하단 탭 아이콘 |
| `{sizes.logo}` | 24dp | 마냑 로고 락업의 높이. 폭은 원본 비율(89:32)로 따라간다 |

`{sizes.tab-icon}`이 `{sizes.icon}`보다 큰 이유는 놓이는 자리가 다르기 때문이다. `{sizes.icon}`은 같은 줄의
라벨 옆에 붙어 글자 크기에 맞추지만, 탭 아이콘은 라벨 위에 놓인 탭의 주된 시각 요소다. 웹 하단
네비게이션도 같은 24px이다.

`{sizes.control}`은 안드로이드 최소 터치 타깃과 같은 값이다. 버튼·탭은 보이는 크기와 눌리는 크기를
48dp 로 맞추고, 여러 개가 밀집하는 입력창·칩·셀렉트 앵커는 `{sizes.input}` 40dp 로 구분한다.
`{sizes.control-small}` 은 라벨 없이 아이콘만 있고 본문 옆에서 눈에 덜 띄어야 하는 보조 버튼용이라
최소 터치 타깃보다 작다 — 주된 동작에는 쓰지 않는다. 토큰 정본에는 높이가 없어 이 값들은 이
레포가 소유한다.

## 모션

| 토큰 | 값 | 쓰임 |
| --- | --- | --- |
| `{motion.screen-transition}` | 150ms | 화면·탭이 바뀔 때의 교차 페이드 |
| `{motion.element-enter}` | 200ms | 화면 안의 작은 요소가 나타날 때 |
| `{motion.element-exit}` | 150ms | 화면 안의 작은 요소가 사라질 때 |
| `{motion.list-item-enter}` | 300ms | 차례로 드러나는 목록에서 항목 하나 |
| `{motion.list-item-stagger}` | 80ms | 그 항목들이 시작하는 간격 |

토큰 정본에 모션이 없어 이 값도 이 레포가 소유한다. 시간만 정하고 무엇을 움직일지는 쓰는 쪽이 정한다.

**전환은 짧다.** 내비게이션 라이브러리 기본값은 700ms 인데, 하단 탭처럼 자주 오가는 전환에서는
눌렀는데 뒤늦게 따라오는 느낌을 준다. 150ms 는 전환이 있었다는 것만 알리고 비켜선다.

**예측형 뒤로가기(predictive back)는 예외로 두고 라이브러리 기본값을 쓴다.** 이쪽 애니메이션은 장식이
아니라 손가락을 따라오는 제스처 피드백 그 자체라, 짧게 만들면 제스처가 반응하지 않는 것처럼 보인다.

**등장은 퇴장보다 길다.** 나타나는 요소는 눈이 따라갈 시간이 필요하지만, 사라지는 요소는 이미 볼 일이
끝나 남아 있으면 기다리게 만든다. 방향도 함께 쓴다 — 목록 끝으로 보내는 버튼은 아래에서 올라오고
아래로 내려가 사라져, 움직임 자체가 버튼이 무엇을 하는지 말한다.

**사용자가 늘리고 줄이는 목록의 칸은 아래 변을 붙잡고 자라고 접힌다.** 채팅 입력 칸과 추가 정보 입력
칸이 같은 규칙을 쓴다(`RowRevealTransition`) — 위를 붙잡으면 손대지 않은 위쪽 칸들이 함께 밀렸다
당겨져 어느 칸이 늘고 줄었는지 읽히지 않는다. 지운 칸은 접힘이 끝난 뒤에 목록에서 뺀다.

**차례로 드러나는 목록은 항목 하나가 더 길다.** 단일 요소보다 느린 300ms 는 뒤 항목이 80ms 씩 늦게
출발해도 앞 항목이 아직 움직이고 있어 목록 전체가 하나의 흐름으로 읽히게 한다. 값은 웹과 같다 —
추천 입력처럼 두 플랫폼에 같은 목록이 있는 자리에서 리듬이 갈리면 안 된다.

**눌림 상태에는 애니메이션을 쓰지 않는다.** 색 변화로만 말한다. **눌림 리플은 앱 전역에서 끈다**(2026-08-24) — `ManyakTheme`이 리플 설정을 비워 내리므로 컴포넌트마다 따로 끄지 않아도 되고, 개별 컴포넌트에서 다시 켜지 않는다.

## 모양

| 토큰 | 값 | 용도 |
| --- | --- | --- |
| `{rounded.checkbox}` | 6dp | 체크박스처럼 한 변이 20dp 남짓인 작은 네모 |
| `{rounded.menu-item}` | 10dp | 셀렉트 메뉴 항목 · 라벨 없는 아이콘 버튼 |
| `{rounded.thumbnail}` | 12dp | 썸네일·작은 아이콘 컨테이너 |
| `{rounded.control}` | 14dp | 버튼·입력창·탭 |
| `{rounded.card}` | 16dp | 카드·리스트 항목 |
| `{rounded.overlay}` | 20dp | 다이얼로그 |
| `{rounded.sheet}` | 20dp (위쪽 두 모서리) | 바텀시트 |
| `{rounded.pill}` | `CircleShape` | 배지·칩·아바타 |

`{rounded.pill}`은 **큰 dp가 아니라 `CircleShape`입니다.** 값으로 두면 큰 요소에서 모서리가 잘못 그려집니다. 그래서 `ManyakShapes`는 dp가 아니라 `Shape`를 내립니다.

## 컴포넌트

> 아래 명세는 **토큰의 용도 정의에서 파생한 조합**입니다. 아직 공용 컴포저블로 구현된 것은 없습니다(로고 두 개만 자산으로 존재). 화면을 만들 때 이 조합을 따르고, 두 번째 사용처가 생기면 공용 컴포넌트로 올립니다.

### 버튼

**`button-primary`** — 주 동작. 배경은 **기본 프라이머리 `{colors.brand}`** 이고(버튼의 프라이머리는 반드시 이 색 — 위 브랜드 절), 텍스트 `{colors.text-inverse}`, `{typography.label-large}`, 모서리 `{rounded.control}`, 내부 여백 세로 `{spacing.compact}` · 가로 `{spacing.component}`. 눌림은 `{component.button-primary-pressed}`로 배경만 `{colors.background-brand-bold}`로 한 단계 어둡게 바꿉니다. M3 슬롯 파생(primary = `{colors.background-brand-bold}`)은 안전망일 뿐이므로, 기본 `Button` 색에 기대지 말고 이 색을 명시합니다.

**`button-danger`** — 파괴적 동작(탈퇴·삭제). 같은 형태에 배경만 `{colors.background-danger-bold}`. 눌림은 `{component.button-danger-pressed}`.

**`checkbox`** — 동의·확인 항목의 체크 표시. `{sizes.icon}` 정사각에 `{rounded.checkbox}` 모서리이고, 기본은 `{colors.surface}` 채움 + `{colors.border}` 1dp, 체크되면 `{colors.brand}` 채움에 같은 색 경계와 `{colors.text-inverse}` 체크 아이콘(16dp)이다. **체크박스 자체는 누르는 대상이 아니다** — 줄 전체가 토글을 맡아 문구를 눌러도 켜지고, 최소 터치 타깃도 그 줄이 확보한다. 체크박스에 따로 접근성 이름을 붙이지 않는다(줄이 이미 이름과 상태를 읽힌다).

**`button-neutral`** — 보조 동작. 배경 `{colors.background-neutral}`, 텍스트 `{colors.text}`. 눌림은 `{component.button-neutral-pressed}`.

**`button-disabled`** — 배경 `{colors.background-disabled}`, 텍스트 `{colors.text-disabled}`. 비활성은 색만으로 전달하지 않고 상태 안내를 함께 둡니다.

### 입력

**`text-field`** — 배경 `{colors.surface-raised}`(흰색), 경계 `{colors.border}`(옅은 회색) 1dp, 텍스트 `{typography.body-medium}`, 모서리 `{rounded.control}`, 내부 여백은 세로 `{spacing.control-vertical}` · 가로 `{spacing.control-horizontal}`, 최소 높이 `{sizes.input}` — 칩과 같은 밀도로 맞춘 값이라 컨트롤(48dp)보다 낮고, 터치 타깃 미달은 칩과 같은 이유로 수용한다.

**`text-field-focused`** — 경계를 한 단계 진한 회색 `{colors.border-input}`으로 바꿉니다. 포커스에 브랜드 색을 쓰지 않습니다(2026-08-24 결정 — 입력 경계는 무채색 사다리로만 말합니다).

**`text-field-error`** — 경계를 `{colors.border-danger}`로 바꾸고, 오류 문구를 `{colors.text-danger}` + `{typography.body-small}`로 아래에 둡니다. 색만으로 오류를 알리지 않습니다.

**셀렉트 메뉴**(성별 등) — 앵커는 text-field 와 같은 형태이고, 미선택(랜덤) 값은 placeholder 색(`{colors.text-disabled}`)으로 낮춥니다. 메뉴는 앵커와 같은 폭으로 항상 앵커 아래에 `{spacing.inline}` 떨어져 열리며, 배경 `{colors.surface-raised}` + 경계 `{colors.border}` + **연한 그림자**를 쓰고, 각 항목은 `{rounded.menu-item}` 모서리와 세로 `{spacing.control-vertical}` · 가로 `{spacing.control-horizontal}` 여백을 사용합니다. 선택된 항목은 `{colors.background-neutral}` 채움과 체크 표시로 드러냅니다 — 그림자 금지 규칙의 예외로, 떠 있는 흰 메뉴가 흰 앵커·표면과 겹쳐 경계만으로는 층이 드러나지 않기 때문입니다.

### 컨테이너

**`card`** — 배경 `{colors.surface}`, 경계 `{colors.border}` 1dp, 모서리 `{rounded.card}`, 내부 여백 `{spacing.component}`. 제목 `{typography.title-medium}`, 본문 `{typography.body-medium}`, 메타 `{typography.body-small}` + `{colors.text-subtle}`.

**`overlay`** — 다이얼로그. 배경 `{colors.surface-raised}`, 모서리 `{rounded.overlay}`, 내부 여백 `{spacing.gutter}`.

**`sheet`** — 바텀시트. 배경 `{colors.surface-raised}`, 모서리는 `{rounded.sheet}`로 위쪽 두 곳만 깎습니다 — 아래쪽은 화면 끝에 붙어 있어 깎으면 그 틈으로 스크림이 비칩니다. 내부 여백은 좌·우·아래 `{spacing.gutter}`이고 **위쪽은 두지 않습니다** — 드래그 핸들이 자체 여백을 갖고 있어 겹칩니다. 하단 안전 영역과 키보드 높이는 그 아래로 시트가 직접 낍니다.

**`badge`** — 배경 `{colors.background-brand-subtle}`, 텍스트 `{colors.text-brand}` + `{typography.body-small}`, 모서리 `{rounded.pill}`, 여백 세로 `{spacing.hairline}` · 가로 `{spacing.compact}`.

**`provider-chip` / `provider-chip-link`** — 마이 프로필 헤더의 연동 계정 표시. 웹 배지와 같은 치수를 씁니다(높이 24dp, 로고 12dp) — 같은 정보를 두 플랫폼에서 나란히 보게 되는 자리라 크기가 다르면 다른 것으로 읽힙니다. 로고와 이름 사이는 `{spacing.dense}`, 가로 여백은 `{spacing.compact}`이고 세로 여백은 두지 않습니다(높이가 고정). 로고는 이 시스템에서 가장 작게 쓰이므로 `{sizes.icon-small}`(16dp)보다 한 단계 아래인 12dp 를 이 컴포넌트 안에서만 씁니다. **연동된 제공자는 `{colors.border}` 실선, 아직 아닌 제공자는 같은 모양의 `{colors.border-strong}` 점선(3dp 대시·3dp 간격) 버튼**입니다 — 점선을 실선과 같은 색으로 두면 잉크가 절반만 닿아 나란히 놓인 칩보다 눈에 띄게 연해집니다 — 미연동을 색이 아니라 선의 모양으로 말하고, 누를 수 있다는 것은 같은 자리에 놓인 형태가 전달합니다. 둘 다 연동되면 점선 버튼이 사라져 칩만 남습니다.

**`banner-danger` / `banner-warning` / `banner-information`** — 배경은 각 `subtle`, 텍스트는 같은 계열의 텍스트 색, 모서리 `{rounded.control}`, 내부 여백 `{spacing.component}`. 배너 자체가 상태를 말하므로 아이콘 없이도 성립하지만, 색만으로 구분되지 않도록 문구를 명시합니다.

### 스토리

**`story-body`** — 배경 `{colors.surface}`, 텍스트 `{colors.text}` + `{typography.body-reading}`, 좌우 여백 `{spacing.gutter}`. 이 시스템에서 MaruBuri가 나타나는 자리는 스토리 본문과 퍼널의 스토리라인 미리보기(아래 퍼널 절)뿐입니다.

### 셸

> 셸의 두 컴포넌트는 **M3 컴포넌트 위에 색만 얹어** 만든다. 인셋·높이·최소 터치 타깃·시맨틱을 직접 계산하지 않기 위해서다. 이 시스템에 없는 요소만 골라 지운다.

**`section-header`** — 메인 탭의 상단 헤더. `TopAppBar` 위에 배경 `{colors.surface}`와 제목 색 `{colors.text}`를 얹는다. 좌우 여백은 앱 바 기본값이 16dp 라 `{spacing.gutter}`와 같고, 로고와 섹션 이름(`{typography.title-large}`) 사이도 `{spacing.gutter}`다. 높이는 최소 64dp 이고 제목이 커지면 함께 늘어난다. 구분선과 그림자를 두지 않는다. `TopAppBar`가 아직 실험 API 라 `@OptIn`이 필요하며, 사용처는 이 컴포넌트와 퍼널 헤더(`funnel-header`) 둘이다.

**`tab-bar`** — 홈·채팅·마이 고정 3탭. 배경 `{colors.surface}`, 위쪽 경계 `{colors.border}` 1dp. 아이콘(`{sizes.tab-icon}`) 아래에 이름을 `{typography.label-small}`로 둔다. 선택은 filled 아이콘 + `{colors.text}`, 비선택은 outline 아이콘 + `{colors.text-subtle}`이고 아이콘과 라벨이 같은 색을 쓴다. 라벨이 이름을 맡으므로 **아이콘은 장식으로 두고 접근성 이름을 붙이지 않는다** — 둘 다 붙이면 탐색 서비스가 같은 이름을 두 번 읽는다. 선택을 색 하나로만 구분하지 않고 아이콘 모양을 함께 바꾼다.

**위쪽 경계선을 두는 이유는 바 배경이 화면 바탕과 같은 `{colors.surface}`이기 때문이다.** 콘텐츠는 바 아래로 흘러 들어가므로, 경계선이 없으면 어디까지가 콘텐츠인지 드러나지 않는다. 이 시스템은 그림자를 쓰지 않으니 경계선이 유일한 수단이다. 상단 헤더는 반대다 — 헤더 위로는 아무것도 흐르지 않아 구분할 대상이 없다.

`NavigationBar` 위에서 지우는 것은 둘이다. **알약 모양 선택 표시자**는 표시자 색을 투명으로 두고, **눌림 피드백**은 `LocalRippleConfiguration`에 `null`을 내려 끈다 — 탭을 누르면 아이콘 모양과 색이 곧바로 바뀌므로 그 변화 자체가 반응이다. 바 높이·최소 터치 타깃·하단 안전 영역은 컴포넌트가 처리한다.

선택에 `{colors.brand}` 계열을 쓰지 않는 것은 **이 시스템에서 초록이 "지금 누를 것"을 뜻하기 때문이다.** 하단 바는 늘 떠 있는 chrome 이라 초록을 상시 띄우면 화면 안의 주 동작과 강조가 겹치고, 어느 쪽이 다음 행동인지 흐려진다. 선택 여부는 아이콘 모양이 이미 말하므로 색은 위계(`{colors.text}` ↔ `{colors.text-subtle}`)만 맡는다.

**`pull-to-refresh`** — 목록을 당겨서 새로고침할 때의 표시자. M3 `PullToRefreshBox` 기본 표시자 위에 배경 `{colors.surface-raised}`와 스피너 색 `{colors.progress-indicator}`를 얹는다. 목록은 셸 헤더 아래로 흘러 들어가도 되지만 표시자는 그 자리에서 헤더에 완전히 가리므로, 셸이 넘긴 콘텐츠 여백의 **상단만큼 내려** 헤더 뒤에서 나오게 한다. 목록이 그려진 상태에만 두고 골격·조회 실패·빈 목록에는 두지 않는다.

### 퍼널

> 간편 제작 퍼널은 셸을 두르지 않는 전체 화면이라 chrome 을 화면이 직접 그린다. 아래 세 컴포넌트는
> 지금 `:feature:create`(FAB 은 `:feature:home`)가 소유하고, 두 번째 모듈 사용처가 생기면 `:core:ui`로 올린다.

**`fab`** — 홈 우측 하단의 제작 진입 버튼. **원형**(`{rounded.pill}`)이고 배경은 주 버튼(`{component.button-primary}`)과 같은 브랜드 원색 `{colors.brand}`, 아이콘 `{colors.text-inverse}`, 크기 56dp(M3 FAB 기본). **고도(그림자)를 0으로 없앤다** — 이 시스템은 층을 표면 색으로만 나눈다. 하단 바에 초록을 두지 않는 것과 어긋나지 않는다 — FAB 은 상시 chrome 이 아니라 화면의 주 동작 그 자체라서, 초록이 "지금 누를 것"을 가리킨다는 규칙 그대로다.

**`funnel-header`** — 섹션 헤더처럼 M3 `TopAppBar` 위에 색만 얹고, 로고 대신 화면 제목(`{typography.title-large}`)과 오른쪽의 임시 저장·닫기 버튼을 둔다. 높이는 앱 바 기본 64dp 다. 구분선과 그림자를 두지 않는다.

**`detail-header`** — 셸을 두르지 않는 하위 화면(스토리 상세·채팅방·친구 초대·피드백)의 앱 바. 같은 `TopAppBar` 위에 뒤로가기 버튼과 제목(`{typography.body-large-strong}`)을 둔다. **섹션·퍼널 헤더보다 한 단 작은 것은 이 자리의 제목이 화면 이름이 아니라 지금 보고 있는 것의 이름이기 때문이다** — 스토리 제목처럼 길어질 수 있는 값이라 한 줄로 자르고, 고정된 화면 이름과 위계도 갈린다.

**`step-indicator`** — 헤더 아래 얇은 막대 분절로 퍼널 진행을 표시한다. 높이 3dp, 양끝 `{rounded.pill}` 라운드, 분절 사이 `{spacing.component}`, 좌우 여백 `{spacing.gutter}`. 완료 단계는 `{colors.text-disabled}`, 현재 단계는 그보다 반 단계 연한 `{colors.step-indicator-active}`(라이트 #9F9F9F · 다크 #666666), 미도달 단계는 `{colors.border}`다. 단계 이름은 시각 라벨 없이 접근성 텍스트로만 제공하고, 막대들은 장식이라 시맨틱을 하나로 묶는다. 채움에 브랜드 초록을 쓰지 않는 이유는 진행 표시가 정보이지 눌러야 할 동작이 아니기 때문이고, 미도달을 `{colors.background-neutral}`이 아니라 `{colors.border}`로 두는 이유는 표면(`{colors.surface}`) 위에서 전자가 거의 보이지 않기 때문이다.

**퍼널 하단 CTA** — 주 버튼(다음·스토리라인 만들기)은 `{component.button-primary}` 그대로이고 보조 버튼(이전)은 `{component.button-neutral}`이다. 두 버튼은 같은 폭(1:1)으로 하단을 나눈다.

**키워드 칩** — 제공·커스텀 태그는 `{component.chip}`(흰 배경 + 옅은 경계)이고 선택은 `{component.chip-selected}`(브랜드 subtle 채움 + 브랜드 경계 + 브랜드 텍스트)로 색 하나가 아니라 채움·경계·글자 셋으로 말한다. 높이는 `{sizes.input}`으로 컨트롤(48dp)보다 낮다 — 여럿이 흐르는 밀도 높은 선택 요소라서이고, 터치 타깃이 최소 48dp 에 못 미치는 것은 알고 수용한다. 모서리는 입력창과 같은 `{rounded.control}`이고, 선택 변화 자체가 반응이므로 눌림 리플을 그리지 않는다. "키워드 추가"·"인물 추가" 트리거는 같은 모양에 `{colors.background-neutral}` 채움 + `{colors.border}` 경계이고, `+` 아이콘은 16dp 로 라벨 크기에 맞춘다. 인물 추가는 폭을 채우지 않고 가운데에 놓인다. 상한에 도달하면 미선택 칩과 트리거를 비활성 색(`{colors.text-disabled}`)으로 내린다.

카테고리 탭은 M3 `SecondaryTabRow` 기본을 쓴다(`TabRow`는 deprecated) — 컨테이너 `{colors.surface}`, 선택 라벨 `{colors.text}`, 비선택 `{colors.text-subtle}`, 잠금 `{colors.text-disabled}`, 필수 표시 `*`는 `{colors.text-danger}`. 선택 표시선은 탭 폭에 맞는 `{colors.text}` 1.5dp 선이다 — 선택 표시는 상태이지 다음 동작이 아니라서 초록을 쓰지 않고, 선택 라벨과 같은 색으로 묶는다. **눌림 리플은 끈다** — 탭을 누르면 라벨 색과 표시선이 곧바로 바뀌므로 그 변화 자체가 반응이고, 하단 내비게이션과 같은 이유다. 스크롤 시 탭만 상단에 고정하고 각 카테고리 콘텐츠는 탭 아래에서 시작한다.

**스토리라인 단계** — 순번 탭(첫·두·세 번째)은 카테고리 탭과 같은 스타일을 그대로 쓰되 잠금과 필수 표시가 없다. 본문은 `story-body`(`{typography.body-reading}`)로 그린다 — 스토리라인은 스토리 본문의 미리보기라서 서사 서체의 자리다. 본문 마크업은 웹과 같은 규칙으로 파싱한다 — `**…**`는 볼드, 단일 `*…*`(내레이션·속마음)는 `{colors.text-narration}`. 평가 버튼(좋아요·별로예요)은 `{sizes.input}` 정사각 아이콘 칩으로, 아이콘은 기본 크기(`{sizes.icon}`) 대신 16dp 로 한 단계 줄여 본문 옆 보조 동작으로 물러나게 하고, 키워드 칩과 같은 선택 문법을 쓴다 — 기본은 `{component.chip}`(흰 배경 + `{colors.border}` 1dp 경계 + `{colors.text}` 아이콘), 활성 시 좋아요는 `{component.chip-selected}`(브랜드 subtle 채움 + 브랜드 경계 + 브랜드 아이콘), 별로예요는 같은 문법의 danger 변형(`{colors.background-danger-subtle}` + `{colors.border-danger}` + `{colors.text-danger}`)이다. 아이콘은 `ic_thumb_up`·`ic_thumb_down`. 하단 CTA 쌍(다시 만들기·선택하기)은 퍼널 CTA 규칙 그대로다. 웹의 선택 키워드 드로어 트리거는 앱에서는 두지 않는다.

**추가 정보 단계** — 선택한 스토리라인 본문은 `{colors.background-neutral}` 상자에 `{typography.body-reading}`으로 놓고, 기본은 한 줄로 접어 말줄임으로 끝낸 뒤 가운데의 더보기·접기(`{colors.text-subtle}` + 16dp 셰브론)로 펼친다 — 웹의 그라디언트 페이드 대신 말줄임을 쓴다. 추천 추가 정보는 키워드 칩과 같은 선택 문법의 칩을 폭을 채워 왼쪽 정렬로 세로 나열한다 — 문장 전체가 들어가는 다중행 칩이다. 자유 입력은 텍스트 필드 문법(`{component.text-field}`) 그대로 여러 줄을 허용하고 오른쪽 아래에 글자 수 카운터를 둔다. 행 오른쪽의 삭제(X)는 `{colors.text-subtle}` 고스트 아이콘 버튼이고, "정보 추가" 트리거는 인물 추가와 같은 모양으로 가운데에 놓인다. 하단 CTA 쌍(다시 선택하기·스토리 완성하기)은 퍼널 CTA 규칙 그대로다.

### 로고

**`logo-google`** — `res/drawable/ic_logo_google.xml`. 공식 4색 G를 그대로 씁니다. **tint·변형·재색칠 금지.**

**`logo-kakao`** — `res/drawable/ic_logo_kakao.xml`. 카카오 말풍선 심벌은 노란 컨테이너 `#FEE500` 위에 검정으로 올립니다(로그인 버튼). 이 노랑은 카카오가 정한 값이라 토큰 팔레트에 넣지 않았습니다. **컨테이너 없이 놓을 때는 `{colors.text}` 단색으로 칠합니다**(`{component.provider-chip}`) — 검정 그대로 두면 다크 모드에서 배경에 묻힙니다. 구글 로고와 달리 카카오는 단색 사용을 허용하므로 이 자리에서만 재색칠합니다.

## 해야 할 것 / 하지 말아야 할 것

### 해야 할 것

- 색·크기·여백·모서리는 `ManyakTheme`의 접근자로만 읽습니다.
- 주 동작 버튼은 기본 프라이머리 `{colors.brand}`, 파괴적 동작은 `{colors.background-danger-bold}`로 구분합니다. 버튼에 프라이머리 색을 넣을 때는 반드시 `{colors.brand}`를 씁니다.
- 보조 설명은 `{colors.text-subtle}`, 강한 대비가 필요 없는 장식만 `{colors.text-disabled}`에 둡니다.
- 배지·칩·아바타는 `{rounded.pill}`(=`CircleShape`)로 그립니다.
- 스토리 본문에는 `{typography.body-reading}`을 씁니다.
- 상태는 색과 문구를 함께 씁니다.

### 하지 말아야 할 것

- `MaterialTheme.colorScheme`·`MaterialTheme.typography`를 직접 참조하지 않습니다. `ManyakTheme`이 두 값을 토큰에서 파생해 채우지만 이는 M3 기본값(보라색·Roboto)이 새는 것을 막는 **안전망**일 뿐 정본이 아닙니다.
- dynamic color를 켜지 않습니다. 브랜드 색이 기기 배경화면에 덮입니다.
- `{colors.brand}`를 텍스트 배경으로 쓰지 않습니다. 큰 그래픽과 버튼 배경(주 동작)에만 씁니다. 버튼에는 `{colors.background-brand-bold}`를 기본 배경으로 쓰지 않습니다 — 그 색은 눌림 상태 전용입니다.
- 그림자로 층을 만들지 않습니다. 표면 색을 바꿉니다.
- `{rounded.pill}`을 큰 dp 값으로 대체하지 않습니다.
- 팔레트 값(`#05A66B` 등)을 화면 코드에 직접 적지 않습니다.
- SemiBold(600)를 요구하지 않습니다. 사다리는 400 / 500 / 700입니다.
- 성공 상태에 새 초록을 만들지 않습니다.

## 화면 대응

- **큰 글자**: 모든 텍스트 크기·행간이 sp라 시스템 글자 크기 설정을 따릅니다. 고정 dp 높이 컨테이너에 텍스트를 가두지 않습니다.
- **라이트·다크**: `ManyakTheme(darkTheme = isSystemInDarkTheme())`로 결정합니다. Compose 첫 프레임 전 창 배경은 `values`/`values-night`의 `@color/surface`가 담당해 다크에서 흰 화면이 번쩍이지 않습니다.
- **창 크기·인셋**: `enableEdgeToEdge()`와 `Scaffold` 인셋까지만 정해져 있습니다. 태블릿·폴더블 대응 정책은 아직 없습니다(아래 알려진 공백).

## 갱신 지침

1. 값을 바꿀 때는 Kotlin 토큰 파일과 이 문서의 표를 같은 커밋에서 함께 고칩니다. 한쪽만 고치면 다음에 어느 쪽이 맞는지 알 수 없습니다.
2. Kotlin 파일은 사람이 옮겨 적습니다. Style Dictionary 같은 생성기는 두지 않습니다 — 토큰 변경 빈도가 낮은데 안드로이드 빌드에 Node 의존을 얹는 비용이 더 큽니다.
3. 새 컴포넌트는 `components:`에 항목을 추가하고 값은 반드시 `{token.ref}`로 적습니다. 헥사·dp를 직접 쓰지 않습니다.
4. 상태 변형(`-pressed`, `-focused`, `-error`)은 별도 항목으로 둡니다. hover는 문서화하지 않습니다.
5. 토큰에 없는 값이 필요하면 화면에서 임시로 만들지 말고 토큰에 단계를 추가합니다(Kotlin + 이 문서). 웹과 공유하는 층이면 디자인 토큰 쪽에도 같은 단계가 필요합니다.
6. 두 번째 사용처가 생기기 전에는 공용 컴포넌트로 올리지 않습니다.

## 알려진 공백

- **`components:` 명세 대부분이 아직 컴포저블이 아닙니다.** 실제로 있는 것은 위 `component/` 표의 넷뿐이고, 버튼·입력·카드·배너는 화면 코드가 조합으로 만들고 있습니다. 두 번째 사용처가 생길 때 올립니다.
- **제목 굵기가 웹과 한 단계 다릅니다.** 토큰은 SemiBold(600)지만 앱은 Medium(500)입니다. 가변 폰트를 쓰는 웹은 600 그대로입니다.
- **M3 확장 슬롯**(`*Fixed` 색, `*Emphasized` 타이포 등)은 파생 대상이 아닙니다. 해당 슬롯을 읽는 컴포넌트를 쓰게 되면 파생을 넓힙니다.
- **폰트가 APK에서 약 5.3MB**를 차지합니다(원본 14MB, 압축 후). 줄여야 하면 Pretendard 공식 subset 빌드로 교체합니다.
- **태블릿·폴더블·가로 모드 정책이 없습니다.**
- **컨트롤 높이(`sizes.*`)는 토큰 정본이 아니라 이 레포가 정한 값입니다.** 웹과 맞추려면 디자인 토큰 쪽에 크기 층을 추가해야 합니다.
- **토큰 값이 웹과 같은지 자동으로 확인할 방법이 없습니다.** 생성기가 레포 밖에 있고 웹은 자체 CSS 변수를 쓰므로, 두 클라이언트의 값이 갈리는지는 사람이 봐야 합니다. 다크 보조 텍스트 두 단계는 지금 앱이 웹보다 밝습니다(2026-08-29).
- **모션 토큰이 화면 전환·요소 등장·퇴장·목록 등장 다섯 단계뿐입니다.** 눌림 상태는 여전히 색 변화로만 정의되어 있고, 스켈레톤 같은 나머지 모션은 정의되지 않았습니다. 필요해지는 시점에 `{motion.*}`에 단계를 추가합니다.
