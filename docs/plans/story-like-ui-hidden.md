# 스토리 좋아요 UI 비노출

- 티켓: [KNK-1322](https://kimandkang.atlassian.net/browse/KNK-1322)
- 작업일: 2026-09-17
- 상태: 구현·로컬 집중 검증 완료. 원격 PR·CI 결과는 연결된 Jira와 PR에서 확인합니다.
- 브랜치: 두 저장소 모두 `design/KNK-1322-hide-story-like-ui`
- 분기 기준: fetch한 `origin/dev` — Android `afa860a3`, 하네스 `7114010b`
- 계약: [공통 스토리 상세](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-003-스토리-상세), [Android 스토리 목록·상세](../../../knk-harness/docs/spec/3-3-android-spec.md#스토리-목록상세)

## 변경

1. 공통 `StoryThumbnail`의 좋아요 배지와 UI 전용 인자를 제거해 홈 카드·상세 히어로에 함께 적용했습니다. 턴 수 배지·ORIGINAL 태그는 유지합니다.
2. 상세 `StartChatCta`의 좋아요 버튼·콜백 연결을 제거했습니다. 채팅 시작 버튼이 가용 폭을 사용합니다.
3. 제작 `StoryMeta`의 좋아요 칩을 제거했습니다. 같은 컴포넌트를 쓰는 옵션 미리보기도 적용됩니다.
4. 좋아요 API·DTO·엔티티·ViewModel 토글 및 기존 토글 테스트는 유지했습니다. 상세 조회·복귀 시 서버 좋아요 데이터가 보존되고 토글 요청을 보내지 않는 단위 테스트를 추가했습니다.
5. 하네스 Android spec·design과 `DESIGN.md`의 현재 UI 서술을 맞췄습니다. 기존 ADR은 보존했습니다.

## 검증

다음 명령이 통과했습니다. 최초 테스트 ktlint 실패는 지역 변수로 상태를 받아 수정한 뒤 같은 검증을 재실행했습니다.

```bash
./gradlew \
  :story:ktlintCheck :story:detekt :story:testDebugUnitTest \
  :studio:ktlintCheck :studio:detekt :studio:testDebugUnitTest \
  :designsystem:ktlintCheck :designsystem:detekt :designsystem:testDebugUnitTest \
  :home:ktlintCheck :home:detekt :home:testDebugUnitTest \
  :app:installDebug
```

- 단위 테스트 59개 통과: story 25, studio 24, home 6, designsystem 4. 실패·오류·건너뜀 0개.
- 변경 모듈과 소비 모듈 컴파일·리소스 처리 및 Pixel_10(Android 17) 에뮬레이터 설치 성공.
- 두 저장소 `git diff --check` 통과. 전체 `check`는 로컬에서 반복하지 않았습니다.
- 변경 전후 APK를 각각 설치하고 기존 `PreviewActivity`의 홈·상세·제작 Preview를 같은 데이터로 실행했습니다. `adb exec-out screencap -p`로 캡처해 좋아요 네 곳 비노출, 턴 수·제작일 유지, 상세 CTA 폭 확장을 시각적으로 확인했습니다.
- 아래 스크린샷은 로컬 증거이며 기존 `.gitignore`의 `/captures` 규칙에 따라 Git에는 포함되지 않습니다. 기존 자동 스크린샷 테스트·golden 파일은 없습니다.

| 화면 | 변경 전 | 변경 후 |
| --- | --- | --- |
| 홈 카드 | [전](../../captures/knk-1322/home-before.png) | [후](../../captures/knk-1322/home-after.png) |
| 상세 히어로·CTA | [전](../../captures/knk-1322/detail-before.png) | [후](../../captures/knk-1322/detail-after.png) |
| 제작 카드 | [전](../../captures/knk-1322/studio-before.png) | [후](../../captures/knk-1322/studio-after.png) |

확인 범위는 샘플 데이터의 라이트 테마 Compose Preview입니다. 로그인 후 실제 서버 데이터·탭 이동·채팅 생성·옵션 미리보기·다크 테마는 수동 실행하지 않았습니다. `android layout`은 instrumentation server 연결 오류로 사용하지 못해 접근성 트리 검증은 하지 않았습니다.

## 복구

게시·공유 기능에서 좋아요 UI를 다시 제공할 때 유지한 API·ViewModel을 연결하고 이 변경의 UI diff를 복원합니다. 저장 데이터와 서버 변경은 없습니다. Android 로컬 `dev`의 기존 미푸시 커밋 `b204836e`는 해당 브랜치에 그대로 보존했습니다.
