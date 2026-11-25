package app.manyak.network.domain

import app.manyak.network.entity.TokenAccess

/** HTTP requests use this contract without owning session storage or refresh policy. */
interface SessionTokenAccess {
    val currentGeneration: Long

    suspend fun accessToken(): TokenAccess

    suspend fun refreshAfterUnauthorized(observedGeneration: Long): TokenAccess
}
