# 소셜 로그인을 동의 후 가입 완료 흐름으로 전환 (KNK-1541)

- 작성일: 2026-10-05
- 브랜치: Android·하네스 모두 `feat/KNK-1541-social-login-consent-before-signup`
- 정본: 공통 [동의 모델](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-010-서비스-이용약관개인정보-처리방침),
  [Android spec §3-3-1](../../../knk-harness/docs/spec/3-3-android-spec.md),
  [Android design §1-2-5 약관 동의 게이트](../../../knk-harness/docs/design/1-2-android-design.md),
  [A-064](../../../knk-harness/docs/adr/1-3-android-adr.md#a-064-동의-전-가입-대기를-세션-없이-auth-메모리에-두기).
  API는 dev Swagger `POST /auth/social/{provider}`, `POST /auth/social/complete`.
- 참고 구현: 웹 KNK-1540([manyak-web#233](https://github.com/KIM-N-KANG/manyak-web/pull/233)), 서버 KNK-1536.

## 목표

필수 동의를 마치기 전에는 계정과 세션을 만들지 않는다. 동의가 남은 로그인은 대기 코드만 메모리에 들고 로그인 화면 위에서
동의를 받고, 가입 완료가 성공해야 토큰을 저장한다. 저장 세션의 재동의는 기존 회원 동의 API 경로를 그대로 쓴다.

## 이 레포에서 새로 내린 결정

1. **대기는 `auth`가 소유한다.** 대기 코드는 `SessionRepositoryImpl` 필드에만 두고 `SignupRepository.pendingSignup`에는 필요한
   항목과 요구 버전만 싣는다. 가입 완료가 기존 로그인과 같은 토큰 저장, 상태 공개, 초대 온보딩 경로를 타야 해서 별도 저장소로
   나누지 않았다. 동의 항목 타입은 `auth`와 `legal`이 함께 쓰므로 `common/entity/consent`로 옮겼다.
2. **시트는 기존 `LegalConsentViewModel`의 가입 모드다.** 세션 상태와 대기를 함께 보고 `SignedOut`에 대기가 있으면 가입 모드,
   회원이면 회원 모드로 판정한다. 가입 완료는 회원 공개 뒤에 대기를 비우므로 같은 대상(`Member`)이 이어져 조회가 한 번만 돈다.
3. **가입 완료 뒤에도 회원 동의를 다시 조회한다.** 정지 회원도 가입 완료는 성공하므로 이 조회의 403이 이용 제한 상태를 연다.
4. **만료와 약관 갱신은 시트를 닫고 재로그인 안내다.** 웹과 같은 문구를 `SessionEndNotice.SIGNUP_EXPIRED`·`SIGNUP_OUTDATED`로
   로그인 화면에 남긴다. ID 토큰을 다시 받아 자동 재시작하지 않는다. 대기를 공개할 때 직전 안내를 지운다.
5. **일시 실패 문구를 웹과 맞춘다.** 가입과 회원 모드 모두 "약관 동의 내역을 저장하지 못했어요"다.

## 구현 순서

1. 하네스 spec 3-3 로그인 동의 흐름 갱신
2. `ConsentItem`·`RequiredConsent`를 `common`으로 이동
3. `AuthApi` 소셜 인증과 가입 완료, `SignupRepository`, `SessionRepositoryImpl` 분기와 오류 처리
4. `LegalConsentViewModel` 가입 모드, 루트 `SignupConsentGate`
5. 단위 테스트와 기기 테스트 대역 갱신
6. 하네스 design, A-064

## 검증

- `:auth`·`:legal`·`:login`·`:common`·`:app`의 ktlintCheck, detekt, testDebugUnitTest, `:app:compileDebugAndroidTestKotlin`,
  `checkModuleArchitecture`
- 기기: 기존 회원 로그인(`COMPLETED`), 가입 대기 시트 표시와 뒤로가기 취소, 가입 완료, 만료·갱신 안내

## 실행 결과 (2026-10-05)

- 위 정적 검사와 단위 테스트 통과. 저장소 테스트(`SessionSignupOnboardingTest`)가 대기 공개와 안내 정리, 대기 코드 헤더 전송,
  만료·약관 갱신의 대기 폐기와 안내, 일시 실패의 대기 유지, 새 로그인의 이전 대기 폐기를 고정한다. ViewModel 테스트가 가입 모드
  시트, 뒤로가기 가입 취소(로그아웃 없음), 가입 완료 뒤 선택 동의 답 유지, 가입 완료 뒤 회원 조회 실패의 재시도 잠금 해제, 일시 실패 재시도, 가입 종료 시 시트 닫힘을
  고정한다.
- 에뮬레이터: 기존 회원 Google 로그인에서 `POST /auth/social/google` 200(`COMPLETED`) 뒤 `GET /users/me/consents`를 거쳐 홈 진입을
  확인했다. 에뮬레이터의 Google 계정이 모두 기존 회원이라 `CONSENT_REQUIRED` 시트와 가입 완료는 기기에서 확인하지 못했다.
