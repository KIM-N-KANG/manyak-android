# 페르소나 UI/UX 개선 (KNK-1977)

- 작성일: 2026-11-02
- 티켓: [KNK-1977](https://kimandkang.atlassian.net/browse/KNK-1977) (상위 KNK-1114), 웹 짝 KNK-1976
- 브랜치: Android `design/KNK-1977-improve-persona-ui-ux`(fetch한 `origin/dev` `39831acd`에서 분기), 하네스 같은 이름(`origin/dev` `de83e69` 기준)
- 계약: 하네스 [공통 요구 2-2-3](../../../knk-harness/docs/domain/STORY/CLIENT/2-2-REQUIREMENTS.md#2-2-3-상세와-플레이-시작), [공통 흐름 3-2-1](../../../knk-harness/docs/domain/STORY/CLIENT/3-2-INTERACTION-FLOWS.md#3-2-1-탐색과-상세) 3번과 6번, [공통 배치 3-3-2](../../../knk-harness/docs/domain/STORY/CLIENT/3-3-LAYOUT.md#3-3-2-상세와-이미지)의 시작, 새 채팅 CTA, 시작 시트, 시작 시트 행, 시작 중. 분석은 `client_storyDetail_chatStartButton_clicked`(시트의 "채팅 시작하기")와 `client_storyDetail_persona_selected`(시트 포함, 같은 값 재선택 제외). Android 결정 A-089.
- 참고 구현: 웹 `src/features/stories/detail/`의 `components/chat-start-sheet.tsx`, `components/story-persona-select.tsx`, `hooks/use-detail-personas.ts`, `constants/start-setting-copy.ts`(manyak-web `e81442bd`)

## 변경

1. 문구: 기본 항목 "기본"을 "스토리 기본 주인공"으로, 페르소나 안내를 "선택한 페르소나가 되어 스토리를 진행해요. 채팅을 시작하기 전까지 바꿀 수 있어요"로 바꿨습니다. 시트 문구 네 개를 `story` 문자열에 더했습니다.
2. 하단 CTA는 한 줄 "새 채팅 시작하기"로 되돌리고 누르면 시작 시트를 엽니다. 요약 줄 함수와 그 테스트, 그것만 쓰던 `typography.labelTiny`를 지웠습니다. 진행 표시 버튼은 `StartChatButton`으로 남겨 시트의 "채팅 시작하기"가 씁니다.
3. 시작 시트(`ChatStartSheet`): 제목과 설명, 회색 카드의 페르소나 행과 시작 상황 행(상황 설명 두 줄), 한 번에 한 행만 펼치는 선택지 목록(5줄 반 높이에서 스크롤, 열 때 고른 항목 위 두 칸부터), 고른 항목의 채움과 체크, 페르소나 목록 아래 고정된 "페르소나 생성하기", 하나뿐인 시작 상황의 펼침 없음, 높이와 투명도 스프링을 구현했습니다. 선택은 본문 셀렉트와 같은 의도를 보내 상태를 공유합니다.
4. ViewModel: 시트 열림을 `isStartSheetOpen`으로 두고 의도는 `StartSheet(Open, Close, Start)`, 사건은 `ChatStart(SheetVisible, Requested, Released)`로 묶었습니다. 생성 중 닫기를 무시하고, 성공하면 시트를 걷고 채팅방으로 가며, 복귀 전에는 다시 열지 않습니다. 시트가 열린 채 생성 화면으로 가면 시트를 걷고 표시를 남겨 다음 `ScreenShown`에서 다시 엽니다(A-089).
5. 본문 셀렉트: 공용 `ManyakSelectMenu`의 값 항목을 `sizes.menuMaxHeight`(240dp, 기존 `genreMenuMaxHeight` 이름 변경)에서 스크롤하고, 동작 항목은 구분선 아래 `textSubtle`로 고정합니다. 항목은 장르 메뉴와 같이 최소 40dp로 맞춰 5줄 반이 보입니다. 성별 셀렉트와 홈 정렬 메뉴 항목도 약 3dp 높아집니다.
6. 토큰: `colors.backgroundNeutralRaised`(라이트 #F5F5F5, 다크 #2C2C2C, 다크에서 시트 바탕과 같아지는 `backgroundNeutral` 대신), `sizes.choiceListMaxHeight`(284dp)를 더하고 `DESIGN.md`에 `chat-start-sheet`, 셀렉트 메뉴 규칙과 토큰을 적었습니다.
7. 스크롤 위치: 상세 목록 상태는 목적지의 저장 상태라 페르소나 생성과 채팅방에서 돌아와도 위치가 남고, 목록에서 새로 연 상세는 새 목적지라 맨 위에서 시작합니다. 코드 변경 없이 기기에서 확인했습니다. 웹의 popstate 리스너 순서 수정은 Android에 해당하지 않습니다.

웹과 다른 점: Android 상세는 회원 전용이라 게스트의 로그인 필요 시트는 없습니다. 시트를 다시 여는 표시는 라우트가 아니라 상세 ViewModel 메모리라 프로세스가 종료되면 남지 않습니다.

## 검증

```bash
./gradlew :story:testDebugUnitTest :story:ktlintCheck :story:detekt \
  :designsystem:ktlintCheck :designsystem:detekt :create:compileDebugKotlin :create:ktlintCheck \
  :story:lintDebug :designsystem:lintDebug :app:compileDebugKotlin
./gradlew :app:installDebug
```

- 모두 통과했습니다. 새 테스트는 `StoryDetailPersonaTest`의 시트에서 생성으로 떠났다 돌아오면 한 번만 다시 열기, 본문에서 떠나면 열지 않기, 상한이면 시트 유지 3건과 `StoryDetailViewModelTest`의 생성 중 닫기 무시와 실패 시 유지, 성공 뒤 시트를 걷고 다시 열지 않기 2건입니다. 요약 말줄임 테스트는 기능과 함께 지웠습니다.
- emulator-5554(dev 서버, 회원 계정)에서 `adb exec-out screencap`으로 확인했습니다. 본문 셀렉트의 새 기본 문구와 5줄 반 목록, 고정된 흐린 생성하기, 시트 제목과 설명, 행 값과 상황 설명 두 줄, 펼친 목록의 체크와 고정된 생성하기, 시트에서 고른 페르소나의 본문 반영, 생성 화면 위에 시트가 남지 않음과 뒤로가기 복귀 때 다시 열림, 저장 뒤 새 페르소나가 고른 채로 다시 열림, 펼친 채 회전 전후 유지, 고른 시작 상황으로 채팅 시작과 시트가 걷힌 채 채팅방 전환, 채팅방에서 돌아온 상세의 스크롤 위치와 선택 유지, 다크 모드 카드와 고른 항목 대비를 봤습니다.
- 생성 중 스피너는 응답이 빨라 기기에서 찍지 못했고 기존 버튼 구성과 유닛 테스트로만 판단했습니다. TalkBack의 펼치기와 접기 안내도 기기에서 듣지 않았습니다.
- 확인하며 dev 서버에 0호선 채팅 하나를 만들었고, 시험으로 만든 페르소나 "1977"은 마이에서 지웠습니다.
- 캡처는 `/captures` 규칙상 Git에 포함하지 않습니다.

| 화면 | 캡처 |
| --- | --- |
| 변경 전 상세 CTA와 셀렉트 | 보관 경로: `../../captures/knk-1977/before-detail.png`, `../../captures/knk-1977/before-persona-menu.png` |
| 본문 셀렉트 | 보관 경로: `../../captures/knk-1977/after-persona-menu-v2.png` |
| 시작 시트와 펼친 페르소나 | 보관 경로: `../../captures/knk-1977/after-sheet.png`, `../../captures/knk-1977/after-sheet-persona.png` |
| 생성 뒤 다시 열린 시트 | 보관 경로: `../../captures/knk-1977/after-created-return.png` |
| 다크 모드 | 보관 경로: `../../captures/knk-1977/after-sheet-dark.png` |

## 목록 끝 끌기에 시트가 닫히던 문제

- 사용자 확인에서 페르소나가 많아 목록을 위로 끌어내릴 때 시트까지 닫힌다는 보고가 있었습니다. 목록이 맨 위에 닿은 뒤 남은 끌기가 중첩 스크롤로 시트에 넘어가 시트를 끌어내렸고, emulator-5554에서 같은 끌기로 시트가 닫히는 것을 재현했습니다.
- 펼친 목록에 중첩 스크롤 연결을 두어, 목록이 스크롤할 수 있을 때는 끝에서 남은 끌기와 플링을 목록이 삼키게 했습니다. 스크롤할 것이 없는 짧은 목록은 그대로 넘겨 다른 시트처럼 끌어서 닫힙니다.
- 수정 뒤 같은 기기에서 느린 끌기와 빠른 플링 모두 시트가 남고 목록만 맨 위로 갔으며, 제목 쪽에서 끌어내리면 시트가 닫히는 것을 확인했습니다.

## 복구

서버, 저장 스키마와 라우트 변경은 없습니다. 되돌릴 때는 이 변경을 revert합니다.
