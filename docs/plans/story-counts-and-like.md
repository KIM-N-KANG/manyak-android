# 스토리 지표 축약과 좋아요 재노출

- 티켓: [KNK-1430](https://kimandkang.atlassian.net/browse/KNK-1430) (상위 KNK-1428)
- 작업일: 2026-09-26
- 브랜치: Android `feat/KNK-1430-abbreviate-story-counts-and-show-likes`, 하네스 `docs/KNK-1430-abbreviate-story-counts-and-show-likes`
- 분기 기준: fetch한 `origin/dev` — Android `aa554767`, 하네스 `31c35e9`
- 계약: [공통 FE-SCREEN-001·003·013](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-003-스토리-상세), [Android 스토리 목록·상세](../../../knk-harness/docs/spec/3-3-android-spec.md#스토리-목록상세)
- 참고: 웹 KNK-1421(manyak-web #209), 웹 `src/lib/format-count.ts`

## 변경

1. `designsystem/text/formatCompactCount`를 추가했습니다. 1,000 미만은 그대로, 이상은 `1K`·`1.2K`·`1.2M`·`2B`처럼 소수 첫째 자리 아래를 버립니다. 홈 카드·상세 히어로 배지(`StoryThumbnail`)와 제작 카드 메타(`MyStoryCard`)의 `NumberFormat.getIntegerInstance()`를 대체했습니다. 채팅 목록 카드의 턴 수는 범위 밖이라 그대로 둡니다.
2. [KNK-1322](story-like-ui-hidden.md)에서 숨긴 좋아요 UI를 복원했습니다. `StoryThumbnail`에 좋아요 수 배지(턴 수 왼쪽), 제작 카드 메타에 좋아요 칩, 상세 `StartChatCta`에 좋아요 버튼(`canLike`일 때만)을 다시 연결했습니다. 토글 로직은 기존 `StoryDetailViewModel.ToggleLike`를 그대로 씁니다.
3. 홈 필터 칩의 선택 채움을 `backgroundBrandBold`에서 `brand`로 바꿨습니다. `DESIGN.md`의 "프라이머리 채움은 브랜드 원색" 결정(2026-08-24)과 웹 `bg-primary`에 맞춘 것입니다.
4. `DESIGN.md`의 필터 칩 색과 상세 좋아요 아이콘 버튼 규칙, 하네스 Android spec·design을 맞췄습니다.

## 검증

```bash
./gradlew --continue \
  :designsystem:ktlintCheck :designsystem:detekt :designsystem:testDebugUnitTest \
  :home:ktlintCheck :home:detekt :home:testDebugUnitTest \
  :story:ktlintCheck :story:detekt :story:testDebugUnitTest \
  :studio:ktlintCheck :studio:detekt :studio:testDebugUnitTest \
  :app:installDebug
```

- 모두 통과했습니다. 축약 규칙은 `CompactCountTest`가 고정합니다.
- emulator-5554에 설치 전후 dev 서버 데이터로 `adb exec-out screencap`을 비교했습니다. 홈 칩이 로고와 같은 초록이 되고, 홈·상세 배지와 제작 메타에 좋아요 수가 보이며, 상세 하단에 하트 버튼이 생겼습니다. 하트를 두 번 눌러 1 → 0 → 1로 배지와 아이콘이 바뀌는 것을 확인했고 원래 상태로 되돌렸습니다.
- 실제 데이터에 1,000 이상인 수가 없어 축약 표기는 화면으로 확인하지 못했고 단위 테스트로만 확인했습니다. 다크 테마는 확인하지 않았습니다.
- 캡처는 `/captures` 규칙상 Git에 포함하지 않습니다.

| 화면 | 변경 전 | 변경 후 |
| --- | --- | --- |
| 홈 카드·필터 칩 | [전](../../captures/knk-1430/home-before.png) | [후](../../captures/knk-1430/home-after.png) |
| 상세 히어로·CTA | [전](../../captures/knk-1430/detail-before.png) | [후](../../captures/knk-1430/detail-after.png) |
| 제작 카드 | [전](../../captures/knk-1430/studio-before.png) | [후](../../captures/knk-1430/studio-after.png) |

## 복구

UI와 표기만 바꿨고 저장 데이터·서버 변경은 없습니다. 되돌릴 때는 이 변경을 revert합니다.
