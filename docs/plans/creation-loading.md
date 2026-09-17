# 제작 로딩 표현 갱신

- 티켓: [KNK-1323](https://kimandkang.atlassian.net/browse/KNK-1323)
- 작업일: 2026-09-17
- 브랜치: Android·하네스 모두 `design/KNK-1323-refresh-creation-loading`
- 분기 기준: fetch한 `origin/dev` — Android `e0119b20`, 하네스 `9afdad8`
- 계약: [공통 제작 퍼널](../../../knk-harness/docs/spec/3-1-client-spec.md#3-1-4-스토리-생성-퍼널), [Android 제작](../../../knk-harness/docs/spec/3-3-android-spec.md#제작과-데이터-보존)

## 변경과 근거

1. 웹 `story-generating-loading.tsx`·`reasoning-text.tsx`처럼 4초 문구 순환, 글자별 25ms 캐스케이드 전환, 점 로더와 전체 텍스트 시머를 적용했습니다. 문구 3종과 15·30초 힌트는 유지합니다.
2. 채팅의 `WritingShimmer` 브러시를 `designsystem/TextShimmer`로 공용화했습니다. 제작은 웹과 같은 4초 주기, 채팅은 기존 2초 주기·색을 유지합니다.
3. `ImageGenerationLoading`은 테마 배경·테두리에 점을 그리고 하이라이트 위치에 따라 위치·크기·밝기를 함께 바꿉니다. 웹 `image-generation.tsx`의 시각적 움직임을 Compose Canvas와 기본 애니메이션으로 표현하며, 포인터 추적·ResizeObserver는 사용하지 않습니다. 유효한 양수 비율을 받아 3:4·4:3에 대응합니다.
4. Completing 카드 표지를 위 컴포넌트로 교체하고 제목에 시머를 적용했습니다. 앱의 완성 알림 안내, 다른 카드 상태와 5초 폴링은 유지합니다.
5. 주변 인물이 0명일 때 인물 추가 위에 랜덤 생성 안내를 표시합니다. 문자열은 `create` 리소스에 둡니다.
6. Android spec·design과 `DESIGN.md` 토큰·컴포넌트 명세를 갱신했습니다. API·저장 스키마·내비게이션 변경은 없습니다.

웹 기준은 현재 로컬 `dev`의 `a02d3fbf`이며, 티켓이 참조한 `9706446a`가 포함된 것을 확인했습니다. 하네스 계약은 두 브랜치의 분기 시점 `origin/dev`에서 확인했습니다.

## 검증

```bash
./gradlew \
  :designsystem:ktlintCheck :designsystem:detekt :designsystem:testDebugUnitTest \
  :create:ktlintCheck :create:detekt :create:testDebugUnitTest \
  :studio:ktlintCheck :studio:detekt :studio:testDebugUnitTest \
  :chat:ktlintCheck :chat:detekt :chat:compileDebugKotlin \
  :app:installDebug
```

- 최종 명령 통과. 단위 테스트 143개(designsystem 5, create 114, studio 24), 실패·오류·건너뜀 0개입니다. 새 점 패턴 테스트는 영향 범위·중심과 가장자리 값·단조 감소·경계에서의 부드러운 변화를 확인합니다.
- 최초 정적 검사에서 밝기 상수, 함수 길이·Preview 개수, 빈 줄 위반을 수정한 뒤 재실행했습니다.
- Pixel_10 / Android 17에서 변경 전후 APK 설치 후 `PreviewActivity`와 `adb exec-out screencap -p`로 같은 샘플을 비교했습니다.
- 제작 카드의 라이트·다크 표현, 스토리라인의 순환 문구·15초와 30초 힌트, 다크 모드·글자 크기 2.0 변경 뒤 힌트 유지를 확인했습니다.
- UI Automator 트리에서 문구가 글자별로 중복 노출되지 않고 전체 문구로 제공되는 것을 확인했습니다. 빈 인물 목록의 안내 노출과 인물이 있는 목록의 비노출도 확인했습니다.
- 기기 설정은 다크 모드 `no`, 글자 크기 `1.0`으로 복구했습니다. 서버 생성 요청·사용자 데이터 변경은 하지 않았습니다.
- 두 저장소 `git diff --check` 통과. 로컬 전체 `check`는 반복하지 않았습니다.

| 증거 | 경로 |
| --- | --- |
| 제작 카드 전·후 | [전](../../captures/knk-1323/studio-before.png) · [후](../../captures/knk-1323/studio-after.png) |
| 제작 카드 다크 | [다크](../../captures/knk-1323/studio-dark.png) |
| 스토리라인 전·후 | [전](../../captures/knk-1323/storyline-before.png) · [후](../../captures/knk-1323/storyline-after.png) |
| 스토리라인 힌트·테마·글자 크기 | [다크](../../captures/knk-1323/storyline-dark.png) · [큰 글자](../../captures/knk-1323/storyline-large.png) |
| 주변 인물 안내 | [0명](../../captures/knk-1323/supporting-empty.png) · [인물 있음](../../captures/knk-1323/supporting-populated.png) |

캡처는 기존 `/captures` ignore 규칙에 따라 로컬에만 남습니다. 화면 검증은 Compose Preview의 샘플 상태이며 실제 로그인·생성 API·폴링 완료 흐름, 4:3 화면, TalkBack 낭독은 수동 실행하지 않았습니다. Preview는 실제 화면의 상단 인셋을 포함하지 않아 상태 표시줄이 겹치는 부분이 있습니다.

## 복구

이 작업의 UI·토큰·공용 브러시 이동을 함께 되돌리면 기존 로딩 표현으로 복구됩니다. 데이터 이전이나 서버 조치는 필요하지 않습니다.

## 애니메이션 보완

초기 구현은 점의 밝기만 바꿔 웹에 비해 움직임이 약했습니다. 사용자 피드백 뒤 실제 화면 녹화로 확인하고, 표지 점의 방사형 변위·크기 변화와 문구 앞 점의 상하 움직임을 추가했습니다. 문구 캐스케이드는 이전·다음 문구를 각각 배치해 글자 폭이 바뀔 때 중간 위치가 흔들리는 구조도 제거했습니다.

보완 후 `:designsystem:ktlintCheck :designsystem:detekt :designsystem:testDebugUnitTest :create:ktlintCheck :create:detekt :create:compileDebugKotlin :app:installDebug`를 다시 통과했습니다. 에뮬레이터 화면 녹화의 연속 프레임에서 점의 이동·크기 변화, 텍스트 시머와 캐스케이드 전환을 확인했습니다.

- [표지 로딩 영상](../../captures/knk-1323/loading-demo.mp4)
- [순환 문구 영상](../../captures/knk-1323/phrases-demo.mp4)

영상은 화면 녹화에서 해당 컴포넌트 영역만 잘랐으며 재생 속도는 바꾸지 않았습니다.

시스템 `animator_duration_scale=0`에서 별도 시점에 캡처한 표지·제목 영역의 픽셀이 같은 것을 확인했습니다. 검사 후 기존 값 `1`로 복구했습니다.

## 이동 경로 미세 조정

- 사용자 요청에 따라 중심의 가로·세로 기본 주기를 10,681→9,710ms, 13,195→11,995ms로 줄여 약 10% 빠르게 조정했습니다.
- 7,319ms 보조 위상을 가로 2%·세로 1.6% 크기로 섞어 이동 경로에 작은 불규칙성을 추가했습니다. 매 프레임 난수를 생성하지 않아 움직임과 반복 경계가 연속적으로 이어집니다. 점의 최대 변위·크기·밝기는 유지합니다.
- `:designsystem:ktlintCheck :designsystem:detekt :designsystem:testDebugUnitTest :app:installDebug` 통과. Pixel_10(Android 17)에 설치한 진행 카드 Preview를 8초 녹화하고 별도 시점 스크린샷을 확인했습니다. [조정 영상](../../captures/knk-1323/tuned-loading.mp4)은 원래 재생 속도입니다.
