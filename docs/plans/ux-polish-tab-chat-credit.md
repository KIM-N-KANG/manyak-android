# 채팅·제작·마이 화면 UX 다듬기 (KNK-1350)

- 작성일: 2026-09-20
- 범위: 새 API·저장 스키마 없이 화면 동작만 고치는 7건. 브랜치 `design/KNK-1350-polish-tab-chat-credit-ux`.

## 결정

1. **채팅 탭 빈 목록은 제작 탭과 같은 한 줄이다.** 설명 문구와 "제작 탭으로 가기" 버튼을 걷어 `MainTabs`의
   `onGoToStudio` 배선도 없앴다. 하네스 클라이언트 스펙의 빈 상태 표(CTA 포함)는 웹 기준이라 Android 스펙에 예외로
   적어야 한다 — 하네스가 다른 브랜치 작업 중이라 이 PR 뒤 별도 문서 PR로 남긴다.
2. **제작 FAB 은 M3 `ExtendedFloatingActionButton` 이다.** `expanded = !listState.canScrollBackward` 로 목록 맨 위에서만
   "만들기" 라벨을 펼치고, 펼침·접힘 전환은 M3 기본 애니메이션에 맡긴다. 상태 화면(조회 중·실패·빈 목록)은 스크롤이
   없어 늘 펼친다.
3. **로그아웃 진행 중에도 라벨은 "로그아웃"이다.** 진행은 오른쪽 표시자와 잠금이 이미 말한다. `my_logout_in_progress` 삭제.
4. **이프 부족은 턴을 열기 전에 거른다.** `ChatRoomViewModel.startTurn` 이 프로필 잔액과 `chatTurnCost`(정책·체험·실시간
   이미지 반영) 를 비교해 모자라면 컴포저를 건드리지 않고 `ShowCreditRequired` 만 올린다. 잔액·정책·체험 중 하나라도 모르면
   막지 않는다 — 판단은 서버(402) 몫이고 기존 복구 경로는 그대로 둔다. 막을 때 프로필을 다시 읽어 낡은 잔액이 다음 탭에서
   바로잡히게 한다. VM 이 `CreditPolicyRepository` 를 새로 주입받는다.
5. **채팅방 토스트는 한 `ReplacingToast` 로 띄운다.** 앞 문구를 지우고 새 문구만 보인다. 잠긴 입력창 안내에만 쓰던 것을
   문구 인자로 일반화했다.
6. **스토리라인 생성 로딩 문구는 `bodyLarge`(16sp)다.** 채팅 실시간 이미지 자리처럼 `CyclingPhrases` 에 `style` 을 넘긴다.
   서체는 서사 서체가 아니라 UI 서체다.
7. **안내 툴팁 버튼을 `designsystem/ManyakInfoTooltipButton` 으로 올렸다.** 스토리 상세 엔딩 안내(첫 사용처)와 이프 충전
   헤더(둘째 사용처)가 같은 버튼을 쓴다. `MyDetailHeader` 에 `titleTrailing` 슬롯을 더했다.

## 검증

- `:chat` `ktlintCheck`·`detekt`·`testDebugUnitTest`(신규: 잔액이 비용에 못 미치면 턴을 열지 않고 안내만 올린다),
  `:studio`·`:my`·`:story`·`:designsystem`·`:create`·`:app` `ktlintCheck`·`detekt`·`compileDebugKotlin` 통과.
- 에뮬레이터(`captures/knk-1350/`): 제작 탭 빈 목록에서 "+ 만들기" 펼침, 이프 충전 헤더 툴팁 표시. 스크롤 접힘·채팅 빈
  목록·이프 부족 선처리·로그아웃 라벨·로딩 문구 크기는 이 계정에서 재현 조건이 없어 코드·유닛 테스트로만 확인했다.
