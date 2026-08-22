package app.manyak.core.data.api

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * 예외를 오류 타입으로 바꾸는 **유일한 지점**이다(하네스 §3-3-2). 상위 계층은 예외가 아니라
 * [DomainResult] 를 받는다.
 *
 * 취소(`CancellationException`)는 여기서 잡지 않는다 — 화면 이탈로 취소된 작업이 실패로 둔갑하면 안 된다.
 * `runCatching` 대신 명시적으로 IO·직렬화 예외만 잡는 이유다.
 */
suspend fun <T : Any> apiCall(request: suspend () -> Response<T>): DomainResult<T> =
    try {
        request().toDomainResult()
    } catch (_: IOException) {
        // 원인 메시지에는 URL·헤더가 섞일 수 있어 상위로 올리지 않는다. 진단은 Crashlytics 배선이 맡는다.
        DomainResult.Failure(DomainError.Network)
    } catch (_: SerializationException) {
        DomainResult.Failure(DomainError.Serialization)
    }

fun <T : Any> Response<T>.toDomainResult(): DomainResult<T> {
    val body = body()
    return when {
        isSuccessful && body != null -> DomainResult.Success(body)
        isSuccessful -> DomainResult.Failure(DomainError.Serialization)
        else -> DomainResult.Failure(toDomainError())
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

private fun Response<*>.parseErrorCode(): String? {
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

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403

private val lenientJson = Json { ignoreUnknownKeys = true }
