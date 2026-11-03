# 탐색 탭과 홈 섹션 (KNK-2004)

- 작성일: 2026-11-03
- 티켓: [KNK-2004](https://kimandkang.atlassian.net/browse/KNK-2004) (상위 KNK-2001), 웹 짝 KNK-2003
- 브랜치: Android `feat/KNK-2004-apply-explore-tab-and-home-sections`(fetch한 `origin/dev` `bf99c53f`에서 분기), 하네스 `docs/KNK-2004-apply-android-explore-and-home-specs`(KNK-2003 #493 머지 뒤 `origin/dev` `6c70db1`에서 분기, PR #496)
- 계약: 하네스 STORY 공통 [2-2-2 탐색과 제작 목록](../../../knk-harness/docs/domain/STORY/CLIENT/2-2-REQUIREMENTS.md#2-2-2-탐색과-제작-목록), [3-3-1 배치](../../../knk-harness/docs/domain/STORY/CLIENT/3-3-LAYOUT.md#3-3-1-홈과-제작-목록), 결정 C-017, C-018, C-019, 서버 `GET /stories/home`, `GET /stories/explore`(dev Swagger로 확인). 분석은 카드 클릭과 노출의 `section` `explore`. Android 결정 A-090.
- 참고 구현: manyak-web `feat/KNK-2003-apply-explore-tab-and-home-sections`(`6a5a45fc`)의 `src/features/stories/explore/`, `list/components/home-section*.tsx`, `_shared/components/story-card.tsx`, `studio/menu/components/created-story-*.tsx`

## 변경

1. 하단 탭: 홈, 채팅, 제작, 탐색, 마이 다섯 탭. 탐색 아이콘은 웹 나침반 SVG를 벡터로 옮겼습니다. 셸이 다른 탭에서 탐색을 고를 때만 진입 횟수를 올려 탐색에 넘깁니다.
2. 탐색(`home/explore`): `GET /stories/explore`를 토큰을 실어 부릅니다. ViewModel은 처음 보는 진입 값일 때만 목록을 비우고 cursor 없이 읽고, 다음 페이지는 받은 nextCursor로 잇습니다. 당겨서 새로고침은 첫 페이지 하나로 바꿉니다. 세 열 2dp 간격 그리드, 각진 표지와 56dp ORIGINAL 태그, 칸별 물결 전환(420ms, 대각선 45ms, 최대 450ms, 96%에서 시작)을 구현했습니다.
3. 홈: 필터, 정렬 줄과 숨김 동작을 지우고 `GET /stories/home` 섹션을 세로로 쌓은 가로 카드 줄로 바꿨습니다. 빈 섹션은 숨기고, 셋째 카드가 16dp 보이게 카드 폭을 계산합니다. 카드는 제목, 두 줄 소개, Compact 장르 뱃지 한 줄이고 작성자가 없어 내 닉네임 변경 때 다시 읽던 동작도 지웠습니다.
4. 제작 탭: 목록 순서는 그대로 두고 진행 중(초안, 제출본)과 내 스토리(완성 중, 완성 스토리) 구역 제목과 개수를 더했습니다. 공개 범위를 표지 왼쪽 위 뱃지로 옮기고 메타 줄에는 제작일만 둡니다.
5. 공용: 장르 뱃지 줄(`StoryGenreBadges`)을 studio에서 designsystem으로 옮기고, 표지 알약 뱃지(`StoryCoverBadge`, 밝은 변형 포함)와 표지 그림 준비 콜백(`StoryCover.onPictureReady`)을 더했습니다. `DESIGN.md`에 `home-section`, `explore-grid`, `studio-section-title`과 바뀐 카드 규칙을 적었습니다.
6. 골격: 홈은 섹션 2개와 카드 3장에 제목, 소개 두 줄, 장르 두 개 자리를, 탐색은 표지 12장을, 제작 탭 전체 골격은 맨 위 구역 제목 자리와 제작일만 남긴 메타 자리를 둡니다(웹 `6a5a45fc`와 같은 구성).
7. 분석: `StoryListSection.EXPLORE`를 더해 탐색 카드 클릭과 노출에 씁니다. 홈 카드 노출은 같은 스토리라도 섹션마다 셉니다.

확인만 한 것: 작성자가 null인 카드는 목록 DTO와 상세 DTO 모두 null을 받아 작성자 줄만 생략합니다. 간편 제작 공개 범위 기본값은 `PUBLIC`입니다. 페르소나는 ID로만 고르고 목록 순서에 기대는 화면이 없습니다. 당김 표시자 뒤에는 원래 배경이 없어 바꿀 것이 없었습니다.

웹과 다른 점: 동작 줄이기는 웹의 0.2초 페이드 대신 시스템 애니메이션 배율을 따릅니다. 탐색 진입은 쿼리 캐시가 아니라 셸의 진입 횟수로 판정하고 프로세스가 다시 시작되면 새 순서입니다. 홈 카드 ORIGINAL 태그는 기존 Android 폭(78dp, 표지 곡률에서 역산)을 유지했습니다.

## 검증

```bash
./gradlew :home:testDebugUnitTest :designsystem:testDebugUnitTest :studio:testDebugUnitTest
./gradlew :home:ktlintCheck :home:detekt :designsystem:ktlintCheck :designsystem:detekt \
  :studio:ktlintCheck :studio:detekt :app:ktlintCheck :app:detekt :analytics:ktlintCheck \
  :navigation:ktlintCheck checkModuleArchitecture
./gradlew :app:installDebug
```

- 모두 통과했습니다(home 20, designsystem 32, studio 53건). 새 테스트는 홈 섹션 필터링, 빈 홈, 실패와 재시도, 새로고침 실패 유지, 탐색 진입 값 판정, 커서 이어 받기, 새로고침의 첫 페이지 교체, 다음 페이지 실패 유지와 홈, 탐색 저장소 매핑입니다.
- emulator-5554(dev 서버, 회원 계정)에서 `adb exec-out screencap`과 `screenrecord`로 확인했습니다. 다섯 탭과 나침반 아이콘, 홈 섹션 두 개와 셋째 카드 16dp, 홈 카드 글자 단위 줄바꿈과 장르 줄, 탐색 세 열 그리드, 상세 복귀 뒤 같은 순서, 탭 재선택은 그대로이고 홈을 거친 재진입은 새 순서, 20칸 뒤 다음 페이지 노출(`position` 20 이후 `section=explore`), 당겨서 새로고침 때 왼쪽 위부터 퍼지는 칸별 전환, 제작 탭 구역 제목과 개수, 공개와 비공개 뱃지를 봤습니다.
- 다크 모드, 회전, TalkBack 낭독, 다음 페이지 실패 재시도와 세 화면의 골격은 기기에서 보지 않았고 코드와 유닛 테스트로만 판단했습니다. 골격은 응답을 멈추는 프록시로 찍으려 했으나 앱 시작의 동의 확인이 먼저 막혀 화면에 닿지 못했고, 프록시 설정은 모두 지웠습니다.
- 캡처는 `/captures` 규칙상 Git에 포함하지 않습니다.

| 화면 | 캡처 |
| --- | --- |
| 변경 전 홈과 제작 | 보관 경로: `../../captures/knk-2004/before-home.png`, `../../captures/knk-2004/before-studio.png` |
| 홈 섹션 | 보관 경로: `../../captures/knk-2004/after-home.png` |
| 탐색 | 보관 경로: `../../captures/knk-2004/after-explore.png`, `../../captures/knk-2004/wave-frames.png` |
| 제작 탭 | 보관 경로: `../../captures/knk-2004/after-studio.png` |

## 사용자 확인 뒤 조정

- 공개 뱃지 아이콘을 디자인이 준 `globe-outline.svg`로 바꿨습니다.
- 구역 제목 옆 개수가 기준선 정렬로 글자보다 내려앉아 보여 세로 가운데 정렬로 바꿨습니다.
- 홈 카드 장르 뱃지가 웹(높이 20px, 가로 여백 8px)보다 낮았습니다. Compose가 한 줄 글자의 줄 상자를 잘라 약 18dp였고, `Compact` 뱃지도 `Large`처럼 줄 상자를 남겨 웹과 같은 20dp로 맞췄습니다. 가로 여백은 웹과 같은 8dp입니다. 표지 위 좋아요 수와 턴 수 뱃지도 같은 단계라 함께 20dp가 됩니다(웹 `sm` 배지와 같음).
- 홈 골격 카드는 제목, 소개 두 줄, 장르 뱃지 자리를 실제와 같은 글자 스타일과 뱃지를 보이지 않게 그려 잡도록 바꿔 실제 카드와 높이가 같습니다. 기기 캡처: `../../captures/knk-2004/badge-compare.png`, `v2-studio-top.png`, `v2-public-badge.png`.

## 복구

- 서버 홈이나 탐색 응답이 실패하면 각 화면이 기존 공통 실패 안내와 재시도를 보입니다. 앱을 되돌릴 때는 이 브랜치 커밋을 되돌리면 되고 저장 스키마와 라우트 직렬화 값은 바꾸지 않았습니다(`ExploreRoute`만 추가).
