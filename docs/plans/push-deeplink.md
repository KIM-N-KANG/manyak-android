# 푸시 `deepLink` 진입 처리

- 작성일: 2026-09-20
- 근거 정본: 하네스 `spec/4-backend-server-spec.md §4-3-5 푸시 발송 모듈`(선택 키 `deepLink`, KNK-1335),
  `spec/3-3-android-spec.md §3-3-5 알림`, `design/1-2-android-design.md §1-2-6 알림 진입과 권한`
- 서버 반영: `manyak-server` `0005fec` [KNK-1335] (v0.5.1, main 포함). 스토리 완성 `/stories/{storyId}`,
  출석 리마인드 `/my/credits?tab=free`, 프로모션은 생략. 기준 origin `manyak.push.web-base-url`(기본 `https://manyak.app`).

## 목표와 제외 범위

알림 data 의 `deepLink` 를 내부 라우트로 해석해 이동한다. 서버 스펙 계약대로 허용 목록 밖 URL 은 홈이고,
`deepLink` 가 없는 페이로드(업데이트 전에 만든 PendingIntent·프로모션)는 지금처럼 `type` 매핑으로 간다.

현재도 `type` 매핑이 두 목적지에 도착하므로 사용자에게 보이는 동작은 바뀌지 않는다. 이 작업의 실질은
**서버 URL 을 진입 정본으로 올리고 해석 경계를 하나로 두는 것**이다 — 이후 서버가 목적지를 바꾸거나 새 알림을
추가할 때 앱 업데이트 없이 따라가고, 공유 URL App Links(KNK-927·928)가 같은 파서를 쓴다.

**제외** — App Links(`https` intent-filter·`autoVerify`·웹 `assetlinks.json`): 슬랙 요청에서도 별도 작업으로
분리했고 웹에 `assetlinks.json` 이 아직 없다. KNK-927·928 범위다. 이프 충전 화면의 탭 라우트 파라미터(`tab=history`):
두 번째 사용처가 없다. 무료 탭이 기본 선택이라 `tab=free` 는 쿼리를 읽지 않아도 도착한다.

## 이 레포에서 새로 내린 결정

1. **`deepLink` 가 있으면 URL 이 정본이고 없으면 `type` 매핑이다.** 하네스 서버 스펙이 "검증한 뒤 이동, 허용하지
   않는 URL 이면 홈" 을 앱 계약으로 두었으므로, URL 이 왔는데 해석에 실패했을 때 `type` 으로 되돌아가지 않는다 —
   되돌아가면 서버가 목적지를 바꾼 뒤에도 구 매핑으로 가서 서버 의도와 어긋난다. `type` 매핑은 `deepLink` 가
   **없는** 페이로드의 하위 호환으로만 남긴다.
2. **파서는 `:navigation` 의 `DeepLink.routeOf(url): NavKey?` 하나다.** `java.net.URI` 로 파싱한다 — `android.net.Uri`
   는 JVM 유닛 테스트에서 비어 있어 Robolectric 을 끌어와야 한다. `https` 이고 호스트가 `manyak.app`·`www.manyak.app`
   일 때만 경로를 본다. `/stories/{id}` 는 `StoryDetailRoute(id)`, `/my/credits` 는 `MyCreditChargeRoute`(쿼리 무시),
   그 외는 null. 파서에 수신자·세션 판단을 넣지 않는다 — 그건 `PushEntry.routeFor` 와 `resolveEntryDestination` 이
   이미 한다.
3. **`PushEntry` 에 `deepLink: String?` 을 더하고 extra 키를 하나 늘린다.** 기존 세 extra 는 그대로라 업데이트 전
   PendingIntent 도 읽힌다. `routeFor` 순서: 수신자 불일치 → 홈, `deepLink` 있음 → 파서 결과 또는 홈, 없음 → 기존
   `type` 매핑.
4. **`PushMessage.from` 이 `data["deepLink"]` 를 읽어 `entry` 에 싣는다.** 표시(제목·채널·알림 ID)는 바꾸지 않는다.

`ponytail:` 호스트 허용 목록은 상수 두 개다. 환경별 origin 이 생기면 `BuildConfig` 로 올린다.

## 합의·확인이 필요한 항목

- **Jira 키.** Android 쪽 서브태스크가 없다(서버는 KNK-1335, 부모 스토리 KNK-1113 진행 중). 브랜치·커밋에 쓸 키를
  사용자가 정한다. 막는 단계: 1번(브랜치 생성).
- **웹 `tab=free`.** 웹 `credit-charge-screen.tsx` 는 `PURCHASE` 탭이 기본이고 `?tab=` 쿼리를 읽지 않는다. 앱과 무관하며
  웹 담당에게 전달만 한다.

## 구현 순서

1. `:navigation` — `DeepLink`(파서) + `PushEntry.deepLink`·extra·`routeFor` 갱신. `DeepLinkTest`·`PushEntryTest` 추가:
   허용 호스트 두 경로, 다른 호스트·`http`·알 수 없는 경로 → null, `deepLink` 있는데 null 이면 홈, 없으면 `type` 매핑.
   완료 조건: `:navigation:testDebugUnitTest` 통과.
2. `:notification` — `PushMessage.from` 이 `deepLink` 를 싣는다. 완료 조건: `:notification` 컴파일·테스트 통과.
   (`:app` 은 `PushEntry` 를 값으로만 다뤄 변경 없음. `ExternalEntryTest` 그대로.)
3. 하네스 — `design/1-2-android-design.md §1-2-6` "알림 진입과 권한" 첫 문장을 URL 우선·`type` 하위 호환으로 고치고,
   `spec/3-3-android-spec.md §3-3-5` 에 "허용 목록 밖 URL 은 홈" 한 줄, `adr/1-3-android-adr.md` 에 결정 1 을 A-045 로
   추가. 1·2 와 병렬 가능.

## 검증 (2026-09-20, emulator-5554 · dev 서버)

- 로컬: `:navigation`·`:notification` `ktlintCheck`·`detekt`·`testDebugUnitTest`(DeepLinkTest 3·PushEntryTest 4 포함 38건),
  `:app:compileDebugUnitTestKotlin` 통과.
- `external-entry`: `am start -n app.manyak/.MainActivity -f 0x34000000` 에 `app.manyak.push.*` extra 를 주입해 확인.
  캡처는 `../captures/KNK-1348/`.
  - ① 실행 중, `deepLink=https://manyak.app/stories/{실제 id}` + `targetId=wrong-id` → 그 스토리 상세(URL 이 `type` 식별자보다 우선).
  - ② 종료 상태, `deepLink=https://manyak.app/my/credits?tab=free` → 이프 충전 무료 탭 출석 카드.
  - ③ 종료 상태, `deepLink=https://evil.example/stories/{id}` + 유효한 `type`·`storyId` → 홈(`type` 으로 되돌아가지 않음).
  - ④ 실행 중, `deepLink` 없이 `type`+`storyId` → 상세(하위 호환).
- 하지 않은 것: dev 서버가 실제로 보낸 푸시의 `deepLink` 값 확인. 서버 코드(`0005fec`)로만 판단했다. 스토리 완성
  푸시를 한 번 받아 data 에 `https://manyak.app/stories/…` 가 실려 오는지 보면 끝난다.
