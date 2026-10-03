# KNK-1516 스토리 상세와 이미지 표시 정합성

## 범위와 근거

- 티켓: [KNK-1516](https://kimandkang.atlassian.net/browse/KNK-1516)
- Android 기준: `fa83a744`, 하네스 기준: `2d9e14c`. 두 저장소 모두 fetch한 `origin/dev`에서 분기했습니다.
- 계약: 하네스 `docs/spec/3-1-client-spec.md`의 스토리 상세 및 기본 심벌, `docs/spec/3-3-android-spec.md`의 플랫폼 계약.
- dev Swagger를 2026-10-03 확인했습니다. `StoryCharacterResponse.description`은 nullable string, `StoryDetailResponse.visibility`는 PUBLIC 또는 PRIVATE입니다.
- 홈 정렬 변경, 일반 제작과 수정 기능, 앱 링크는 제외합니다.

## 구현과 결정

1. 공용 인물 이미지 허용 목록에 uploaded 경로를 추가했습니다. 상세 뷰어, 확정 턴 마커, SSE 표시가 같은 판정을 사용합니다.
2. 인물 소개는 공백이면 생략하고, 공개 범위는 알려진 값만 소유자 메타에 표시합니다.
3. CTA는 "새 채팅 시작하기"로 변경하고 실패는 일회성 토스트로 안내합니다.
4. 삭제 성공은 제작 탭으로 이동합니다. 일반 뒤로가기와 채팅에서의 복귀는 기존 동작을 유지합니다.
5. 좋아요는 즉시 반영하고 실패 시 원래 상태와 수를 복원합니다. 요청 중 중복 전송과 응답 뒤 쿨다운을 유지합니다. 성공한 수만 홈의 같은 카드에 전달합니다. 상세와 홈에서 늦은 조회가 변경을 덮지 않게 처리합니다.
6. 공용 StoryCover에서 표지가 없으면 초안과 동일한 심벌 및 배경색을 사용합니다. 상세 헤더의 투명도는 표지 유무와 무관하게 스크롤로 전환하며, 표지 없는 경우 기본 전경색을 사용합니다. 별도 헤더 그라데이션은 제거했습니다.
7. 웹의 `useStoryFooterBackground`와 같이 스크롤 끝까지 남은 거리에 따라 CTA와 페이드 배경을 메타 정보 배경색으로 전환합니다. 메타 정보가 화면에 처음 들어오는 지점부터 스크롤 끝까지를 전환 범위로 삼아, 항목이 나타나는 순간 진행률이 뛰지 않도록 했습니다. 스크롤 위치로 계산한 진행률을 elementEnterMillis(200ms)의 선형 애니메이션으로 따라가 빠른 스크롤에서도 색상이 급격히 바뀌지 않게 합니다.

구조 결정은 하네스 [A-049](../../../knk-harness/docs/adr/1-3-android-adr.md#a-049)에 기록합니다.

## 검증

- 위험: failure-recovery, race-condition, state-restore.
- `:story:compileDebugKotlin`, `:home:compileDebugKotlin`, `:app:compileDebugKotlin` 통과.
- 변경 모듈 `common`, `designsystem`, `story`, `home`, `chat`, `app`의 `ktlintCheck`, `detekt` 통과.
- `:story:testDebugUnitTest` 32개, `:home:testDebugUnitTest` 16개, `:chat:testDebugUnitTest` 175개, 총 223개 통과.
- `checkModuleArchitecture` 통과: 17개 모듈, 422개 Kotlin 파일.
- `:app:installDebug` 통과. Pixel_10 API 37 에뮬레이터에서 실제 서버의 인물 소개 있음과 없음, 업로드 인물 이미지 및 뷰어, 소유자 공개 범위, CTA를 확인했습니다.
- 실제 좋아요 등록 후 홈 카드가 새로고침 없이 0에서 1로 바뀌고 취소 후 0으로 복원되는 것을 확인했습니다. 카드 순서와 위치도 유지했습니다.
- 표지가 없는 공개 스토리는 조회한 서버 목록에 없어 `PreviewActivity`로 상세의 기본 심벌과 투명 헤더를 라이트 및 다크 모드에서 확인했습니다. 히어로를 지난 뒤 불투명 헤더와 제목 표시도 확인했습니다. 미리보기는 앱 루트의 시스템 바 제어를 포함하지 않습니다.
- 테마 재생성에서 본문 제목이 화면 밖에 있을 때 헤더 제목이 사라지는 것을 발견해, LazyColumn이 히어로 항목을 지났는지도 제목 표시 조건에 포함했습니다. 변경 후 라이트 및 다크 전환에서 제목 유지 확인.
- 기기 테마는 `night no`, 검증용 좋아요는 원래 상태로 복원했습니다.
- 증거는 로컬 `captures/KNK-1516/`에 보관했습니다. 이 디렉터리는 Git 추적 대상이 아닙니다.
- 미검증: 실제 채팅 서버의 업로드 이미지 SSE 발생부터 확정 턴까지 이어지는 통합 흐름, 서버 스토리 삭제 후 이동, 네 종류 카드 전체의 표지 null 실데이터 화면. SSE 매핑과 확정 턴 및 스트리밍 파서는 단위 테스트로 검증했고, 카드 기본 심벌은 모든 사용처의 공용 StoryCover 변경으로 적용했습니다.
- 전체 `check`는 실행하지 않았습니다. 변경 모듈 검사와 설치에 필요한 debug 패키징만 수행했습니다.
- 하단 배경 후속 변경: `:story:compileDebugKotlin`, story 및 designsystem의 `ktlintCheck`와 `detekt`, `:app:installDebug` 통과. 에뮬레이터 스크린샷으로 라이트 및 다크 모드의 최하단 배경 일치와 다크 모드의 중간 전환 및 위쪽 복귀를 확인했습니다. 증거는 `footer-light.png`, `footer-dark.png`, `footer-mid.png`, `footer-top.png`, `footer-check.log`에 저장했습니다.

- 배경 전환 완화: story 컴파일, ktlintCheck, detekt 및 installDebug 재통과. 다크 모드에서 시작, 중간, 최하단 배경을 확인했고 원래 라이트 모드로 복원했습니다. 증거는 `footer-smooth-start.png`, `footer-smooth-mid.png`, `footer-smooth-end.png`, `footer-smooth-check.log`입니다.

## 복구

서버 계약이나 저장 스키마는 바꾸지 않습니다. 이 변경을 되돌리면 기존 표시와 좋아요 갱신 방식으로 돌아갑니다.
