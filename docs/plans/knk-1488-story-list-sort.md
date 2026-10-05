# KNK-1488 스토리 목록 정렬 개편

## 목표와 확인 근거

[KNK-1488](https://kimandkang.atlassian.net/browse/KNK-1488)에 따라 홈 목록 정렬을 인기순·최신순·좋아요순·채팅순 네 가지로 맞추고 기본 정렬을 인기순으로 바꿉니다.

- 분기 기준: fetch한 `origin/dev`의 `080c19f6`.
- 사용자 계약은 하네스 `origin/dev`의 [공통 spec 필터·정렬](../../../knk-harness/docs/spec/3-1-client-spec.md)과 [Android spec](../../../knk-harness/docs/spec/3-3-android-spec.md)(진입 기본값 전체·인기순, 선택은 홈 탭 수명 동안만 유지)을 따릅니다.
- 서버 `origin/dev`의 `GET /stories`가 `sort=popular|latest|likes|chats`를 받고 기본값이 `popular`임을 `StoryController.kt`·`StoryListCursor.kt`에서 확인했습니다. 인기 점수 계산은 서버가 소유하며 앱은 값을 그대로 보냅니다.

## 구현

| 항목 | 반영 내용 |
| --- | --- |
| 정렬 모델 | `StoryListSort`에 `POPULAR`를 맨 앞에 추가해 메뉴 순서를 인기순·최신순·좋아요순·채팅순으로 맞추고, `StoryListQuery` 기본값을 인기순으로 바꿨습니다. |
| 요청값 | `POPULAR`는 `popular`로 보냅니다. 정렬은 기존처럼 항상 명시합니다. |
| 문구 | 기존 `likes`의 "인기순" 표기를 "좋아요순"으로 바로잡고 `home_sort_popular`를 추가했습니다. |
| 페이징 | 정렬 변경 시 진행 중 요청 취소와 첫 페이지 재조회는 기존 `HomeViewModel.select`를 그대로 씁니다. |

## 검증 결과

- `./gradlew :home:ktlintCheck :home:detekt :home:testDebugUnitTest` 통과. 진입 시 `all`·`popular` 첫 페이지 요청, 정렬·필터 조합과 다음 페이지 커서 테스트를 포함합니다.
- 에뮬레이터 `installDebug` 후 홈 진입 시 "인기순"이 선택돼 있고, 메뉴가 인기순·최신순·좋아요순·채팅순 순서로 열리며, 좋아요순 선택 시 좋아요 수 내림차순 목록으로 바뀌는 것을 확인했습니다.
