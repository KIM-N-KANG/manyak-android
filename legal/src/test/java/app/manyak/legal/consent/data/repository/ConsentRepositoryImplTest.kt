package app.manyak.legal.consent.data.repository

import app.manyak.auth.domain.SessionGate
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.consent.ConsentItem
import app.manyak.legal.consent.data.api.ConsentApi
import app.manyak.legal.consent.data.api.dto.ConsentStatusDto
import app.manyak.legal.consent.data.api.dto.UserConsentRequestDto
import app.manyak.legal.consent.data.api.dto.UserConsentResponseDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ConsentRepositoryImplTest {
    @Test
    fun `서버 확인 전과 정리 후에는 미완료이며 기존 동의 회원은 조회로 완료된다`() =
        runTest {
            val repository = ConsentRepositoryImpl(FakeApi(), SessionGate())
            assertFalse(repository.isSatisfied.value)
            assertTrue(repository.get() is DomainResult.Success)
            assertTrue(repository.isSatisfied.value)
            repository.clearUserData()
            assertFalse(repository.isSatisfied.value)
        }

    @Test
    fun `필요 항목이 남으면 차단하고 기록 응답까지 완료된 뒤에만 연다`() =
        runTest {
            val api = FakeApi().apply { response = complete().copy(terms = ConsentStatusDto("v2", true)) }
            val repository = ConsentRepositoryImpl(api, SessionGate())
            repository.get()
            assertFalse(repository.isSatisfied.value)
            repository.record(mapOf(ConsentItem.TERMS to "v2"))
            assertFalse(repository.isSatisfied.value)
            api.response = complete()
            repository.record(mapOf(ConsentItem.TERMS to "v2"))
            assertTrue(repository.isSatisfied.value)
        }

    @Test
    fun `항목 또는 완료 판정 필드가 누락된 응답은 동의 완료가 아니다`() =
        runTest {
            for (response in listOf(
                complete().copy(terms = null),
                complete().copy(age14 = ConsentStatusDto("1", null)),
                complete().copy(privacy = ConsentStatusDto(null, false)),
            )) {
                val api = FakeApi().apply { this.response = response }
                val repository = ConsentRepositoryImpl(api, SessionGate())
                assertEquals(DomainResult.Failure(DomainError.Serialization), repository.get())
                assertFalse(repository.isSatisfied.value)
            }
        }

    @Test
    fun `로그아웃 중 늦은 성공 응답은 동의 상태를 되살리지 못한다`() =
        runTest {
            val gate = SessionGate()
            val response = CompletableDeferred<Unit>()
            val api = FakeApi().apply { wait = { withContext(NonCancellable) { response.await() } } }
            val repository = ConsentRepositoryImpl(api, gate)
            val load = launch { repository.get() }
            testScheduler.runCurrent()
            val logout = launch { gate.raiseBarrier() }
            testScheduler.runCurrent()
            repository.clearUserData()
            response.complete(Unit)
            load.join()
            logout.join()
            assertFalse(repository.isSatisfied.value)
            gate.lowerBarrier()
            repository.get()
            assertTrue(repository.isSatisfied.value)
        }
}

private fun complete() =
    UserConsentResponseDto(ConsentStatusDto("v1", false), ConsentStatusDto("v1", false), ConsentStatusDto("1", false))

private class FakeApi : ConsentApi {
    var response = complete()
    var wait: suspend () -> Unit = {}

    override suspend fun get(): Response<UserConsentResponseDto> {
        wait()
        return Response.success(response)
    }

    override suspend fun record(request: UserConsentRequestDto): Response<UserConsentResponseDto> = get()
}
