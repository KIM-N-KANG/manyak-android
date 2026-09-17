# 인물 이미지 풀스크린 뷰어

- 티켓: [KNK-1321](https://kimandkang.atlassian.net/browse/KNK-1321)
- 작업일: 2026-09-17
- 상태: 구현·로컬 집중 검증 완료. 원격 PR·CI 결과는 연결된 Jira와 PR에서 확인합니다.
- 브랜치: Android·하네스 모두 `feat/KNK-1321-character-image-viewer`
- 분기 기준: fetch한 `origin/dev` — Android `ebafa9b7`, 하네스 `6361c069`
- 계약: [공통 스토리 상세](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-003-스토리-상세), [Android 화면 계약](../../../knk-harness/docs/spec/3-3-android-spec.md#3-3-2-내비게이션화면-계약), [분석 이벤트](../../../knk-harness/docs/spec/6-analytics.md)
- 구조·결정: [Android design](../../../knk-harness/docs/design/1-2-android-design.md#1-2-4-내비게이션과-화면), [A-041](../../../knk-harness/docs/adr/1-3-android-adr.md#a-041)

## 변경

1. 상세 전용 `StoryImageViewer`를 `designsystem/FullscreenImageViewer`로 옮겼습니다. 기존 핀치·팬·더블탭 계산과 화면 탭·X·뒤로가기 닫기를 유지합니다. 상태 바 아이콘 처리와 닫기 문자열도 공용으로 옮겼습니다.
2. `CharacterImage`의 탭 콜백과 “{이름} 인물 이미지 크게 보기” 접근성 이름을 연결했습니다. URL 허용 검사·4:3 전체 이미지 표시·로드 실패 시 영역 제거는 유지합니다.
3. 상세·채팅의 `imageViewerUrl`과 MVI Intent/Event를 연결했습니다. 상세 재조회는 선택 이미지가 존재하는 동안 유지하며 사라지면 닫습니다. 채팅의 프롤로그·확정 턴·이어쓰기·재생성 스트리밍이 동일 콜백을 사용합니다.
4. `client_storyDetail_characterImage_clicked`에는 `story_id`, `client_chat_characterImage_clicked`에는 `chat_id`만 기록합니다.
5. 공용 뷰어 스크림을 테마 토큰으로 옮기고 `DESIGN.md`와 하네스 spec·design·ADR을 맞췄습니다. 함수 복잡도 제한을 지키기 위해 채팅 입력 처리와 방 액션 처리를 분리했고, `ChatRoomViewModel`의 함수 수 제한은 해당 클래스에만 예외를 적용했습니다.

## 검증

다음 명령이 통과했습니다. 최초 detekt의 함수 복잡도·길이 및 테스트 줄 길이 지적을 정리한 뒤 동일 집중 검증을 재실행했습니다.

```bash
./gradlew \
  :story:ktlintCheck :story:detekt :story:testDebugUnitTest \
  :chat:ktlintCheck :chat:detekt :chat:testDebugUnitTest \
  :designsystem:ktlintCheck :designsystem:detekt :designsystem:testDebugUnitTest \
  :analytics:ktlintCheck :analytics:detekt :analytics:testDebugUnitTest \
  checkModuleArchitecture :app:installDebug
```

- 단위 테스트 174개 통과: story 27, chat 140, designsystem 5, analytics 2. 실패·오류·건너뜀 0개.
- 뷰어 열기·닫기의 URL 상태, 허용하지 않은 URL 차단, 상세 재조회 시 대상 유지·제거, 이벤트 이름·필수 속성, 스트리밍 중 뷰어를 열고 닫아도 누적 응답·입력 잠금 유지 확인.
- 모듈 경계 검사: 17개 모듈·385개 Kotlin 파일 통과. 변경 모듈 컴파일·리소스 처리 및 Pixel_10(Android 17) 설치 성공.
- 실제 로그인된 앱에서 상세 주변 인물·채팅 확정 턴 이미지 탭 → 뷰어 → 시스템 뒤로가기 확인. 원래 화면·스크롤 위치 유지 및 접근성 트리의 인물 이미지 이름 확인.
- 상세 X 닫기, 다크 모드 전환 시 같은 이미지의 뷰어 유지 확인. `always_finish_activities`는 설정되지 않은 기본 상태였으며 다크 모드는 검사 후 원래 `no`로 복원했습니다. 서버 데이터 생성·수정은 하지 않았습니다.
- 핀치·팬·더블탭의 실제 확대 배율, 회전·큰 글자, 프로세스 종료 복원, 실제 SSE 진행 중 UI 조작, Amplitude 수신은 수동 검증하지 않았습니다. ADB 연속 tap은 더블탭으로 인식되지 않아 확대 검증 근거로 삼지 않았습니다. 확대 계산은 기존 구현을 그대로 유지했습니다.
- 두 저장소 `git diff --check` 통과. 전체 `check`·CI는 실행하지 않았습니다.

스크린샷은 Git에서 제외되는 로컬 증거입니다.

| 화면 | 열기 전 | 뷰어 | 시스템 뒤로가기 후 |
| --- | --- | --- | --- |
| 상세 | [전](../../captures/knk-1321/detail-before-open.png) | [열림](../../captures/knk-1321/detail-open.png) | [닫힘](../../captures/knk-1321/detail-after-back.png) |
| 채팅 | [전](../../captures/knk-1321/chat-before-open.png) | [열림](../../captures/knk-1321/chat-open.png) | [닫힘](../../captures/knk-1321/chat-after-back.png) |

[상세 다크 모드의 열린 뷰어](../../captures/knk-1321/detail-dark-open.png)는 별도 인물 이미지로 구성 변경을 확인한 캡처입니다.

## 복구

이 변경을 되돌리면 인물 이미지 탭 확장과 두 분석 이벤트를 제거하고 상세 썸네일 전용 뷰어로 돌아갑니다. API·저장 스키마·영속 데이터 변경은 없습니다.
