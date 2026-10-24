# 공지사항 목록과 상세 화면 (KNK-1819)

- 작성일: 2026-10-24
- 근거 정본: 하네스 [FE-SCREEN-014 공지사항](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-014-공지사항)(웹 KNK-1818과 공통 계약),
  [Android 공지사항](../../../knk-harness/docs/spec/3-3-android-spec.md#마이법적-문서), [Android 구조](../../../knk-harness/docs/design/1-2-android-design.md#1-2-4-내비게이션과-화면),
  ADR [A-079](../../../knk-harness/docs/adr/1-3-android-adr.md#a-079)
- 서버 API: dev Swagger `GET /api/v1/notices?limit=&cursor=`, `GET /api/v1/notices/{noticeId}`(둘 다 permitAll). 공지 게시 푸시의 `deepLink`는 `https://manyak.app/notices/{noticeId}`.
- 웹 구현 참고: `manyak-web/src/features/notices/`(KNK-1818, PR #318)

## 목표와 제외 범위

홈·채팅·제작 헤더의 종 버튼과 마이 알림 섹션의 공지사항 메뉴로 목록·상세를 열고, 기기에 둔 읽음 기록으로 안 읽은 공지를 점·배지·굵은 제목으로 드러낸다. 공지 게시 푸시와 앱 열기 링크의 `/notices/{id}` 를 상세로 연다.

**제외** — 분석 이벤트(계약에 없음), 회원별 알림함(생기면 헤더 버튼 목적지만 바뀐다), App Links(KNK-927·928).

## 이 레포에서 새로 내린 결정

1. **새 모듈 `:notice`.** 공지는 알림 설정·푸시와 소유자가 다르고(공개 API, 기기 저장) 헤더·마이·딥링크 세 진입점이 있어 `notification` 안의 패키지로 두면 `my` 와 `app` 이 알림 모듈에 묶인다. `ModuleArchitecture` 기능 집합, `settings.gradle.kts`, `.idea/gradle.xml`, README 를 같이 고쳤다.
2. **안 읽음 판정은 셸 수명 `NoticeUnreadViewModel` 하나.** `app` 의 `MainTabsScreen` 이 들고 헤더 점(`NoticeHeaderButton`)과 `MyScreen` 의 `hasUnreadNotice` 로 내린다. `my` 가 `notice` 를 알 수 없고, 두 번째 소비처 전에 `common` 계약을 만들지 않는다(A-079). `ON_RESUME` 마다 첫 페이지를 다시 읽는다.
3. **읽음 기록은 `@DeviceDataStore` 의 `notice_read_ids` JSON 배열(최신순, 100개).** 문자열 집합은 순서가 없다. 기기 귀속이라 `UserScopedStore` 에 넣지 않는다. 읽기·쓰기 실패는 "읽은 적 없음".
4. **공개 API 는 `@PlainClient`.** 토큰 재발급이 공지 조회에 엮이지 않는다.
5. **헤더 `actions` 슬롯.** `ManyakBrandHeader`·`ManyakSectionHeader` 에 `RowScope` 슬롯을 더하고 마이 탭만 비워 둔다. 공지 버튼 자체는 `notice` 가 소유한다.
6. **딥링크 상세는 목록을 먼저 쌓는다.** `openExternalDestination` 이 `NoticeDetailRoute` 를 받으면 `NoticeListRoute` 를 깔아 뒤로가기가 목록으로 간다. `DeepLink` 는 `/notices` 와 `/notices/{id}` 를 더 안다.
7. **목록 줄 점은 `InlineTextContent`.** 두 줄 말줄임 뒤 제목 끝에 붙어야 하므로 `Row` 가 아니라 인라인 자리다. 보조 기술 이름 "새 공지".
8. **디자인 토큰.** `sizes.unread-dot`(8dp)·`unread-dot-inline`(6dp), 컴포넌트 `badge-danger`·`unread-dot` 을 `DESIGN.md` 와 `ManyakSizes` 에 추가. 색은 기존 `background-danger-bold`·`background-danger-subtle`·`text-danger`.
9. **당김 새로고침.** 채팅 목록과 같은 `ManyakPullToRefreshBox`. 목록이 그려진 상태에서만 두고, 실패는 보던 목록을 지우지 않고 공통 "새로고침하지 못했어요" 토스트로 알린다.
10. **아이콘.** 헤더는 마이 알림 설정과 같은 `ic_bell`, 마이 공지 메뉴는 사용자가 준 `megaphone-03-stroke-rounded.svg` 를 옮긴 `ic_megaphone`.

`ponytail:` 안 읽음 판정은 첫 페이지 20건만 본다. 20건 안에 30일 안 공지가 다 들어오지 않으면 그때 두 페이지를 읽는다.

## 구현 순서

1. `:navigation` — `NoticeListRoute`·`NoticeDetailRoute`, `DeepLink` 경로, `DeepLinkTest`.
2. `:notice` — entity(`NoticeSummary`·`NoticePage`·`NoticeDetail`), domain(`NoticeRepository`·`NoticeReadRepository`·`NoticeUnread` 순수 함수), data(`NoticeApi`·DTO·`NoticeRepositoryImpl`·`NoticeReadStore`·`NoticeModule`), presentation(`NoticeDate`, `NoticeUnreadViewModel`, `NoticeListViewModel`·Screen, `NoticeDetailViewModel`·Screen, `NoticeTopBar`, `NoticeHeaderButton`), `strings.xml`.
3. `:designsystem` — 헤더 `actions` 슬롯, `ic_megaphone`, `unreadDot` 토큰. `DESIGN.md`.
4. `:my` — `MyMenuItem.labelBadge`, `NotificationSection`(알림 설정 아래 공지사항), `MySection` 분리.
5. `:app` — `MainTabsScreen` 의 셸 VM·헤더 버튼·배지 전달, `NoticeEntries`, `openExternalDestination`, `ExternalEntryTest`.
6. 하네스 worktree `../knk-harness-knk-1819` — Android spec·design·QA(MY-NOTICES-A01~A07)·ADR A-079.

## 검증 (2026-10-24, emulator-5554 · dev 서버 공지 3건)

- 로컬: `:build-logic:ktlintCheck`, `:notice`·`:my`·`:app`·`:designsystem` `ktlintCheck`·`detekt`, `:navigation`·`:app`·`:notice` `testDebugUnitTest`(`NoticeUnreadTest` 4·`NoticeDateTest` 2·`NoticeViewModelsTest` 7·`DeepLinkTest` 추가 1·`ExternalEntryTest` 추가 1), `checkModuleArchitecture`, `installDebug` 통과.
- 캡처 `../captures/KNK-1819/`:
  - `01_home` 헤더 종 버튼 + 점(접근 이름 "새 공지사항 있음") → `02_list` 3건 굵은 제목 + 점 + 게시일 → `03_detail` 제목·"게시 2026-10-24"·줄바꿈 본문 → `04_list_after_read` 첫 줄만 점 꺼짐 → `05_home_still_unread` 헤더 점 유지 → `06_my_badge` 알림 설정 아래 확성기 "공지사항" + "새 공지" 배지.
  - `07_deeplink_detail` 종료 상태에서 `manyak://manyak.app/notices/{id}` → 상세 → `08_deeplink_back_list` 뒤로가기가 목록(그 줄 점 꺼짐).
  - `09_not_found` 없는 ID → "공지를 찾을 수 없어요" + "공지사항 목록으로".
  - `10_home_all_read` 3건 모두 연 뒤 헤더 점 없음(접근 이름 "공지사항").
  - `11_list_load_failed` 비행기 모드 → "공지사항을 불러오지 못했어요" + "다시 시도" → `12_list_retry` 해제 후 재시도로 목록 복구.
  - `16_list_ripple` 줄 눌림 리플, `19_list_row_margins` 채팅·제작과 같은 여백, `21_list_pull` 당김 표시자, `22_list_refresh_failed_toast` 오프라인 당김 실패 토스트(목록 유지).
- 하지 않은 것: 다음 페이지 이어 붙이기·실패 자리는 dev 공지가 3건(`nextCursor: null`)이라 화면에서 보지 못했고 `NoticeViewModelsTest` 로만 고정했다. "수정 YYYY-MM-DD" 표시도 dev 공지에 날짜가 다른 수정이 없어 `NoticeDateTest` 로만 확인했다. 실제 FCM 공지 푸시 탭은 발송하지 않았고, 같은 `deepLink` 를 받는 앱 열기 링크로 경로를 확인했다.
