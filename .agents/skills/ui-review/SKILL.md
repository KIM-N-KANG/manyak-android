---
name: ui-review
description: manyak-android의 화면 변경을 PR 전에 에뮬레이터에서 검사해 달라는 요청에 사용합니다. 예를 들어 "UI 검사해줘", "화면 확인해줘", "PR 전에 화면 점검해줘"에 적용합니다. debug 빌드를 에뮬레이터에 설치하고 android CLI로 캡처와 레이아웃 트리를 받아 라이트와 다크, 큰 글자, 회전, 접근성, 크래시를 확인한 뒤 보고서를 만듭니다. diff를 읽는 코드 리뷰는 pr-review 스킬이 맡습니다. 코드는 고치지 않고 보고만 합니다.
---

# UI 검사 (manyak-android)

PR 전 화면 검사입니다. 에뮬레이터에서 화면을 실행해서 보는 것만 맡고, 코드 규칙과 모듈 경계 같은 diff 리뷰는 pr-review 스킬이 맡습니다. 결과는 사람이 판단할 보고서이고, 머지를 막는 관문은 PR CI입니다.

## 원칙

- 보고만 합니다. 코드 수정은 사용자가 보고서를 보고 요청한 뒤 합니다.
- 기기 조작은 `android` CLI(설치, 캡처, 레이아웃)와 `adb`(탭, 기기 설정, 로그)로 합니다. 처음 쓰기 전에 android-cli 스킬을 읽습니다.
- 캡처와 레이아웃 덤프는 `build/ui-review/<브랜치 또는 키>/`(gitignore)에만 남기고 커밋하지 않습니다.
- 에뮬레이터는 사용자와 함께 쓰는 기기입니다. 로그인 상태와 앱 데이터를 지우지 않습니다. `adb uninstall`, `pm clear`, `:app:connectedDebugAndroidTest`(끝나면 앱을 지움)는 쓰지 않습니다. 소셜 로그인을 직접 하지 않으며, 로그아웃 상태면 회원 화면은 확인하지 못했다고 보고합니다.
- 바꾼 기기 설정(다크 모드, 글자 크기, 회전, 비행기 모드, 프록시)은 끝나기 전에 처음 값으로 되돌립니다. 시작할 때 처음 값을 적어 둡니다.

## 1. 대상 정하기

- `git diff --name-only origin/dev...HEAD`와 `git status --short`로 바뀐 파일을 모읍니다. `feature/<모듈>/src/main`의 `presentation`과 `res`를 화면 단위로 묶습니다.
- 여러 화면이 함께 쓰는 코드(`core/designsystem`, `app`의 `MainActivity`와 내비게이션 셸, 테마, `core/common`의 공용 화면 코드)가 바뀌었으면 핵심 화면도 넣습니다: 홈, 스토리 상세, 채팅방, 제작 탭, 마이.
- 데이터 계층이나 빌드 설정만 바뀌었으면 UI 변경이 아니므로 검사하지 않고 그 이유를 보고합니다.
- 이번 변경이 만든 로딩, 빈 상태, 오류 분기도 대상입니다. 재현에 기기 데이터 조작이 필요하면 방법만 적고, 재현하지 못했으면 확인하지 못한 것으로 둡니다.

## 2. 준비

1. 기기를 확인합니다: `adb devices`. 없으면 `android emulator list`로 AVD를 보고 `android emulator start <AVD>`로 켭니다(지금은 `Pixel_10`). 기기가 둘 이상이면 모든 명령에 serial을 붙입니다(`adb -s <serial>`, `android ... --device=<serial>`).
2. 처음 값을 적습니다: `adb shell cmd uimode night`, `adb shell settings get system font_scale`, `adb shell settings get system accelerometer_rotation`, `adb shell settings get system user_rotation`, `adb shell settings get global always_finish_activities`.
   - `always_finish_activities`가 1이면 "활동 유지 안 함"이 켜져 있어 회전이 프로세스 종료처럼 보입니다. 사용자에게 알리고 그 상태의 결과를 구성 변경 결과로 쓰지 않습니다.
3. 현재 브랜치를 설치합니다: `./gradlew :app:installDebug`. 덮어쓰기 설치라 로그인과 앱 데이터는 유지됩니다.
4. 앱을 엽니다: `adb shell am start -n app.manyak/.MainActivity`. 로그아웃 상태인지 첫 화면 캡처로 확인합니다.

## 3. 살펴보기

- 화면 이동은 레이아웃 트리에서 요소를 찾아 탭합니다.
  - `android layout --pretty -o build/ui-review/<키>/<이름>.json`으로 트리를 받고 text, content description, bounds로 대상을 고릅니다. 같은 글자가 여럿이면 정확히 일치하는 노드를 먼저 고릅니다.
  - 좌표는 `adb shell input tap <x> <y>`로 누릅니다. 글자로 찾기 어려우면 `android screen capture --annotate -o <파일>`로 번호를 붙여 보고, `android screen resolve --screenshot=<파일> --string="input tap #3"`으로 좌표를 받습니다.
  - `adb shell input text`는 한글 IME에서 영문이 자모로 바뀌므로 숫자만 넣습니다.
- 캡처는 `android screen capture -o build/ui-review/<키>/<이름>.png`로 남깁니다. 고해상도라 Read 전에 `sips -Z 800 <파일>`로 줄입니다. 존재나 로딩 확인에는 캡처 대신 레이아웃 트리를 씁니다.
- 확인 항목
  - 화면 표현: 잘림, 넘침, 겹침, 정렬과 간격, 말줄임, 시스템 바와 키보드 인셋, 하단 고정 요소와 스크롤 영역의 경계
  - 다크 모드: `adb shell cmd uimode night yes` 뒤 같은 화면을 다시 찍고 대비와 하드코딩 색을 봅니다. 끝나면 처음 값으로 되돌립니다.
  - 큰 글자: `adb shell settings put system font_scale 1.3`(필요하면 2.0) 뒤 잘림과 겹침을 봅니다.
  - 회전과 재생성: `adb shell settings put system accelerometer_rotation 0` 뒤 `user_rotation 1`과 `0`으로 돌려, 입력 중인 값, 스크롤 위치, 열린 시트가 유지되는지와 되돌리기, 초기화, 저장 효과가 다시 실행되지 않는지 봅니다. 기준은 configuration-changes 스킬입니다.
  - 접근성: 레이아웃 트리에서 클릭할 수 있는데 text와 content description이 모두 없는 요소, bounds가 48dp(밀도 기준 픽셀로 환산)보다 작은 터치 대상, 장식인데 낭독되는 요소를 찾습니다.
  - 상호작용: 바뀐 버튼, 시트, 다이얼로그를 직접 열고 닫습니다. 시스템 뒤로가기(`adb shell input keyevent KEYCODE_BACK`)로 닫히는지, 잠긴 시트는 닫히지 않는지 봅니다.
  - 크래시와 오류: 검사 전 `adb logcat -c`, 끝난 뒤 `adb logcat -d -b crash`와 `adb logcat -d --pid=$(adb shell pidof app.manyak) '*:E'`로 앱 오류를 봅니다.
- 변경 전과 비교는 사용자가 요청했을 때만 합니다. 기본 체크아웃의 HEAD는 옮기지 않습니다.
  1. `git worktree add --detach /tmp/ui-review-base origin/dev`에서 `./gradlew :app:assembleDebug`로 기준 APK를 만듭니다. `local.properties`는 기본 체크아웃의 것을 복사합니다.
  2. `adb install -r -d <기준 APK>`로 바꿔 같은 화면을 찍고, 현재 브랜치에서 다시 `./gradlew :app:installDebug`로 되돌립니다.
  3. 끝나면 `git worktree remove /tmp/ui-review-base`로 지웁니다. 이 단계에서 DB 버전이 내려가면 앱이 시작하지 못할 수 있으므로, 브랜치가 Room 스키마를 바꿨으면 비교하지 않고 그 이유를 보고합니다.

## 4. 원인 위치 짚기

- 찾은 문제마다 원인이 되는 컴포저블, 토큰, 리소스를 찾아 `파일:줄`로 적습니다. diff 전체를 규칙대로 훑는 리뷰는 하지 않습니다.
- 고칠 방법을 제안할 때 인셋과 시스템 바는 edge-to-edge 스킬, 창 크기와 상태 보존은 adaptive 스킬, 재구성과 버벅임은 jetpack-compose-performance 스킬을 참고합니다.

## 5. 정리

- 2번에서 적은 처음 값으로 다크 모드, 글자 크기, 회전을 되돌리고 값을 다시 읽어 확인합니다.
- 비교용 worktree를 만들었으면 지웠는지 확인합니다. 직접 켠 에뮬레이터는 켜 둔 채로 둡니다.

## 6. 보고

```markdown
## UI 검사 결과

- 대상: 화면 목록, 기기(AVD, API, 해상도), 설치한 커밋, 로그인 상태, 실행한 단계(라이트, 다크, 큰 글자, 회전, 접근성, 비교)
- 문제: 심각도(높음, 보통, 낮음)별로 화면, 증거(캡처나 레이아웃 경로, 명령 출력), 원인 위치(`파일:줄`), 제안
- 확인하지 못한 것: 건너뛴 단계와 이유(로그아웃 상태, 재현하지 못한 분기, 비교 생략 등)
- 기기 설정 복구: 되돌린 값
```

- 문제가 없으면 무엇을 확인했는지만 짧게 적습니다. 확인하지 않은 항목을 통과로 쓰지 않습니다.
- 캡처와 레이아웃 경로는 저장소 기준으로 적어 호출한 쪽이 Read로 바로 열 수 있게 합니다.
- PR 본문 화면 절에는 대표 캡처와 확인 결과를 옮깁니다. 이미지 첨부는 create-pr 스킬의 절차를 따릅니다.
