package app.manyak.feature.my

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.manyak.core.analytics.AnalyticsEvent
import app.manyak.core.analytics.LocalAnalytics
import app.manyak.core.ui.R
import app.manyak.designsystem.theme.ManyakTheme

/** 이프 충전의 두 탭. 이프를 얻는 수단은 무료 충전이, 쓰고 받은 기록은 내역이 맡는다. */
internal enum class CreditChargeTab(
    val labelRes: Int,
) {
    FREE(R.string.my_credit_charge_tab_free),
    HISTORY(R.string.my_credit_history),
}

/** 퍼널의 카테고리 탭과 같은 밑줄 탭이다. 누르면 라벨 색과 표시선이 곧바로 바뀌므로 리플은 끈다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreditChargeTabRow(
    selected: CreditChargeTab,
    onSelect: (CreditChargeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = LocalAnalytics.current
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        SecondaryTabRow(
            modifier = modifier.fillMaxWidth(),
            selectedTabIndex = selected.ordinal,
            containerColor = ManyakTheme.colors.surface,
            contentColor = ManyakTheme.colors.text,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selected.ordinal),
                    height = TabIndicatorHeight,
                    color = ManyakTheme.colors.text,
                )
            },
        ) {
            CreditChargeTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selected,
                    onClick = {
                        // 같은 탭 재선택은 전환이 아니다.
                        if (tab !=
                            selected
                        ) {
                            analytics.track(AnalyticsEvent.CreditChargeTabSelected(tab.name.lowercase()))
                        }
                        onSelect(tab)
                    },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = ManyakTheme.typography.labelLarge,
                            color =
                                if (tab == selected) {
                                    ManyakTheme.colors.text
                                } else {
                                    ManyakTheme.colors.textSubtle
                                },
                        )
                    },
                )
            }
        }
    }
}

/** 퍼널 카테고리 탭과 같은 표시선 두께. */
private val TabIndicatorHeight = 1.5.dp
