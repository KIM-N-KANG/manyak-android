# 간편 제작 입력 수위 거절 안내 (KNK-1870)

- 작성일: 2026-10-27
- 상위: KNK-1868. 웹 KNK-1869. 서버 KNK-1865
- 근거 정본: 하네스 ADR [A-082](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/DECISIONS.md#a-082),
  [Android 간편 제작 흐름과 보존](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/AGENTS.md),
  [Android 구조](../../../knk-harness/docs/domain/STORY/CLIENT/ANDROID/AGENTS.md),
  서버 계약은 manyak-server KNK-1865(PR #419, 2026-10-27 dev 병합·배포)와 하네스 `STORY/BACKEND/CONTRACT.md`의 422 `CONTENT_REJECTED`.
  dev OpenAPI에는 422가 없다(컨트롤러 응답 명세를 바꾸지 않음). 응답 형식은 서버 코드와 계약 문서로 확인했다

## 목표와 제외 범위

AI가 입력 수위를 이유로 거절한 생성(422 `CONTENT_REJECTED`)을 일반 실패와 "다시 만들기"로 두지 않고, 입력을 고치도록 키워드 단계로 돌려보내 "수위가 높아 만들 수 없어요"를 보인다. 스토리 완성의 거절은 제작 탭 토스트 문구를 바꾼다. 문구는 사용자가 정한 한 문장이며 웹과 같다. 다시 하라는 안내는 붙이지 않는다. 키워드 단계에서는 "키워드를 하나 이상 선택해주세요"와 같은 푸터 오류 자리(버튼 바로 위)에 보인다.

**제외**: 퍼널 안의 완성 거절 안내(Android는 완성 제출과 함께 제작 탭으로 떠난다), 복구 조회로 확정한 완성 실패의 사유 구분(조회가 사유를 돌려주지 않는다).

## 결정

1. **판별**은 `common`의 `DomainError.isContentRejected()`(Server이고 `code == "CONTENT_REJECTED"`). 문구는 `common`의 `error_content_rejected`이고 `DomainErrorMessages`도 같은 문구로 바꾼다.
2. **스토리라인 생성 거절**은 첫 생성, 다시 만들기, 복구 실패 뒤 같은 요청 ID 재요청을 가리지 않는다. `StorylineGenerationStore`가 다른 상태 코드 실패처럼 기록을 정리(결과 없으면 삭제, 다시 만들기면 직전 결과를 기기 안에 되돌림)한 뒤 메모리의 생성과 편집만 비우고 `StorylineGenerationState.Rejected`가 된다. 세션의 저장 위치는 유지한다.
3. **스토리라인 화면**은 `Rejected`에서 `RestartAtKeyword(notFound = false)`로 같은 `draftId`의 키워드 라우트로 바꾼다.
4. **키워드 화면**은 진입 판정 뒤 `takeRejectedKeyword()`로 거절된 요청을 키워드 스냅숏으로 채우고 `contentRejected`로 푸터에 안내한다. 기준선은 빈 입력이라 돌아온 키워드는 저장하지 않은 편집이다. 다음 생성 요청에서 안내를 내리고 새 요청 ID를 쓴다.
5. **완성 거절**은 `StoryCompletionExecutor`가 원 응답의 422를 복구 조회 없이 추가 정보 단계 초안으로 되돌리고 `CompletionFailure.CONTENT_REJECTED`를 낸다. `CreationProgressAccess.completionFailures`가 `Flow<CompletionFailure>`가 되고 제작 탭이 사유별 문구로 토스트를 띄운다.

## 검증

- `common`, `create`, `studio`의 `ktlintCheck`, `detekt`, `testDebugUnitTest`와 `:app:assembleDebug` 통과.
  - 스토어: 첫 생성 거절은 기록을 지우고 키워드를 한 번만 돌려주며 다음 생성은 새 요청 ID, 다시 만들기 거절은 직전 결과를 기기 안에 남기고 메모리를 비움.
  - 스토리라인 화면: 거절이 오면 키워드 단계 전환 효과를 내고 인라인 오류를 켜지 않음.
  - 키워드 화면: 돌아온 키워드와 안내, 저장하지 않은 편집 판정, 다시 만들면 안내를 내리고 단계 전환.
  - 완성 실행자: 422는 복구 조회 없이 초안으로 되돌리고 거절로 알림. 제작 탭: 사유를 실은 효과.
- 에뮬레이터(`installDebug`): dev에서 실제 AI 거절을 일으킬 입력을 정하기 어려워, 생성과 완성 응답만 임시로 바꾼 빌드로 확인한 뒤 되돌렸다.
  - 생성 응답을 502로 두면 기존처럼 스토리라인 단계의 실패와 "다시 만들기"가 보이고, 다시 만들기 응답을 422로 두면 키워드 단계로 바뀌어 고른 장르와 주인공 특징이 남은 채 푸터에 거절 문구가 보인다. 다시 만들면 실제 생성이 성공했다.
  - 완성 응답을 422로 두면 제작 탭이 추가 정보 단계 초안 카드와 거절 문구 토스트를 보인다.
  - 실제 서버의 422 응답과 다시 만들기 거절 뒤 직전 스토리라인 카드는 기기에서 확인하지 않았다.
