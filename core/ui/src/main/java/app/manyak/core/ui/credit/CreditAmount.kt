package app.manyak.core.ui.credit

import androidx.compose.runtime.Composable
import app.manyak.core.ui.component.rememberSkeletonPulseAlpha

/**
 * 자리표시 숫자에 얹는 옅어짐. 골격을 깔아 두고 값을 기다리는 자리라 조회 중 목록과 같은 맥박을 쓴다.
 * 값이 도착하면 [pending] 이 false 가 되어 애니메이션이 조합에서 빠진다.
 */
@Composable
fun creditAmountAlpha(pending: Boolean): Float = if (pending) rememberSkeletonPulseAlpha() else 1f
