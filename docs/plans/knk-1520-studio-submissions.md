# KNK-1520 제작 탭 검수 제출본과 검수 완료 알림

## 목표와 확인 근거

[KNK-1520](https://kimandkang.atlassian.net/browse/KNK-1520)의 신규 등록 제출본 카드와 검수 완료 알림을 구현합니다. Android와 하네스는 같은 `feat/KNK-1520-studio-submissions-and-moderation-notifications` 브랜치에서 작업합니다.

- Android 분기 기준: fetch한 `origin/dev`의 `bb74e94aae727d6938ae1bffd571bed28e80a5e3`.
- 하네스 분기 기준: fetch한 `origin/dev`의 `634702e6b0483a0076ca155457c296758c97d042`. 관련 정본이 이 커밋에 포함된 것을 확인했습니다.
- 작업 시작 시 두 저장소의 작업 트리는 깨끗했습니다.
- 사용자 계약은 [공통 제작 목록 spec](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-013-제작--내-스토리-목록), [Android spec](../../../knk-harness/docs/spec/3-3-android-spec.md), 구조는 [Android design](../../../knk-harness/docs/design/1-2-android-design.md), 결정 이유는 [A-056](../../../knk-harness/docs/adr/1-3-android-adr.md#a-056)을 따릅니다.
- dev Swagger의 `GET /api/v1/stories/submissions`와 `DELETE /api/v1/stories/submissions/{id}`를 확인했습니다. 목록의 상세 응답 스키마가 자유 형식 객체이므로 서버 `StorySubmissionService.kt`의 직렬화 필드, 삭제의 204, 404, 409 처리를 직접 대조했습니다.
- 서버 `ModerationPushListeners.kt`에서 알림의 type, status, submissionId, 선택적 storyId, title, body 및 `/studio` deepLink를 확인했습니다.
- 웹 `submission-card.tsx`, `use-story-submissions.ts`, `story-submission.ts`, `created-story-list.tsx`와 대조했습니다. 실제 브라우저의 제작 페이지는 로그아웃 상태여서 제출본 상태별 화면 비교는 웹 소스를 기준으로 했습니다.

## 구현

| 항목 | 반영 내용 |
| --- | --- |
| 제출본 목록 | CREATE이면서 storyId가 없는 PENDING, REJECTED, FAILED만 표시합니다. 초안 다음, 완성 요청과 내 스토리 앞에 등록 요청 최신순으로 둡니다. 제목, 표지, KST 등록 시각과 상태별 배지 및 설명을 표시합니다. |
| 앱 디자인 | `ManyakTheme`, 기존 표지, 옵션 시트와 삭제 확인창을 사용합니다. 기존 표지 너비 128dp를 `studioCoverWidth` 토큰으로 옮기고 DESIGN.md에 반영했습니다. 사용자 문구는 studio의 strings.xml에 둡니다. |
| 등록 취소와 삭제 | 검토 중이면 등록 취소, 반려와 실패이면 삭제를 확인합니다. 삭제 중 중복 요청을 막고, 204만 성공으로 처리합니다. 404와 409 등 실패도 재조회하여 승인 경합을 해소합니다. |
| 목록 갱신 | 제작 화면의 STARTED 수명에서 검토 중 제출본이 있을 때 5초마다 조회합니다. 화면 복귀, 당겨서 새로고침과 알림 진입은 즉시 조회합니다. 사라진 제출본이 있으면 진행 중인 내 스토리 조회 이후에도 한 번 더 갱신합니다. |
| 실패와 경쟁 상태 | 제출본 조회 실패는 해당 카드만 숨깁니다. 마지막 검토 중 판정을 유지하며 조회와 삭제를 직렬화합니다. 카드가 숨겨진 동안 일부를 삭제해도 남은 검토 중 제출본의 조회를 계속합니다. |
| 검수 완료 알림 | 제출본 ID로 알림과 PendingIntent를 구분하고 같은 제출본의 재발송은 교체합니다. 기존 서비스 채널과 수신자 확인을 사용하며 서버 제목 및 본문을 보존합니다. 공개 버전에는 제목만 남깁니다. |
| 알림 진입 | 허용한 `/studio` URL을 기존 제작 탭으로 연결합니다. 이미 제작 탭을 보고 있어도 다시 조회합니다. 기존 로그인 보류와 수신자 확인을 유지하며 불일치하면 홈 탭을 선택합니다. |
| 분석 이벤트 | 등록 취소 및 삭제 선택 시 `client_storyList_submissionCard_clicked`에 `submission_id`, 소문자 `status`, `action`을 기록합니다. |

일반 제작 폼과 제출본 수정 및 재제출은 티켓에서 분리된 범위이므로 수정하기 버튼은 추가하지 않습니다. API, 저장 스키마 및 인증 계약의 신규 변경은 없습니다.

## 검증 결과

2026-10-05, Pixel_10 API 37 에뮬레이터에서 검증했습니다.

| 검증 | 결과 |
| --- | --- |
| studio 컴파일, ktlint, detekt 및 단위 테스트 | 통과, 41개 |
| notification ktlint, detekt 및 단위 테스트 | 통과, 36개 |
| navigation ktlint, detekt 및 단위 테스트 | 통과, 10개 |
| app 컴파일, ktlint, detekt 및 단위 테스트 | 통과, 14개 |
| analytics ktlint, detekt 및 단위 테스트 | 통과, 3개 |
| designsystem ktlint 및 detekt | 통과 |
| checkModuleArchitecture | 17개 모듈, Kotlin 파일 437개 검사 통과 |
| app installDebug | 최종 앱 설치 성공 |
| StudioSubmissionUiTest | 통과, 1개. 세 상태, 라이트와 다크 테마, 취소 및 삭제 확인, Compose 상태 복원 후 확인창 유지 |
| ModerationNotificationUiTest | 통과, 1개. 실제 시스템 알림 두 개, 별도 PendingIntent, 서비스 채널, 제목과 본문, 공개 버전의 본문 제거, 재발송 교체 |

단위 테스트 104개가 실패와 생략 없이 통과했습니다. 새로운 회귀 검증은 5초 조회 및 화면 이탈 취소, 목록 실패와 복구, 승인 및 삭제 경합, 중복 삭제 방지, 확인 대상 갱신, DTO 필터와 오류 수, 알림 대상 식별, 로그인 및 수신자 분기와 제작 탭 선택을 포함합니다.

### 화면 근거와 검증 한계

- [캡처 폴더](../../build/qa/knk-1520)의 `before-live.png`는 변경 전 실제 앱의 제작 탭, `after-live.png`는 최종 앱의 제작 탭입니다. 실제 계정에는 검증 시점의 제출본이 없어 상태별 카드는 테스트 데이터로 검증했습니다.
- `cards-light.png`, `cards-dark.png`, `cancel-confirmation.png`, `delete-confirmation.png`는 실제 StudioScreen과 ViewModel에 fake 저장소를 연결한 Compose 테스트 화면을 `adb exec-out screencap -p`로 캡처한 결과입니다. 앱 셸 전체 캡처는 아닙니다.
- 다크 테마와 Compose 상태 복원은 테스트 호스트에서 확인했습니다. 실제 Activity 재생성, 회전과 글자 크기 변경은 이번 검증에 포함하지 않았습니다.
- 시스템 알림 검증은 실제 NotificationManager에 테스트 알림을 게시한 결과이며 검증 후 해당 알림을 제거했습니다. 실제 FCM 전송, 서버 검수 완료부터 알림 탭을 거치는 통합 흐름은 미검증입니다.
- 실제 서버 제출본 삭제나 계정 데이터를 변경하는 검수 시나리오는 실행하지 않았습니다. 삭제 응답 분기는 서버 소스 및 fake API 테스트로 확인했습니다.
- 전체 `check`와 `assembleDebug`는 CI 소유이므로 로컬에서 반복하지 않았습니다. 변경 모듈 검증과 앱 설치에 필요한 빌드만 실행했습니다.

## 복구 및 배포 전 확인

저장 스키마 변경이 없으므로 이번 변경을 되돌리면 이전 화면과 알림 해석으로 복구됩니다. 배포 전 검증 계정으로 실제 서버 제출본의 상태 전환, FCM 수신과 알림 탭, 확인창을 띄운 상태의 Activity 재생성을 확인해야 합니다. CI 결과와 배포 근거는 PR 및 배포 시점에 추가합니다.
