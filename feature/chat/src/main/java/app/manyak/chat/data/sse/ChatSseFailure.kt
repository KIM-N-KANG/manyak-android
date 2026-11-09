package app.manyak.chat.data.sse

import app.manyak.common.domain.error.DomainError
import app.manyak.network.data.interceptor.SessionUnavailableException

/**
 * 스트림이 실패로 끝난 이유를 오류 타입으로 옮긴다.
 *
 * **응답 본문을 읽지 않는다.** 웹은 402 본문의 `code` 로 게스트 체험 한도와 이프 부족을 갈랐지만
 * 앱은 로그인 필수라 402 의 사유가 하나뿐이다. 상태만으로 충분하고, 스트리밍 응답의 본문을 건드리면
 * 라이브러리가 닫는 시점과 얽힌다.
 *
 * @param throwable 전송 자체가 실패했을 때의 원인. 응답을 받았으면 `null`
 * @param status 응답 상태. 응답을 받지 못했으면 `null`
 */
internal fun sseDomainError(
    throwable: Throwable?,
    status: Int?,
    requestId: String?,
): DomainError =
    when {
        throwable is SessionUnavailableException -> DomainError.Unauthorized
        throwable != null -> DomainError.Network
        status == null -> DomainError.Unknown
        status == HTTP_UNAUTHORIZED -> DomainError.Unauthorized
        status == HTTP_FORBIDDEN -> DomainError.AccountSuspended
        // 2xx 인데 실패로 들어온 경우는 본문이 `text/event-stream` 이 아니었다는 뜻이다. 재시도로
        // 낫지 않는 계약 불일치라 네트워크 실패와 구분한다.
        status in HttpSuccessRange -> DomainError.Serialization
        else -> DomainError.Server(status = status, code = null, requestId = requestId)
    }

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private val HttpSuccessRange = 200..299
