package app.manyak.chat.testing

import app.manyak.common.domain.credit.CreditPolicyRepository
import app.manyak.common.entity.credit.CreditPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeCreditPolicyRepository(
    initial: CreditPolicy? = null,
) : CreditPolicyRepository {
    override val policy: StateFlow<CreditPolicy?> = MutableStateFlow(initial)

    override suspend fun refresh() = Unit
}
