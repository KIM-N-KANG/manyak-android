# 회원 알림함 구현

## 범위와 기준

- Jira: KNK-1827
- Android와 하네스 브랜치: `feat/KNK-1827-user-notification-inbox`, fetch한 `origin/dev` 기준
- [공통 계약](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-015-알림함), [Android 계약](../../../knk-harness/docs/spec/3-3-android-spec.md), [현재 구조](../../../knk-harness/docs/design/1-2-android-design.md#회원-알림함)
- 웹 `src/features/notifications`의 문구, 분류, 읽음 처리와 목적지 우선순위를 확인했습니다. 서버 계약은 2026-10-24 dev Swagger의 알림 API 네 개와 공지 `tag`를 확인했습니다.

## 변경

- 기존 notification 모듈에 API, Repository, MVI 화면과 분류별 커서 상태를 추가했습니다.
- app의 회원 메인 헤더에서 알림함을 열고 셸 복귀 시 안 읽은 배지를 갱신합니다. 마이 공지 메뉴는 유지합니다.
- 기존 DeepLink 파서를 사용하며 허용 origin 검증을 보완했습니다. 공지와 스토리 상세는 알림함 위에 쌓습니다.
- 공지 목록과 상세의 태그를 표시합니다. 색, 크기, 여백, 글꼴과 모양은 ManyakTheme 토큰을 사용합니다.
- 공지 태그는 `StoryGenreBadge`를 사용하며 목록은 기존 작은 크기인 `Compact`, 상세는 제작 탭 장르 배지와 같은 `Large` 크기를 사용합니다. 점검 배지의 주의색은 유지합니다.
- 소식과 활동 탭은 크레딧 충전과 간편 제작처럼 `LocalRippleConfiguration`을 null로 제공해 리플을 끕니다.
- 모두 읽음 버튼은 `ManyakNeutralButton(compact = true)`로 채팅 모델 선택 버튼과 같은 `controlCompact` 높이와 `controlHorizontal` 좌우 여백을 사용합니다.
- 새 모듈, 의존성, 저장 스키마, 세션 계약을 추가하지 않았습니다.

## 검증

- notification, notice, navigation, app의 `ktlintCheck`, `detekt`, Kotlin 컴파일 통과
- `checkModuleArchitecture` 통과
- 단위 테스트 91개 통과: notification 45개, notice 15개, navigation 15개, app 16개
- `installDebug`로 Pixel 10 에뮬레이터에 설치했습니다.
- `NotificationInboxUiTest`의 기본 흐름 통과: 소식과 활동 전환, 화면 상태 복원, 다크 모드, 모두 읽음, 알림 설정과 공지 목적지 콜백
- 테스트 Repository 데이터로 UI를 확인하고 `adb exec-out screencap -p`로 복원 전후, 테마 전후, 읽음 전후를 캡처해 비교했습니다. Activity 회전이나 실제 회원 API를 통한 전체 흐름은 확인하지 않았습니다.
- 두 저장소의 `git diff --check` 통과
- 전체 `check`와 CI는 로컬에서 반복하지 않았습니다.

화면 캡처와 UI 테스트 결과는 `/Users/dongwoo/.codex/visualizations/2026/10/24/01a16d60-c71d-7602-b8cd-4604665fa8f3/knk1827/`에 보관했습니다.

## 복구

해당 변경을 되돌리면 헤더는 기존 공지사항을 엽니다. 로컬 저장 형식이나 서버 데이터 마이그레이션은 없습니다.

## 2026-10-24 실제 공지 발송 확인

- 사용자 승인 후 dev 공지 `c6cc2855-45cd-4f84-8b07-ff0a6310f285`를 게시했습니다.
- 캠페인 `99a1e68b-a377-48a5-90b5-c43c8a9b5cae`: SENT, 대상 및 도달 회원 3명, 도달 기기 5대, 실패 기기 1대입니다. 실패 원인은 관리자 상세에 표시되지 않았습니다.
- 실제 로그인된 Android 앱 소식 목록에서 테스트 알림 수신과 해당 공지 상세 이동을 확인했습니다. 확인 시 이미 읽음 상태여서 이번 실제 발송으로 읽음 전환은 검증하지 못했습니다. 에이전트가 OS 알림 표시와 알림 클릭 진입을 직접 관찰하지는 않았습니다. 사용자가 후속 메시지에서 알림 동작을 직접 확인했다고 알려 주셨습니다.
- compact 버튼 변경 후 designsystem과 notification의 ktlintCheck, detekt 및 installDebug가 통과했습니다. 에뮬레이터 화면 캡처로 버튼 크기를 확인했습니다.

- PR 준비 시 로딩, 조회 실패 후 재시도, 빈 목록의 UI 검증과 캡처를 추가했습니다.
