package app.manyak.core.domain.error

/**
 * 상위 계층은 예외가 아니라 이 타입으로 결과를 받는다. 예외를 오류 타입으로 바꾸는 지점은
 * `:core:data` 한 곳이다(하네스 §3-3-2).
 */
sealed interface DomainResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : DomainResult<T>

    data class Failure(
        val error: DomainError,
    ) : DomainResult<Nothing>
}

fun <T> DomainResult<T>.valueOrNull(): T? = (this as? DomainResult.Success)?.value

fun <T> DomainResult<T>.errorOrNull(): DomainError? = (this as? DomainResult.Failure)?.error

inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> =
    when (this) {
        is DomainResult.Success -> DomainResult.Success(transform(value))
        is DomainResult.Failure -> this
    }
