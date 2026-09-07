package app.manyak.my.credit.data.repository

import app.manyak.my.credit.data.api.CreditPolicyApi
import app.manyak.my.credit.data.dto.CreditPolicyResponseDto
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class CreditPolicyRepositoryImplTest {
    @Test
    fun `조회하기 전에는 수치를 모른다`() {
        val repository = CreditPolicyRepositoryImpl(FakeCreditPolicyApi())

        assertNull(repository.policy.value)
    }

    @Test
    fun `성공하면 서버가 준 수치를 그대로 들고 있는다`() =
        runTest {
            val api = FakeCreditPolicyApi(response = Response.success(policyDto(attendanceReward = 350)))
            val repository = CreditPolicyRepositoryImpl(api)

            repository.refresh()

            assertEquals(350L, repository.policy.value?.attendanceReward)
        }

    @Test
    fun `실패해도 들고 있던 수치를 지우지 않는다`() =
        runTest {
            val api = FakeCreditPolicyApi(response = Response.success(policyDto(attendanceReward = 700)))
            val repository = CreditPolicyRepositoryImpl(api)
            repository.refresh()

            api.response = Response.error(500, "".toResponseBody("application/json".toMediaType()))
            repository.refresh()

            assertEquals(700L, repository.policy.value?.attendanceReward)
        }

    private fun policyDto(attendanceReward: Long) =
        CreditPolicyResponseDto(
            signupReward = 1_000,
            inviteReward = 2_000,
            inviteMonthlyCap = 10,
            attendanceReward = attendanceReward,
            storyCreationCost = 200,
            chatTurnCost = 20,
        )
}

private class FakeCreditPolicyApi(
    var response: Response<CreditPolicyResponseDto> = Response.success(CreditPolicyResponseDto()),
) : CreditPolicyApi {
    override suspend fun creditPolicies(): Response<CreditPolicyResponseDto> = response
}
