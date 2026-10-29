# 알림함 이프 만료 라벨과 이프 충전 탭 쿼리 (KNK-1843)

- 작성일: 2026-10-25
- 상위: KNK-1840. 서버 KNK-1835(이프 만료 예정 알림 `CREDIT_EXPIRY_REMINDER`, ACTIVITY 분류), 웹 KNK-1842
- 근거 정본: 하네스 ADR [A-080](../../../knk-harness/docs/domain/PAYMENT/CLIENT/ANDROID/DECISIONS.md#a-080)(A-045의 탭 쿼리 미해석 대체),
  [Android 알림 계약](../../../knk-harness/docs/spec/3-3-android-spec.md), [Android 구조](../../../knk-harness/docs/design/1-2-android-design.md)

## 목표와 제외 범위

알림함의 `CREDIT_EXPIRY_REMINDER` 카드에 "이프 만료" 라벨을 붙이고, `/my/credits?tab=history` 링크가 이프 충전의 내역 탭으로 열리게 한다. 탭 값은 웹과 맞춘 `purchase`, `free`, `history`이고 Android는 `history`만 내역 탭, 나머지와 쿼리 없음은 무료 충전 탭이다.

**제외**: 이프 부족 바텀 시트(KNK-1845), 앱 열기 링크(`manyak://`)의 쿼리 전달, `deepLink` 없는 `CREDIT_EXPIRY_REMINDER` 의 `type` 매핑(서버가 항상 링크를 싣는다).

## 결정

1. **라우트는 `MyCreditChargeRoute(openHistory: Boolean = false)`.** 탭이 둘이라 Boolean으로 충분하고, `:navigation` 이 `my` 의 `CreditChargeTab` 을 참조하지 않는다. 구매 탭이 생기면 탭 값으로 바꾼다.
2. **`DeepLink` 는 `rawQuery` 에서 `tab` 만 고른다.** UTM 등 다른 키는 무시하고, 값이 정확히 `history` 일 때만 내역 탭이다.
3. **화면은 처음 탭만 받는다.** `rememberSaveable` 초기값으로만 쓰고 탭 전환을 라우트에 다시 쓰지 않는다.

## 검증

- `./gradlew :navigation:ktlintCheck :navigation:detekt :navigation:testDebugUnitTest :notification:ktlintCheck :notification:detekt :notification:testDebugUnitTest :my:ktlintCheck :my:detekt :my:testDebugUnitTest :app:ktlintCheck :app:detekt :app:compileDebugKotlin` 통과.
  - `DeepLinkTest`: `tab=history`(UTM 앞뒤 포함, `www`, 끝 슬래시), `free`, `purchase`, `HISTORY`, 빈 값, 키만, 쿼리 없음, 비슷한 키 `mytab`.
  - `NotificationDisplayTest`: `CREDIT_EXPIRY_REMINDER` → `inbox_credit_expiry`.
- 에뮬레이터(`installDebug`)에서 `am start` 로 푸시 extra(`type=CREDIT_EXPIRY_REMINDER`, 현재 회원 `recipientId`)를 넣어 확인:
  `?utm_source=push&tab=history&utm_campaign=credit_expiry` → 내역 탭, `?tab=free&utm_source=push` → 무료 충전 탭, 쿼리 없음 → 무료 충전 탭.
- 알림함 라벨은 dev 서버에 아직 이 유형의 알림이 없어(KNK-1835 미배포) 기기에서 보지 못했고 단위 테스트로만 확인했다.
