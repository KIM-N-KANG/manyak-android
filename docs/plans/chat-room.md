# 채팅방 — 턴 진행

> **KNK-1197 구조 이전 안내 (2026-09-05)** — 아래 구현 순서·모듈 경로·코드 예시·본문의 문서 절 번호와 검수 항목은 작성 당시 기록입니다. 현재 소유 위치와 검증 결과는 [모듈 재구성 기록](./module-reorganization.md), 현재 계층·의존 규칙은 [하네스 모듈 아키텍처](../../../knk-harness/docs/design/1-2-android-design.md#1-2-2-모듈과-소유권)를 따릅니다.

- 작성일: 2026-08-29
- 지라: KNK-868(기능) · KNK-1032(스펙 확정)
- 근거 정본: 하네스 `design/1-2-android-design.md §1-2-4 채팅방 — 턴 진행`, `spec/3-1-client-spec.md §3-1-5`

## 목표와 제외 범위

채팅방을 조회 전용 화면에서 **턴을 진행할 수 있는 화면**으로 만듭니다. 입력 컴포저, SSE 턴 진행,
추천 입력·선택지, 응답 재생성, 엔딩 배지, 채팅 삭제, 이프 부족 처리까지가 범위입니다.

**제외** — 채팅 배경 이미지 마커(`[[image:<imageKey>]]`, 서버·AI·웹 모두 미구현), **채팅 공유 열람**(웹 소유.
발급은 2026-08-29 앱 비적용 결정이었으나 KNK-1320에서 메뉴 시트의 "공유하기"로 적용 — 아래), 이프 잔액·보상 표시(KNK-870),
첫 진입 오버레이 투어(추천 입력 위 1회성 힌트만 옮기고 투어는 컴포저 확정 이후 판단).

## 새로 내린 결정

### 1. SSE 전송은 `okhttp-sse`의 `EventSource`를 쓰고 전용 클라이언트를 파생합니다

하네스 §3-3-2가 확정한 스택 그대로입니다. `EventSources.createFactory(client)`는 임의 요청을 받으므로
POST 본문을 실을 수 있고, 자동 재연결이 없어(`RealEventSource`는 종료 시 `onClosed`만 부릅니다)
"서버 재개 스트림 없음" 계약과 맞습니다.

전용 클라이언트는 인증 클라이언트에서 파생해 연결 풀·인터셉터를 공유하되 셋만 바꿉니다.

| 값 | 설정 | 근거 |
| --- | --- | --- |
| `readTimeout` | 60초 | OkHttp의 읽기 상한은 **연속된 읽기 사이**의 간격이라 SSE에서는 "토큰이 끊긴 시간"이 됩니다. 무제한(0)으로 두면 죽은 연결이 영원히 남습니다 |
| `callTimeout` | 0(무제한) | 전체 요청 상한은 스트림 길이를 자릅니다. 전체 상한은 서버가 이미 갖고 있습니다 |
| 로깅 인터셉터 | 제외 | 스트리밍 응답에 개입하지 않게 합니다. 진단은 Crashlytics 배선이 맡습니다 |

`AuthInterceptor`는 그대로 씁니다 — 응답 코드만 보고 본문을 읽지 않으며, 401 재시도는 스트림이
시작되기 전이라 턴을 두 번 만들지 않습니다. 토큰이 없어 던지는 `SessionUnavailableException`은
`IOException`이라 `onFailure`로 들어오고 `DomainError.Unauthorized`로 옮깁니다.

### 2. Repository는 cold `Flow<ChatStreamEvent>`를 돌려주고 공유하지 않습니다

```text
fun streamTurn(chatId, userInput, userSource, sourceTurnId, choiceOrder): Flow<ChatStreamEvent>
fun regenerateTurn(chatId, turnId): Flow<ChatStreamEvent>
```

- **cold입니다.** 구독이 곧 요청이라 `shareIn`·`stateIn`을 쓰지 않습니다. 재구독이 새 턴을 만들면
  안 되므로 구독자는 ViewModel의 Job 하나뿐이고, 화면은 이 Flow를 직접 보지 않습니다.
- `callbackFlow`로 감싸고 `awaitClose { eventSource.cancel() }`을 둡니다. 취소가 곧 스트림 abort입니다.
- 리스너 콜백은 OkHttp 스레드에서 불리므로 `trySendBlocking`으로 보냅니다. 소비가 밀리면 소켓 읽기가
  같이 느려지는 것이 옳은 역압이고, **이벤트를 버리지 않습니다**(`DROP_OLDEST` 금지 — 토큰이 사라집니다).
- `flowOn(ioDispatcher)`로 구독 시작이 메인에서 일어나지 않게 합니다.
- **예외를 던지지 않습니다.** 실패도 이벤트로 흘려 상위가 `DomainResult`와 같은 규칙(예외 아님)으로
  받습니다. 취소만 예외로 전파합니다.
- **취소를 실패로 흘리지 않습니다**(가장 큰 위험). `EventSource.cancel()`은 `onFailure`를 부르므로
  그대로 두면 방을 나갈 때마다 `Failed`가 하나 흘러 "응답 생성에 실패했어요"가 뜹니다. 소스가 취소
  플래그를 들고 `onFailure`에서 먼저 확인해 **아무 이벤트도 만들지 않고 채널만 닫습니다.** 웹도 같은
  자리에서 `signal.aborted`를 확인합니다. 이 분기를 테스트로 고정합니다.

`:core:domain`에 두는 타입입니다.

```text
sealed interface ChatStreamEvent {
  data object Started
  data class  Token(text)
  data class  CharacterImage(name, imageUrl)
  data object Completed          // aiOutput 은 싣지 않는다 — 화면이 쓰지 않는다
  data class  Failed(error, message)
  data object Interrupted        // completed·error 없이 끝남
}
```

Flow는 취소를 빼면 항상 종단 이벤트 하나(`Completed`·`Failed`·`Interrupted`)로 끝납니다.

### 3. 토큰 이벤트는 프레임 예산 단위로 합쳐 dispatch합니다

`MviViewModel`은 이벤트 하나마다 `reduce`를 돌리고 상태가 바뀌면 recomposition이 일어납니다. 토큰을
그대로 흘리면 초당 수십~수백 번 상태가 바뀌고, 자라는 본문을 매번 다시 파싱해 비용이 길이의 제곱이 됩니다.

- **ViewModel이 토큰을 모아 일정 간격마다 한 번** `TokenBatchAppended`로 dispatch합니다. 배칭 위치가
  ViewModel인 이유는 Repository가 UI 프레임 예산을 알 필요가 없고, 가상 시간으로 테스트할 수 있기 때문입니다.
  **간격 50ms는 초기값입니다** — 프레임 하나(16.7ms)보다 크게 잡아 recomposition을 초당 20회로 묶는다는
  근거만 있고 측정한 값이 아닙니다. 실제 값은 `performance` 태그 검증에서 확정합니다.
- **이미지·종단 이벤트가 오면 모아 둔 토큰을 먼저 흘린 뒤** 그 이벤트를 dispatch합니다. 순서가 뒤집히면
  이미지가 잘못된 문단에 끼어듭니다.

### 4. 강조 파싱 결과를 조각 단위로 캐시합니다

`storyAnnotatedString`은 recomposition마다 전문을 다시 파싱합니다. 스트리밍 본문은 계속 자라므로
`remember(text)`로 캐시하고, 확정 턴은 리스트 항목 안에서 각자 캐시합니다. 측정 없이 구조를 바꾸지는
않지만 재파싱이 길이의 제곱이 되는 것은 구조상 확실해 캐시만 넣습니다.

### 5. 상태 수명

| 상태 | 위치 | 근거 |
| --- | --- | --- |
| 프롤로그·확정 턴·추천 입력 | ViewModel | 서버에서 다시 받을 수 있는 화면 데이터 |
| 진행 중 조각·`isStreaming`·재생성 대상 턴 | ViewModel | 회전에서 스트림이 끊기면 안 됩니다 |
| 컴포저 초안(일반 텍스트·블럭 목록) | ViewModel | 회전에서 보존. **프로세스 종료 뒤에는 복원하지 않습니다** |
| 커서 위치·포커스·블럭 삭제 확인 대상 | ViewModel | 구성 변경에서 되돌아가면 안 되는 진행 상태(§3-3-5) |
| 입력 모드·추천 토글·힌트 열람 | DataStore `@DeviceDataStore` | 기기 설정 |

**컴포저 초안을 영속 저장하지 않습니다.** 웹도 새로고침에서 잃고, 저장하면 사용자 귀속 저장소가 하나
늘어 세션 정리 계약에 들어와야 합니다. 제작 퍼널의 임시 저장은 여러 화면을 거치는 긴 작업이라 값이
있었지만, 채팅 입력은 한 화면 안의 짧은 작업입니다.

### 6. 스트림은 화면이 아니라 ViewModel 수명입니다

`viewModelScope`의 Job으로 돌립니다. 회전으로 컴포저블이 사라져도 스트림은 이어지고, 뒤로가기로
ViewModel이 사라질 때 취소돼 `awaitClose`가 스트림을 끊습니다 — 웹의 "방 이탈 시 abort"와 같은 지점입니다.
백그라운드 전환에서는 끊지 않습니다. 앱 종료 뒤까지 이어야 할 작업이 아니므로 WorkManager를 쓰지 않습니다.

### 7. 새 DataStore 키는 기기 귀속이라 세션 정리에 참여하지 않습니다

입력 모드·추천 토글·힌트 열람 셋 모두 계정이 아니라 기기의 설정입니다(웹도 브라우저 단위 저장).
`DeviceIdStore`와 같은 `@DeviceDataStore` 파일을 쓰고 **`UserScopedStore`를 구현하지 않습니다.**
언어·테마와 같은 분류이며, 이번 기능으로 사용자 귀속 저장소는 늘지 않습니다. 그 사실을 저장소 KDoc에 남겨
다음 감사에서 누락으로 오인하지 않게 합니다.

### 8. 402·409는 새 오류 타입을 만들지 않고 `Server.status`로 가릅니다

스토리 상세가 404를 같은 방식으로 다룹니다. 새 타입을 만들면 `DomainErrorMessages`가 채팅 전용 문구를
알게 되고, 오류 타입이 화면 수만큼 늘어납니다.

**402 응답 본문의 `code`를 읽지 않습니다.** 웹은 게스트 체험 한도와 이프 부족을 `code`로 갈라야 했지만
앱은 로그인 필수라 402의 사유가 이프 부족 하나뿐입니다(§3-3-1). 상태 코드만으로 충분하므로
`onFailure`가 넘겨준 응답 본문을 읽는 경로를 만들지 않고, `Accept`에 `application/json`을 함께 싣는 것은
서버가 오류를 SSE가 아닌 동기 응답으로 내도록 하는 목적만 남습니다.

### 9. 삭제는 본문 없는 성공이고 404를 성공으로 접습니다

`DELETE /chats/{chatId}`는 204라 `emptyBodyApiCall`을 씁니다. **404는 Repository에서 성공으로 바꿉니다** —
이미 지워진 채팅을 지우려 한 것이라 사용자가 할 일이 없고, 실패로 올리면 화면이 지워진 채팅을 계속 띄웁니다.
그 밖의 실패는 그대로 올려 화면이 안내와 함께 방을 유지합니다.

### 10. 자동 재시도를 넣지 않습니다

턴 진행은 멱등하지 않은 쓰기이고 서버와 멱등 키 계약이 없습니다. EOF·네트워크 실패는 **저장 여부가
불명**이라 재요청 대신 상세 재조회로 확정 상태를 가져옵니다. 사용자가 다시 보내는 것은 재시도가 아니라
새 턴입니다.

### 11. 선택지 생성 실패는 턴을 실패로 만들지 않습니다

별도 요청이고 무료입니다. 실패는 선택지 자리에만 표시하고 재시도 버튼을 둡니다. 진행 상태를 **대상
turnId에 귀속**시키고 최신 요청 turnId를 들고 있어, 늦게 끝난 응답이 최신 상태를 덮지 않게 합니다.

### 12. 경쟁 상태와 막는 방법

| 경로 | 처리 |
| --- | --- |
| 전송 연타 | 진행 중 Job을 ViewModel이 확인해 두 번째 요청을 버립니다. **UI 비활성화에만 기대지 않습니다** |
| 스트림 완료 ↔ 상세 재조회 | 스트리밍 블록이 전송 시점의 확정 턴 수를 들고, 재조회로 턴이 늘어난 렌더에서 함께 숨겨 한 프레임에 교체합니다 |
| 재생성 중 새 턴 도착(409) | 기존 본문 복원이 아니라 재조회 |
| 선택지 생성 중 새 턴 시작 | turnId 귀속이라 낡은 상태가 자연히 빠집니다 |
| 삭제 ↔ 진행 중 스트림 | 삭제를 확정하기 전에 스트림을 취소합니다 |

### 13. 채우기 버튼은 32dp 시각 크기 대신 48dp 터치 타깃을 씁니다

하네스 §1-2-4은 채우기 버튼을 "32dp 정사각"으로 적었지만, 앱의 다른 아이콘 버튼은 모두
`sizes.control`(48) 안에 `sizes.icon`(20) 글리프를 두는 배치입니다. 배경이 없어 **보이는 것은 글리프뿐**
이라 웹과 같은 크기로 읽히고, 터치 타깃만 최소 크기를 지킵니다. 메시지 목록 안에 32dp 타깃을 두면
이 화면에서만 최소 크기가 깨집니다. 10단계에서 하네스 값을 48로 맞춥니다.

### 14. 삭제 후 복귀는 탭 선택을 셸 밖으로 올려서 만듭니다

"채팅 탭으로 돌아간다"를 지키려면 방을 걷어내는 것만으로는 모자랍니다 — 상세에서 시작한 채팅은
뒤로가기가 상세로 돌아가고, 제작 완료로 들어온 채팅은 제작 탭이 펼쳐집니다. 선택 탭 상태를
`MainTabsScreen` 안의 `rememberSaveable` 에서 `MainNavDisplay` 로 올려, 삭제 성공이 셸 위 목적지를
모두 걷어낸 뒤 채팅 탭을 고르게 합니다. 탭 전환은 여전히 이력에 쌓이지 않습니다.

### 15. 파괴적 확인 다이얼로그를 `:core:ui` 로 올립니다

제작 탭 스토리 삭제에 이어 두 번째 사용처가 생겼습니다. 자리·색·잠금 규칙이 앱 전체에서 같아야
하는 표기라(§3-3-5) 문구만 받는 `ManyakDestructiveDialog` 하나로 모읍니다.

### 16. 상단 앵커는 목록 끝 패드 하나와 규칙 둘로 만듭니다

웹은 스페이서·ResizeObserver 재앵커 루프·제스처로 앵커를 끊는 모드 상태기계·`overflow-anchor:none`까지
동원하지만, 앱에 옮겨야 하는 것은 **자리를 만드는 것과 회수하는 것 둘뿐**입니다. 웹의 나머지는 DOM
스크롤이 픽셀 기준이라 생긴 부채이고, `LazyListState`는 (인덱스, 오프셋)으로 위치를 잡아 조각이 붙어도
앵커가 저절로 유지됩니다. 되잡는 루프가 없으니 사용자 스크롤과 싸울 일도 없습니다.

목록 끝 항목(`bottom`)의 높이를 상태로 만들고 두 규칙을 겁니다.

| 시점 | 패드 높이 |
| --- | --- |
| 스트리밍 중 | 앵커부터 목록 끝까지가 한 화면에 모자란 만큼(양방향) |
| 스트리밍 뒤 | `min(현재, 패드 중 화면 안에 남은 높이)`(줄이기만) |

두 번째 규칙이 웹의 `computeCollapsedSpacerHeight`와 같은 식입니다 — 웹의 `slack`을 대입하면
`max(0, 패드 − slack) = 뷰포트 끝 − 패드 시작`으로 정리됩니다. 표현만 목록 좌표계로 바뀝니다.

기기에서 확인하고 고친 것이 셋입니다.

- **자리 만들기와 앵커는 한 측정 패스에 넣습니다.** 패드 높이를 쓴 직후 `scrollToItem`을 부르면
  `forceRemeasure`가 아직 옛 높이(20dp)로 재고 있어 바닥에 걸리고, 레이아웃을 기다렸다 부르면 그 사이
  프레임에 옛 위치가 한 번 그려져 화면이 깜빡입니다. `requestScrollToItem`(다음 측정에 미뤄 적용)으로
  둘을 같은 패스에 넣습니다.
- **첫 앵커는 `LaunchedEffect`가 아니라 `SideEffect`에서 겁니다.** 효과 코루틴은 다음 프레임에 돌아
  전송 프레임이 옛 위치로 그려집니다(위와 같은 깜빡임). `SideEffect`는 컴포지션 적용 직후·그리기 전에
  동기로 돌아 전송이 그려지는 프레임 안에서 앵커됩니다.
- **뷰포트 높이가 바뀌면 다시 잡아야 합니다.** 키보드가 내려가 화면이 커지면 깔아 둔 자리가 한 화면에
  모자라져 목록이 앵커를 놓고 되돌아갑니다. 이때는 최소치가 아니라 **한 화면을 통째로** 깝니다 —
  키보드 애니메이션은 높이가 프레임마다 이어서 변해, 계산한 최소치가 다음 레이아웃에서 이미 모자랍니다.

같은 이유로 `KeepReadingPosition`(줄어든 높이만큼 스크롤을 밀어 아래쪽 줄을 붙잡는 보정)은 앵커가 잡혀
있는 동안 멈춥니다. 붙잡아야 할 줄이 아래가 아니라 위입니다. 앵커가 풀리면 새 높이를 기준만 다시 잡고
시작해 밀린 값이 남지 않습니다.

### 17. 스트리밍 본문은 글자 단위 타자기로 드러냅니다

결정 3의 배칭은 상태 갱신을 초당 스무 번으로 묶지만, 그대로 그리면 글이 50ms 덩이로 나타납니다. 웹은
토큰 도착 즉시 반영하는데 토큰이 몇 자 단위라 글자 단위로 보입니다 — 같은 인상을 배칭을 유지한 채
만들기 위해, 도착한 전체를 목표로 두고 프레임마다 공개 수를 올리는 타자기 레이어를 UI에 얹습니다.

- **공개 걸음은 시정수 기반입니다** — `밀린 분량 × 프레임 경과 / 시정수`(하한 1자). 처음에 쓴 "밀린
  분량 ÷ 8"(시정수 약 0.13초)은 서버 청크(Gemini가 문장 단위 수십 자를 수백 ms 간격으로 흘림)를 몇
  프레임에 쏟아내고 다음 청크까지 멈춰 "촤르륵 → 멈춤"이 반복됐습니다 — 기기에서 뭉텅뭉텅으로 확인된
  원인. 진행 중 시정수를 청크 간격보다 긴 0.8초로 잡아 청크 사이를 글자로 잇고, 새 배치가 0.25초 없으면
  스트림이 멈춘 것으로 보고 0.13초 시정수로 빠르게 비워 확정 교체 전에 남은 글을 소화합니다.
- 이미지는 공개 단위 1을 차지해 앞 텍스트가 모두 드러난 뒤에야 나타납니다 — 반만 보여 줄 방법이 없고,
  앞당기면 아직 안 읽은 문단 뒤의 이미지가 먼저 보입니다.
- 공개 수는 `rememberSaveable`이라 회전해도 읽던 위치부터 이어집니다. **목록이 같은 키의 항목 상태를
  항목이 사라져도 보존하므로**, 다음 전송의 새 스트리밍 블록에 이전 턴의 공개 수가 그대로 복원됩니다 —
  새 총량보다 크면 "다 공개됨"으로 판정돼 두 번째 턴부터 타자기가 통째로 건너뛰는 버그가 있었습니다.
  스트림 안에서 총량은 줄지 않으므로, 총량을 넘어선 공개 수는 새 스트림의 시작으로 보고 0으로 되돌립니다.
- 강조 마커는 닫히기 전까지 원문(`*`)으로 보이다가 닫히면 스타일로 바뀝니다 — 웹과 같은 동작입니다.
- 프레임 단위 재구성은 밀린 분량이 있는 동안만 돌고, 결정 4의 파싱 캐시는 공개 중인 마지막 조각에서만
  깨집니다.


### 18. 재생성은 응답 뒤 쿨다운으로, 선택지 스위치는 디바운스로 연타를 거릅니다

스트림이 끝난 직후의 재생성 연타는 AI 호출과 이프 차감을 곧바로 반복합니다. 스트림 Job 이 끝나는 자리에서
0.5초짜리 쿨다운 Job 을 띄우고 `startTurn` 이 재생성일 때만 그 Job 이 살아 있으면 버립니다 — 이어쓰기는
새 입력이라 막지 않습니다. 선택지 스위치는 켜기·끄기마다 생성 요청이 나가므로 스위치·저장은 즉시 바꾸고
생성만 0.5초 디바운스 Job 으로 미뤄 마지막 값으로 한 번 보냅니다. 선택지는 로컬 상태라 좋아요와 달리
선반영 제약이 없어 디바운스가 맞습니다. 스토리라인 재생성도 같은 쿨다운을 생성 스토어의 실행 Job 에 둡니다.

## 외부 합의가 필요한 항목

**없습니다.** 서버 계약(`turns/stream`·`turns/regenerate/stream`·`turns/{turnId}/choices`·`DELETE /chats/{id}`)은
모두 확정·구현 상태입니다. 배경 이미지 마커만 서버·AI 미구현이라 범위에서 뺐습니다.

## 구현 순서

1·2단계는 서로 막지 않아 병렬 가능합니다.

1. **데이터 계층 스트리밍** — `okhttp-sse` 의존성 추가, `ChatStreamEvent`, `ChatRepository`에
   `streamTurn`·`regenerateTurn`·`generateChoices`·`deleteChat` 추가, `@SseClient` provider와
   `ChatSseSource`, 요청 DTO, `ChatRepositoryImpl` 확장.
   `okhttp-sse`가 `event:`·`data:` 프레이밍을 이미 처리하므로 **앱이 소유하는 로직은 이름·데이터 →
   `ChatStreamEvent` 매핑 하나입니다.** 그 매핑을 순수 함수로 떼어내 테스트하고 **MockWebServer를
   도입하지 않습니다** — 서버를 세워 검증되는 것은 라이브러리 자신의 프레이밍입니다.
   *완료 조건* — `started`·`token`(JSON·평문 둘 다)·`character_image`(둘 중 하나라도 없으면 버림)·
   `completed`·`error`·알 수 없는 이름의 매핑과, 취소·EOF·비2xx 세 종료 분기가 단위 테스트로 고정됩니다.
2. **기기 설정 저장소** — `ChatPreferencesStore`(입력 모드·추천 토글·힌트 열람), 도메인 계약, DI 배선.
   *완료 조건* — 기본값(블럭 모드·추천 on·힌트 미열람)이 저장 실패에서도 유지됩니다.
3. **컴포저** (KNK-899) — 블럭·일반 모드 UI와 직렬화·파싱 순수 함수, 전송 조건·잠금·아이콘 3상태.
   **추천 입력 유무는 `hasSuggestions` 파라미터로 받습니다** — 값을 공급하는 쪽은 6단계이고, 여기서
   목록을 알면 6단계에서 컴포저를 다시 뜯게 됩니다.
   *완료 조건* — 직렬화 구분자 두 갈래와 모드 전환 왕복이 단위 테스트로 고정되고, `hasSuggestions`·
   `isStreaming`·입력 유무 조합에서 전송 버튼의 활성 여부와 아이콘이 스펙 표와 일치합니다.
4. **렌더 정합** (KNK-1033 + 밴드 교체) — 말풍선을 밴드로 교체, `spacing.passage` 적용, 인물 이미지
   조각 렌더와 마커 파싱, 진입·앵커·하단 이동 스크롤. 3단계와 병렬 가능하지만 5단계보다 앞서야 합니다 —
   스트리밍 블록이 같은 렌더러를 씁니다.
   *완료 조건* — 마커 인정 조건 셋과 CDN 검사가 단위 테스트로 고정되고, 확정 턴 렌더가 웹과 같은 값입니다.
5. **턴 진행** (KNK-900·902·903) — 토큰 배칭, 낙관적 밴드, "작성 중" 자리표시자, 원자적 교체, 중단 분기.
   *완료 조건* — 가짜 Repository로 완료·`error`·EOF·취소 네 경로의 상태 전이가 검증됩니다.
6. **추천 입력·선택지** (KNK-901) — 소스 분기, 즉시 전송·채우기, 초안 덮어쓰기 확인, `userSource`
   판정, 선택지 생성 요청과 골격·실패 상태.
   *완료 조건* — `userSource` 네 갈래와 `choiceOrder` 1-base 변환이 단위 테스트로 고정됩니다.
7. **재생성·엔딩 배지** (KNK-904·1034) — 표시 조건 넷, 네 실패 분기, `reachedEnding` 배지.
8. **삭제** (KNK-1035) — 헤더 옵션 메뉴·확인 다이얼로그·204·404·복귀 백스택. 헤더 오른쪽에는 이 메뉴 하나만 둡니다(공유 버튼 비적용). 메뉴는 KNK-1320에서 바텀 시트로 바뀌었습니다(아래).
9. **이프 402** (KNK-906) — 스트림 열기 전 동기 402 처리와 낙관적 밴드 제거.
10. **하네스 갱신** — §3-3-3 매트릭스 FE-SCREEN-005 행과 FLOW-002·004·006을 구현 상태로 옮깁니다.

## 검증

`android-change-verification`에 넘길 위험 태그입니다.

| 태그 | 무엇 때문에 |
| --- | --- |
| `coroutine-lifecycle` | 스트림 수명과 취소 지점(ViewModel 수명·`awaitClose`) |
| `flow-sharing` | cold Flow 유지와 공유 금지 |
| `state-restore` | 컴포저 초안·진행 중 스트림의 구성 변경 |
| `race-condition` | 원자적 교체·선택지 turnId 귀속·전송 연타 |
| `failure-recovery` | `error`·EOF·409·402 분기 |
| `performance` | 토큰 배칭과 강조 파싱 캐시 |
| `data-retry` | SSE 클라이언트의 읽기·전체 상한 |

`session-cleanup`은 넘기지 않습니다 — 새 저장 지점이 모두 기기 귀속이라 정리 계약이 바뀌지 않습니다(결정 7).

수동 확인은 5·6·7단계가 끝난 뒤 에뮬레이터에서 한 번에 봅니다.

- 실제 스트리밍 진행과 **방을 나갈 때 실패 안내가 뜨지 않는지**(결정 2의 취소 분기)
- 회전 중 스트림 유지와 컴포저 초안 보존
- 키보드가 올라온 상태의 블럭 목록, 큰 글자에서의 컴포저 높이 상한
- **같은 채팅을 웹과 나란히 놓고 확정 턴 렌더 비교** — 4단계의 "웹과 같은 값"은 캡처 대조로만 확인됩니다

## KNK-1317 실시간 이미지 수신 경로·토글·설정 시트 (2026-09-17)

- 티켓: [KNK-1317](https://kimandkang.atlassian.net/browse/KNK-1317) (부모 KNK-1315)
- 브랜치: Android·하네스 모두 `feat/KNK-1317-chat-realtime-image-settings-sheet`, 분기 기준 fetch한 `origin/dev` — Android `ecf50831`, 하네스 `4b34221`
- 계약: [채팅 설정 시트와 메뉴 시트](../../../knk-harness/docs/spec/3-1-client-spec.md#채팅-설정-시트와-메뉴-시트), [인물 이미지 렌더](../../../knk-harness/docs/spec/3-1-client-spec.md#응답-재생성과-채팅-이미지), Swagger `ContinueChatRequest`·`RegenerateChatRequest`의 `realtimeImage`
- 제외: 로딩 표현·스크롤 높이 유지(KNK-1318), 이프 비용 배지·안내 팝오버(KNK-1319), 채팅 메뉴 시트(KNK-1320)

### 변경

1. `CharacterImage`의 허용 경로에 `/chat-images/`를 더했습니다. 서버가 보내던 실시간 인물 이미지가 그동안 허용 검사에서 버려져 저장 마커가 평문으로 남던 것이 이 한 줄의 원인이었습니다.
2. `ChatTurnStreamRequestDto`·`ChatRegenerateRequestDto`에 `realtimeImage: Boolean`을 **기본값 없이** 두었습니다. 서버 기본이 켬이라 빠뜨리면 끈 사용자에게도 이미지가 만들어지므로 컴파일러가 명시를 강제하게 했습니다. `ChatRepository.streamTurn/regenerateTurn`과 `ChatSseSource` 본문 테스트까지 같은 값을 실어 보냅니다.
3. `ChatPreferencesRepository`에 `realtimeImageEnabled`(기기 단위, 기본 켬, 키 `chat_realtime_image_enabled`)를 더했습니다. 결정 7 그대로 `UserScopedStore`에 참여하지 않습니다.
4. `ChatRoomViewModel`은 `startTurn`에서 지금 값을 한 번 읽어 스트림 람다와 `StreamingTurn.realtimeImage`에 같이 넘깁니다. 응답을 받는 중에 토글을 바꿔도 진행 중 턴은 전송 시점 값을 유지하고 다음 턴부터 새 값을 씁니다(KNK-1318의 로딩 표현이 이 스냅샷을 읽습니다).
5. 컴포저 툴바의 드롭다운 둘(`ComposerMenu`)을 설정 아이콘 버튼 하나("채팅 설정")로 바꾸고 `ManyakBottomSheet` 기반 `ChatSettingsSheet`를 만들었습니다. 마이 메뉴 행 배치(아이콘 20dp · 라벨 · 설명 · 오른쪽 스위치)로 "채팅 기능"(실시간 이미지, AI 추천 입력)·"입력 모드"(블럭 입력) 두 그룹이고, 행 전체가 `Role.Switch` `toggleable`(`indication = null`)이며 스위치는 표시만 맡습니다. 닫기 버튼은 두지 않습니다 — 확정할 것이 없는 시트라 스크림·끌어내리기·뒤로가기로만 닫습니다(2026-09-17 사용자 결정, 티켓의 닫기 버튼 서술을 대체). 열림 상태는 `ChatRoomLoaded`의 `rememberSaveable`이 들어 입력 모드가 바뀌어 컴포저가 갈려도 시트가 남습니다.
6. 두 번째 사용처가 생겨 알림 설정의 `PushSwitch`를 `designsystem/ManyakSwitch`로 올렸습니다. 시트 아이콘 `ic_ai_image`·`ic_ai_chat`·`ic_form`(hugeicons `ai-image`·`ai-chat-02`·`form`, 웹과 동일)을 추가하고 쓰임이 없어진 `ic_pen_sparkle`과 드롭다운 문자열 10개를 지웠습니다.
7. 실시간 이미지 행의 이프 비용 배지·안내 팝오버 자리는 비워 두었습니다(KNK-1319).

### 검증

```bash
./gradlew :chat:ktlintCheck :chat:detekt :chat:testDebugUnitTest \
  :designsystem:ktlintCheck :designsystem:detekt :designsystem:testDebugUnitTest \
  :notification:ktlintCheck :notification:detekt :chat:compileDebugAndroidTestKotlin installDebug
```

- 단위 테스트 통과(chat 145, designsystem 5). 새 `ChatRoomSettingsTest` 5개 — 저장값 진입 반영, 끔 → 요청 `false`·기기 저장, 기본 켬 → 요청 `true` 명시, 스트리밍 중 토글 변경에도 `StreamingTurn.realtimeImage` 유지·다음 턴부터 새 값, 재생성 요청의 값. `ChatSseSourceTest` 본문 기대값에 `realtimeImage`를 더했고 `ChatMessageSegmentsTest`에 `/chat-images/` 허용·빈 경로 거부 케이스를 더했습니다.
- `ChatComposerKeyboardTest`(androidTest)에서 드롭다운 단계를 뺐습니다. 설정 버튼은 모달 시트를 열므로 키보드 유지 대상이 아닙니다. 컴파일만 확인했고 실행하지 않았습니다.
- 에뮬레이터(Pixel, emulator-5554, 회원 로그인) 캡처 — `always_finish_activities`는 기본(null)이었습니다.
  - 저장 마커 `[[…/chat-images/…]]`가 있던 방 "0호선"에서 이전에 평문으로 남던 자리에 차민재 인물 이미지가 그려집니다: [stored-chat-image.png](../../../captures/knk-1317/stored-chat-image.png)
  - 설정 시트 열림·세 스위치 켬: [settings-sheet-open.png](../../../captures/knk-1317/settings-sheet-open.png) → 블럭 입력·실시간 이미지 끔(시트 유지): [settings-sheet-toggled.png](../../../captures/knk-1317/settings-sheet-toggled.png) → 닫은 뒤 컴포저가 일반 입력: [composer-plain-after-toggle.png](../../../captures/knk-1317/composer-plain-after-toggle.png)
  - 시트를 연 채 회전·다크 모드 전환에서 시트와 스위치 값 유지: [settings-sheet-rotated.png](../../../captures/knk-1317/settings-sheet-rotated.png), [settings-sheet-dark.png](../../../captures/knk-1317/settings-sheet-dark.png). 회전·야간 모드는 검사 후 원래대로 되돌렸습니다.
  - 방을 나갔다 다시 들어와도 끔 값이 유지되고(uiautomator `checked` false/true/false), 다시 켠 뒤 추천 입력 랜덤 전송으로 실제 턴 하나를 보냈습니다. 약 20초 뒤 새 턴의 본문 위치에 실시간 인물 이미지(차민재, 새 표정)가 그려지고 확정 후 재생성 버튼·선택지 3개가 나타났습니다: [realtime-turn-image.png](../../../captures/knk-1317/realtime-turn-image.png), [realtime-turn-confirmed.png](../../../captures/knk-1317/realtime-turn-confirmed.png). 이 턴은 dev 서버에 저장됐고 이프가 차감됐습니다.
- 코드로만 판단한 것: 요청 본문의 `realtimeImage` 값(SSE 로깅 인터셉터가 없어 기기에서는 보지 못했고 `ChatSseSourceTest`·`ChatRoomSettingsTest`가 고정), 실시간 이미지 끈 채 보낸 턴에 이미지가 오지 않는지(이프를 더 쓰지 않으려 보내지 않음), 글자 크기 변경.

### 복구

되돌리면 허용 경로·요청 필드·설정 키·시트가 함께 빠지고 드롭다운 둘로 돌아갑니다. `chat_realtime_image_enabled` 키는 남아도 읽는 곳이 없어 무해합니다. 서버 계약·저장 스키마 변경은 없습니다.

## KNK-1318 실시간 이미지 로딩 표현과 스트리밍 블록 높이 유지 (2026-09-18)

- 티켓: [KNK-1318](https://kimandkang.atlassian.net/browse/KNK-1318) (부모 KNK-1315)
- 브랜치: `feat/KNK-1318-chat-stream-loading-height`, 분기 기준 fetch한 `origin/dev` `b61d3704`
- 계약: [렌더와 스크롤](../../../knk-harness/docs/spec/3-1-client-spec.md#렌더와-스크롤)(부분 렌더·새 턴 앵커), 메뉴 시트 AI 안내 문구(`CHAT_AI_NOTICE`)
- 제외: 앵커·패드 회수·진입 위치·하단 이동 버튼(현재 구현 유지), 토글·설정 시트(KNK-1317), 비용 배지(KNK-1319)

### 변경

1. `StreamingBlock`을 `ChatStreamingBlock.kt`로 떼어내고(파일당 함수 수 제한) 로딩과 본문이 **같은 `Box` 자리**를 쓰게 했습니다. 첫 조각(글자든 이미지든)이 오면 로딩은 `AnimatedVisibility` `fadeOut`(150ms)으로 그 자리에서 빠지고 본문이 그 아래에서 드러납니다. 이미지가 토큰보다 먼저 오면 `revealed`에 이미지 조각이 먼저 들어와 같은 경로로 교체됩니다.
2. **로딩 높이를 최소 높이로 유지합니다.** 로딩이 그려지는 동안 `onSizeChanged`로 잰 높이를 `rememberSaveable`에 두고, 본문이 보이는 동안 `heightIn(min)`으로 겁니다. 웹의 `useLayoutEffect` minHeight와 같은 시점(로딩이 흐름에 있는 동안 측정)입니다. 목록이 같은 키(`streaming`·턴 id)의 저장 상태를 다음 전송에도 돌려주지만 새 스트림은 늘 로딩부터 그려 값을 다시 잽니다. 재생성은 같은 `StreamingBlock`이라 동일합니다.
3. 실시간 이미지가 켜진 채 보낸 턴(`StreamingTurn.realtimeImage`, KNK-1317 스냅샷)은 `ScenePlaceholder` — 순환 문구 4개(4초 주기, `chat_room_scene_phrases`, 본문과 같은 `bodyReading` 서체 — `CyclingPhrases`에 `style` 파라미터를 더해 제작 퍼널은 기본 `bodyMedium` 유지) 아래 `spacing.passage`(20dp) 간격으로 `ImageGenerationLoading`(4:3, `shapes.overlay` — `CharacterImage`와 같은 실루엣)을 둡니다. 보조기술에는 `clearAndSetSemantics`로 "다음 장면을 만들고 있어요" 하나만 읽힙니다. 꺼진 턴은 기존 `WritingPlaceholder`(MaruBuri 2초 시머) 그대로이고 문구만 `chat_room_writing` "다음 내용 준비 중"으로 바꿨습니다(라벨 "답변을 작성하고 있어요" 유지).
4. 순환 문구는 `create`의 `CyclingPhrases`(점 3개 + 글자 단위 교차 + 4초 시머)를 두 번째 사용처가 생겨 `designsystem/CyclingPhrases.kt`로 올렸습니다. `create`는 import만 바뀝니다. `CharacterImage`의 `CHARACTER_IMAGE_ASPECT_RATIO`(4:3)를 공개해 로딩 자리가 같은 값을 읽습니다. KNK-1323이 만든 `ImageGenerationLoading`을 그대로 재사용했으며 새 4:3 컴포넌트는 만들지 않았습니다.
5. 대화 최상단(프롤로그 위)에 `chat_room_ai_notice` "이 채팅은 AI로 생성된 가상의 내용이에요"를 `bodySmall`·`textSubtle`·가운데 정렬로 추가했습니다(항목 키 `ai-notice`, 위 여백 `passage`). 항목이 하나 늘어 `AnchorStreamingTurn`의 `prologueCount`를 `headerCount`(안내 + 프롤로그)로 바꿔 재생성 앵커 인덱스가 맞게 했습니다. 앵커·패드 로직 자체는 손대지 않았습니다.

### 검증

```bash
./gradlew :designsystem:ktlintCheck :designsystem:detekt :create:ktlintCheck :create:detekt \
  :chat:ktlintCheck :chat:detekt :chat:compileDebugKotlin :create:compileDebugKotlin \
  :chat:testDebugUnitTest :designsystem:testDebugUnitTest :create:testDebugUnitTest installDebug
```

- 단위 테스트 통과(chat 153, designsystem 5, create 114). 새 테스트는 없습니다 — 바뀐 것이 모두 Compose 레이아웃·애니메이션이라 순수 함수로 뗄 로직이 없었습니다.
- 에뮬레이터(Pixel, emulator-5554, 방 "0호선", `always_finish_activities` null) — `adb exec-out screencap`을 초당 약 8장으로 연속 캡처해 **사용자 밴드 영역(y 250–650)의 픽셀 해시가 전송부터 확정까지 한 번도 바뀌지 않는 것**으로 앵커 유지를 판정했습니다.
  - AI 안내 문구(최상단, 프롤로그 위): [ai-notice-top.png](../../../captures/knk-1318/ai-notice-top.png)
  - 실시간 이미지 켬(80 이프): 순환 문구 + 4:3 점 패턴 [realtime-on-loading.png](../../../captures/knk-1318/realtime-on-loading.png) → 로딩이 반투명으로 빠지며 본문 첫 줄이 비치는 프레임 [realtime-on-fade.png](../../../captures/knk-1318/realtime-on-fade.png) → 본문 타자기 [realtime-on-body.png](../../../captures/knk-1318/realtime-on-body.png) → 확정 [realtime-on-done.png](../../../captures/knk-1318/realtime-on-done.png). 400프레임(45초) 동안 밴드 영역 해시 1종. 문구가 6초 시점에 "다음 내용 준비 중"으로 바뀐 것도 확인.
  - 실시간 이미지 끔(20 이프): "다음 내용 준비 중" 시머 [realtime-off-loading.png](../../../captures/knk-1318/realtime-off-loading.png) → 본문 [realtime-off-body.png](../../../captures/knk-1318/realtime-off-body.png) → 확정 [realtime-off-done.png](../../../captures/knk-1318/realtime-off-done.png). 250프레임 동안 밴드 영역 해시 1종.
  - 응답 재생성(끔, 20 이프): 대상 턴이 상단에 앵커된 채 로딩 → 본문 [regenerate-loading.png](../../../captures/knk-1318/regenerate-loading.png), [regenerate-body.png](../../../captures/knk-1318/regenerate-body.png). 200프레임 동안 밴드 영역 해시 1종 — 안내 문구 추가 뒤 `headerCount` 보정이 맞습니다.
  - 구성 변경(켬, 80 이프): 로딩 3초 시점 다크 모드 → 8초 시점 가로 회전 → 본문 도착 뒤 다크 해제. 세 번 재생성돼도 밴드는 상단에 남고 로딩 상태(순환 문구·점 패턴)가 이어졌습니다: [config-dark-loading.png](../../../captures/knk-1318/config-dark-loading.png), [config-landscape-loading.png](../../../captures/knk-1318/config-landscape-loading.png), [config-light-after-body.png](../../../captures/knk-1318/config-light-after-body.png), 세로 복귀 뒤 실시간 인물 이미지가 본문 자리에 그려진 상태 [config-restored-portrait.png](../../../captures/knk-1318/config-restored-portrait.png). 회전·야간 모드·`accelerometer_rotation`은 검사 후 원래 값(세로·no·1)으로 되돌렸습니다.
- `configuration-changes` 점검(코드): 로딩 높이는 `rememberSaveable`이라 본문이 보이는 중 재생성돼도 복원된 `padPx`와 어긋나지 않습니다. 회전은 폭이 바뀌어 저장한 높이가 새 폭의 로딩 높이와 다를 수 있지만, 뷰포트 높이가 바뀌면 `AnchorStreamingTurn`이 한 화면을 통째로 다시 깔아 되잡으므로 내려앉지 않습니다. `CyclingPhrases`의 문구 인덱스도 `rememberSaveable`입니다. 재생성마다 다시 도는 효과는 순환 타이머(4초부터 다시 셈)뿐이고 값을 초기화하는 효과는 없습니다.
- 코드로만 판단한 것: 이미지 조각이 토큰보다 먼저 오는 경우(이번 네 턴은 모두 텍스트가 먼저 왔음), 본문이 로딩보다 짧아 최소 높이가 실제로 작동하는 경우(dev 응답이 모두 로딩보다 길어 최소 높이 없이도 내려앉지 않는 상황이었음), 가로에서의 로딩→본문 전환 화면(컴포저가 뷰포트를 거의 다 차지해 밴드만 보임), 큰 글자, TalkBack 읽기.
- 이 검증으로 dev 서버의 "0호선" 방에 턴 4개(재생성 1회 포함)가 저장됐고 이프 260이 차감됐습니다.

### 복구

되돌리면 로딩 자리·최소 높이·안내 문구·문자열이 함께 빠지고 `CyclingPhrases`는 `create` 전용으로 돌아갑니다. 서버 계약·저장 스키마·DataStore 키 변경은 없습니다.

## KNK-1320 카드 옵션·상세 헤더·채팅 메뉴 시트 전환 (2026-09-18)

- 티켓: [KNK-1320](https://kimandkang.atlassian.net/browse/KNK-1320) (부모 KNK-1315)
- 브랜치: Android·하네스 모두 `feat/KNK-1320-options-bottom-sheets`, 분기 기준 fetch한 `origin/dev` — Android `cddd12df`, 하네스 `105b313`
- 계약: [카드 옵션 시트](../../../knk-harness/docs/spec/3-1-client-spec.md#fe-screen-013-제작--내-스토리-목록)(FE-SCREEN-013), [채팅 설정 시트와 메뉴 시트](../../../knk-harness/docs/spec/3-1-client-spec.md#채팅-설정-시트와-메뉴-시트), Swagger `POST /chats`(`startSettingId` 생략 시 첫 시작 설정). 결정: [A-042](../../../knk-harness/docs/adr/1-3-android-adr.md#a-042)
- 제외: 공유 열람 화면(웹 소유). 삭제 확인은 다이얼로그 그대로.

### 변경

1. `designsystem`에 `ManyakOptionItem`(48dp · 아이콘 20dp · 간격 12dp · destructive · 오른쪽 스피너)과 `ManyakOptionsSheet`(`ManyakBottomSheet` 위에 머리글 → 항목. 주 동작 버튼이 없는 시트라 닫기 버튼은 두지 않음 — 2026-09-18 사용자 결정, 티켓·`DESIGN.md`의 옵션 시트 닫기 서술을 대체)·`ManyakOptionsSheetHeader`(종류 소문 + 제목 한 줄)를 만들고, `ManyakOptionsDialog`·`ManyakOptionsMenu`와 카드 축소판(`MyStoryCardPreview`·`CreationProgressCardPreview`·`ChatCardPreview`, 카드의 `compact` 분기)을 지웠습니다. 축소판만 쓰던 `shapes.thumbnailSmall` 토큰도 함께 지웠습니다. `ManyakMoreButton`·`moreButtonTitleAlignment`은 `ManyakMoreButton.kt`로 옮겼습니다. `ManyakDestructiveDialog`에 `inProgressLabel`("삭제 중")을 더해 확정 스피너가 보조기술에 그 이름으로 읽히게 했고, 옵션→확인이 한 창을 나눠 쓰던 `ManyakDestructiveDialogContent`는 private 으로 접었습니다.
2. 제작 카드(`StudioDialogs`)·채팅 카드(`ChatListScreen`)는 `optionsTarget`이 있으면 시트, `deleteTarget`이 있으면 다이얼로그를 띄웁니다. ViewModel의 "삭제하기는 시트를 닫고 확인을 요청" 흐름은 이미 그대로라 상태·의도는 바꾸지 않았습니다. 머리글 종류는 "내가 만든 스토리" / "만들던 스토리"(초안·실패 요청) / "채팅"이고, 삭제된 스토리의 채팅은 제목 자리에 "삭제된 스토리"를 씁니다. 더보기·길게 누르기 진입은 유지합니다.
3. 스토리 상세 `StoryDetailHeaderMenu`는 더보기 아이콘 버튼 + 같은 시트("내가 만든 스토리" / "스토리")입니다. 열림은 컴포넌트 안 `rememberSaveable`입니다.
4. 채팅방 헤더는 더보기 버튼 하나(접근 이름 "채팅 메뉴", 아이콘은 `DESIGN.md` 규칙대로 가로 점 `ic_more_horizontal`)이고 `ChatMenuSheet`("채팅 메뉴")를 엽니다 — 내 이프 카드 → "새 채팅 시작하기"(`ic_comment_plus`) → "공유하기"(`ic_share`) → "신고하기"(`ic_alert_triangle`, 옵션 시트 4곳의 신고 항목 공통) → destructive "삭제하기". 참조 스토리가 삭제된 방(`storyId` 빈 문자열, 서버가 `orEmpty()`로 보냄)은 새 채팅·신고를 두지 않습니다. 신고·삭제·충전은 시트를 닫고 엽니다 — 시트는 별도 창이라 닫지 않고 이동하면 다음 화면 위에 남습니다.
   - **공유하기**(2026-09-18 사용자 결정으로 앱 비적용을 뒤집음, [A-043](../../../knk-harness/docs/adr/1-3-android-adr.md#a-043); 채팅 목록 카드 옵션 시트에도 같은 항목을 신고하기 앞에 둠 — `ChatListViewModel.share`, 성공 시 시트를 닫고 실패 시 유지): `POST /chats/{id}/shares`로 `shareId`를 받아 `ChatRepositoryImpl`이 `DataLayerConfig.webBaseUrl`(`BuildConfig.WEB_BASE_URL`)로 `{웹 origin}/share/{shareId}`를 완성합니다. 앱은 클립보드 복사 대신 초대 코드와 같은 **Android 공유 시트**(`ACTION_SEND`, 제목은 스토리 제목, 본문은 `chat_room_share_message` — "「{제목}」에 제 선택을 좀 섞어봤어요. / (빈 줄) / {N}턴 뒤에 어떻게 됐냐면요… 👀 / (빈 줄) / 링크: {링크}", N 은 발급 시점 확정 턴 수. 턴이 0이면 `chat_room_share_message_prologue` "「{제목}」에 들어와 봤어요. / (빈 줄) / 어떤 이야기냐면요… 👀 / (빈 줄) / 링크: {링크}". 삭제된 스토리는 제목 자리에 "삭제된 스토리")로 바로 보냅니다 — `my/InviteScreen`의 `shareText`를 두 번째 사용처가 생겨 `common/presentation/share/ShareText.kt`로 올렸습니다. `ShareRequested` → `shareJob` single-flight·`isSharing` 스피너 → 성공 `ShareLink(url, turnCount)` 효과(시트 닫고 공유 시트), 실패 토스트 "공유 링크 생성에 실패했어요"(시트 유지). 문구 조립은 `chat/presentation/ChatShareMessage.kt`를 채팅방·목록이 함께 씁니다. 분석 `client_chat_shareButton_clicked`(`chat_id`, `turn_number`)를 발급 시도에 기록합니다. 디버그 빌드는 API 가 dev, `WEB_BASE_URL` 기본이 운영 웹이라 발급된 링크를 운영 웹이 못 찾습니다 — dev 웹은 Vercel SSO 뒤라 공개 origin 이 없고, 릴리스는 API·웹 모두 운영이라 맞습니다.
5. 내 이프 카드는 두 번째 사용처가 생겨 `my/MyCreditCard.kt`의 `CreditBalanceCard`를 `designsystem/credit/CreditBalanceCard.kt`로 올렸습니다(`balance: Long?`만 받고 바깥 여백은 호출부가). 문자열 `my_credit_label`·`my_credit_charge`는 `credit_balance_label`·`credit_balance_charge`로 옮겼습니다.
6. `ChatRoomViewModel`에 `UserProfileRepository`를 주입해 `profile` 흐름을 `creditBalance`로 내리고, `MenuOpened`(메뉴 버튼 탭)에 `refresh()`를 띄워 방금 턴에서 차감된 잔액이 낡게 보이지 않게 했습니다(웹 카드의 `refetchOnMount: 'always'`와 같은 의도). `NewChatRequested`는 `chatRepository.createChat(storyId)`(`ChatStarter` 계약, 시작 설정 생략)를 `newChatJob` single-flight로 부르고 `isStartingNewChat`으로 항목 스피너·시트 닫기 잠금을 겁니다. 성공은 `NavigateToChat(chatId)` 효과이고 잠금은 풀지 않습니다(화면이 교체되므로). 실패는 잠금 해제 + 토스트 "채팅을 시작하지 못했어요".
7. 교체 이동은 `app`의 `NavBackStackOps.replaceTop`이 맡습니다 — `ChatRoomRoute(new)`로 맨 위를 바꿔 끼워 뒤로가기가 이전 방으로 돌아가지 않습니다. `ChatRoomScreen`은 `onReplaceChat`·`onOpenCreditCharge`(→ `MyCreditChargeRoute`)를 받습니다. detekt 한도 때문에 채팅방 항목 등록은 `chatRoomEntry`, 삭제 다이얼로그·신고 시트는 `ChatRoomOverlays.kt`, 토스트 문구는 `ChatRoomEffect.toastText`로 뗐습니다.
8. 채팅 삭제 확인 문구를 "채팅을 삭제할까요?" / "삭제하면 목록에서 사라지며 되돌릴 수 없어요" / "남겨두기"·"삭제하기"로 맞췄습니다. 스토리·초안 삭제 확인의 취소도 공통 카드 옵션 시트 계약(FE-SCREEN-013)대로 "닫기" → "남겨두기"로 바꿨습니다(`common` `studio_delete_dialog_cancel`).

### 검증

```bash
./gradlew :designsystem:ktlintCheck :designsystem:detekt :designsystem:testDebugUnitTest \
  :studio:ktlintCheck :studio:detekt :studio:testDebugUnitTest \
  :chat:ktlintCheck :chat:detekt :chat:testDebugUnitTest \
  :story:ktlintCheck :story:detekt :story:testDebugUnitTest \
  :my:ktlintCheck :my:detekt :my:testDebugUnitTest \
  :app:ktlintCheck :app:detekt :app:compileDebugAndroidTestKotlin checkModuleArchitecture installDebug
```

- 단위 테스트 통과(designsystem 5, studio 24, chat 162, story 27, my 27, network). `ChatListViewModelTest`에 카드 공유 2개(발급 → `ShareLink`·시트 닫힘·연타 1회, 실패 → 시트 유지 + `ShowShareFailed`), 새 `ChatRoomMenuTest` 7개 — 새 채팅이 이 방의 `storyId`로 생성되고 `NavigateToChat`, 연타 시 하나만, 실패 시 잠금 해제 + `ShowNewChatFailed`, 삭제된 스토리(`storyId` 빈 값)는 요청 없음, 공유 발급 → `ShareLink(url)`(연타 시 요청 하나), 공유 실패 → 잠금 해제 + `ShowShareFailed`, `MenuOpened`가 프로필을 다시 읽어 `creditBalance`를 채움. 기존 채팅방 테스트 6개 파일은 `FakeUserProfileRepository`만 더했고 `DataLayerConfig` 픽스처에 `webBaseUrl`을 더했습니다.
- 에뮬레이터(Pixel, emulator-5554, 회원 로그인, `always_finish_activities` null) 캡처는 `captures/knk-1320/`:
  - 제작 스토리 카드 시트 [studio-story-options.png](../../../captures/knk-1320/studio-story-options.png) → 가로 회전에도 시트 유지 [studio-story-options-rotated.png](../../../captures/knk-1320/studio-story-options-rotated.png) → 삭제하기로 시트가 닫히고 확인 다이얼로그 [studio-story-delete-dialog.png](../../../captures/knk-1320/studio-story-delete-dialog.png)("남겨두기"). 초안 카드 "만들던 스토리" [studio-draft-options.png](../../../captures/knk-1320/studio-draft-options.png).
  - 채팅 카드 시트(공유·신고·삭제) [chat-card-options.png](../../../captures/knk-1320/chat-card-options.png) → 삭제 확인("남겨두기") [chat-card-delete-dialog.png](../../../captures/knk-1320/chat-card-delete-dialog.png), 카드 공유하기 → 그 카드의 제목·턴 수를 실은 공유 시트 [chat-card-share-sheet.png](../../../captures/knk-1320/chat-card-share-sheet.png). 검증 중 만든 채팅 4개를 이 흐름으로 지웠습니다(카드 제거·목록 유지 확인).
  - 상세 헤더: 내 스토리 [story-detail-options-owner.png](../../../captures/knk-1320/story-detail-options-owner.png), 오리지널(신고만) [story-detail-options-original.png](../../../captures/knk-1320/story-detail-options-original.png).
  - 채팅방 메뉴(내 이프 카드·새 채팅·공유·신고·삭제) [chat-room-menu.png](../../../captures/knk-1320/chat-room-menu.png), 다크 모드 전환에도 시트 유지 [chat-room-menu-dark.png](../../../captures/knk-1320/chat-room-menu-dark.png). "공유하기" → dev 서버가 발급한 `https://manyak.app/share/{shareId}`를 실은 Android 공유 시트가 뜨고 메뉴 시트는 닫힘 [chat-room-share-sheet.png](../../../captures/knk-1320/chat-room-share-sheet.png)(4턴 문구), 턴 0 새 방에서는 프롤로그 문구 [chat-room-share-sheet-prologue.png](../../../captures/knk-1320/chat-room-share-sheet-prologue.png). "새 채팅 시작하기" → 같은 스토리("0호선")의 턴 0 새 방으로 교체됨 [chat-room-new-chat-result.png](../../../captures/knk-1320/chat-room-new-chat-result.png), 뒤로가기가 이전 방이 아니라 채팅 목록으로 감(목록 맨 위에 턴 0회 새 방). 비행기 모드에서 실패 토스트와 항목 복구 [chat-room-new-chat-failed.png](../../../captures/knk-1320/chat-room-new-chat-failed.png). "충전"은 시트를 닫고 이프 충전 화면을 엽니다(뒤로가기로 방 복귀, 시트 없음).
  - 회전·야간 모드·`accelerometer_rotation`·비행기 모드·프록시는 검사 후 원래 값으로 되돌렸습니다.
- `configuration-changes` 점검: 시트 열림은 제작·채팅 목록이 ViewModel 상태(`optionsTarget`), 채팅방·상세가 `rememberSaveable`이라 회전·다크 모드에서 유지됩니다(기기 확인 위). 진행 중 표시(`isStartingNewChat`·`isDeleting`)는 ViewModel이 들고, `MenuOpened`의 프로필 갱신은 탭에서만 나가므로 재생성에서 다시 돌지 않습니다. 재생성에서 값을 초기화하는 효과는 없습니다.
- 코드로만 판단한 것: 새 채팅 항목의 오른쪽 스피너와 그 상태의 회전 유지 — dev 서버 `POST /chats`가 0.5초 안에 끝나 캡처하지 못했고(에뮬레이터 네트워크 지연·프록시 설정은 이미 풀린 연결에 먹지 않음), `ChatRoomMenuTest`의 `isStartingNewChat` 단정과 `ManyakOptionItem(inProgress)` 코드로 판단했습니다. 삭제 확정 스피너의 "삭제 중" 낭독, TalkBack, 큰 글자도 실행하지 않았습니다.
- 이 검증으로 dev 서버에 "0호선" 채팅 4개가 만들어졌다가 삭제됐습니다(이프 변동 없음).

### 복구

되돌리면 시트·항목 컴포넌트·아이콘·문자열·`replaceTop`·`UserProfileRepository` 주입·공유 발급 API 배선(`ChatApi.createShare`·`DataLayerConfig.webBaseUrl`)이 함께 빠지고 다이얼로그·드롭다운으로 돌아갑니다. 서버 계약·저장 스키마·DataStore 키 변경은 없습니다.
