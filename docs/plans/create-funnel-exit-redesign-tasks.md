# 간편 제작 퍼널 이탈 개편 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 퍼널 전 화면의 이탈 수단을 오른쪽 끝 arrow-down으로 통일하고, 어느 단계에서 나가도 그 단계로 재개할 수 있게 한다.

**Architecture:** 이탈 지점을 퍼널 3개 목적지 전부로 넓힌다. 재개 지점은 이미 있는 `CreationProgress.selectedStorylineIndex` 미러 하나로 가른다. 키워드 단계 입력은 새 `KEYWORD_DRAFT` 레코드로 저장하고, 진행 레코드의 저장 수단을 Preferences DataStore에서 Room 단일 행으로 옮긴다. 퍼널 진입·이탈 전환은 `NavEntry.metadata` 표식을 전역 `transitionSpec`이 읽어 수직 슬라이드로 바꾼다.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation 3 (1.1.6), Hilt, Room 2.8.4, kotlinx.serialization, JUnit4 + kotlinx-coroutines-test

**Spec:** `docs/plans/create-funnel-exit-redesign.md`

## Global Constraints

- 커밋 메시지는 `[KNK-778] {태그}: {제목}` 형식이며 제목·본문 모두 한국어다. 태그는 `Feat`·`Fix`·`Docs`·`Refactor`·`Chore` 중 변경에 맞는 것을 쓴다.
- 커밋에 Claude 귀속 trailer(`Co-Authored-By` 등)를 넣지 않는다.
- 요청 없이 push하거나 PR을 만들지 않는다.
- 코드 주석에 하네스 절 번호(`§3-3-5`)·`FE-SCREEN-*`·검수 항목 번호를 적지 않는다. 왜 그렇게 했는지만 남긴다.
- 모든 사용자 노출 문자열은 `core/ui/src/main/res/values/strings.xml`에 둔다. 화면 코드에 리터럴을 쓰지 않는다.
- `:feature:*` 모듈끼리 직접 참조하지 않는다. 공유가 필요하면 `:core:*`를 거친다.
- `:core:domain`은 Kotlin JVM 모듈이라 Android 타입(`Context`·`Uri`·Room 애너테이션)을 참조할 수 없다.
- ViewModel은 `MviViewModel`을 상속하고 `reduce`는 순수 함수다. 부수효과는 `handleIntent`에서만 일으킨다.
- ktlint·detekt는 `ignoreFailures = false`다. 한 클래스의 함수 수 상한이 있어 `StorylineGenerationStore`는 이미 `@Suppress("TooManyFunctions")`로 예외 처리돼 있다.
- 각 태스크 끝에서 실행: `./gradlew :<모듈>:testDebugUnitTest`(또는 JVM 모듈은 `:test`), `./gradlew :<모듈>:ktlintCheck :<모듈>:detekt`.

## File Structure

| 파일 | 책임 | 상태 |
| --- | --- | --- |
| `core/domain/.../story/KeywordDraftSnapshot.kt` | 키워드 편집 상태 스냅숏과 "저장할 입력이 있는가" 판정 | 생성 |
| `core/domain/.../story/PendingStoryCreation.kt` | 레코드 변형에 `KeywordDraft`, 재개 지점에 `KeywordStep` 추가 | 수정 |
| `core/data/.../database/PendingStoryCreationEntity.kt` | Room 단일 행 엔티티와 도메인 ↔ 엔티티 매핑(JSON DTO 포함) | 생성 |
| `core/data/.../database/PendingRecordPayloads.kt` | JSON 컬럼에 담기는 페이로드 DTO와 도메인 변환. 기존 DataStore 파일에서 그대로 옮겨 온다 | 생성 |
| `core/data/.../database/PendingStoryCreationDao.kt` | 단일 행 관찰·조회·upsert·삭제 | 생성 |
| `core/data/.../database/ManyakDatabase.kt` | 앱의 첫 Room DB. 앞으로 다른 테이블도 여기 붙는다 | 생성 |
| `core/data/.../database/PendingStoryCreationRoomStore.kt` | `PendingStoryCreationStore`·`UserScopedStore` 구현 | 생성 |
| `core/data/.../datastore/PendingStoryCreationDataStore.kt` | Room 스토어로 대체 | 삭제 |
| `core/data/.../datastore/LegacyPendingCreationFile.kt` | 구버전 DataStore 파일 삭제 | 생성 |
| `core/ui/.../values/strings.xml` | 앱 바 접근성 이름, 초기화 경고 문구 | 수정 |
| `feature/create/.../CreateFunnelChrome.kt` | 앱 바 arrow-down, 초기화 경고 다이얼로그 | 수정 |
| `feature/create/.../StorylineGenerationStore.kt` | 추가 정보 진행 초기화 API | 수정 |
| `feature/create/.../CreateAdditionalInfoViewModel.kt` | 이탈·초기화 인텐트와 미러링 차단 | 수정 |
| `feature/create/.../CreateAdditionalInfoScreen.kt` | `BackHandler`, 이탈 콜백, 초기화 경고 표시 | 수정 |
| `feature/create/.../CreateKeywordViewModel.kt` | 키워드 스냅숏 저장·복원 | 수정 |
| `feature/create/.../CreateKeywordScreen.kt` | 복원 대기 중 본문 비우기 | 수정 |
| `app/.../root/NavTransitions.kt` | 퍼널 표식과 수직 전환 판정 | 수정 |
| `app/.../root/ManyakApp.kt` | 퍼널 엔트리 메타데이터, 추가 정보 이탈 배선, 키워드 재개 체인 | 수정 |

---

### Task 0: 하네스 결정 갱신

구현이 정본과 어긋난 채로 남지 않도록 스펙을 먼저 고친다. 코드 변경 없음.

**Files:**
- Modify: `../knk-harness/docs/product-specs/3-3-android-app.md`
- Modify: `docs/plans/create-funnel-recovery.md`

- [ ] **Step 1: 하네스 §3-3-3 퍼널 chrome 문단 갱신**

`### 스토리 생성 퍼널 구조 — 간편 제작` 절의 "퍼널 화면의 chrome은 각 단계 화면이 공용 컴포넌트로 그립니다" 문단 뒤에 결정 기록을 추가한다.

```markdown
**결정 기록 수정 — 퍼널 이탈은 오른쪽 끝 arrow-down이고 진입·이탈은 수직 전환입니다(2026-08-25)**

- **배경.** 최초 결정은 퍼널 헤더 왼쪽에 뒤로가기를 두었습니다. 퍼널이 홈 위에 얹히는 한 덩어리로 읽히도록, 진입은 아래에서 올라오고 이탈은 위에서 내려가는 전환으로 바꾸고 헤더 버튼도 그 방향과 짝이 맞는 arrow-down으로 바꿉니다.
- **적용 범위.** 수직 전환은 퍼널 **경계**에서만 씁니다 — 홈에서 퍼널로 들어갈 때와 퍼널에서 나갈 때입니다. 퍼널 단계 간 이동(스토리라인 → 추가 정보, 추가 정보 → 스토리라인)은 기존 교차 페이드를 유지합니다. 단계 이동까지 수직이면 "다음 단계"와 "퍼널 진입"이 같은 모션이 되어 구분되지 않습니다.
- **구현 수단.** 퍼널 목적지의 `NavEntry.metadata`에 표식을 달고, 전역 `transitionSpec`이 출발지·목적지 `Scene.metadata`를 비교해 판정합니다(`Scene.metadata`는 마지막 엔트리의 metadata를 그대로 노출). 엔트리별 spec만으로는 퍼널→퍼널과 퍼널→바깥을 구분할 수 없습니다.
- **영향.** 완성 직후 채팅방 진입도 이 규칙에 걸려 퍼널이 내려가며 채팅방이 드러납니다.
```

- [ ] **Step 2: 하네스 §3-3-5 요청 영속 문단과 대안 표 갱신**

"**요청 영속 — 단일 슬롯 Preferences DataStore.**" 문단과 그 아래 대안 표를 다음으로 교체한다.

```markdown
**요청 영속 — Room 단일 행.** 생성·완성 요청 전에 `requestId`·단계·퍼널 컨텍스트를 Room 테이블 `pending_story_creation`의 단일 행(`id` 고정 0)에 저장합니다(웹 `manyak:pending-creation-request` 키의 앱 대응). 임시 저장(`STORY_DRAFT`)과 키워드 임시 저장(`KEYWORD_DRAFT`)도 같은 행을 씁니다. 컬럼은 `id`·`stage`만 정규화하고 나머지 페이로드는 필드별 JSON 문자열 컬럼에 담습니다 — 슬롯이 하나라 조인할 대상이 없어 중첩 리스트까지 테이블로 쪼갤 이유가 없습니다. 스키마는 export하되 마이그레이션은 파괴적 폴백을 씁니다(진행 레코드는 재생성 가능한 스냅숏이라 해석 불가 시 폐기가 안전하며, 이는 기존 레코드 정리 규칙과 같습니다). 레코드 스토어는 사용자 귀속 저장소 정리 계약(`UserScopedStore` 멀티바인딩 — §3-3-4 로그아웃 절차)에 참여합니다.

| 대안 | 채택 안 한 이유 |
| --- | --- |
| `SavedStateHandle`·saved instance state | 명시적 앱 재실행 이후에도 남아야 하고, 홈 배너·로그아웃 정리가 퍼널 화면 밖에서 읽고 지워야 하므로 화면 수명 저장소로는 충족 불가 |
| Preferences DataStore(2026-08-25 이전 결정) | 슬롯이 하나라 키-값으로 충분했고 실제로 그렇게 구현했으나, 앞으로 Room이 필요한 사용자 귀속 저장소가 늘 것을 보고 저장소 기반을 하나로 모읍니다. 진행 레코드는 재생성 가능해 이관 없이 옮길 수 있는 첫 후보였습니다 |
```

- [ ] **Step 3: 하네스 §3-3-5 이탈 가드 문단 갱신**

"**이탈 가드 — 이탈 지점은 키워드 단계와 스토리라인 단계의 뒤로가기입니다.**"로 시작하는 문단의 마지막 문장(“추가 정보 단계 뒤로가기는 스토리라인 복귀(백스택 보존)라 이탈이 아니며, 이탈하려면 스토리라인 단계를 거치므로 가드는 그 화면 한 곳에 붙습니다.”)을 지우고, 문단 제목을 **이탈 지점은 퍼널 3개 목적지 전부입니다**로 바꾼 뒤 아래를 덧붙인다.

```markdown
**추가 정보 단계도 이탈 지점입니다.** arrow-down과 디바이스 뒤로가기는 스토리라인 복귀가 아니라 퍼널 이탈이며, 스토리라인 단계와 같은 보존 규칙을 따릅니다. 스토리라인 단계로 되돌아가는 수단은 하단 "다시 선택하기" 하나만 남깁니다 — 이탈과 인퍼널 복귀가 같은 제스처를 쓰면 어느 쪽인지 알 수 없습니다. "다시 선택하기"는 추천 선택이나 입력값이 있으면 초기화 경고를 거치고, 확정 시 추가 정보를 비우고 선택 스토리라인 순번도 되돌립니다(그래야 이후 이탈이 스토리라인 단계로 재개됩니다).

**키워드 단계 임시 저장 — 앱 전용 확장.** 3-1은 키워드 단계를 저장 범위 밖에 두지만, 앱은 키워드 입력(선택 장르·커스텀 장르·주인공과 주변 인물의 이름·성별·특징)을 `KEYWORD_DRAFT` 스테이지로 저장해 재개할 수 있게 합니다. 레코드 슬롯은 플랫폼별로 분리돼 있어 웹과 충돌하지 않습니다. 저장은 입력이 하나라도 있을 때만 하며(진입 시 놓인 빈 주변 인물 섹션은 입력으로 세지 않습니다), 없으면 조용히 이탈합니다. 키워드 단계에 소실 경고를 두지 않는 것은 3-1 제외 규칙 그대로입니다. 진행 중 레코드(`STORYLINE_GENERATION`·`STORY_COMPLETION`)가 있으면 키워드 스냅숏으로 덮지 않습니다.
```

- [ ] **Step 4: 하네스 §3-3-5 백스택 복원 문단 갱신**

"키워드 단계는 저장 범위 밖(3-1)이라 체인에 넣지 않습니다."를 다음으로 교체한다.

```markdown
재개 지점은 레코드가 정합니다 — `KEYWORD_DRAFT`는 `[키워드]`, `STORYLINE_GENERATION`과 선택 순번이 없는 `STORY_DRAFT`는 `[스토리라인]`, `STORY_COMPLETION`과 선택 순번이 있는 `STORY_DRAFT`는 `[스토리라인, 추가 정보]`입니다.
```

- [ ] **Step 5: 배너 닫기 미채택을 하네스 §3-3-5에 기록**

이미 구현에 반영됐지만(`22a4ae9`) 어느 문서에도 없는 차이다. Step 3에서 덧붙인 "키워드 단계 임시 저장 — 앱 전용 확장" 문단 뒤에 추가한다.

```markdown
**이어서 만들기 배너에 닫기(X)를 두지 않습니다 — 앱 전용 차이.** 3-1은 배너의 닫기가 레코드를 폐기한다고 정하지만, 앱은 그 버튼을 두지 않습니다. 확인 없이 만들던 스토리를 버리는 파괴적 동작인데, 같은 일을 하는 FAB 진입 다이얼로그의 "새로 만들기"는 한 번 묻기 때문입니다. 되돌릴 수 없는 폐기는 확인이 있는 경로 하나로 모읍니다. 레코드 정리 경로는 재개 소비·"새로 만들기"·새 생성 덮어쓰기·로그아웃 넷이며, 3-1이 세는 넷 중 배너 닫기가 "새로 만들기"로 대체된 것입니다.
```

- [ ] **Step 6: 선행 계획 문서에 대체 표기**

`docs/plans/create-funnel-recovery.md`의 "새로 내린 결정" 표 바로 위에 한 줄 추가한다.

```markdown
> D1·D3·D6은 `create-funnel-exit-redesign.md`의 E14·E2·E9로 대체되었습니다.
```

- [ ] **Step 7: 커밋**

```bash
git add -- docs/plans/create-funnel-recovery.md
git commit -m "[KNK-778] Docs: 퍼널 이탈 개편으로 대체된 결정 표기"
```

하네스는 별도 레포이므로 그쪽에서 따로 커밋한다.

```bash
git -C ../knk-harness add -- docs/product-specs/3-3-android-app.md
git -C ../knk-harness commit -m "[KNK-967] Docs: 퍼널 이탈 개편 — arrow-down·수직 전환·전 단계 이탈·Room 영속"
```

---

### Task 1: 키워드 드래프트 모델과 재개 지점

**Files:**
- Create: `core/domain/src/main/java/app/manyak/core/domain/story/KeywordDraftSnapshot.kt`
- Modify: `core/domain/src/main/java/app/manyak/core/domain/story/PendingStoryCreation.kt`
- Test: `core/domain/src/test/java/app/manyak/core/domain/story/CreationResumePointTest.kt`

`core:domain`에는 아직 테스트 파일이 없지만 `testImplementation(libs.junit)`은 이미 선언돼 있어 빌드 파일 변경이 필요 없다.

**Interfaces:**
- Produces:
  - `KeywordDraftSnapshot(selectedGenreTagIds: List<Long>, customGenreTags: List<KeywordCustomTagSnapshot>, protagonist: KeywordCharacterSnapshot, supportingCharacters: List<KeywordCharacterSnapshot>)`, 프로퍼티 `hasInput: Boolean`
  - `KeywordCharacterSnapshot(name: String, gender: CharacterGender?, selectedTagIds: List<Long>, customTags: List<KeywordCustomTagSnapshot>)`, 프로퍼티 `hasInput: Boolean`
  - `KeywordCustomTagSnapshot(name: String, selected: Boolean)`
  - `PendingStoryCreation.KeywordDraft(snapshot: KeywordDraftSnapshot)`
  - `CreationResumePoint.KeywordStep`

- [ ] **Step 1: 실패하는 테스트 작성**

`core/domain/src/test/java/app/manyak/core/domain/story/CreationResumePointTest.kt`

```kotlin
package app.manyak.core.domain.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationResumePointTest {
    @Test
    fun `키워드 임시 저장본은 키워드 단계로 재개한다`() {
        val record = PendingStoryCreation.KeywordDraft(snapshot = snapshot(genreTagIds = listOf(1L)))

        assertEquals(CreationResumePoint.KeywordStep, record.resumePoint())
    }

    @Test
    fun `선택 순번이 없는 임시 저장본은 스토리라인 단계로 재개한다`() {
        val record = draft(CreationProgress(selectedStorylineIndex = null))

        assertEquals(CreationResumePoint.StorylineStep, record.resumePoint())
    }

    @Test
    fun `선택 순번이 있는 임시 저장본은 추가 정보 단계로 재개한다`() {
        val record = draft(CreationProgress(selectedStorylineIndex = 2))

        assertEquals(CreationResumePoint.AdditionalInfoStep(storylineIndex = 2), record.resumePoint())
    }

    @Test
    fun `아무것도 고르지 않은 키워드 스냅숏은 저장 대상이 아니다`() {
        assertFalse(snapshot().hasInput)
    }

    @Test
    fun `진입 시 놓인 빈 주변 인물 섹션만 있으면 저장 대상이 아니다`() {
        assertFalse(snapshot(supporting = listOf(character())).hasInput)
    }

    @Test
    fun `주인공 이름만 입력해도 저장 대상이다`() {
        assertTrue(snapshot(protagonist = character(name = "홍길동")).hasInput)
    }

    @Test
    fun `선택 해제된 커스텀 키워드도 저장 대상이다`() {
        assertTrue(
            snapshot(customGenre = listOf(KeywordCustomTagSnapshot(name = "느와르", selected = false))).hasInput,
        )
    }

    private fun character(
        name: String = "",
        gender: CharacterGender? = null,
        tagIds: List<Long> = emptyList(),
        customTags: List<KeywordCustomTagSnapshot> = emptyList(),
    ) = KeywordCharacterSnapshot(
        name = name,
        gender = gender,
        selectedTagIds = tagIds,
        customTags = customTags,
    )

    private fun snapshot(
        genreTagIds: List<Long> = emptyList(),
        customGenre: List<KeywordCustomTagSnapshot> = emptyList(),
        protagonist: KeywordCharacterSnapshot = character(),
        supporting: List<KeywordCharacterSnapshot> = emptyList(),
    ) = KeywordDraftSnapshot(
        selectedGenreTagIds = genreTagIds,
        customGenreTags = customGenre,
        protagonist = protagonist,
        supportingCharacters = supporting,
    )

    private fun draft(progress: CreationProgress) =
        PendingStoryCreation.Draft(
            generationCommand = null,
            generation = StorylineGeneration(simpleCreationId = 1L, storylines = emptyList()),
            progress = progress,
        )
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :core:domain:test --tests "app.manyak.core.domain.story.CreationResumePointTest"`
Expected: 컴파일 실패 — `KeywordDraftSnapshot`, `PendingStoryCreation.KeywordDraft`, `CreationResumePoint.KeywordStep` 미해결

- [ ] **Step 3: 스냅숏 타입 작성**

`core/domain/src/main/java/app/manyak/core/domain/story/KeywordDraftSnapshot.kt`

```kotlin
package app.manyak.core.domain.story

/**
 * 키워드 단계 입력 스냅숏.
 *
 * 요청 페이로드인 [StoryCharacterInput] 을 재사용하지 않는다 — 커스텀 키워드는 선택을 해제해도
 * 목록에 남는 편집 상태라, 선택된 것만 담는 요청 타입으로는 화면을 그대로 되살릴 수 없다.
 */
data class KeywordDraftSnapshot(
    val selectedGenreTagIds: List<Long>,
    val customGenreTags: List<KeywordCustomTagSnapshot>,
    val protagonist: KeywordCharacterSnapshot,
    val supportingCharacters: List<KeywordCharacterSnapshot>,
) {
    /**
     * 저장할 만한 입력이 있는지. 빈 화면을 열었다 닫은 것까지 배너로 남기면 배너가 신호를 잃는다.
     * 진입 시 놓여 있는 빈 주변 인물 섹션은 사용자가 넣은 입력이 아니므로 세지 않는다.
     */
    val hasInput: Boolean
        get() =
            selectedGenreTagIds.isNotEmpty() ||
                customGenreTags.isNotEmpty() ||
                protagonist.hasInput ||
                supportingCharacters.any(KeywordCharacterSnapshot::hasInput)
}

data class KeywordCharacterSnapshot(
    val name: String,
    val gender: CharacterGender?,
    val selectedTagIds: List<Long>,
    val customTags: List<KeywordCustomTagSnapshot>,
) {
    val hasInput: Boolean
        get() =
            name.isNotBlank() ||
                gender != null ||
                selectedTagIds.isNotEmpty() ||
                customTags.isNotEmpty()
}

/** 선택 해제된 항목도 목록에 남으므로 이름과 선택 여부를 함께 담는다. */
data class KeywordCustomTagSnapshot(
    val name: String,
    val selected: Boolean,
)
```

- [ ] **Step 4: 레코드 변형과 재개 지점 추가**

`core/domain/src/main/java/app/manyak/core/domain/story/PendingStoryCreation.kt`의 `sealed interface PendingStoryCreation` 안, `Draft` 선언 뒤에 추가한다.

```kotlin
    /**
     * 키워드 단계에서 이탈하며 저장한 입력 스냅숏.
     *
     * 공용 계약은 키워드 단계를 저장 범위 밖에 두지만 앱은 확장해 재개를 지원한다. 복원할 AI
     * 생성 결과가 없다는 점이 다른 스테이지와 구분되므로 별도 변형으로 둔다 — 기존 세 스테이지의
     * "생성 결과가 반드시 있다"는 불변식을 깨지 않기 위해서다.
     */
    data class KeywordDraft(
        val snapshot: KeywordDraftSnapshot,
    ) : PendingStoryCreation
```

같은 파일의 `sealed interface CreationResumePoint` 안, `StorylineStep` 앞에 추가한다.

```kotlin
    data object KeywordStep : CreationResumePoint
```

`resumePoint()`의 `when` 첫 분기로 추가한다.

```kotlin
        is PendingStoryCreation.KeywordDraft -> CreationResumePoint.KeywordStep
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :core:domain:test --tests "app.manyak.core.domain.story.CreationResumePointTest"`
Expected: PASS (7 tests)

- [ ] **Step 6: 정적 검사와 커밋**

```bash
./gradlew :core:domain:ktlintCheck :core:domain:detekt
git add -- core/domain/src/main/java/app/manyak/core/domain/story/KeywordDraftSnapshot.kt core/domain/src/main/java/app/manyak/core/domain/story/PendingStoryCreation.kt core/domain/src/test/java/app/manyak/core/domain/story/CreationResumePointTest.kt
git commit -m "[KNK-778] Feat: 키워드 임시 저장 레코드와 재개 지점 추가"
```

---

### Task 2: Room 엔티티와 레코드 매핑

Room 기반을 깔고 도메인 ↔ 엔티티 매핑을 만든다. 스토어 교체는 Task 3이다.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `core/data/build.gradle.kts`
- Create: `core/data/src/main/java/app/manyak/core/data/database/PendingStoryCreationEntity.kt`
- Create: `core/data/src/main/java/app/manyak/core/data/database/PendingStoryCreationDao.kt`
- Create: `core/data/src/main/java/app/manyak/core/data/database/ManyakDatabase.kt`
- Test: `core/data/src/test/java/app/manyak/core/data/database/PendingStoryCreationEntityTest.kt`

**Interfaces:**
- Consumes: Task 1의 `PendingStoryCreation.KeywordDraft`, `KeywordDraftSnapshot`
- Produces:
  - `PendingStoryCreationEntity(id: Int, stage: String, generationCommand: String?, completionCommand: String?, generation: String?, progress: String?, keywordSnapshot: String?)`, 상수 `PendingStoryCreationEntity.SINGLE_ROW_ID: Int = 0`
  - `internal fun PendingStoryCreation.toEntity(): PendingStoryCreationEntity`
  - `internal fun PendingStoryCreationEntity.toDomainOrNull(): PendingStoryCreation?`
  - `PendingStoryCreationDao`: `observe(id: Int): Flow<PendingStoryCreationEntity?>`, `suspend find(id: Int): PendingStoryCreationEntity?`, `suspend upsert(entity: PendingStoryCreationEntity)`, `suspend clear()`
  - `ManyakDatabase.pendingStoryCreationDao(): PendingStoryCreationDao`, 상수 `ManyakDatabase.NAME: String = "manyak.db"`

- [ ] **Step 1: 버전 카탈로그에 Room 추가**

`gradle/libs.versions.toml`의 `[versions]`에 `datastore = "1.2.1"` 아래 줄로 추가한다.

```toml
room = "2.8.4"
```

`[libraries]`의 `androidx-datastore-preferences` 아래 줄로 추가한다.

```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

`[plugins]`의 `ksp` 아래 줄로 추가한다.

```toml
room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 2: 루트 빌드 파일에 플러그인 선언**

`build.gradle.kts`의 `plugins` 블록에서 `alias(libs.plugins.hilt) apply false` 아래 줄로 추가한다.

```kotlin
    alias(libs.plugins.room) apply false
```

- [ ] **Step 3: `:core:data`에 Room 적용**

`core/data/build.gradle.kts`의 `plugins` 블록에서 `alias(libs.plugins.hilt)` 아래 줄로 추가한다.

```kotlin
    alias(libs.plugins.room)
```

`android { }` 블록 뒤, `dependencies { }` 앞에 추가한다.

```kotlin
// 스키마를 파일로 남겨 컬럼 변경이 리뷰 diff 에 드러나게 한다. 진행 레코드는 재생성 가능한
// 스냅숏이라 마이그레이션 대신 파괴적 폴백을 쓰지만, 무엇이 바뀌었는지는 보여야 한다.
room {
    schemaDirectory("$projectDir/schemas")
}
```

`dependencies`의 `implementation(libs.androidx.datastore.preferences)` 아래 줄로 추가한다.

```kotlin
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
```

- [ ] **Step 4: 실패하는 매핑 테스트 작성**

`core/data/src/test/java/app/manyak/core/data/database/PendingStoryCreationEntityTest.kt`

```kotlin
package app.manyak.core.data.database

import app.manyak.core.domain.story.CharacterGender
import app.manyak.core.domain.story.CreationProgress
import app.manyak.core.domain.story.KeywordCharacterSnapshot
import app.manyak.core.domain.story.KeywordCustomTagSnapshot
import app.manyak.core.domain.story.KeywordDraftSnapshot
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.StoryCharacterInput
import app.manyak.core.domain.story.StoryCompletionCommand
import app.manyak.core.domain.story.Storyline
import app.manyak.core.domain.story.StorylineGeneration
import app.manyak.core.domain.story.StorylineGenerationCommand
import app.manyak.core.domain.story.StorylineRecommendedInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingStoryCreationEntityTest {
    @Test
    fun `생성 진행 레코드는 왕복해도 같다`() {
        val record = PendingStoryCreation.GeneratingStorylines(command = generationCommand())

        assertEquals(record, record.toEntity().toDomainOrNull())
    }

    @Test
    fun `완성 진행 레코드는 왕복해도 같다`() {
        val record =
            PendingStoryCreation.CompletingStory(
                generationCommand = generationCommand(),
                generation = generation(),
                command = completionCommand(),
                progress = progress(),
            )

        assertEquals(record, record.toEntity().toDomainOrNull())
    }

    @Test
    fun `임시 저장본은 왕복해도 같다`() {
        val record =
            PendingStoryCreation.Draft(
                generationCommand = generationCommand(),
                generation = generation(),
                progress = progress(),
                lastCompletionCommand = completionCommand(),
            )

        assertEquals(record, record.toEntity().toDomainOrNull())
    }

    @Test
    fun `키워드 임시 저장본은 선택 해제된 커스텀 키워드까지 왕복한다`() {
        val record = PendingStoryCreation.KeywordDraft(snapshot = keywordSnapshot())

        assertEquals(record, record.toEntity().toDomainOrNull())
    }

    @Test
    fun `모든 레코드는 단일 행 식별자를 쓴다`() {
        assertEquals(
            PendingStoryCreationEntity.SINGLE_ROW_ID,
            PendingStoryCreation.KeywordDraft(keywordSnapshot()).toEntity().id,
        )
    }

    @Test
    fun `모르는 스테이지는 없는 것으로 취급한다`() {
        val entity = PendingStoryCreationEntity(stage = "SOMETHING_ELSE")

        assertNull(entity.toDomainOrNull())
    }

    @Test
    fun `필수 페이로드가 빠진 행은 없는 것으로 취급한다`() {
        val entity = PendingStoryCreationEntity(stage = "STORY_DRAFT", generation = null)

        assertNull(entity.toDomainOrNull())
    }

    @Test
    fun `깨진 JSON 은 없는 것으로 취급한다`() {
        val entity = PendingStoryCreationEntity(stage = "KEYWORD_DRAFT", keywordSnapshot = "{ not json")

        assertNull(entity.toDomainOrNull())
    }

    private fun generationCommand() =
        StorylineGenerationCommand(
            requestId = "req-1",
            genreTagIds = listOf(1L, 2L),
            customGenreTags = listOf("느와르"),
            protagonist =
                StoryCharacterInput(
                    name = "홍길동",
                    gender = CharacterGender.MALE,
                    featureTagIds = listOf(10L),
                    customTags = listOf("과묵함"),
                ),
            supportingCharacters = emptyList(),
            parentCreationId = null,
            isRegenerated = false,
        )

    private fun completionCommand() =
        StoryCompletionCommand(
            requestId = "req-2",
            simpleCreationId = 7L,
            storylineId = 21L,
            additionalInfos = listOf("배경은 현대의 서울"),
        )

    private fun generation() =
        StorylineGeneration(
            simpleCreationId = 7L,
            storylines =
                listOf(
                    Storyline(
                        id = 21L,
                        storyline = "첫 번째 스토리라인",
                        recommendedInfos = listOf(StorylineRecommendedInfo(id = 31L, text = "추천 정보")),
                    ),
                ),
        )

    private fun progress() =
        CreationProgress(
            selectedStorylineIndex = 1,
            activeStorylineIndex = 1,
            additionalInfoInputs = listOf("입력", ""),
            selectedRecommendations = listOf("추천 정보"),
        )

    private fun keywordSnapshot() =
        KeywordDraftSnapshot(
            selectedGenreTagIds = listOf(1L),
            customGenreTags = listOf(KeywordCustomTagSnapshot(name = "느와르", selected = false)),
            protagonist =
                KeywordCharacterSnapshot(
                    name = "홍길동",
                    gender = CharacterGender.MALE,
                    selectedTagIds = listOf(10L),
                    customTags = listOf(KeywordCustomTagSnapshot(name = "과묵함", selected = true)),
                ),
            supportingCharacters =
                listOf(
                    KeywordCharacterSnapshot(
                        name = "",
                        gender = null,
                        selectedTagIds = emptyList(),
                        customTags = emptyList(),
                    ),
                ),
        )
}
```

- [ ] **Step 5: 테스트가 실패하는지 확인**

Run: `./gradlew :core:data:testDebugUnitTest --tests "app.manyak.core.data.database.PendingStoryCreationEntityTest"`
Expected: 컴파일 실패 — `PendingStoryCreationEntity`, `toEntity`, `toDomainOrNull` 미해결

- [ ] **Step 6: 엔티티와 매핑 작성**

`core/data/src/main/java/app/manyak/core/data/database/PendingStoryCreationEntity.kt`

기존 `core/data/src/main/java/app/manyak/core/data/datastore/PendingStoryCreationDataStore.kt`의 `PendingRecordDto` 아래 DTO들(`GenerationCommandDto`·`CharacterInputSnapshotDto`·`CompletionCommandDto`·`GenerationSnapshotDto`·`StorylineSnapshotDto`·`RecommendedInfoSnapshotDto`·`ProgressDto`)과 그 `toDto`/`toDomain` 확장을 이 파일로 그대로 옮긴다. 최상위 `PendingRecordDto`만 엔티티로 대체되고, 나머지는 이제 컬럼 하나씩의 JSON 페이로드가 된다. 여기에 키워드 스냅숏 DTO를 더한다.

```kotlin
package app.manyak.core.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.manyak.core.domain.story.CharacterGender
import app.manyak.core.domain.story.CreationProgress
import app.manyak.core.domain.story.KeywordCharacterSnapshot
import app.manyak.core.domain.story.KeywordCustomTagSnapshot
import app.manyak.core.domain.story.KeywordDraftSnapshot
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.StoryCharacterInput
import app.manyak.core.domain.story.StoryCompletionCommand
import app.manyak.core.domain.story.Storyline
import app.manyak.core.domain.story.StorylineGeneration
import app.manyak.core.domain.story.StorylineGenerationCommand
import app.manyak.core.domain.story.StorylineRecommendedInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 간편 제작 진행 레코드의 단일 행.
 *
 * 슬롯이 하나라 [id] 는 항상 [SINGLE_ROW_ID] 이고, 새 레코드는 같은 행을 덮어쓴다. 중첩 구조는
 * 조인할 대상이 없어 필드별 JSON 문자열로 담는다. 스테이지에 맞는 페이로드가 없거나 JSON 을
 * 해석할 수 없으면 없는 것으로 취급한다 — 재생성 가능한 스냅숏이라 복구보다 폐기가 안전하다.
 */
@Entity(tableName = "pending_story_creation")
data class PendingStoryCreationEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val stage: String,
    val generationCommand: String? = null,
    val completionCommand: String? = null,
    val generation: String? = null,
    val progress: String? = null,
    val keywordSnapshot: String? = null,
) {
    companion object {
        const val SINGLE_ROW_ID: Int = 0
    }
}

private val json = Json { ignoreUnknownKeys = true }

private inline fun <reified T> encode(value: T): String = json.encodeToString(value)

private inline fun <reified T> decodeOrNull(raw: String?): T? =
    raw?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

internal fun PendingStoryCreation.toEntity(): PendingStoryCreationEntity =
    when (this) {
        is PendingStoryCreation.GeneratingStorylines ->
            PendingStoryCreationEntity(
                stage = STAGE_STORYLINE_GENERATION,
                generationCommand = encode(command.toDto()),
            )

        is PendingStoryCreation.CompletingStory ->
            PendingStoryCreationEntity(
                stage = STAGE_STORY_COMPLETION,
                generationCommand = generationCommand?.let { encode(it.toDto()) },
                completionCommand = encode(command.toDto()),
                generation = encode(generation.toDto()),
                progress = encode(progress.toDto()),
            )

        is PendingStoryCreation.Draft ->
            PendingStoryCreationEntity(
                stage = STAGE_STORY_DRAFT,
                generationCommand = generationCommand?.let { encode(it.toDto()) },
                completionCommand = lastCompletionCommand?.let { encode(it.toDto()) },
                generation = encode(generation.toDto()),
                progress = encode(progress.toDto()),
            )

        is PendingStoryCreation.KeywordDraft ->
            PendingStoryCreationEntity(
                stage = STAGE_KEYWORD_DRAFT,
                keywordSnapshot = encode(snapshot.toDto()),
            )
    }

internal fun PendingStoryCreationEntity.toDomainOrNull(): PendingStoryCreation? =
    when (stage) {
        STAGE_STORYLINE_GENERATION ->
            decodeOrNull<GenerationCommandDto>(generationCommand)
                ?.let { PendingStoryCreation.GeneratingStorylines(it.toDomain()) }

        STAGE_STORY_COMPLETION -> {
            val command = decodeOrNull<CompletionCommandDto>(completionCommand)
            val snapshot = decodeOrNull<GenerationSnapshotDto>(generation)
            if (command == null || snapshot == null) {
                null
            } else {
                PendingStoryCreation.CompletingStory(
                    generationCommand = decodeOrNull<GenerationCommandDto>(generationCommand)?.toDomain(),
                    generation = snapshot.toDomain(),
                    command = command.toDomain(),
                    progress = (decodeOrNull<ProgressDto>(progress) ?: ProgressDto()).toDomain(),
                )
            }
        }

        STAGE_STORY_DRAFT ->
            decodeOrNull<GenerationSnapshotDto>(generation)?.let { snapshot ->
                PendingStoryCreation.Draft(
                    generationCommand = decodeOrNull<GenerationCommandDto>(generationCommand)?.toDomain(),
                    generation = snapshot.toDomain(),
                    progress = (decodeOrNull<ProgressDto>(progress) ?: ProgressDto()).toDomain(),
                    lastCompletionCommand = decodeOrNull<CompletionCommandDto>(completionCommand)?.toDomain(),
                )
            }

        STAGE_KEYWORD_DRAFT ->
            decodeOrNull<KeywordSnapshotDto>(keywordSnapshot)
                ?.let { PendingStoryCreation.KeywordDraft(it.toDomain()) }

        else -> null
    }

@Serializable
private data class KeywordSnapshotDto(
    val selectedGenreTagIds: List<Long> = emptyList(),
    val customGenreTags: List<CustomTagDto> = emptyList(),
    val protagonist: KeywordCharacterDto = KeywordCharacterDto(),
    val supportingCharacters: List<KeywordCharacterDto> = emptyList(),
)

@Serializable
private data class KeywordCharacterDto(
    val name: String = "",
    val gender: String? = null,
    val selectedTagIds: List<Long> = emptyList(),
    val customTags: List<CustomTagDto> = emptyList(),
)

@Serializable
private data class CustomTagDto(
    val name: String,
    val selected: Boolean,
)

private fun KeywordDraftSnapshot.toDto(): KeywordSnapshotDto =
    KeywordSnapshotDto(
        selectedGenreTagIds = selectedGenreTagIds,
        customGenreTags = customGenreTags.map { CustomTagDto(it.name, it.selected) },
        protagonist = protagonist.toDto(),
        supportingCharacters = supportingCharacters.map { it.toDto() },
    )

private fun KeywordCharacterSnapshot.toDto(): KeywordCharacterDto =
    KeywordCharacterDto(
        name = name,
        gender = gender?.name,
        selectedTagIds = selectedTagIds,
        customTags = customTags.map { CustomTagDto(it.name, it.selected) },
    )

private fun KeywordSnapshotDto.toDomain(): KeywordDraftSnapshot =
    KeywordDraftSnapshot(
        selectedGenreTagIds = selectedGenreTagIds,
        customGenreTags = customGenreTags.map { KeywordCustomTagSnapshot(it.name, it.selected) },
        protagonist = protagonist.toDomain(),
        supportingCharacters = supportingCharacters.map { it.toDomain() },
    )

private fun KeywordCharacterDto.toDomain(): KeywordCharacterSnapshot =
    KeywordCharacterSnapshot(
        name = name,
        gender = gender?.let { value -> CharacterGender.entries.firstOrNull { it.name == value } },
        selectedTagIds = selectedTagIds,
        customTags = customTags.map { KeywordCustomTagSnapshot(it.name, it.selected) },
    )

private const val STAGE_STORYLINE_GENERATION = "STORYLINE_GENERATION"
private const val STAGE_STORY_COMPLETION = "STORY_COMPLETION"
private const val STAGE_STORY_DRAFT = "STORY_DRAFT"
private const val STAGE_KEYWORD_DRAFT = "KEYWORD_DRAFT"
```

- [ ] **Step 6b: 옮겨 온 페이로드 DTO를 별도 파일로 분리**

`core/data/src/main/java/app/manyak/core/data/database/PendingRecordPayloads.kt`를 만들고, 기존 `datastore/PendingStoryCreationDataStore.kt`의 `GenerationCommandDto` 선언부터 `ProgressDto.toDomain()`까지를 **내용 변경 없이** 옮긴다. 옮기는 선언은 정확히 다음과 같다.

- `GenerationCommandDto`, `CharacterInputSnapshotDto`, `CompletionCommandDto`, `GenerationSnapshotDto`, `StorylineSnapshotDto`, `RecommendedInfoSnapshotDto`, `ProgressDto`
- `StorylineGenerationCommand.toDto()`, `GenerationCommandDto.toDomain()`, `StoryCompletionCommand.toDto()`, `CompletionCommandDto.toDomain()`, `StorylineGeneration.toDto()`, `GenerationSnapshotDto.toDomain()`, `CreationProgress.toDto()`, `ProgressDto.toDomain()`

패키지 선언만 `app.manyak.core.data.database`로 바꾸고 `PendingRecordDto`는 옮기지 않는다 — 엔티티가 그 자리를 대신한다. 이 DTO들은 이제 컬럼 하나씩의 JSON 페이로드이므로 파일 상단에 그 뜻을 한 줄로 남긴다.

```kotlin
// 컬럼 하나에 담기는 JSON 페이로드. 슬롯이 하나라 조인할 대상이 없어 중첩 구조는 여기 남는다.
```

가시성은 `private`이 아니라 `internal`로 바꾼다 — 매핑이 `PendingStoryCreationEntity.kt`에서 이 타입들을 참조한다.

- [ ] **Step 7: DAO 작성**

`core/data/src/main/java/app/manyak/core/data/database/PendingStoryCreationDao.kt`

```kotlin
package app.manyak.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingStoryCreationDao {
    @Query("SELECT * FROM pending_story_creation WHERE id = :id LIMIT 1")
    fun observe(id: Int): Flow<PendingStoryCreationEntity?>

    @Query("SELECT * FROM pending_story_creation WHERE id = :id LIMIT 1")
    suspend fun find(id: Int): PendingStoryCreationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingStoryCreationEntity)

    @Query("DELETE FROM pending_story_creation")
    suspend fun clear()
}
```

- [ ] **Step 8: 데이터베이스 작성**

`core/data/src/main/java/app/manyak/core/data/database/ManyakDatabase.kt`

```kotlin
package app.manyak.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 앱의 로컬 데이터베이스.
 *
 * 지금은 간편 제작 진행 레코드 한 테이블뿐이지만, 앞으로 생기는 로컬 저장소도 여기 붙는다.
 * 사용자 귀속 테이블은 각자 세션 종료 정리 계약에 참여해야 한다.
 */
@Database(
    entities = [PendingStoryCreationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ManyakDatabase : RoomDatabase() {
    abstract fun pendingStoryCreationDao(): PendingStoryCreationDao

    companion object {
        const val NAME: String = "manyak.db"
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew :core:data:testDebugUnitTest --tests "app.manyak.core.data.database.PendingStoryCreationEntityTest"`
Expected: PASS (8 tests)

Room 버전이 해석되지 않으면 `gradle/libs.versions.toml`의 `room` 값을 `https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/maven-metadata.xml`의 `<release>` 값으로 바꾼 뒤 다시 실행한다.

- [ ] **Step 10: 정적 검사와 커밋**

```bash
./gradlew :core:data:ktlintCheck :core:data:detekt
git add -- gradle/libs.versions.toml build.gradle.kts core/data/build.gradle.kts core/data/schemas core/data/src/main/java/app/manyak/core/data/database core/data/src/test/java/app/manyak/core/data/database
git commit -m "[KNK-778] Feat: 진행 레코드 Room 엔티티와 매핑 추가"
```

---

### Task 3: 레코드 스토어를 Room으로 교체

**Files:**
- Create: `core/data/src/main/java/app/manyak/core/data/database/PendingStoryCreationRoomStore.kt`
- Create: `core/data/src/main/java/app/manyak/core/data/datastore/LegacyPendingCreationFile.kt`
- Delete: `core/data/src/main/java/app/manyak/core/data/datastore/PendingStoryCreationDataStore.kt`
- Modify: `core/data/src/main/java/app/manyak/core/data/di/StorageModule.kt`
- Modify: `core/data/src/main/java/app/manyak/core/data/di/Dispatchers.kt`
- Modify: `core/data/src/main/java/app/manyak/core/data/di/StoryModule.kt`
- Modify: `core/data/src/main/java/app/manyak/core/data/di/AuthModule.kt`

**Interfaces:**
- Consumes: Task 2의 `PendingStoryCreationDao`, `ManyakDatabase`, `toEntity()`, `toDomainOrNull()`
- Produces: `PendingStoryCreationRoomStore`가 `PendingStoryCreationStore`·`UserScopedStore`를 구현. 공개 API는 기존 인터페이스와 같아 호출부 변경이 없다.

- [ ] **Step 1: Room 스토어 작성**

`core/data/src/main/java/app/manyak/core/data/database/PendingStoryCreationRoomStore.kt`

```kotlin
package app.manyak.core.data.database

import app.manyak.core.data.di.IoDispatcher
import app.manyak.core.data.session.UserScopedStore
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.PendingStoryCreationStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 간편 제작 진행 레코드의 단일 슬롯.
 *
 * 해석할 수 없는 행은 없는 것으로 취급한다 — 재생성 가능한 진행 스냅숏이라 복구보다 폐기가
 * 안전하다. 사용자 귀속 데이터이므로 [UserScopedStore] 정리 계약에 참여한다.
 */
@Singleton
class PendingStoryCreationRoomStore
    @Inject
    constructor(
        private val dao: PendingStoryCreationDao,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PendingStoryCreationStore,
        UserScopedStore {
        override val storeName: String = "pending_story_creation"

        override val record: Flow<PendingStoryCreation?> =
            dao
                .observe(PendingStoryCreationEntity.SINGLE_ROW_ID)
                .map { entity -> entity?.toDomainOrNull() }
                .flowOn(ioDispatcher)

        override suspend fun read(): PendingStoryCreation? =
            withContext(ioDispatcher) {
                runCatching { dao.find(PendingStoryCreationEntity.SINGLE_ROW_ID) }
                    .getOrNull()
                    ?.toDomainOrNull()
            }

        override suspend fun write(record: PendingStoryCreation) {
            withContext(ioDispatcher) {
                runCatching { dao.upsert(record.toEntity()) }
            }
        }

        override suspend fun clear() {
            withContext(ioDispatcher) {
                runCatching { dao.clear() }
            }
        }

        override suspend fun clearUserData(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dao.clear() }.isSuccess
            }
    }
```

- [ ] **Step 2: 구버전 DataStore 파일 정리 작성**

`core/data/src/main/java/app/manyak/core/data/datastore/LegacyPendingCreationFile.kt`

```kotlin
package app.manyak.core.data.datastore

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 진행 레코드가 Room 으로 옮겨 가기 전에 쓰던 DataStore 파일을 지운다.
 *
 * 레코드는 재생성 가능한 스냅숏이라 이관하지 않는다. 그렇다고 파일을 남겨 두면 읽는 곳 없는
 * 사용자 귀속 데이터가 기기에 남으므로, 앱 시작 시 한 번 지운다. 파일이 없으면 아무 일도 없다.
 */
@Singleton
class LegacyPendingCreationFile
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        fun delete() {
            runCatching { context.preferencesDataStoreFile(LEGACY_STORE_NAME).delete() }
        }

        private companion object {
            const val LEGACY_STORE_NAME = "pending_creation"
        }
    }
```

- [ ] **Step 3: 구버전 스토어 삭제**

```bash
git rm core/data/src/main/java/app/manyak/core/data/datastore/PendingStoryCreationDataStore.kt
```

- [ ] **Step 4: DI 재배선**

`core/data/src/main/java/app/manyak/core/data/di/StorageModule.kt`

`providePendingCreationDataStore` 함수 전체와 `private const val PENDING_CREATION_STORE_NAME = "pending_creation"` 줄을 지운다. `androidx.datastore` import는 다른 provider가 계속 쓰므로 남긴다. `object StorageModule` 안 마지막 `@Provides` 뒤에 추가한다.

```kotlin
    @Provides
    @Singleton
    fun provideManyakDatabase(
        @ApplicationContext context: Context,
    ): ManyakDatabase =
        Room
            .databaseBuilder(context, ManyakDatabase::class.java, ManyakDatabase.NAME)
            // 진행 레코드는 재생성 가능한 스냅숏이다. 스키마가 바뀌면 되살리는 것보다 버리는 쪽이
            // 안전하며, 이는 해석 불가 레코드를 없는 것으로 보는 기존 규칙과 같다.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun providePendingStoryCreationDao(database: ManyakDatabase): PendingStoryCreationDao =
        database.pendingStoryCreationDao()
```

import에 `androidx.room.Room`, `app.manyak.core.data.database.ManyakDatabase`, `app.manyak.core.data.database.PendingStoryCreationDao`를 추가한다.

`core/data/src/main/java/app/manyak/core/data/di/Dispatchers.kt`에서 `@PendingCreationDataStore` 애너테이션 선언(37~38행 부근)을 지운다.

`core/data/src/main/java/app/manyak/core/data/di/StoryModule.kt`의 바인딩을 교체한다.

```kotlin
    @Binds
    @Singleton
    abstract fun bindPendingStoryCreationStore(impl: PendingStoryCreationRoomStore): PendingStoryCreationStore
```

import를 `app.manyak.core.data.database.PendingStoryCreationRoomStore`로 바꾼다.

`core/data/src/main/java/app/manyak/core/data/di/AuthModule.kt`의 멀티바인딩도 교체한다.

```kotlin
    abstract fun bindPendingCreationAsUserScoped(impl: PendingStoryCreationRoomStore): UserScopedStore
```

import를 `app.manyak.core.data.database.PendingStoryCreationRoomStore`로 바꾼다.

- [ ] **Step 5: 앱 시작 시 구버전 파일 삭제 호출**

`app/src/main/java/app/manyak/ManyakApplication.kt`에 주입 필드와 호출 한 줄을 더한다. `:app`은 이미 `implementation(projects.core.data)`를 갖고 있어 빌드 파일 변경은 없다.

`sessionBootstrapper` 필드 뒤에 추가한다.

```kotlin
    @Inject
    lateinit var legacyPendingCreationFile: LegacyPendingCreationFile
```

`onCreate`의 `sessionBootstrapper.start()` 뒤에 추가한다.

```kotlin
        // 진행 레코드가 Room 으로 옮겨 가기 전 쓰던 파일을 치운다. 읽는 곳이 없어진 사용자
        // 귀속 데이터를 기기에 남기지 않는다.
        legacyPendingCreationFile.delete()
```

import에 `app.manyak.core.data.datastore.LegacyPendingCreationFile`을 추가한다.

- [ ] **Step 6: 기존 테스트가 그대로 통과하는지 확인**

Run: `./gradlew :core:data:testDebugUnitTest :feature:create:testDebugUnitTest :feature:home:testDebugUnitTest`
Expected: PASS. 기능 모듈 테스트는 `FakePendingStoryCreationStore`를 쓰므로 이 교체의 영향을 받지 않아야 한다. 실패하면 저장 수단 교체가 인터페이스를 넘어 새어 나간 것이다.

- [ ] **Step 7: 앱이 빌드되고 실행되는지 확인**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 정적 검사와 커밋**

```bash
./gradlew :core:data:ktlintCheck :core:data:detekt :app:ktlintCheck :app:detekt
git add -A -- core/data/src core/data/schemas app/src
git commit -m "[KNK-778] Refactor: 진행 레코드 저장 수단을 Room 으로 교체"
```

---

### Task 4: 퍼널 앱 바를 arrow-down으로 교체

**Files:**
- Modify: `core/ui/src/main/res/values/strings.xml`
- Modify: `feature/create/src/main/java/app/manyak/feature/create/CreateFunnelChrome.kt`

**Interfaces:**
- Produces: `CreateFunnelHeader(onClose: () -> Unit, modifier: Modifier)` — 파라미터 이름이 `onBack`에서 `onClose`로 바뀐다. 호출부 3곳(`CreateKeywordScreen`·`CreateStorylineScreen`·`CreateAdditionalInfoScreen`)이 이름 있는 인자를 쓰므로 함께 고친다.

- [ ] **Step 1: 문자열 추가**

`core/ui/src/main/res/values/strings.xml`에서 `create_draft_saved` 근처, 퍼널 문자열 무리 안에 추가한다.

```xml
    <string name="create_close_funnel">만들기 닫기</string>
```

- [ ] **Step 2: 헤더 교체**

`feature/create/src/main/java/app/manyak/feature/create/CreateFunnelChrome.kt`의 `CreateFunnelHeader` 전체를 교체한다.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateFunnelHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.create_title),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        // 퍼널은 아래에서 올라와 아래로 닫히는 한 덩어리로 보이므로, 이탈 버튼도 그 방향과
        // 짝이 맞는 아래 화살표를 오른쪽 끝에 둔다.
        actions = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_angle_down),
                    contentDescription = stringResource(R.string.create_close_funnel),
                    tint = ManyakTheme.colors.text,
                )
            }
        },
        // 화면 루트에서 적용한 safeDrawing 인셋이 중복되지 않게 한다.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = ManyakTheme.colors.surface,
                titleContentColor = ManyakTheme.colors.text,
            ),
    )
}
```

`ic_arrow_back`·`common_back`을 더 쓰지 않으므로 import는 그대로 두되(같은 `R`), 미사용 import가 생기면 ktlint가 잡는다.

- [ ] **Step 3: 호출부 3곳의 인자 이름 변경**

각 파일에서 `CreateFunnelHeader(onBack = onBack)`를 `CreateFunnelHeader(onClose = onBack)`로 바꾼다. 콜백 자체의 이름은 Task 5·7에서 정리한다.

- `feature/create/src/main/java/app/manyak/feature/create/CreateKeywordScreen.kt`
- `feature/create/src/main/java/app/manyak/feature/create/CreateStorylineScreen.kt`
- `feature/create/src/main/java/app/manyak/feature/create/CreateAdditionalInfoScreen.kt`

- [ ] **Step 4: 컴파일과 기존 테스트 확인**

Run: `./gradlew :feature:create:testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: 정적 검사와 커밋**

```bash
./gradlew :feature:create:ktlintCheck :feature:create:detekt :core:ui:ktlintCheck
git add -- core/ui/src/main/res/values/strings.xml feature/create/src/main/java/app/manyak/feature/create
git commit -m "[KNK-778] Design: 퍼널 앱 바 이탈 버튼을 오른쪽 arrow-down 으로 변경"
```

---

### Task 5: 추가 정보 단계의 이탈과 초기화 경고

**Files:**
- Modify: `feature/create/src/main/java/app/manyak/feature/create/StorylineGenerationStore.kt`
- Modify: `feature/create/src/main/java/app/manyak/feature/create/CreateAdditionalInfoViewModel.kt`
- Test: `feature/create/src/test/java/app/manyak/feature/create/CreateAdditionalInfoViewModelTest.kt`

**Interfaces:**
- Consumes: 기존 `StorylineGenerationStore.leaveFunnel(): Boolean`, `hasContentToPreserve(): Boolean`
- Produces:
  - `StorylineGenerationStore.clearAdditionalInfoProgress()` — `progress`의 `selectedStorylineIndex`를 null로, 입력·추천 선택을 빈 목록으로 되돌린다
  - `CreateAdditionalInfoIntent.LeaveFunnel`, `ConfirmLeaveFunnel`, `DismissExitWarning`, `ReselectStoryline`, `ConfirmReselect`, `DismissReselectWarning`
  - `CreateAdditionalInfoEffect.ExitFunnel(contentPreserved: Boolean)`, `CreateAdditionalInfoEffect.NavigateBackToStoryline`
  - `CreateAdditionalInfoUiState.showExitWarningDialog: Boolean`, `showReselectWarningDialog: Boolean`, 프로퍼티 `hasAdditionalInfo: Boolean`

- [ ] **Step 1: 실패하는 테스트 작성**

`feature/create/src/test/java/app/manyak/feature/create/CreateAdditionalInfoViewModelTest.kt`의 기존 헬퍼를 먼저 넓힌다. `LoadedFixture`(54행)에 스토어를 더하고, `loadedViewModel()`(62행)이 선택 순번을 받게 한다.

```kotlin
    private class LoadedFixture(
        val repository: FakeStoryCreationRepository,
        val chatRepository: FakeChatRepository,
        val pendingStore: FakePendingStoryCreationStore,
        val store: StorylineGenerationStore,
        val viewModel: CreateAdditionalInfoViewModel,
    )

    /** 스토리라인 생성 성공 결과를 스냅숏한 ViewModel 을 만든다. */
    private fun TestScope.loadedViewModel(selectedStorylineIndex: Int? = null): LoadedFixture {
        val repository = FakeStoryCreationRepository()
        val chatRepository = FakeChatRepository()
        val pendingStore = FakePendingStoryCreationStore()
        val store = StorylineGenerationStore(repository, pendingStore, this)
        store.generate(sampleGenerationInput())
        advanceUntilIdle()
        // "선택하기"로 추가 정보 단계에 들어온 상태를 만든다. 재개 지점이 이 값으로 갈린다.
        selectedStorylineIndex?.let(store::markStorylineSelected)
        return LoadedFixture(
            repository = repository,
            chatRepository = chatRepository,
            pendingStore = pendingStore,
            store = store,
            viewModel = CreateAdditionalInfoViewModel(store, repository, chatRepository, pendingStore),
        )
    }
```

같은 파일 끝에 테스트를 추가한다.

```kotlin
    @Test
    fun `이탈하면 선택 순번과 입력이 담긴 임시 저장본이 남는다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 1)
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId = 0, value = "배경은 서울"))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.LeaveFunnel)
            advanceUntilIdle()

            val record = fixture.pendingStore.read() as PendingStoryCreation.Draft
            assertEquals(1, record.progress.selectedStorylineIndex)
            assertEquals(listOf("배경은 서울", "", ""), record.progress.additionalInfoInputs)
        }

    @Test
    fun `이탈은 임시 저장 여부와 함께 이탈 효과를 낸다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.LeaveFunnel)
            advanceUntilIdle()

            assertEquals(
                CreateAdditionalInfoEffect.ExitFunnel(contentPreserved = true),
                fixture.viewModel.uiEffect.first(),
            )
        }

    @Test
    fun `추가 정보가 없으면 다시 선택하기는 곧바로 돌아간다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ReselectStoryline)
            advanceUntilIdle()

            assertFalse(fixture.viewModel.uiState.value.showReselectWarningDialog)
            assertEquals(
                CreateAdditionalInfoEffect.NavigateBackToStoryline,
                fixture.viewModel.uiEffect.first(),
            )
        }

    @Test
    fun `입력이 있으면 다시 선택하기는 초기화 경고를 띄운다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId = 0, value = "배경은 서울"))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ReselectStoryline)
            advanceUntilIdle()

            assertTrue(fixture.viewModel.uiState.value.showReselectWarningDialog)
            assertNull(withTimeoutOrNull(100) { fixture.viewModel.uiEffect.first() })
        }

    @Test
    fun `추천만 골라도 다시 선택하기는 초기화 경고를 띄운다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)
            val recommendation =
                fixture.viewModel.uiState.value.storylines
                    .first()
                    .recommendedInfos
                    .first()
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ToggleRecommendation(recommendation))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ReselectStoryline)
            advanceUntilIdle()

            assertTrue(fixture.viewModel.uiState.value.showReselectWarningDialog)
        }

    @Test
    fun `초기화를 확정하면 선택 순번이 사라져 다음 이탈은 스토리라인 단계로 재개한다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId = 0, value = "배경은 서울"))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ReselectStoryline)
            advanceUntilIdle()
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ConfirmReselect)
            advanceUntilIdle()

            assertNull(fixture.store.progress.selectedStorylineIndex)
            assertEquals(emptyList<String>(), fixture.store.progress.additionalInfoInputs)
            assertEquals(emptyList<String>(), fixture.store.progress.selectedRecommendations)
            assertEquals(
                CreateAdditionalInfoEffect.NavigateBackToStoryline,
                fixture.viewModel.uiEffect.first(),
            )
        }

    @Test
    fun `초기화를 취소하면 입력이 남는다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId = 0, value = "배경은 서울"))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ReselectStoryline)
            advanceUntilIdle()
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.DismissReselectWarning)
            advanceUntilIdle()

            assertFalse(fixture.viewModel.uiState.value.showReselectWarningDialog)
            assertEquals(
                "배경은 서울",
                fixture.viewModel.uiState.value.additionalInfos
                    .first()
                    .value,
            )
            assertEquals(0, fixture.store.progress.selectedStorylineIndex)
        }
```

`loadedViewModel()`을 인자 없이 쓰던 기존 테스트는 그대로 동작한다(기본값 `null`).

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :feature:create:testDebugUnitTest --tests "app.manyak.feature.create.CreateAdditionalInfoViewModelTest"`
Expected: 컴파일 실패 — `LeaveFunnel`·`ReselectStoryline`·`ConfirmReselect`·`DismissReselectWarning`·`showReselectWarningDialog`·`ExitFunnel`·`NavigateBackToStoryline` 미해결

- [ ] **Step 3: 스토어에 초기화 API 추가**

`feature/create/src/main/java/app/manyak/feature/create/StorylineGenerationStore.kt`의 `updateAdditionalInfoProgress` 뒤에 추가한다.

```kotlin
        /**
         * "다시 선택하기"로 스토리라인 단계에 되돌아간다. 입력·추천 선택과 함께 선택 순번도
         * 지운다 — 순번이 남으면 이후 이탈이 추가 정보 단계로 재개되어 이미 버린 입력 화면으로 돌아간다.
         */
        fun clearAdditionalInfoProgress() {
            progress =
                progress.copy(
                    selectedStorylineIndex = null,
                    additionalInfoInputs = emptyList(),
                    selectedRecommendations = emptyList(),
                )
        }
```

- [ ] **Step 4: 상태·인텐트·이펙트 추가**

`CreateAdditionalInfoViewModel.kt`의 `CreateAdditionalInfoUiState`에 필드 두 개와 파생 프로퍼티를 더한다.

```kotlin
    /** 보존할 내용 없이 이탈을 시도해 소실 경고 다이얼로그를 띄운 상태. */
    val showExitWarningDialog: Boolean = false,
    /** "다시 선택하기"가 추가 정보를 버린다고 알리는 중. */
    val showReselectWarningDialog: Boolean = false,
```

```kotlin
    /** 버리면 아쉬운 추가 정보가 있는지. 빈 입력 칸만 있는 상태는 아니다. */
    val hasAdditionalInfo: Boolean
        get() = selectedRecommendations.isNotEmpty() || additionalInfos.any { it.value.isNotBlank() }
```

`CreateAdditionalInfoIntent`에 추가한다.

```kotlin
    /** 앱 바 arrow-down·디바이스 뒤로가기 — 퍼널 이탈. */
    data object LeaveFunnel : CreateAdditionalInfoIntent

    /** 소실 경고 다이얼로그의 "그만 만들기". */
    data object ConfirmLeaveFunnel : CreateAdditionalInfoIntent

    data object DismissExitWarning : CreateAdditionalInfoIntent

    /** 하단 "다시 선택하기" — 스토리라인 단계 복귀. */
    data object ReselectStoryline : CreateAdditionalInfoIntent

    /** 초기화 경고 다이얼로그의 "다시 선택하기". */
    data object ConfirmReselect : CreateAdditionalInfoIntent

    data object DismissReselectWarning : CreateAdditionalInfoIntent
```

`CreateAdditionalInfoEvent`에 추가한다.

```kotlin
    data class ExitWarningVisibleChanged(
        val visible: Boolean,
    ) : CreateAdditionalInfoEvent

    data class ReselectWarningVisibleChanged(
        val visible: Boolean,
    ) : CreateAdditionalInfoEvent
```

`CreateAdditionalInfoEffect`에 추가한다.

```kotlin
    /** 퍼널 이탈 확정. 내용이 남았으면 "임시 저장되었어요" 토스트를 함께 띄운다. */
    data class ExitFunnel(
        val contentPreserved: Boolean,
    ) : CreateAdditionalInfoEffect

    /** "다시 선택하기" 확정 — 스토리라인 단계로 pop 한다. */
    data object NavigateBackToStoryline : CreateAdditionalInfoEffect
```

- [ ] **Step 5: 이탈·초기화 처리 구현**

`CreateAdditionalInfoViewModel`의 `private var completedStoryId: String? = null` 아래에 플래그를 더한다.

```kotlin
        /**
         * 이탈·초기화 처리 중. 이 전이는 스토어의 진행 미러를 비우는데, 그 사이 화면 상태를
         * 미러링하면 방금 저장한 재료를 빈 값으로 덮어쓴다. 이후 미러링을 멈춘다.
         */
        private var isLeaving = false
```

`init` 블록의 미러링 수집에서 가드를 더한다.

```kotlin
                uiState.collect { state ->
                    // 복원 전의 빈 입력을 미러링하면 되살릴 임시 저장 재료를 덮어쓴다.
                    if (state.isRestoring || isLeaving) return@collect
```

`handleIntent`의 `when`에 분기를 더한다.

```kotlin
                CreateAdditionalInfoIntent.LeaveFunnel ->
                    if (storylineGenerationStore.hasContentToPreserve()) {
                        isLeaving = true
                        val preserved = storylineGenerationStore.leaveFunnel()
                        dispatchEffect(CreateAdditionalInfoEffect.ExitFunnel(contentPreserved = preserved))
                    } else {
                        dispatchEvent(CreateAdditionalInfoEvent.ExitWarningVisibleChanged(visible = true))
                    }

                CreateAdditionalInfoIntent.ConfirmLeaveFunnel -> {
                    isLeaving = true
                    storylineGenerationStore.leaveFunnel()
                    dispatchEvent(CreateAdditionalInfoEvent.ExitWarningVisibleChanged(visible = false))
                    dispatchEffect(CreateAdditionalInfoEffect.ExitFunnel(contentPreserved = false))
                }

                CreateAdditionalInfoIntent.DismissExitWarning ->
                    dispatchEvent(CreateAdditionalInfoEvent.ExitWarningVisibleChanged(visible = false))

                CreateAdditionalInfoIntent.ReselectStoryline ->
                    if (state.hasAdditionalInfo) {
                        dispatchEvent(CreateAdditionalInfoEvent.ReselectWarningVisibleChanged(visible = true))
                    } else {
                        confirmReselect()
                    }

                CreateAdditionalInfoIntent.ConfirmReselect -> {
                    dispatchEvent(CreateAdditionalInfoEvent.ReselectWarningVisibleChanged(visible = false))
                    confirmReselect()
                }

                CreateAdditionalInfoIntent.DismissReselectWarning ->
                    dispatchEvent(CreateAdditionalInfoEvent.ReselectWarningVisibleChanged(visible = false))
```

같은 클래스에 헬퍼를 더한다.

```kotlin
        /**
         * 스토리라인 단계로 되돌아간다. 미러링을 먼저 끊고 스토어를 비운다 — 순서를 바꾸면
         * 남아 있던 화면 상태가 방금 비운 진행을 다시 채운다.
         */
        private suspend fun confirmReselect() {
            isLeaving = true
            storylineGenerationStore.clearAdditionalInfoProgress()
            dispatchEffect(CreateAdditionalInfoEffect.NavigateBackToStoryline)
        }
```

`reduce`에 분기를 더한다.

```kotlin
                is CreateAdditionalInfoEvent.ExitWarningVisibleChanged ->
                    state.copy(showExitWarningDialog = event.visible)

                is CreateAdditionalInfoEvent.ReselectWarningVisibleChanged ->
                    state.copy(showReselectWarningDialog = event.visible)
```

`SnapshotRestored` 분기의 `event.snapshot.copy(...)`에 두 다이얼로그 상태도 보존하도록 더한다.

```kotlin
                    event.snapshot.copy(
                        isRestoring = false,
                        isCompletingStory = state.isCompletingStory,
                        completionFailure = state.completionFailure,
                        showExitWarningDialog = state.showExitWarningDialog,
                        showReselectWarningDialog = state.showReselectWarningDialog,
                    )
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :feature:create:testDebugUnitTest --tests "app.manyak.feature.create.CreateAdditionalInfoViewModelTest"`
Expected: PASS

- [ ] **Step 7: 정적 검사와 커밋**

```bash
./gradlew :feature:create:ktlintCheck :feature:create:detekt
git add -- feature/create/src
git commit -m "[KNK-778] Feat: 추가 정보 단계 이탈과 다시 선택하기 초기화 경고 추가"
```

---

### Task 6: 추가 정보 화면과 앱 배선

Task 5의 인텐트를 화면과 백스택에 붙여 요구 2·3·4·5·6을 완결한다.

**Files:**
- Modify: `core/ui/src/main/res/values/strings.xml`
- Modify: `feature/create/src/main/java/app/manyak/feature/create/CreateFunnelChrome.kt`
- Modify: `feature/create/src/main/java/app/manyak/feature/create/CreateAdditionalInfoScreen.kt`
- Modify: `app/src/main/java/app/manyak/root/ManyakApp.kt`

**Interfaces:**
- Consumes: Task 5의 인텐트·이펙트·상태 필드
- Produces:
  - `ReselectWarningDialog(onConfirmReselect: () -> Unit, onDismiss: () -> Unit)`
  - `CreateAdditionalInfoScreen(storylineIndex: Int, onLeaveFunnel: () -> Unit, onBackToStoryline: () -> Unit, onEnterChat: (String) -> Unit, modifier: Modifier, viewModel: CreateAdditionalInfoViewModel)` — 기존 `onBack` 파라미터가 두 개로 갈라진다

- [ ] **Step 1: 초기화 경고 문구 추가**

`core/ui/src/main/res/values/strings.xml`의 `create_exit_warning_leave` 아래에 추가한다.

```xml
    <string name="create_reselect_warning_title">스토리라인을 다시 고를까요?</string>
    <string name="create_reselect_warning_description">지금 다시 고르면 입력한 추가 정보가 사라져요</string>
    <string name="create_reselect_warning_confirm">다시 선택하기</string>
    <string name="create_reselect_warning_cancel">그대로 두기</string>
```

- [ ] **Step 2: 초기화 경고 다이얼로그 작성**

`feature/create/src/main/java/app/manyak/feature/create/CreateFunnelChrome.kt`의 `ExitWarningDialog` 뒤에 추가한다.

```kotlin
/**
 * "다시 선택하기"가 추가 정보를 버린다고 알리는 다이얼로그.
 *
 * 기본 동작은 그대로 두기다 — 스토리라인을 다시 고르는 쪽이 파괴적인 선택이라 확인 버튼 자리를
 * 되돌리기 쉬운 쪽에 준다.
 */
@Composable
internal fun ReselectWarningDialog(
    onConfirmReselect: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.overlay,
        title = {
            Text(
                text = stringResource(R.string.create_reselect_warning_title),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.create_reselect_warning_description),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = ManyakTheme.shapes.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.brand,
                        contentColor = ManyakTheme.colors.textInverse,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.create_reselect_warning_cancel),
                    style = ManyakTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onConfirmReselect) {
                Text(
                    text = stringResource(R.string.create_reselect_warning_confirm),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
        },
    )
}
```

- [ ] **Step 3: 화면 배선**

`CreateAdditionalInfoScreen.kt`의 시그니처와 본문을 고친다.

```kotlin
/**
 * 추가 정보 단계. 앱 바 arrow-down 과 디바이스 뒤로가기는 퍼널 이탈(홈 복귀)이고, 스토리라인
 * 단계로 되돌아가는 수단은 하단 "다시 선택하기" 하나뿐이다 — 이탈과 단계 복귀가 같은 제스처를
 * 쓰면 어느 쪽인지 알 수 없다.
 */
@Composable
fun CreateAdditionalInfoScreen(
    storylineIndex: Int,
    onLeaveFunnel: () -> Unit,
    onBackToStoryline: () -> Unit,
    onEnterChat: (chatId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateAdditionalInfoViewModel = hiltViewModel(),
) {
```

`val currentOnEnterChat by rememberUpdatedState(onEnterChat)` 아래에 두 줄을 더한다.

```kotlin
    val currentOnLeaveFunnel by rememberUpdatedState(onLeaveFunnel)
    val currentOnBackToStoryline by rememberUpdatedState(onBackToStoryline)
```

`val context = LocalContext.current` 아래에 추가한다.

```kotlin
    // 디바이스 뒤로가기도 앱 바 arrow-down 과 같은 이탈 처리를 거친다.
    BackHandler { viewModel.onIntent(CreateAdditionalInfoIntent.LeaveFunnel) }
```

이펙트 수집의 `when`에 분기를 더한다.

```kotlin
                    is CreateAdditionalInfoEffect.ExitFunnel -> {
                        if (effect.contentPreserved) {
                            Toast
                                .makeText(context, R.string.create_draft_saved, Toast.LENGTH_SHORT)
                                .show()
                        }
                        currentOnLeaveFunnel()
                    }

                    CreateAdditionalInfoEffect.NavigateBackToStoryline -> currentOnBackToStoryline()
```

`CreateAdditionalInfoContent` 호출을 고치고 다이얼로그 두 개를 더한다.

```kotlin
    CreateAdditionalInfoContent(
        storylineIndex = storylineIndex,
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )

    if (state.showExitWarningDialog) {
        ExitWarningDialog(
            onConfirmLeave = { viewModel.onIntent(CreateAdditionalInfoIntent.ConfirmLeaveFunnel) },
            onDismiss = { viewModel.onIntent(CreateAdditionalInfoIntent.DismissExitWarning) },
        )
    }

    if (state.showReselectWarningDialog) {
        ReselectWarningDialog(
            onConfirmReselect = { viewModel.onIntent(CreateAdditionalInfoIntent.ConfirmReselect) },
            onDismiss = { viewModel.onIntent(CreateAdditionalInfoIntent.DismissReselectWarning) },
        )
    }
```

`CreateAdditionalInfoContent`와 `CreateAdditionalInfoFooter`에서 `onBack: () -> Unit` 파라미터를 지운다. 헤더는 인텐트를 직접 쓴다.

```kotlin
            CreateFunnelHeader(onClose = { onIntent(CreateAdditionalInfoIntent.LeaveFunnel) })
```

푸터의 "다시 선택하기" 버튼도 인텐트를 쓴다.

```kotlin
        FunnelNeutralButton(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.create_cta_reselect_storyline),
            enabled = true,
            onClick = { onIntent(CreateAdditionalInfoIntent.ReselectStoryline) },
        )
```

두 `@Preview` 함수의 `onBack = {}` 인자도 지운다.

- [ ] **Step 4: 백스택 배선**

`app/src/main/java/app/manyak/root/ManyakApp.kt`의 `entry<CreateAdditionalInfoRoute>` 블록에서 `onBack`을 둘로 나눈다.

```kotlin
                entry<CreateAdditionalInfoRoute> { route ->
                    CreateAdditionalInfoScreen(
                        storylineIndex = route.storylineIndex,
                        // 이탈은 퍼널 단계를 전부 걷어내고 홈으로 돌아간다. 스토리라인 단계만
                        // pop 하면 홈으로 나가려던 조작이 한 단계 뒤로 가기로 보인다.
                        onLeaveFunnel = {
                            while (backStack.size > 1 && backStack.lastOrNull() != MainTabsRoute) {
                                backStack.removeLastOrNull()
                            }
                        },
                        onBackToStoryline = { backStack.removeLastOrNull() },
                        onEnterChat = { chatId ->
                            while (backStack.size > 1 && backStack.lastOrNull() != MainTabsRoute) {
                                backStack.removeLastOrNull()
                            }
                            backStack.add(ChatRoomRoute(chatId))
                        },
                    )
                }
```

- [ ] **Step 5: 빌드와 테스트 확인**

Run: `./gradlew :app:assembleDebug :feature:create:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 테스트 PASS

- [ ] **Step 6: 기기에서 확인**

디버그 앱을 설치해 다음을 확인한다.

1. 추가 정보 화면에서 arrow-down → 홈으로 가고 "임시 저장되었어요" 토스트가 뜬다.
2. 홈 배너 "이어서 만들기" → 추가 정보 화면으로 오고 선택한 스토리라인·추천 선택·입력값이 그대로다.
3. 추가 정보 화면에서 디바이스 뒤로가기 → 1번과 같다.
4. 입력이 있는 상태에서 "다시 선택하기" → 초기화 경고가 뜬다. "그대로 두기"면 입력이 남는다.
5. 초기화를 확정해 스토리라인 화면으로 온 뒤 arrow-down → 재개하면 **스토리라인 화면**으로 온다.

- [ ] **Step 7: 정적 검사와 커밋**

```bash
./gradlew :feature:create:ktlintCheck :feature:create:detekt :app:ktlintCheck :app:detekt :core:ui:ktlintCheck
git add -- core/ui/src feature/create/src app/src
git commit -m "[KNK-778] Feat: 추가 정보 단계 이탈·초기화 경고 화면 배선"
```

---

### Task 7: 키워드 단계 임시 저장과 복원

**Files:**
- Modify: `feature/create/src/main/java/app/manyak/feature/create/CreateKeywordViewModel.kt`
- Test: `feature/create/src/test/java/app/manyak/feature/create/CreateKeywordViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1의 `KeywordDraftSnapshot`·`PendingStoryCreation.KeywordDraft`, 기존 `PendingStoryCreationStore`
- Produces:
  - `CreateKeywordUiState.isRestoring: Boolean` (기본값은 스토어 상태로 정해진다)
  - `CreateKeywordViewModel` 생성자에 `pendingCreationStore: PendingStoryCreationStore` 추가
  - `internal fun CreateKeywordUiState.toKeywordSnapshot(): KeywordDraftSnapshot`
  - `internal fun KeywordDraftSnapshot.toKeywordUiState(base: CreateKeywordUiState): CreateKeywordUiState`

- [ ] **Step 1: 실패하는 테스트 작성**

`feature/create/src/test/java/app/manyak/feature/create/CreateKeywordViewModelTest.kt`의 기존 헬퍼(41행 `TestScope.viewModel(repository)`)가 레코드 스토어를 받도록 넓힌다.

```kotlin
    private fun TestScope.viewModel(
        repository: StoryCreationRepository,
        pending: FakePendingStoryCreationStore = FakePendingStoryCreationStore(),
    ): CreateKeywordViewModel =
        CreateKeywordViewModel(
            storyCreationRepository = repository,
            storylineGenerationStore = StorylineGenerationStore(repository, pending, this),
            pendingCreationStore = pending,
        )
```

기존 호출부는 인자 하나만 넘기므로 그대로 동작한다. 같은 파일 끝에 테스트를 추가한다. `fixedTagsRepository()`(196행)는 기존 헬퍼다.

```kotlin
    @Test
    fun `입력이 없으면 이탈해도 레코드를 남기지 않는다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()

            viewModel.onIntent(CreateKeywordIntent.LeaveFunnel)
            advanceUntilIdle()

            assertNull(pending.read())
            assertEquals(
                CreateKeywordEffect.ExitFunnel(contentPreserved = false),
                viewModel.uiEffect.first(),
            )
        }

    @Test
    fun `장르를 고르고 이탈하면 키워드 임시 저장본이 남는다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            advanceUntilIdle()

            viewModel.onIntent(CreateKeywordIntent.LeaveFunnel)
            advanceUntilIdle()

            val record = pending.read() as PendingStoryCreation.KeywordDraft
            assertEquals(listOf(1L), record.snapshot.selectedGenreTagIds)
            assertEquals(
                CreateKeywordEffect.ExitFunnel(contentPreserved = true),
                viewModel.uiEffect.first(),
            )
        }

    @Test
    fun `진행 중 레코드가 있으면 키워드 스냅숏이 덮어쓰지 않는다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val inFlight = PendingStoryCreation.GeneratingStorylines(command = generationCommand())
            pending.write(inFlight)
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            advanceUntilIdle()

            viewModel.onIntent(CreateKeywordIntent.LeaveFunnel)
            advanceUntilIdle()

            assertEquals(inFlight, pending.read())
        }

    @Test
    fun `키워드 임시 저장본이 있으면 복원하고 레코드를 소비한다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            pending.write(
                PendingStoryCreation.KeywordDraft(
                    snapshot =
                        KeywordDraftSnapshot(
                            selectedGenreTagIds = listOf(2L),
                            customGenreTags = listOf(KeywordCustomTagSnapshot("느와르", selected = false)),
                            protagonist =
                                KeywordCharacterSnapshot(
                                    name = "홍길동",
                                    gender = CharacterGender.MALE,
                                    selectedTagIds = listOf(10L),
                                    customTags = emptyList(),
                                ),
                            supportingCharacters = emptyList(),
                        ),
                ),
            )
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRestoring)
            assertEquals(setOf(2L), state.selectedGenreTagIds)
            assertEquals(listOf(CustomTag("느와르", selected = false)), state.customGenreTags)
            assertEquals("홍길동", state.protagonist.name)
            assertEquals(CharacterGender.MALE, state.protagonist.gender)
            assertEquals(setOf(10L), state.protagonist.selectedTagIds)
            assertNull(pending.read())
        }

    @Test
    fun `복원할 레코드가 없어도 복원 대기는 끝난다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(fixedTagsRepository())
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRestoring)
        }

    @Test
    fun `복원이 끝나기 전에 이탈해도 저장해 둔 입력을 잃지 않는다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            pending.write(
                PendingStoryCreation.KeywordDraft(
                    snapshot =
                        KeywordDraftSnapshot(
                            selectedGenreTagIds = listOf(2L),
                            customGenreTags = emptyList(),
                            protagonist =
                                KeywordCharacterSnapshot(
                                    name = "홍길동",
                                    gender = null,
                                    selectedTagIds = emptyList(),
                                    customTags = emptyList(),
                                ),
                            supportingCharacters = emptyList(),
                        ),
                ),
            )
            val viewModel = viewModel(fixedTagsRepository(), pending)

            // 복원이 화면에 반영되기 전에 헤더를 누른 상황. advanceUntilIdle 없이 곧바로 보낸다.
            viewModel.onIntent(CreateKeywordIntent.LeaveFunnel)
            advanceUntilIdle()

            val record = pending.read() as PendingStoryCreation.KeywordDraft
            assertEquals(listOf(2L), record.snapshot.selectedGenreTagIds)
            assertEquals("홍길동", record.snapshot.protagonist.name)
        }
```

`generationCommand()`는 Task 2 테스트에 쓴 것과 같은 형태로 이 파일에도 `private fun` 으로 둔다.

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :feature:create:testDebugUnitTest --tests "app.manyak.feature.create.CreateKeywordViewModelTest"`
Expected: 컴파일 실패 — `isRestoring`, 생성자 파라미터 미해결

- [ ] **Step 3: 상태에 복원 대기 필드 추가**

`CreateKeywordUiState`에 필드를 더한다.

```kotlin
    /**
     * 진행 레코드 복원을 기다리는 중. 저장해 둔 키워드가 있는지 아직 몰라 화면을 그리지 않는다 —
     * 빈 입력 화면이 스쳐 간 뒤 값이 채워지면 재개 진입에서 화면이 번쩍인다.
     */
    val isRestoring: Boolean = true,
```

- [ ] **Step 4: 스냅숏 변환 함수 작성**

`CreateKeywordViewModel.kt` 파일 끝에 추가한다.

```kotlin
/** 이탈 시 저장할 편집 상태. 선택 해제된 커스텀 키워드도 그대로 담는다. */
internal fun CreateKeywordUiState.toKeywordSnapshot(): KeywordDraftSnapshot =
    KeywordDraftSnapshot(
        selectedGenreTagIds = selectedGenreTagIds.toList(),
        customGenreTags = customGenreTags.map { KeywordCustomTagSnapshot(it.name, it.selected) },
        protagonist = protagonist.toSnapshot(),
        supportingCharacters = supportingCharacters.map { it.toSnapshot() },
    )

private fun KeywordCharacter.toSnapshot(): KeywordCharacterSnapshot =
    KeywordCharacterSnapshot(
        name = name,
        gender = gender,
        selectedTagIds = selectedTagIds.toList(),
        customTags = customTags.map { KeywordCustomTagSnapshot(it.name, it.selected) },
    )

/**
 * 저장본으로 화면 상태를 되살린다. 인물 식별자는 화면 로컬 값이라 저장하지 않고 다시 매긴다.
 * 활성 카테고리는 담지 않아 항상 첫 탭에서 시작한다 — 완료된 카테고리는 잠금이 풀려 있어
 * 사용자가 바로 이동할 수 있다.
 */
internal fun KeywordDraftSnapshot.toKeywordUiState(base: CreateKeywordUiState): CreateKeywordUiState {
    val supporting =
        supportingCharacters.mapIndexed { index, character ->
            character.toCharacter(id = CreateKeywordUiState.FIRST_SUPPORTING_ID + index)
        }
    return base.copy(
        isRestoring = false,
        selectedGenreTagIds = selectedGenreTagIds.toSet(),
        customGenreTags = customGenreTags.map { CustomTag(it.name, it.selected) },
        protagonist = protagonist.toCharacter(id = CreateKeywordUiState.PROTAGONIST_ID),
        // 저장본에 인물이 없으면 진입 때와 같이 빈 섹션 하나를 놓는다.
        supportingCharacters =
            supporting.ifEmpty {
                listOf(KeywordCharacter(id = CreateKeywordUiState.FIRST_SUPPORTING_ID))
            },
        nextSupportingId = CreateKeywordUiState.FIRST_SUPPORTING_ID + supporting.size.coerceAtLeast(1),
    )
}

private fun KeywordCharacterSnapshot.toCharacter(id: Long): KeywordCharacter =
    KeywordCharacter(
        id = id,
        name = name,
        gender = gender,
        selectedTagIds = selectedTagIds.toSet(),
        customTags = customTags.map { CustomTag(it.name, it.selected) },
    )
```

- [ ] **Step 5: ViewModel에 저장·복원 배선**

생성자에 스토어를 더한다.

```kotlin
        private val pendingCreationStore: PendingStoryCreationStore,
```

`private var tagsLoadJob: Job? = null` 뒤에 복원 작업을 두고 `init`은 그대로 둔다.

```kotlin
        init {
            startTagsLoad()
            viewModelScope.launch {
                // 레코드가 남아 있는 진입은 곧 재개다. 재개 의도를 따로 저장하지 않는다.
                val record = pendingCreationStore.read()
                if (record is PendingStoryCreation.KeywordDraft) {
                    // 복원은 레코드를 소비한다 — 재개 후 다시 이탈하면 그 시점 상태로 새로 저장된다.
                    pendingCreationStore.clear()
                    dispatchEvent(CreateKeywordEvent.SnapshotRestored(record.snapshot))
                } else {
                    dispatchEvent(CreateKeywordEvent.RestoreFinished)
                }
            }
        }
```

`CreateKeywordEvent`에 이벤트 둘을 더한다.

```kotlin
    /** 키워드 임시 저장본이 도착했다. */
    data class SnapshotRestored(
        val snapshot: KeywordDraftSnapshot,
    ) : CreateKeywordEvent

    /** 되살릴 저장본이 없었다. 복원 대기만 끝낸다. */
    data object RestoreFinished : CreateKeywordEvent
```

`reduce`에 분기를 더한다.

```kotlin
                is CreateKeywordEvent.SnapshotRestored -> event.snapshot.toKeywordUiState(state)
                CreateKeywordEvent.RestoreFinished -> state.copy(isRestoring = false)
```

`handleIntent`의 `LeaveFunnel` 분기를 교체한다. 인자로 받은 `state`는 쓰지 않는다 — 아래 헬퍼가 복원이 끝난 상태를 직접 기다려 읽는다.

```kotlin
                CreateKeywordIntent.LeaveFunnel -> leaveFunnel()
```

헬퍼를 더한다.

```kotlin
        /**
         * 키워드 단계 이탈. 생성 전이라 소실 경고는 없고, 입력이 남아 있으면 조용히 저장한다.
         *
         * 복원이 화면에 반영될 때까지 기다린다 — 헤더는 복원 중에도 눌리므로, 기다리지 않으면
         * 아직 비어 있는 상태를 스냅숏해 방금 소비한 저장분을 잃는다.
         *
         * 뒤 단계의 진행 중 레코드가 슬롯에 있으면 덮지 않는다 — 서버에서 실제로 돌고 있는
         * 복구 대상이 편집 스냅숏보다 우선한다. 뒤 단계에서 시작한 생성 결과의 임시 저장도
         * 스토어가 이미 처리하므로 여기서는 판정만 승계한다.
         */
        private suspend fun leaveFunnel() {
            val restored = uiState.first { !it.isRestoring }
            val storePreserved = storylineGenerationStore.leaveFunnel()
            if (storePreserved) {
                dispatchEffect(CreateKeywordEffect.ExitFunnel(contentPreserved = true))
                return
            }
            val snapshot = restored.toKeywordSnapshot()
            if (!snapshot.hasInput) {
                dispatchEffect(CreateKeywordEffect.ExitFunnel(contentPreserved = false))
                return
            }
            pendingCreationStore.write(PendingStoryCreation.KeywordDraft(snapshot))
            dispatchEffect(CreateKeywordEffect.ExitFunnel(contentPreserved = true))
        }
```

import에 `kotlinx.coroutines.flow.first`를 추가한다.

`generateStorylines`는 손대지 않는다 — `storylineGenerationStore.generate(...)`가 진행 중 레코드를 써서 키워드 저장본을 자연히 덮는다.

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :feature:create:testDebugUnitTest --tests "app.manyak.feature.create.CreateKeywordViewModelTest"`
Expected: PASS

- [ ] **Step 7: 정적 검사와 커밋**

```bash
./gradlew :feature:create:ktlintCheck :feature:create:detekt
git add -- feature/create/src
git commit -m "[KNK-778] Feat: 키워드 단계 입력 임시 저장과 복원 추가"
```

---

### Task 8: 키워드 화면과 재개 체인 배선

**Files:**
- Modify: `feature/create/src/main/java/app/manyak/feature/create/CreateKeywordScreen.kt`
- Modify: `app/src/main/java/app/manyak/root/ManyakApp.kt`

**Interfaces:**
- Consumes: Task 7의 `CreateKeywordUiState.isRestoring`, Task 1의 `CreationResumePoint.KeywordStep`

- [ ] **Step 1: 복원 대기 중 본문 비우기**

`CreateKeywordScreen.kt`의 `CreateKeywordContent`에서 `CreateStepIndicator(...)` 호출 뒤의 분기를 고친다. 기존 `if (state.providedTags is ProvidedTags.Failed) { ... } else { ... }`를 `when` 으로 바꾸고 첫 분기를 더한다.

```kotlin
            when {
                // 복원 결과를 기다리는 동안은 본문을 비워 둔다 — 저장해 둔 키워드가 있는지
                // 모르는 채로 빈 입력 화면을 그리면 재개 진입에서 화면이 번쩍인다.
                state.isRestoring -> Spacer(modifier = Modifier.weight(1f))

                state.providedTags is ProvidedTags.Failed -> {
```

나머지 두 분기는 기존 본문을 그대로 옮긴다.

- [ ] **Step 2: 재개 체인에 키워드 분기 추가**

`app/src/main/java/app/manyak/root/ManyakApp.kt`의 `onResumeCreation` 람다를 교체한다.

```kotlin
                        // 재개·복구 진입 — 레코드가 가리키는 단계까지 체인을 쌓는다.
                        onResumeCreation = { resumePoint ->
                            when (resumePoint) {
                                CreationResumePoint.KeywordStep -> backStack.add(CreateKeywordRoute)

                                CreationResumePoint.StorylineStep -> backStack.add(CreateStorylineRoute)

                                is CreationResumePoint.AdditionalInfoStep -> {
                                    backStack.add(CreateStorylineRoute)
                                    backStack.add(CreateAdditionalInfoRoute(resumePoint.storylineIndex))
                                }
                            }
                        },
```

- [ ] **Step 3: 빌드와 테스트 확인**

Run: `./gradlew :app:assembleDebug :feature:create:testDebugUnitTest :feature:home:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 테스트 PASS

- [ ] **Step 4: 기기에서 확인**

1. 키워드 화면에서 아무것도 고르지 않고 arrow-down → 홈으로 가고 배너가 뜨지 않는다.
2. 장르와 주인공 이름을 넣고 arrow-down → 홈에 배너가 뜨고 토스트가 뜬다.
3. "이어서 만들기" → 키워드 화면으로 오고 고른 키워드·이름·성별·특징이 그대로다. 화면이 번쩍이지 않는다.
4. 다시 arrow-down → 그 시점 상태로 다시 저장된다.
5. 앱을 강제 종료한 뒤 다시 실행해도 2~3이 같다.

- [ ] **Step 5: 정적 검사와 커밋**

```bash
./gradlew :feature:create:ktlintCheck :feature:create:detekt :app:ktlintCheck :app:detekt
git add -- feature/create/src app/src
git commit -m "[KNK-778] Feat: 키워드 단계 재개 진입 배선"
```

---

### Task 9: 퍼널 진입·이탈 수직 전환

**Files:**
- Modify: `app/src/main/java/app/manyak/root/NavTransitions.kt`
- Modify: `app/src/main/java/app/manyak/root/ManyakApp.kt`
- Test: `app/src/test/java/app/manyak/root/FunnelSceneTransitionTest.kt`

`:app`에는 이미 `test` 소스 세트와 `testImplementation(libs.junit)`이 있어 빌드 파일 변경이 없다.

**Interfaces:**
- Produces:
  - `fun funnelScreenMetadata(): Map<String, Any>` — 퍼널 엔트리에 다는 표식
  - `internal fun funnelTransitionDirection(fromFunnel: Boolean, toFunnel: Boolean): FunnelTransitionDirection`
  - `enum class FunnelTransitionDirection { ENTER, EXIT, NONE }`

- [ ] **Step 1: 실패하는 판정 테스트 작성**

`app/src/test/java/app/manyak/root/FunnelSceneTransitionTest.kt`

```kotlin
package app.manyak.root

import org.junit.Assert.assertEquals
import org.junit.Test

class FunnelSceneTransitionTest {
    @Test
    fun `홈에서 퍼널로 들어가면 올라온다`() {
        assertEquals(
            FunnelTransitionDirection.ENTER,
            funnelTransitionDirection(fromFunnel = false, toFunnel = true),
        )
    }

    @Test
    fun `퍼널에서 나가면 내려간다`() {
        assertEquals(
            FunnelTransitionDirection.EXIT,
            funnelTransitionDirection(fromFunnel = true, toFunnel = false),
        )
    }

    @Test
    fun `퍼널 단계 사이 이동은 수직 전환이 아니다`() {
        assertEquals(
            FunnelTransitionDirection.NONE,
            funnelTransitionDirection(fromFunnel = true, toFunnel = true),
        )
    }

    @Test
    fun `퍼널 밖끼리의 이동은 수직 전환이 아니다`() {
        assertEquals(
            FunnelTransitionDirection.NONE,
            funnelTransitionDirection(fromFunnel = false, toFunnel = false),
        )
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "app.manyak.root.FunnelSceneTransitionTest"`
Expected: 컴파일 실패 — `funnelTransitionDirection`, `FunnelTransitionDirection` 미해결

- [ ] **Step 3: 전환 규칙 구현**

`app/src/main/java/app/manyak/root/NavTransitions.kt`를 교체한다.

```kotlin
package app.manyak.root

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import app.manyak.core.ui.theme.ManyakTheme

/** 제작 퍼널 목적지임을 알리는 엔트리 표식. 전환 판정이 이것만 본다. */
private const val FUNNEL_SCREEN_KEY = "app.manyak.root.funnelScreen"

/** 퍼널 목적지의 `NavEntry` metadata. 표식이 없는 목적지는 퍼널 밖으로 본다. */
fun funnelScreenMetadata(): Map<String, Any> = mapOf(FUNNEL_SCREEN_KEY to true)

/** 퍼널 경계를 넘는 전환의 방향. 경계를 넘지 않으면 [NONE] 이고 기존 교차 페이드를 쓴다. */
enum class FunnelTransitionDirection {
    ENTER,
    EXIT,
    NONE,
}

internal fun funnelTransitionDirection(
    fromFunnel: Boolean,
    toFunnel: Boolean,
): FunnelTransitionDirection =
    when {
        fromFunnel == toFunnel -> FunnelTransitionDirection.NONE
        toFunnel -> FunnelTransitionDirection.ENTER
        else -> FunnelTransitionDirection.EXIT
    }

/** `Scene.metadata` 는 마지막 엔트리의 metadata 를 그대로 노출한다. */
private fun Scene<*>.isFunnel(): Boolean = metadata[FUNNEL_SCREEN_KEY] == true

/**
 * 화면·탭 전환.
 *
 * 기본은 교차 페이드다. 제작 퍼널은 홈 위에 얹히는 한 덩어리로 읽혀야 하므로 퍼널 경계를
 * 넘을 때만 수직으로 밀어 올리고 내린다. 퍼널 단계 사이 이동까지 수직이면 "다음 단계"와
 * "퍼널 진입"이 같은 모션이 되어 구분되지 않는다.
 *
 * 기본 지속 시간(700ms)은 하단 탭처럼 자주 오가는 전환에서 눌렀는데 뒤늦게 따라오는 느낌을 준다.
 * 예측형 뒤로가기는 손가락을 따라오는 제스처 피드백 그 자체이므로 여기서 바꾸지 않고 기본값을 쓴다.
 */
@Composable
fun rememberScreenTransition(): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform {
    val durationMillis = ManyakTheme.motion.screenTransitionMillis
    return remember(durationMillis) {
        {
            val spec = tween<Float>(durationMillis)
            when (funnelTransitionDirection(initialState.isFunnel(), targetState.isFunnel())) {
                FunnelTransitionDirection.NONE -> fadeIn(spec) togetherWith fadeOut(spec)

                // 들어오는 퍼널을 위에 얹고 아래 화면은 그대로 둔다. 아래를 함께 페이드하면
                // 퍼널이 덮는 게 아니라 두 화면이 동시에 바뀌는 것으로 보인다.
                FunnelTransitionDirection.ENTER ->
                    (
                        slideInVertically(tween(durationMillis)) { height -> height } togetherWith
                            ExitTransition.None
                    ).apply { targetContentZIndex = FUNNEL_Z_INDEX }

                // 나가는 퍼널이 위에 남은 채 내려가고 아래 화면이 드러난다.
                FunnelTransitionDirection.EXIT ->
                    (
                        EnterTransition.None togetherWith
                            slideOutVertically(tween(durationMillis)) { height -> height }
                    ).apply { targetContentZIndex = BASE_Z_INDEX }
            }
        }
    }
}

private const val FUNNEL_Z_INDEX = 1f
private const val BASE_Z_INDEX = 0f
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "app.manyak.root.FunnelSceneTransitionTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: 퍼널 엔트리에 표식 달기**

`app/src/main/java/app/manyak/root/ManyakApp.kt`의 `MainNavDisplay` 안에서 퍼널 목적지 세 개의 `entry` 호출에 metadata를 준다.

```kotlin
                entry<CreateKeywordRoute>(metadata = funnelScreenMetadata()) {
```

```kotlin
                entry<CreateStorylineRoute>(metadata = funnelScreenMetadata()) {
```

```kotlin
                entry<CreateAdditionalInfoRoute>(metadata = funnelScreenMetadata()) { route ->
```

`ChatRoomRoute`에는 달지 않는다 — 채팅방은 퍼널이 아니라 완성 이후의 목적지다.

- [ ] **Step 6: 빌드 확인**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 기기에서 확인**

1. 홈 FAB → 키워드 화면이 아래에서 올라오고 홈이 그 아래에 남아 있다.
2. 키워드 → 스토리라인, 스토리라인 → 추가 정보는 기존 교차 페이드다.
3. arrow-down·디바이스 뒤로가기 → 퍼널이 아래로 내려가며 홈이 드러난다.
4. 스토리 완성 → 퍼널이 내려가며 채팅방이 드러난다.
5. 퍼널이 홈 아래로 들어가거나 잔상이 남지 않는다. z-order가 어긋나면 `ENTER`의 `ExitTransition.None`을 `fadeOut(tween(durationMillis))`으로 바꾼다.

- [ ] **Step 8: 정적 검사와 커밋**

```bash
./gradlew :app:ktlintCheck :app:detekt
git add -- app/src app/build.gradle.kts
git commit -m "[KNK-778] Design: 퍼널 진입·이탈을 수직 전환으로 변경"
```

---

### Task 10: 전체 검증과 매트릭스 갱신

**Files:**
- Modify: `../knk-harness/docs/product-specs/3-3-android-app.md`

- [ ] **Step 1: 전체 게이트 실행**

Run: `./gradlew check assembleDebug`
Expected: BUILD SUCCESSFUL. 실패하면 해당 태스크로 돌아가 고친다.

- [ ] **Step 2: 회귀 시나리오 수동 확인**

1. 키워드 → 스토리라인 → 추가 정보 → 완성 → 채팅방까지 한 번에 통과한다.
2. 각 단계에서 이탈 후 "이어서 만들기"가 그 단계로 돌아온다(키워드·스토리라인·추가 정보).
3. 스토리라인 생성 중 이탈 후 재개하면 폴링이 이어져 결과가 도착한다.
4. 완성 중 이탈 후 재개하면 완성 로딩으로 이어지고 채팅방까지 간다.
5. 로그아웃하면 배너가 사라지고, 다시 로그인해도 되살아나지 않는다.
6. 홈 FAB로 진입하면 이어서/새로 만들기를 묻고, "새로 만들기"는 키워드 단계부터 시작한다.

- [ ] **Step 3: 구현 상태 매트릭스 갱신**

하네스 §3-3-3의 `플랫폼 구현 상태 매트릭스`에서 `FE-SCREEN-002 백그라운드 생성 복귀·제작 임시 저장` 행의 비고를 갱신한다.

```markdown
| FE-SCREEN-002 | 백그라운드 생성 복귀·제작 임시 저장 | Phase 1 | 확정 | 구현 | 간편 제작 필수 | 구현 | requestId 영속(Room 단일 행)·복구 3초 폴링·`STORY_DRAFT` 임시 저장·소실 경고 다이얼로그·홈 배너·재개 다이얼로그 구현. 앱 전용 차이 — `KEYWORD_DRAFT` 키워드 단계 임시 저장을 더하고 배너 닫기(X)는 두지 않음(§3-3-5) | §3-3-5 |
```

- [ ] **Step 4: 커밋**

```bash
git -C ../knk-harness add -- docs/product-specs/3-3-android-app.md
git -C ../knk-harness commit -m "[KNK-967] Docs: 퍼널 이탈 개편 구현 상태 매트릭스 갱신"
```
