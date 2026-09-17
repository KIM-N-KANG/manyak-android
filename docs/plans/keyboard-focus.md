# 공통 입력란 포커스와 키보드 규칙

## 원인과 변경

- 공통 `clearFocusOnTap`이 Initial 패스에서 입력란 터치까지 포커스를 해제했습니다. 앱 루트·공통 시트·입력 다이얼로그에서 입력란 밖의 탭만 처리하도록 바꿉니다. 화면별 중복 처리는 제거합니다.
- 입력란 이동·재터치(라벨·여백 포함)는 키보드를 유지하고, 빈 영역·버튼·칩의 탭은 키보드를 닫습니다. 스크롤·드래그·다중 터치는 포커스를 유지합니다. 입력란의 `keepKeyboardOnTap`이 조상에게 입력 영역임을 알리고, 루트가 자식 동작 후 Final 패스에서 판정합니다. 이벤트를 소비하지 않으므로 버튼 동작과 스크롤을 막지 않습니다. 공통 단일행·다중행 입력, 채팅 작성란, 초대 코드 입력의 모든 `BasicTextField` 정의에 적용합니다.
- 시트·다이얼로그는 별도 창이므로 modifier node에서 해당 창의 `LocalFocusManager`를 읽습니다. 포커스 영역 좌표는 커서·선택 영역으로 좁아질 수 있어 입력란의 터치 영역 판정에 사용하지 않습니다.
- 기존 BOM은 2026.02.01이지만 최종 앱의 Compose UI·Foundation은 AboutLibraries 등의 전이 의존성으로 1.12.0이었습니다. 모듈의 컴파일 버전만 보면 실행 버전을 잘못 판단할 수 있어 `:app:dependencyInsight`의 `debugRuntimeClasspath`로 확인했습니다.
- 1.12.0의 `isOutOfFrameSchedulerForTextInputEventsEnabled`가 입력 종료 명령을 메인 큐 앞에 넣어, 다음 입력 시작 전에 키보드 숨김이 실행됐습니다. [AndroidX 수정](https://android.googlesource.com/platform/frameworks/support/+/9d039d835daa553abea4ff30ccf569219a6c2fc6)은 이 플래그를 1.12.1에서 비활성화합니다. BOM을 2026.09.00(UI·Foundation 1.12.1)으로 맞추어 공식 수정을 적용합니다. 앱의 별도 플래그·지연·강제 키보드 표시 코드는 두지 않습니다.
- 입력 값·커서 동기화·화면 상태·서버 요청은 바꾸지 않습니다. 포커스 동작은 [디자인 규칙](../../DESIGN.md#컴포넌트), 구성 변경은 [Android 계약](../../../knk-harness/docs/spec/3-3-android-spec.md#3-3-4-디바이스접근성상태-복원)을 따릅니다.

## 검증

- 변경 전 Pixel_10(Android 17)에서 채팅 상황↔대사, 인물 이름, 추가 정보, 피드백 본문↔이메일의 `onRequestHide → onHidden → onShown`을 확인했습니다. 인물 이름과 추가 정보는 같은 필드 재터치에서도 발생했습니다.
- `KeyboardFocusTest`는 값 기반·상태 기반 입력란의 양방향 전환과 재터치에서 키보드 숨김 로그가 없는지, 빈 영역·버튼 터치가 포커스와 키보드를 닫는지, 스크롤이 동작하며 포커스를 유지하는지 검사합니다. 실제 공통 시트와 입력 다이얼로그의 별도 창에서도 재터치·바깥 버튼 동작을 검사합니다.
- 컴파일·실행 classpath 모두 Compose UI 1.12.1로 일치합니다. Google Maven의 1.12.1 소스에서도 해당 스케줄러 플래그의 기본값이 `false`임을 확인했습니다.
- `:app:assembleDebugAndroidTest :app:installDebug`와 변경 모듈(`designsystem`, `app`, `chat`, `create`, `my`)의 `ktlintCheck`·`detekt`가 통과했습니다. 설치 작업이 전체 앱과 소비 모듈을 컴파일했습니다. 직접 실행한 `KeyboardFocusTest` 다섯 테스트가 모두 통과했습니다.
- 시트는 `ModalBottomSheet`에 넘기는 외부 modifier에서 터치가 관찰되지 않아, 실제 콘텐츠와 드래그 핸들에 처리를 연결했습니다. 별도 창 회귀 테스트가 이 누락을 검출했고 수정 후 통과했습니다.
- 최초 `connectedDebugAndroidTest` 실행에서 대상 앱이 제거되어 기존 로그인·로컬 상태가 초기화됐고, 이 부작용을 사용자에게 알렸습니다. 이후에는 `:app:assembleDebugAndroidTest :app:installDebug`, 테스트 APK의 `adb install -r`, `adb shell am instrument -w -e class app.manyak.KeyboardFocusTest app.manyak.test/androidx.test.runner.AndroidJUnitRunner`를 사용합니다. 앱 제거·데이터 초기화는 실행하지 않습니다.
- 설치한 앱의 실행형 미리보기에서 채팅·피드백·주변 인물 이름·추가 정보의 전환→재터치→역방향 전환을 검증했습니다. 모든 단계에서 대상 입력란의 포커스를 확인했고 `onRequestHide`는 0건이었습니다. 인물 이름·추가 정보는 스크롤 후에도 포커스를 유지했습니다. 신고·친구 초대·초대 안내의 단일 입력란 재터치도 숨김 요청 없이 통과했습니다.
- 공통 규칙 확장 후 신고 시트에서 입력란 포커스·키보드 표시 → 바깥 신고 사유 행 터치 → 포커스 해제·`onHidden`을 실제 화면으로 확인했습니다. [키보드 표시](../../captures/keyboard-audit/uniform-report-focused.png)·[바깥 탭 후](../../captures/keyboard-audit/uniform-report-outside.png)를 보존했습니다.
- 채팅 [수정 전 로그](../../captures/keyboard-audit/chat-switch-dialogue.log)·[수정 후 로그](../../captures/keyboard-audit/fixed-chat-switch.log)·[수정 후 화면](../../captures/keyboard-audit/fixed-chat-switch.png)을 보존했습니다. 다른 화면 증거도 같은 로컬 `captures/keyboard-audit`에 있으며 기존 ignore 규칙에 따라 커밋하지 않습니다.
- 검증 범위는 Pixel_10(Android 17) 한 대와 위 입력 컴포넌트입니다. 미리보기의 서버 콜백은 비어 있으므로 실제 제출·전송·초안 저장 성공을 검증한 것은 아닙니다. 전체 `check`·다른 기기·다크 모드·큰 글자 조합은 실행하지 않았습니다.

## 복구

이번 입력 규칙 변경(공통 modifier와 연결부)과 BOM 변경을 함께 되돌립니다. 저장 데이터나 API 변경은 없습니다. 이전 시트 UI 커밋은 그대로 유지합니다.

## 채팅 작성 버튼 예외

- 채팅의 `ComposerIconButton`·`ComposerChipButton`·`ComposerSendButton`에 기존 `keepKeyboardOnTap`을 적용합니다. 버튼 주변의 빈 여백에는 적용하지 않습니다. 사용자 동작 계약은 [Android 채팅 계약](../../../knk-harness/docs/spec/3-3-android-spec.md#채팅-목록과-채팅방)을 따릅니다.
- `BlockInputList`가 현재 포커스와 블럭별 `FocusRequester`를 관리합니다. 현재 입력칸 삭제 시 비활성화·퇴장 전에 남은 다음 칸, 없으면 이전 칸으로 이동합니다. 다른 입력칸 삭제는 현재 포커스를 유지합니다. 마지막 칸 삭제는 키보드를 닫고, 구성 변경 후 강제 포커스 복원은 하지 않습니다.
- `ChatComposerKeyboardTest`는 실제 컴포저와 상태 갱신 콜백으로 상황·대사 추가, 비포커스 칸 삭제, 현재 칸 삭제 후 다음·이전 칸 이동, 추천 설정 선택, 바깥 영역 탭, 마지막 칸 삭제를 검사합니다. Pixel_10(Android 17)에서 통과했습니다. 서버 전송 성공·입력 모드 교체·내용이 있는 칸의 확인 다이얼로그는 이번 자동화 검증 범위에 포함하지 않았습니다.
- `:chat:testDebugUnitTest`, `:chat:detekt`, `:chat:assembleDebugAndroidTest`, `:app:installDebug`를 통과했습니다. 테스트 APK는 `adb install -r chat/build/outputs/apk/androidTest/debug/chat-debug-androidTest.apk`로 설치하고 `adb shell am instrument -w -e class app.manyak.chat.room.presentation.composer.ChatComposerKeyboardTest app.manyak.chat.test/androidx.test.runner.AndroidJUnitRunner`로 실행했습니다. 기존 앱 데이터는 제거하지 않았습니다.
- 설치 후 테스트 실행 중 `adb exec-out screencap -p`로 [입력 전](../../captures/chat-keyboard/frame-03.png), [추가·삭제 후 키보드 유지](../../captures/chat-keyboard/frame-05.png), [추천 메뉴와 키보드](../../captures/chat-keyboard/frame-06.png), [마지막 칸 삭제 후](../../captures/chat-keyboard/frame-08.png)를 확인했습니다. 캡처는 기존 ignore 규칙으로 로컬에만 보관합니다.
- 이 예외만 복구하려면 채팅 작성 버튼 modifier와 삭제 전 포커스 이동 변경을 되돌립니다. 공통 입력 규칙과 Compose BOM은 유지합니다.

## 추가 정보 편집 버튼 확장

- 추가 정보의 정보 추가·삭제, 추천 선택·해제, 스토리라인 더보기·접기에 `keepKeyboardOnTap`을 적용합니다. `AddTrigger` 공용 정의 대신 추가 정보 화면의 호출부에 적용해 다른 제작 단계의 동작은 유지합니다.
- `AdditionalInfoRows`는 채팅과 동일하게 삭제 전 다음 입력칸, 없으면 이전 칸으로 포커스를 이동합니다. 퇴장 중인 입력과 삭제 버튼은 비활성화하고, 마지막 입력칸 삭제는 키보드를 닫습니다. 입력 값·선택·저장·완성 요청의 계약은 바꾸지 않습니다.
- `AdditionalInfoKeyboardTest`는 실제 `AdditionalInfoList`를 사용해 추천 선택, 더보기·접기, 정보 추가, 현재 입력 삭제 후 포커스 이동, 빈 영역 탭과 마지막 입력 삭제를 검사합니다. Pixel_10(Android 17)에서 통과했습니다. 콜백으로 화면 상태를 갱신하는 테스트이며 서버 완성·저장 성공과 구성 변경은 이번 검증 범위에 포함하지 않습니다.
- `:create:ktlintCheck :create:detekt :create:testDebugUnitTest :create:assembleDebugAndroidTest :app:installDebug`를 통과했습니다. 테스트 APK를 `adb install -r create/build/outputs/apk/androidTest/debug/create-debug-androidTest.apk`로 설치한 뒤 `adb shell am instrument -w -e class app.manyak.create.additionalinfo.presentation.AdditionalInfoKeyboardTest app.manyak.create.test/androidx.test.runner.AndroidJUnitRunner`로 실행했습니다.
- 설치 후 `adb exec-out screencap -p`로 [입력 포커스](../../captures/additional-keyboard/frame-03.png), [추천 선택·더보기 후 키보드 유지](../../captures/additional-keyboard/frame-04.png), [정보 추가 후](../../captures/additional-keyboard/frame-05.png)를 확인했습니다. 캡처는 로컬에만 보관합니다.
