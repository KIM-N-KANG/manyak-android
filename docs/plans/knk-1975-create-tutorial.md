# 간편 제작 첫 진입 안내 (KNK-1975)

- 작성일: 2026-11-02
- 티켓: [KNK-1975](https://kimandkang.atlassian.net/browse/KNK-1975) (상위 KNK-1114)
- 브랜치: Android `feat/KNK-1975-android-create-tutorial`(fetch한 `origin/dev` `3a77e5d3`에서 분기), 하네스 같은 이름(KNK-1974 병합 뒤 fetch한 `origin/dev` `16210d7` 기준)
- 계약: 하네스 [공통 제작 안내 흐름](../../../knk-harness/docs/domain/STORY/CLIENT/3-2-INTERACTION-FLOWS.md#3-2-6-제작-안내), [공통 문구](../../../knk-harness/docs/domain/STORY/CLIENT/3-3-LAYOUT.md#3-3-7-제작-안내), [Android 요구](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/2-2-REQUIREMENTS.md#2-2-4-간편-제작-첫-진입-안내), 결정 C-016과 A-088. 하네스 계약은 KNK-1975 하네스 PR이 병합되기 전까지 그 브랜치에만 있다.
- 참고 구현: 웹 `src/features/stories/_shared/`의 `constants/create-tutorial.ts`, `hooks/use-create-tutorial.ts`, `utils/create-tutorial-storage.ts`, `components/create-tutorial-dialog.tsx`, `utils/draw-tutorial-mascot-stage.ts`

## 변경

1. `common`에 단계(`CreateTutorialStage`, 기록 값은 웹과 같은 문자열과 `done`), 기록 계약(`CreateTutorialProgress`)과 절차(`CreateTutorialController`)를 둡니다. 절차는 다음 단계를 처음 한 번 읽고, 화면 조건이 그 단계와 맞으면 200ms 뒤 열며, 버튼으로 닫을 때만 기록합니다. 시작하기는 대화창을 닫지 않고 키워드 단계로 잇습니다.
2. `create`의 `CreateTutorialStore`가 기기 DataStore(`device_id`와 같은 파일)의 `create_tutorial_stage` 키로 계약을 구현합니다. 쓰기는 `NonCancellable`이라 닫자마자 화면을 떠나도 남습니다.
3. 키워드, 스토리라인, 추가 정보 ViewModel과 제작 탭 ViewModel이 각자 `tutorialScreen`(안내를 받을 수 있는 화면 조건)으로 절차를 만들어 `tutorial`로 엽니다. 열린 단계는 화면 상태가 아니라 절차의 `StateFlow`라 기존 의도, 사건과 함수 수를 늘리지 않습니다. 화면은 STARTED 동안 `drive()`를 돌리고 버튼을 절차에 바로 전달합니다.
4. 스토리라인 문구의 장르 이름은 열기 직전에 퍼널의 태그 목록(`tagCatalog`, 키워드 단계가 넘긴 캐시)으로 풉니다. 추가 정보 초안을 열어 스토리라인을 거쳐 넘어가는 경우에는 거쳐 가는 화면에서 열지 않습니다.
5. 대화창(`designsystem`의 `CreateTutorialDialog`)은 다이얼로그 창이라 하단 탭까지 딤으로 덮습니다. 바깥 탭은 무시하고 뒤로가기는 보조 버튼, 없으면 주 버튼과 같습니다. 모양은 웹 카드에 맞춰 창 딤을 검정 50%(`tourScrim`)로 바꾸고, 좌우 12dp만 남긴 폭, `surface` 배경, 다크 전용 경계(새 토큰 `borderOverlay`), 창 전환 대신 250ms 0.95배 등장을 씁니다. 퍼널은 단계와 무관하게 한 호출 자리에서 대화창을 그려 시작하기에서 창이 닫혔다 열리지 않고 문구만 바뀝니다. 프로필의 말하는 마스코트는 웹과 같은 안무 값(5초 루프, 폭 66%, 3.5% 도약, 3도 기울기)을 공용 `Choreography`로 옮겼습니다. 토큰 `sizes.mascotAvatar`(48dp)를 더하고 `DESIGN.md`에 `create-tutorial-dialog`를 적었습니다.
6. 제작 탭의 "오리지널 스토리 보기"는 안내를 마친 뒤 하단 탭처럼 홈 탭으로 바꿉니다(`MainTabs`의 `onLeaveTab`).
7. 분석은 웹과 같은 `client_storyCreate_tutorialStep_viewed`, `client_storyCreate_tutorialSkipButton_clicked`, `client_storyCreate_tutorial_completed` 세 개입니다.

웹과 다른 점: 조작 없이 열린 대화창도 뒤로가기는 대화창만 닫습니다(웹은 더미 히스토리가 없어 화면을 떠남). 딤은 플랫폼 딤이라 짧게 페이드됩니다. 제작 탭 단계는 완성 중(PENDING) 카드가 있을 때만 엽니다.

## 검증

```bash
./gradlew --continue \
  :common:ktlintCheck :common:detekt :common:testDebugUnitTest \
  :designsystem:ktlintCheck :designsystem:detekt \
  :analytics:ktlintCheck :analytics:detekt :analytics:testDebugUnitTest \
  :create:ktlintCheck :create:detekt :create:testDebugUnitTest :create:lintDebug \
  :studio:ktlintCheck :studio:detekt :studio:testDebugUnitTest :studio:lintDebug \
  :app:ktlintCheck :app:detekt :app:compileDebugAndroidTestKotlin checkModuleArchitecture
./gradlew installDebug
```

- 모두 통과했습니다. 새 테스트는 `CreateTutorialControllerTest`(기록 해석, 지연 뒤 열기와 시작하기 연결, 건너뛰기, 뒤 화면에서 앞 단계 미표시, 지연 중 조건 변화, 마지막 단계 기록 6건), `EuroParticleTest`, `StudioViewModelTest`의 완성 중 단계 1건, `AnalyticsEventContractTest`의 안내 이벤트입니다.
- emulator-5554(dev 서버, 로그인 계정)에서 `adb exec-out screencap`으로 확인했습니다. 간편 제작 진입 200ms 뒤 1단계 대화창이 키워드 화면 가운데에 떴고, 시작하기로 대화창을 유지한 채 2단계로 바뀌었습니다. 시스템 뒤로가기는 퍼널을 떠나지 않고 대화창만 닫았고, 퍼널을 닫았다가 다시 들어가면 안내가 열리지 않았습니다.
- 3단계 이후(실제 스토리라인 생성과 완성 제출), 회전, 다크 모드와 애니메이터 배율 0은 기기에서 보지 않았고 코드와 유닛 테스트로만 판단했습니다. 확인 뒤 이 에뮬레이터의 기록은 `keyword`로 남아 있어 다음 스토리라인 결과에서 3단계가 열립니다.
- 캡처는 `/captures` 규칙상 Git에 포함하지 않습니다.

| 화면 | 캡처 |
| --- | --- |
| 1단계 인사 | 보관 경로: `../../captures/knk-1975/tutorial-intro.png` |
| 2단계 키워드 | 보관 경로: `../../captures/knk-1975/tutorial-keyword.png` |

## 시작하기 깜빡임과 웹 모양 맞춤

- 처음 구현은 단계마다 `CreateTutorialDialog`를 다른 `when` 분기에서 불러, 시작하기를 누르면 인사 창이 닫히고 키워드 창이 새로 열렸습니다. 녹화 프레임에서 약 0.1초 동안 딤이 옅어지고 두 카드가 겹쳤습니다(보관 경로: `../../captures/knk-1975/tutorial-start-flicker.png`).
- 한 호출 자리로 바꾼 뒤 같은 녹화에서 딤과 카드가 유지되고 문구만 교차 페이드됐습니다(보관 경로: `../../captures/knk-1975/tutorial-start-no-flicker.png`).
- 웹 모양으로 바꾼 1단계(보관 경로: `../../captures/knk-1975/tutorial-intro-web-layout.png`)와, 2단계가 열린 채 다크 모드로 바꿨을 때 같은 단계가 유지되고 경계가 보이는 화면(보관 경로: `../../captures/knk-1975/tutorial-keyword-dark.png`)을 emulator-5554에서 확인했습니다.
- 재현을 위해 기기 DataStore 파일을 백업한 뒤 `create_tutorial_stage` 키만 지웠고, 확인 뒤 기록은 다시 `keyword`입니다.

## 복구

기기 설정 키 `create_tutorial_stage` 하나를 더했고 서버 변경은 없습니다. 되돌릴 때는 이 변경을 revert하며 남은 키는 읽는 곳이 없어 무해합니다.
