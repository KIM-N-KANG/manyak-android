package app.manyak.core.data.api

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * 예외를 오류 타입으로 바꾸는 **유일한 지점**이다. 상위 계층은 예외가 아니라
 * [DomainResult] 를 받는다.
 *
 * 취소(`CancellationException`)는 여기서 잡지 않는다 — 화면 이탈로 취소된 작업이 실패로 둔갑하면 안 된다.
 * `runCatching` 대신 명시적으로 IO·직렬화 예외만 잡는 이유다.
 */
suspend fun <T : Any> apiCall(
    mapError: Response<*>.() -> DomainError = { toDomainError() },
    request: suspend () -> Response<T>,
): DomainResult<T> =
    try {
        request().toDomainResult(mapError)
    } catch (_: IOException) {
        // 원인 메시지에는 URL·헤더가 섞일 수 있어 상위로 올리지 않는다. 진단은 Crashlytics 배선이 맡는다.
        DomainResult.Failure(DomainError.Network)
    } catch (_: SerializationException) {
        DomainResult.Failure(DomainError.Serialization)
    }

/**
 * 본문 없는 성공(204 등)을 다루는 변형. Retrofit 은 204·205 응답의 본문을 컨버터에 넘기지
 * 않아 `body()` 가 null 이므로, [apiCall] 의 "성공인데 본문 없음 = 역직렬화 실패" 판정을 쓸 수 없다.
 */
suspend fun emptyBodyApiCall(
    mapError: Response<*>.() -> DomainError = { toDomainError() },
    request: suspend () -> Response<Unit>,
): DomainResult<Unit> =
    try {
        val response = request()
        if (response.isSuccessful) {
            DomainResult.Success(Unit)
        } else {
            DomainResult.Failure(response.mapError())
        }
    } catch (_: IOException) {
        DomainResult.Failure(DomainError.Network)
    } catch (_: SerializationException) {
        DomainResult.Failure(DomainError.Serialization)
    }

fun <T : Any> Response<T>.toDomainResult(
    mapError: Response<*>.() -> DomainError = { toDomainError() },
): DomainResult<T> {
    val body = body()
    return when {
        isSuccessful && body != null -> DomainResult.Success(body)
        isSuccessful -> DomainResult.Failure(DomainError.Serialization)
        else -> DomainResult.Failure(mapError())
    }
}

fun Response<*>.toDomainError(): DomainError {
    val requestId = headers()[HEADER_REQUEST_ID]
    val code = parseErrorCode()
    return when (code()) {
        HTTP_UNAUTHORIZED -> DomainError.Unauthorized
        HTTP_FORBIDDEN -> DomainError.AccountSuspended
        else -> DomainError.Server(status = code(), code = code, requestId = requestId)
    }
}

fun Response<*>.parseErrorCode(): String? {
    val raw = errorBody()?.string().orEmpty()
    if (raw.isBlank()) return null
    return try {
        lenientJson.decodeFromString<app.manyak.core.data.api.dto.ApiErrorResponseDto>(raw).code
    } catch (_: SerializationException) {
        null
    }
}

const val HEADER_DEVICE_ID = "X-Manyak-Device-Id"
const val HEADER_REQUEST_ID = "X-Manyak-Request-Id"

internal const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403

private val lenientJson = Json { ignoreUnknownKeys = true }
