# KNK-1472 간편 제작 화풍 선택

## 범위와 정본

간편 제작 추가 정보 단계에 필수 "이미지 스타일" 선택을 더하고, 고른 화풍을 완성 요청 `artStyle`, 임시 저장, 재개에 연결합니다. 추가 정보 입력 행은 처음 1개로 줄이고 섹션 간격을 24dp로 맞춥니다. 공개 스위치는 2026-10-14 결정으로 넣지 않습니다.

- 사용자 계약: [공통 추가 정보 진입](../../../knk-harness/docs/spec/3-1-client-spec.md#추가-정보-진입스토리라인-재선택), [추가 정보 (퍼널)](../../../knk-harness/docs/spec/3-1-client-spec.md#추가-정보-퍼널)
- 현재 구조: [Android design 화풍 선택](../../../knk-harness/docs/design/1-2-android-design.md#화풍-선택)
- 결정: [A-072 저장 형식과 재생성 유지](../../../knk-harness/docs/adr/1-3-android-adr.md#a-072-간편-제작-화풍을-기존-초안-json에-더하고-스토리라인을-다시-만들어도-남기기)
- 웹 기준: [manyak-web#265](https://github.com/KIM-N-KANG/manyak-web/pull/265), [manyak-web#266](https://github.com/KIM-N-KANG/manyak-web/pull/266)
- API 근거: 2026-10-14 dev Swagger의 `GET /api/v1/stories/art-styles`(`ArtStyleCatalogItem`)와 `CreateSimpleStoryRequest.artStyle`(누락·null은 ANIME, 미등록·비활성·빈 문자열은 400). 실제 응답은 `NONE`부터 11개이고 `NONE`만 미리보기가 null입니다. 하네스 백엔드 스펙의 "서버 미구현" 문구는 서버 dev `52fcae5`에 이미 구현되어 있어 지웠습니다.

## 구현 순서와 결과

1. `ArtStyle` entity, `SimpleStoryApi.artStyles`, `StoryCreationRepository.artStyles`를 추가했습니다. 응답의 `description`은 화면에 쓰지 않아 받지 않습니다.
2. `StoryCompletionCommand`와 `CreationProgress`에 선택 필드 `artStyle`을 더하고 요청 DTO와 저장 JSON(`CompletionCommandDto`, `ProgressDto`)에 실었습니다. Room 컬럼과 버전은 그대로입니다.
3. `CreateAdditionalInfoViewModel`이 화면 진입 시 목록을 받고(Job 하나로 재시도 중복 방지), 선택·미선택 안내·조회 실패를 상태로 둡니다. 미선택 완성은 요청 없이 안내를 띄우고, 화풍을 고르면 내립니다. requestId 재사용 조건에 화풍을 더했습니다. 늘어난 분기는 `ArtStyleEvent` 묶음으로 나눠 reduce 복잡도 상한을 지켰습니다.
4. 진행 미러에 화풍을 더했습니다. "다시 선택하기"와 스토리라인 재생성 결과는 화풍을 지우지 않고, 재선택 경고 판정에도 넣지 않습니다.
5. `ArtStyleSection`을 추가했습니다. 스크롤 목록 안이라 세 칸씩 나눈 줄로 그리고, 칸은 `StoryCover`와 `StoryOverlayScrim` 이름 필드, RadioButton 시맨틱을 씁니다. 스토리 상세 인물 썸네일에만 있던 바깥 선택 테두리를 두 번째 사용처가 생겨 `designsystem`의 `selectionRing`으로 올리고 두 곳에서 씁니다.
6. 푸터의 비용 행과 CTA 사이에 미선택 안내를 두고, 추천 섹션 위 여백을 32dp에서 24dp로, 초기 입력 행을 3개에서 1개로 바꿨습니다.
7. 화풍 목록 조회 실패는 키워드 단계의 `TagsLoadFailure`에 제목만 넘겨 같은 모양(가운데 정렬 제목과 안내, 아래 "다시 불러오기" 버튼)으로 그립니다. 웹은 한 줄 안내와 오른쪽 "다시 시도하기" 버튼이라 이 자리는 웹과 다릅니다.

## 검증

- `:create:ktlintCheck`, `:create:detekt`, `:create:testDebugUnitTest`, `:designsystem:ktlintCheck`, `:designsystem:detekt`, `:story:ktlintCheck`, `:story:detekt`, `:story:compileDebugKotlin`, `:create:compileDebugAndroidTestKotlin`: 통과. 첫 실행에서 reduce 복잡도와 줄 길이 검사가 실패해 수정 뒤 재실행했습니다.
- 단위 테스트: 처음 미선택과 입력 1칸, 미선택 완성의 요청 없음과 안내 해제, 제출값 `artStyle`, 화풍을 바꾼 재시도의 새 requestId, 조회 실패와 다시 시도, 복원 스냅숏이 먼저 받은 목록을 덮지 않음, 임시 저장과 재개의 화풍 복원, 화풍만 고른 재선택의 경고 없음과 화풍 유지, 재생성 결과의 화풍 유지, 화풍 이전 초안 JSON의 null 해석을 추가·갱신했습니다.
- 에뮬레이터(Pixel_10 API 37, `adb exec-out screencap`):
  - 이전 빌드(dev `1ee5c333`)에서 입력 3칸, 화풍 섹션 없음 상태로 추가 정보 초안을 저장한 뒤 새 빌드를 설치해 같은 초안을 재개했습니다. 저장된 3칸은 그대로, 화풍은 미선택으로 열렸고 추천 섹션 위 여백이 줄었습니다.
  - 서버 순서 11칸, `NONE`의 기본 심벌, 미선택 완성의 푸터 안내와 요청 없음, 선택 시 안내 해제와 brand 테두리, 회전 후 선택 유지, 임시 저장 뒤 재개의 선택 복원을 확인했습니다. 선택 칸은 접근성 트리에서 checked 라디오로 노출됩니다.
  - 비행기 모드 재개로 조회 실패 안내와 다시 시도를, 응답 없는 호스트 프록시로 골격 칸 6개를 확인했습니다. 프록시 설정 세 키는 지우고 Wi-Fi 검증 상태를 확인했습니다.
  - 새 퍼널은 입력 1칸으로 시작했습니다.
  - dev 실제 완성 2건(각 250 이프): `NONE`은 저장 명령에 `"artStyle":"NONE"`이 실렸고 완성 스토리에 표지와 인물 이미지가 없었습니다. `INK_WASH`는 `"artStyle":"INK_WASH"`가 실렸고 수묵 표지로 완성됐습니다.
- UI 캡처: `/tmp/knk1472/before-additional-info.png`, `after-art-style-unselected.png`, `after-required-error.png`, `after-selected.png`, `after-load-failed.png`, `after-loading-skeleton.png`, `after-fresh-additional-info.png`, `after-inkwash-detail.png`.

## 표지 위 뱃지의 웹 정렬

사용자 요청으로 화풍 이름 뱃지와 함께 모든 표지 위 뱃지를 웹과 같은 배경 흐림으로 바꿨습니다. 결정: [A-073](../../../knk-harness/docs/adr/1-3-android-adr.md#a-073-표지-위-뱃지에-웹과-같은-배경-흐림을-graphicslayer로-적용하기).

1. `designsystem`에 `CoverBackdrop`(흐림 원천), `coverBackdropSource`, `coverBackdrop`, `originalTagBackdrop`을 추가했습니다. `StoryCover`는 표지 그림만 원천으로 기록하고 겹침 요소에 `CoverOverlayScope.backdrop`을 넘깁니다. API 31 미만은 원천이 없어 기존 #111414 70%를 깝니다.
2. 목록 카드의 좋아요 수와 턴 수 뱃지, 화풍 이름, 상세 인물 카드의 이전·다음 버튼은 흐림 위에 검정 20%를, ORIGINAL 태그는 도안에서 뺀 #111414 20% 바탕을 같은 방식으로 깝니다. 상세 히어로 뱃지도 사용자 요청으로 같은 흐린 바탕을 씁니다(웹 상세는 흐림 없는 검정 70%).
3. 턴 수 아이콘 `ic_dialog`를 웹과 같은 Hugeicons BubbleChat으로 바꿨습니다. 내 스토리 카드의 턴 수도 같은 아이콘을 씁니다.

검증:

- `:designsystem`, `:home`, `:story`, `:create`의 `ktlintCheck`와 `detekt`, `:studio`, `:chat`, `:home`, `:story`, `:create`의 `compileDebugKotlin`: 통과. 첫 실행에서 RowScope 안의 암묵 receiver 사용이 컴파일 오류였고, 값을 미리 꺼내 고쳤습니다.
- 에뮬레이터에서 홈 카드 뱃지와 ORIGINAL 태그, 상세 히어로, 상세 인물 카드 버튼, 화풍 그리드를 캡처하고, Aside로 같은 카드를 띄운 웹(manyak.app) 캡처와 나란히 비교했습니다. 뱃지 바탕의 흐림과 그늘, 태그 바탕, 턴 수 아이콘이 웹과 같은 결로 보입니다.
- 화풍 그리드 스크롤을 흐림을 끈 빌드와 켠 빌드로 세 번씩 측정해 끊긴 프레임 4.3%, 99번째 백분위 19~20ms로 같았습니다. 설치 직후 첫 측정만 8.4%로 높았고 이후 측정에서 재현되지 않았습니다.
- API 31 미만의 대체 바탕은 해당 이미지의 에뮬레이터가 없어 코드로만 확인했습니다.
- UI 캡처: `/tmp/knk1472/web-home.png`, `badge-after-home2.png`, `hero-after-bright.png`, `badge-after-characters.png`, `badge-final-art-style.png`.

## 남은 일

- 임시 저장한 화풍이 그 사이 비활성화되면 아무 칸도 선택되지 않은 것처럼 보이지만 저장된 code로 완성이 요청되어 400으로 실패하고, 실패 카드의 재시도도 같은 명령이라 다시 실패합니다. 웹과 같이 별도 처리하지 않았습니다.
