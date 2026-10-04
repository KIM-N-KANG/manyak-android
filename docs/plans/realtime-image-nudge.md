# 실시간 이미지 기본값과 설정 안내

- 티켓: [KNK-1517](https://kimandkang.atlassian.net/browse/KNK-1517)
- 기준: Android `4638183`, 하네스 `c0b33af`의 `origin/dev`
- 참고 구현: 웹 `src/features/chats/room/hooks/use-realtime-image-nudge.ts`, `utils/realtime-image-nudge.ts`, `components/input/chat-settings-sheet.tsx`
- 계약: [공통 채팅 설정](../../../knk-harness/docs/spec/3-1-client-spec.md#채팅-설정-시트와-메뉴-시트), [Android spec](../../../knk-harness/docs/spec/3-3-android-spec.md#채팅-목록과-채팅방)
- 구조와 결정: [Android design](../../../knk-harness/docs/design/1-2-android-design.md#1-2-4-내비게이션과-화면), [A-050](../../../knk-harness/docs/adr/1-3-android-adr.md#a-050)

## 변경

1. `ChatPreferencesStore`와 ViewModel의 실시간 이미지 초기값을 false로 변경했습니다. 저장된 설정은 그대로 읽고 기존 요청 DTO는 false도 명시적으로 전송합니다. 비용 배지는 같은 화면 설정을 사용합니다.
2. 기존 기기 DataStore에 `chat_completed_turn_count`를 추가했습니다. `edit` 안에서 증가와 상한 3을 적용하고 저장이 완료된 값만 반환합니다. 저장 실패는 null로 처리합니다. 사용자 귀속 저장소가 아니므로 로그아웃 정리에 참여하지 않습니다.
3. ViewModel은 정상 이어쓰기 완료만 기록하고 두 번째 완료 당시 이미지 설정으로 한 번 판단합니다. 재생성, 실패, 중단, 중복 완료는 세지 않습니다. 예약 중 새 전송이 시작되면 지연을 취소하고 응답 종료 후 다시 기다립니다.
4. 설정 시트와 안내 상태, 예약 작업은 ViewModel이 소유합니다. 구성 변경에서는 유지하고 방 이탈과 프로세스 종료에서는 복원하지 않습니다.
5. 시트가 펼쳐진 뒤 별도 Popup으로 전체 화면을 덮습니다. 투어의 둥근 딤 그리기를 `ChatSpotlightDim`으로 분리해 재사용하고 화면 좌표로 행 위치를 맞춥니다. 실시간 이미지 행은 그대로 보이며 같은 위치의 터치 및 접근성 영역이 토글을 전달합니다. 카드와 딤은 시간 토큰을 따라 나타나고 사라집니다.
6. 실시간 이미지를 켜거나 확인, 딤, 뒤로가기를 누르면 안내만 닫고 시트는 유지합니다. 전송 잠금 중에도 설정 버튼이 활성 상태이며 진행 중 턴의 스냅샷은 바뀌지 않습니다.
7. 하네스 spec, design, ADR과 `DESIGN.md`를 함께 갱신했습니다. 새 서버 계약이나 요청 필드는 없습니다.

## 검증

- `:chat:testDebugUnitTest`: 187건 통과. 완료 횟수 보존과 상한, 동시 기록, 저장 실패, 정확한 두 번째 완료, 지연 중 다음 전송, 실패한 다음 응답, 재생성 제외, 중복 완료 제외, 재진입 미노출, 시트 닫기, 요청 스냅샷과 비용 계산을 포함합니다.
- `:chat:ktlintCheck`, `:chat:detekt`, `:designsystem:ktlintCheck`, `:designsystem:detekt`: 최종 통과.
- `installDebug`: emulator-5554에 설치했습니다.
- UI 계측 테스트 `RealtimeImageNudgeTest`: 활성화 시 자동 닫힘을 포함한 4건 통과. 하이라이트 토글, 확인 후 시트 유지, 시스템 뒤로가기 순서, 카드 본문 탭 유지, 딤 탭 닫기와 뒤 시트의 접근성 차단을 확인했습니다. 안내를 닫기 전후 행의 위아래 좌표가 유지되는 검증도 포함합니다.
- 두 저장소의 `git diff --check`와 추가 문서 링크 19개 검증도 통과했습니다.
- 전체 `check`는 반복 실행하지 않았습니다. 전체 게이트는 PR CI 대상입니다.

## 기기 확인

2026-10-04, Pixel_10 에뮬레이터 Android 17에서 실제 dev 앱을 확인했습니다. `always_finish_activities`는 설정되지 않은 기본 비활성 상태였습니다.

- 기존 미저장 설정이 OFF로 표시됐고 비용 배지는 턴 비용만 합산했습니다.
- 응답 생성 중 설정 버튼으로 시트를 열었습니다.
- dev의 기존 테스트 대화 「그냥 스토리」에 정상 전송 2회를 추가했습니다. 두 번째 완료 뒤 설정 시트와 안내가 자동으로 열렸습니다. 서버 테스트 턴과 기기의 완료 횟수 2가 남습니다.
- 하이라이트 스위치를 켜고 가로 회전, 다크 모드, 글자 크기 2배를 적용해 전후 캡처를 비교했습니다. 안내와 스위치 상태가 유지되고 하이라이트가 새 위치를 따라갔습니다. 안내 뒤 화면은 접근성 트리에서 사라지고 스위치와 카드만 남았습니다.
- 뒤로가기 후 안내만 사라지고 설정 시트가 남는 것을 확인했습니다.
- 실시간 이미지는 OFF로 되돌렸습니다. 글자 크기 1.0, 야간 모드 no, 세로 방향 0, 자동 회전 1로 원래 설정을 복원했습니다.

| 확인 | 캡처 |
| --- | --- |
| 초기 설정과 비용 | [시트](../../captures/knk-1517/settings-before.png), [비용](../../captures/knk-1517/default-off-cost.png) |
| 전송 잠금 중 설정 | [응답 생성](../../captures/knk-1517/streaming.png), [설정 열기](../../captures/knk-1517/locked-settings.png) |
| 자동 안내와 스위치 | [세로 안내](../../captures/knk-1517/nudge-portrait.png), [스위치 ON](../../captures/knk-1517/nudge-toggle-on.png) |
| 구성 변경 | [가로](../../captures/knk-1517/nudge-landscape.png), [다크](../../captures/knk-1517/nudge-dark.png), [큰 글자](../../captures/knk-1517/nudge-large-text.png) |
| 안내 닫기 | [뒤로가기 후 시트](../../captures/knk-1517/back-keeps-sheet.png) |

캡처는 기존 저장소 규칙에 따라 Git 추적 대상에서 제외합니다. 캡처 비교에서 안내 표시 중 시트에 섹션 간격 하나가 추가되는 현상을 발견해 Popup의 0 크기 앵커를 실시간 이미지 행 안으로 옮겼습니다. 이 최종 위치 보정은 행 좌표를 비교하는 UI 계측 테스트로 검증했습니다. 예약 중 회전과 프로세스 종료는 실제 기기에서 재현하지 않았으며 ViewModel 수명과 재진입 단위 테스트로 판단했습니다.

## 복구

기능 변경을 되돌리면 기존 동작으로 돌아갑니다. 추가한 정수 키는 이전 버전에서 읽지 않으며 저장소 삭제나 마이그레이션이 필요하지 않습니다. 실시간 이미지 설정 키와 명시적으로 저장한 선택은 바꾸지 않았습니다.

## 후속 조정

실시간 이미지 스위치를 켜는 것으로 안내 목적을 달성하므로, 활성화 즉시 설정 변경 콜백과 안내 닫기 콜백을 함께 보냅니다. 딤과 카드는 기존 퇴장 모션으로 사라지고 시트 및 켜진 설정은 유지합니다. 확인 버튼으로 닫을 때는 설정을 바꾸지 않습니다.

위 기기 캡처는 후속 조정 전 동작입니다. 활성화 시 안내 닫힘은 에뮬레이터 UI 계측 테스트로 검증했으며 실제 앱에서 캡처를 다시 비교하지는 않았습니다.
