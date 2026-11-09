package app.manyak.create.testing

import app.manyak.common.domain.credit.TrialsRepository
import app.manyak.common.entity.credit.Trials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeTrialsRepository(
    initial: Trials? = null,
) : TrialsRepository {
    override val trials: StateFlow<Trials?> = MutableStateFlow(initial)

    var refreshCount = 0
        private set

    override suspend fun refresh() {
        refreshCount++
    }
}
