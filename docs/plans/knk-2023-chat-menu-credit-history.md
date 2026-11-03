# 채팅 메뉴의 이프 내역 (KNK-2023)

- 작성일: 2026-11-04
- 티켓: [KNK-2023](https://kimandkang.atlassian.net/browse/KNK-2023) (상위 KNK-2021), 웹 짝 KNK-2022
- 브랜치: Android `feat/KNK-2023-chat-menu-credit-history`(fetch한 `origin/dev` `7ea996a5`에서 분기한 뒤 KNK-2006 #146이 머지된 `a4b93296`로 앞당김), 하네스 같은 이름(`origin/dev` `847f595`에서 분기한 뒤 KNK-2022 #501과 KNK-2006 #500이 머지된 `2cfafc4`로 앞당김)
- 계약: 하네스 CHAT 공통 3-1, 3-2-2, 3-3-3과 결정 C-005, PAYMENT 공통 2-2-4 내역 진입, 3-3-2 적용 위치(KNK-2022 #501). 서버는 기존 `GET /users/me/credits/transactions`와 `GET /auth/me`만 씁니다. 분석 이벤트는 더하지 않습니다. Android 결정 A-092.
- 참고 구현: manyak-web KNK-2022의 `chat-menu-drawer.tsx`, `chat-menu-credit-history.tsx`, `credit-balance-card.tsx`, `credit-history-list.tsx`

## 변경

1. 메뉴 이프 카드: "충전" 왼쪽 4dp 간격에 "내역" 버튼을 둡니다. 바탕은 웹 `bg-foreground/5`처럼 반투명 중립 톤인 `overlayPressed`(라이트 검정 6%, 다크 흰색 8%)를 카드 위에 한 겹 더해 라이트는 카드보다 진하고 다크는 밝으며, 글자는 기본색이고 강조색은 "충전"만 씁니다. 다크에서 시트와 카드 바탕이 같은 Gray900이라 둘 다 묻혀 채팅 메뉴의 카드만 `backgroundNeutralRaised`로 바꿨습니다(라이트는 같은 Gray100이라 그대로입니다).
2. 내역 화면: 같은 시트 안에서 `AnimatedContent`로 메뉴와 내역을 바꿉니다. 내역은 오른쪽, 메뉴 복귀는 왼쪽에서 24dp 밀리며 페이드하고 시트 높이는 기본 크기 전환이 잇습니다. 내역은 화면 높이의 70%이고 머리글(돌아가기, "이프 내역", "새로고침")은 고정, 목록만 스크롤합니다. 목록을 끝까지 끌어도 시트는 닫히지 않습니다. 잔액은 머리글에 두지 않고 메뉴 카드가 보입니다.
3. 새로고침: 배경 없는 보조색 텍스트 버튼이고 글자 끝을 목록 줄 오른쪽 선에 맞춥니다. 누르면 목록을 맨 위로 즉시 올린 뒤(부드러운 스크롤은 지나가는 동안 목록 끝을 다시 잡음) 읽던 다음 페이지를 취소하고 커서 없이 첫 페이지만 다시 읽습니다. 응답까지 보던 목록은 그대로 두고, 성공하면 첫 페이지 하나로 바꾸며 실패하면 그대로 둡니다. 다시 읽는 동안 문구 자리를 유지한 가운데 스피너("새로고침 중")와 잠금이고, 다음 페이지 자동 요청도 멈춥니다. 첫 조회 중에는 잠급니다.
4. 뒤로가기: 시트 창의 뒤로가기 콜백보다 나중에 등록되는 `BackHandler`가 내역에서만 메뉴로 돌립니다. M3 1.4 시트 창은 `shouldDismissOnBackPress`를 만들 때만 읽어 내역에서 끄는 방법은 쓸 수 없었습니다. 시트를 닫으면 내역 상태를 비워 다시 열면 메뉴부터입니다.
5. 공용화: 내역 entity와 조회 계약(`CreditTransactionReader`)을 `common`으로 올리고, 목록 상태와 전이, 첫 페이지, 다음 페이지, 다시 읽기 절차(`CreditHistoryController`), 원장 줄 문구를 `common/presentation/credit`에 둡니다. 행, 골격, 빈 상태, 오류와 다음 페이지 자리, 목록 끝 감지는 `designsystem/credit/CreditHistory.kt`로 옮겼습니다. 이프 충전 내역 탭도 같은 구현을 쓰고, 당겨서 새로고침도 같은 다시 읽기 상태(`isRefreshing`)를 씁니다. 문구 리소스는 `my`에서 `common`(사유, 대상, 날짜, 금액)과 `designsystem`(빈 상태, 오류, 조회 중)으로 옮겼습니다.
6. 채팅방 ViewModel: `creditHistory` 상태와 열기, 닫기, 재시도, 다음 페이지, 새로고침 의도를 더했습니다. 조회는 화면 범위의 자식 작업이라 생성과 재연결 입력을 막지 않고, 닫을 때 읽던 페이지를 취소해 늦은 응답이 내역을 되살리지 않습니다.

웹과 다른 점: 동작 줄이기는 시스템 애니메이션 배율을 따릅니다. 열릴 때 초점은 옮기지 않고 TalkBack에는 창 제목 "이프 내역"으로 화면 전환을 알립니다. 웹은 새로고침을 누르는 즉시 뒷 페이지를 지우지만 Android는 응답까지 그대로 두고 성공할 때 첫 페이지로 바꿉니다. 맨 위로 올린 뒤라 보이는 화면은 같고, 다시 읽기가 실패하면 Android는 뒷 페이지까지 남습니다. 첫 조회 중 새로고침은 웹처럼 받지 않고 잠급니다.

## 검증

```bash
./gradlew :common:testDebugUnitTest :my:testDebugUnitTest :chat:testDebugUnitTest :designsystem:compileDebugKotlin \
  :common:ktlintCheck :designsystem:ktlintCheck :my:ktlintCheck :chat:ktlintCheck \
  :common:detekt :designsystem:detekt :my:detekt :chat:detekt checkModuleArchitecture
./gradlew :app:installDebug
```

- 모두 통과했습니다. 새 테스트(`ChatRoomMenuTest`)는 열면 첫 페이지, 닫으면 메뉴, 다시 열면 첫 페이지부터 다시 읽기, 다음 커서 이어 붙이기와 다음 페이지 실패 뒤 목록 유지, 새로고침이 응답까지 목록을 두고 다음 페이지를 멈춘 뒤 커서 없는 첫 페이지 하나로 바꾸기, 닫은 뒤 늦은 응답 미반영입니다.
- emulator-5554(dev 서버, 회원 계정)에서 `adb exec-out screencap`과 `screenrecord`로 확인했습니다. 메뉴 카드의 내역과 충전(4dp 간격, 라이트와 다크의 내역 톤), 내역 전환과 높이 애니메이션, 돌아가기와 시스템 뒤로가기 두 번(메뉴 다음 시트 닫기), 내역에서 바깥 탭으로 닫은 뒤 다시 열면 메뉴, 내역 중 다크 모드 전환 뒤 내역 유지, 목록 끝의 커서 요청, 목록을 아래로 끌어도 시트가 남고 핸들 끌기로 닫힘, 여러 페이지를 내린 뒤 새로고침하면 맨 위로 올라가고 커서 없는 요청 한 번으로 첫 페이지만 남음(스피너는 응답이 빨라 녹화 첫 프레임에만 보임), 이프 충전 내역 탭과 당겨서 새로고침 회귀를 봤습니다.
- 첫 조회 실패와 재시도, 다음 페이지 실패 재시도, 새로고침 실패, 빈 내역, 골격, TalkBack 낭독은 기기에서 보지 않았고 코드와 유닛 테스트로만 판단했습니다.
- 캡처는 `/captures` 규칙상 Git에 포함하지 않습니다.

| 화면 | 캡처 |
| --- | --- |
| 메뉴, 내린 내역, 새로고침 뒤(라이트) | 보관 경로: `../../captures/knk-2023/light-refresh-set.png` |
| 메뉴와 내역(다크) | 보관 경로: `../../captures/knk-2023/dark-pair-v2.png` |
| 새로고침 중 머리글 | 보관 경로: `../../captures/knk-2023/refresh-frames.png` (녹화 프레임) |
| 이프 충전 내역 탭 | 보관 경로: `../../captures/knk-2023/07-credit-charge-history-tab.png` |

## 복구

- 서버 응답이 실패하면 내역 화면이 기존 내역 탭과 같은 실패 안내와 재시도를 보입니다. 저장 스키마와 라우트는 바꾸지 않았으므로 앱을 되돌릴 때는 이 브랜치 커밋을 되돌리면 됩니다.
