package app.manyak.chat.testing

import app.manyak.common.domain.credit.CreditTransactionReader
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.credit.CreditTransactionPage
import kotlinx.coroutines.CompletableDeferred

/** 커서별로 [pages] 의 결과를 돌려준다. 없는 커서는 네트워크 실패다. [gate] 를 주면 열릴 때까지 응답을 미룬다. */
internal class FakeCreditTransactionReader(
    var pages: Map<String?, DomainResult<CreditTransactionPage>> = emptyMap(),
    var gate: CompletableDeferred<Unit>? = null,
) : CreditTransactionReader {
    val requests = mutableListOf<String?>()

    override suspend fun getTransactions(cursor: String?): DomainResult<CreditTransactionPage> {
        requests += cursor
        gate?.await()
        return pages[cursor] ?: DomainResult.Failure(DomainError.Network)
    }
}
