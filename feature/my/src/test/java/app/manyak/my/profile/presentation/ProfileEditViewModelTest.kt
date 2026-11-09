package app.manyak.my.profile.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.my.profile.domain.ProfileEditRepository
import app.manyak.my.profile.entity.ProfilePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `규칙을 어긴 닉네임은 보내지 않고 칸을 고치면 오류를 지운다`() =
        runTest(dispatcher) {
            val repository = FakeProfileEditRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.onIntent(ProfileEditIntent.NicknameChanged("달빛 작가"))
            advanceUntilIdle()
            viewModel.onIntent(ProfileEditIntent.Submit)
            advanceUntilIdle()
            assertEquals(NicknameError.INVALID_CHARACTER, viewModel.uiState.value.nicknameError)

            viewModel.onIntent(ProfileEditIntent.NicknameChanged("달"))
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.nicknameError)
            viewModel.onIntent(ProfileEditIntent.Submit)
            advanceUntilIdle()
            assertEquals(NicknameError.TOO_SHORT, viewModel.uiState.value.nicknameError)

            viewModel.onIntent(ProfileEditIntent.NicknameChanged("ㄷㄹ"))
            advanceUntilIdle()
            viewModel.onIntent(ProfileEditIntent.Submit)
            advanceUntilIdle()
            assertEquals(NicknameError.INVALID_CHARACTER, viewModel.uiState.value.nicknameError)
            assertTrue(repository.updates.isEmpty())
        }

    @Test
    fun `바뀐 필드만 보낸다`() =
        runTest(dispatcher) {
            val repository = FakeProfileEditRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.onIntent(ProfileEditIntent.SelectPreset(Preset))
            advanceUntilIdle()
            viewModel.onIntent(ProfileEditIntent.Submit)
            advanceUntilIdle()
            assertEquals(ProfileEditEffect.Saved, withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.uiEffect.first() })

            val nicknameOnly = viewModel(repository)
            advanceUntilIdle()
            nicknameOnly.onIntent(ProfileEditIntent.NicknameChanged("Storyteller7"))
            advanceUntilIdle()
            nicknameOnly.onIntent(ProfileEditIntent.Submit)
            advanceUntilIdle()

            assertEquals(listOf(null to "작가", "Storyteller7" to null), repository.updates)
        }

    @Test
    fun `원래 이미지와 같은 프리셋은 바뀐 내용이 아니다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeProfileEditRepository())
            advanceUntilIdle()

            viewModel.onIntent(ProfileEditIntent.SelectPreset(Preset.copy(imageUrl = Original.profileImageUrl!!)))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isDirty)
        }

    @Test
    fun `서버 거부 사유는 칸 아래에, 그 밖의 실패는 토스트로 알리고 입력을 둔다`() =
        runTest(dispatcher) {
            val repository = FakeProfileEditRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            viewModel.onIntent(ProfileEditIntent.NicknameChanged("달빛작가"))
            advanceUntilIdle()

            repository.result = DomainResult.Failure(DomainError.Server(409, "NICKNAME_TAKEN", null))
            viewModel.onIntent(ProfileEditIntent.Submit)
            advanceUntilIdle()
            assertEquals(NicknameError.TAKEN, viewModel.uiState.value.nicknameError)

            repository.result = DomainResult.Failure(DomainError.Server(400, "BAD_REQUEST", null))
            viewModel.onIntent(ProfileEditIntent.Submit)
            advanceUntilIdle()
            assertEquals(NicknameError.REJECTED, viewModel.uiState.value.nicknameError)

            repository.result = DomainResult.Failure(DomainError.Network)
            viewModel.onIntent(ProfileEditIntent.Submit)
            advanceUntilIdle()
            assertEquals(
                ProfileEditEffect.ShowSaveFailed,
                withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.uiEffect.first() },
            )
            assertEquals("달빛작가", viewModel.uiState.value.nickname)
            assertFalse(viewModel.uiState.value.isSubmitting)
        }

    @Test
    fun `바뀐 내용이 있을 때만 나가기 전에 묻는다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeProfileEditRepository())
            advanceUntilIdle()

            viewModel.onIntent(ProfileEditIntent.NicknameChanged("달빛작가"))
            advanceUntilIdle()
            viewModel.onIntent(ProfileEditIntent.Back)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isExitDialogOpen)

            // 다이얼로그가 떠 있을 때 뒤로가기는 다이얼로그만 닫는다.
            viewModel.onIntent(ProfileEditIntent.Back)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isExitDialogOpen)

            viewModel.onIntent(ProfileEditIntent.NicknameChanged(Original.nickname))
            advanceUntilIdle()
            viewModel.onIntent(ProfileEditIntent.Back)
            advanceUntilIdle()
            assertEquals(ProfileEditEffect.Close, withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.uiEffect.first() })
        }

    @Test
    fun `캐시가 비었으면 서버에서 읽고 실패하면 다시 시도할 수 있다`() =
        runTest(dispatcher) {
            val profiles = FakeProfiles(cached = null, refreshed = DomainResult.Failure(DomainError.Network))
            val viewModel = ProfileEditViewModel(profiles, FakeProfileEditRepository(), NoOpAnalytics)
            advanceUntilIdle()
            assertEquals(ProfileEditLoad.FAILED, viewModel.uiState.value.load)

            profiles.refreshed = DomainResult.Success(Original)
            viewModel.onIntent(ProfileEditIntent.Retry)
            advanceUntilIdle()

            assertEquals(ProfileEditLoad.READY, viewModel.uiState.value.load)
            assertEquals(Original.nickname, viewModel.uiState.value.nickname)
        }

    private fun viewModel(repository: ProfileEditRepository) =
        ProfileEditViewModel(FakeProfiles(cached = Original), repository, NoOpAnalytics)

    private class FakeProfiles(
        cached: UserProfile?,
        var refreshed: DomainResult<UserProfile> = DomainResult.Success(Original),
    ) : UserProfileRepository {
        override val profile: StateFlow<UserProfile?> = MutableStateFlow(cached)

        override suspend fun refresh(): DomainResult<UserProfile> = refreshed
    }

    private class FakeProfileEditRepository : ProfileEditRepository {
        var result: DomainResult<UserProfile> = DomainResult.Success(Original)
        val updates = mutableListOf<Pair<String?, String?>>()

        override suspend fun presets(): DomainResult<List<ProfilePreset>> = DomainResult.Success(listOf(Preset))

        override suspend fun update(
            nickname: String?,
            presetKey: String?,
        ): DomainResult<UserProfile> {
            updates += nickname to presetKey
            return result
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 1_000L

        val Original =
            UserProfile(
                id = "user-1",
                nickname = "낭만적인표류자",
                profileImageUrl = "https://example.com/이야기꾼.png",
                profileThumbnailBase64 = null,
                status = AccountStatus.ACTIVE,
                creditBalance = 0,
                attendedToday = false,
                linkedProviders = emptyList(),
            )

        val Preset = ProfilePreset(key = "작가", imageUrl = "https://example.com/작가.png", thumbnailBase64 = null)
    }
}
