# 시트 닫기·메뉴 규격과 선택 행 리플

- 티켓: [KNK-1324](https://kimandkang.atlassian.net/browse/KNK-1324)
- 작업일: 2026-09-17
- 브랜치: Android·하네스 모두 `design/KNK-1324-align-sheet-controls`
- 분기 기준: fetch한 `origin/dev` — Android `984e910d`, 하네스 `fed16a42`
- 표현 규칙: [DESIGN.md](../../DESIGN.md#컴포넌트)
- 계약: [Android spec](../../../knk-harness/docs/spec/3-3-android-spec.md), [공통 마이·계정](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-008-로그인마이-페이지)

## 변경 순서와 범위

1. 기존 `CLAUDE.md`·`README.md`·`_project.md` 변경을 그대로 새 브랜치에 가져왔습니다.
2. 신고·초대 온보딩·광고 알림 동의 시트의 닫기를 기존 `ManyakTextButton`으로 통일했습니다. 전체 폭·최소 48dp·`labelLarge`·보조색이며 제출 중 비활성을 유지합니다.
3. 선택 키워드 시트에는 닫기 버튼이 없어 같은 규격으로 추가했습니다. 기존 `DismissSelectedKeywords`로 연결해 조회 취소·시트 숨김만 수행합니다. 실패 상태의 작은 버튼은 닫기가 아닌 재시도이므로 유지합니다.
4. `MyMenuItem`의 왼쪽 아이콘을 기존 `sizes.icon`(20dp), 간격을 `spacing.component`(12dp)로 맞췄습니다. trailing은 16dp를 유지합니다.
5. 신고 `selectable`과 탈퇴 `toggleable`에 `interactionSource = null`, `indication = null`을 추가했습니다. 행 터치·접근성 역할·선택 상태와 기존 전환 표현은 유지합니다.
6. `DESIGN.md`에 닫기·메뉴·선택 행 규칙을 반영하고 하네스 Android design에는 정본 링크를 연결했습니다.

채팅 설정 시트는 [KNK-1317](https://kimandkang.atlassian.net/browse/KNK-1317), 채팅 메뉴·카드 옵션 시트 전환은 [KNK-1320](https://kimandkang.atlassian.net/browse/KNK-1320)의 별도 구현입니다. 현재 Android에는 해당 시트가 없으므로 이번에 새 기능을 만들지 않았습니다. 해당 티켓 구현 시 `DESIGN.md`의 닫기·메뉴 규격과 스위치 행 리플 제거를 함께 적용해야 합니다. 알림 설정의 스위치 단독 터치 계약과 퍼널 이탈 확인은 유지합니다.

사용자 화면 검토 후 닫기 글자는 위의 주 동작 버튼과 같은 `labelLarge`로 조정했습니다. 높이·전체 폭·모서리는 그대로 맞추고 보조색 텍스트 표현을 유지합니다. 티켓의 본문 글자 지정은 이 후속 요청으로 대체합니다. 이어진 간격 요청에 따라 신고·초대·광고 동의 시트의 주 버튼과 닫기 사이는 `spacing.compact`(8dp)로 맞췄습니다. 선택 키워드 시트는 위에 주 버튼이 없어 본문 간격을 유지합니다.

## 검증 결과

다음 명령이 통과했습니다.

```bash
./gradlew \
  :report:compileDebugKotlin :report:ktlintCheck :report:detekt \
  :my:compileDebugKotlin :my:ktlintCheck :my:detekt \
  :create:compileDebugKotlin :create:ktlintCheck :create:detekt \
  :notification:compileDebugKotlin :notification:ktlintCheck :notification:detekt
./gradlew :app:installDebug
```

- Pixel_10(Android 17)에 설치하고 기존 Compose Preview 6개를 비교했습니다. 변경 전은 기존 설치 APK, 변경 후는 이번 소스로 빌드한 APK입니다.
- 시트 4곳의 닫기 표시와 마이 아이콘·간격 변경을 확인했습니다. 탈퇴 확인 화면의 정적 배치는 동일합니다.
- `uiautomator`에서 신고의 RadioButton 역할 3개와 선택 상태, 탈퇴의 Checkbox 역할 4개 및 행 전체의 체크·클릭 상태를 확인했습니다. 클래스와 체크 상태가 부모·자식 노드로 나뉘므로 체크 가능한 행을 기준으로 검증했습니다.
- 리플 제거는 `indication = null` 코드와 컴파일로 확인했습니다. 눌림 애니메이션의 동영상 비교, TalkBack 실제 낭독, 실제 계정의 제출·탈퇴·동의 요청, 다크 모드·큰 글자는 실행하지 않았습니다. Preview 콜백은 빈 함수이므로 실제 상태 전환 검증으로 보지 않습니다.
- 관련 기존 UI 자동화 테스트는 없습니다. 로직 변경 없이 Compose 속성·버튼 연결만 조정했으므로 단위 테스트·전체 `check`는 추가 실행하지 않았습니다.
- 두 저장소 `git diff --check`와 추가한 문서 링크·문자열 XML을 확인했습니다.

후속 글자 조정 후 위의 모듈 컴파일·ktlint·detekt와 `:app:installDebug`를 다시 통과했습니다. 신고 시트 Preview에서 두 버튼의 글자 크기·굵기 일치를 확인했습니다([조정 전](../../captures/knk-1324/report-typography-before.png), [최종 화면](../../captures/knk-1324/report-typography-after.png)). 간격 조정 후 report·my·notification 모듈의 컴파일·ktlint·detekt와 `:app:installDebug`를 다시 통과했고, 신고 시트의 8dp 간격을 [최종 화면](../../captures/knk-1324/report-gap-after.png)에서 확인했습니다. 아래 표는 최초 구현 시점의 비교입니다.

| 화면 | 변경 전 | 변경 후 |
| --- | --- | --- |
| 신고 | [전](../../captures/knk-1324/report-before.png) | [후](../../captures/knk-1324/report-after.png) |
| 초대 | [전](../../captures/knk-1324/invite-before.png) | [후](../../captures/knk-1324/invite-after.png) |
| 광고 동의 | [전](../../captures/knk-1324/marketing-before.png) | [후](../../captures/knk-1324/marketing-after.png) |
| 선택 키워드 | [전](../../captures/knk-1324/keywords-before.png) | [후](../../captures/knk-1324/keywords-after.png) |
| 마이 | [전](../../captures/knk-1324/my-before.png) | [후](../../captures/knk-1324/my-after.png) |
| 탈퇴 확인 | [전](../../captures/knk-1324/withdrawal-before.png) | [후](../../captures/knk-1324/withdrawal-after.png) |

캡처와 접근성 XML은 기존 `/captures` ignore 규칙에 따른 로컬 증거이며 Git에는 포함하지 않습니다.

## 복구

이번 UI·리소스·디자인 문서 diff만 되돌리면 됩니다. 기존 문서 3개 변경은 보존합니다. 서버·저장 데이터·API 계약 변경은 없습니다. 로컬 커밋 범위에는 기존 문서 3개 변경도 포함합니다. 푸시·PR은 수행하지 않았고 Jira는 진행 중입니다.
