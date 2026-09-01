package app.manyak.core.ui.credit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import app.manyak.core.domain.credit.CreditPolicy
import app.manyak.core.ui.component.rememberSkeletonPulseAlpha
import java.text.NumberFormat
import java.util.Locale

/**
 * 서버가 내려준 이프 수치. 아직 받지 못했으면 null 이다.
 *
 * 수치를 쓰는 화면이 세 기능 모듈에 흩어져 있어 화면마다 상태·의도를 늘리는 대신 여기로 내린다 —
 * 화면은 조회를 시작하지 않고 "지금 값이 무엇인가"만 읽는다.
 */
val LocalCreditPolicy = compositionLocalOf<CreditPolicy?> { null }

/** 값을 아직 모를 때 문구의 숫자 자리를 채우는 표시. 대체 수치가 아니다. */
const val CREDIT_AMOUNT_PLACEHOLDER = "000"

/**
 * 이프 금액을 문구에 넣을 문자열로 바꾼다. 값이 없으면 자리표시 숫자를 돌려주므로 문구의 골격은
 * 그대로 남고, 쓰는 쪽이 [creditAmountAlpha] 를 함께 얹어 확정된 값이 아님을 드러낸다.
 *
 * 구분자는 기기 로케일이 아니라 한국어로 고정한다 — 문구가 한국어 하나뿐이라 기기 설정에 따라
 * 숫자만 다른 규칙으로 끊기면 같은 문장 안에서 어긋난다.
 */
fun creditAmountText(amount: Long?): String =
    amount?.let { NumberFormat.getIntegerInstance(Locale.KOREA).format(it) } ?: CREDIT_AMOUNT_PLACEHOLDER

/**
 * 자리표시 숫자에 얹는 옅어짐. 골격을 깔아 두고 값을 기다리는 자리라 조회 중 목록과 같은 맥박을 쓴다.
 * 값이 도착하면 [pending] 이 false 가 되어 애니메이션이 조합에서 빠진다.
 */
@Composable
fun creditAmountAlpha(pending: Boolean): Float = if (pending) rememberSkeletonPulseAlpha() else 1f
