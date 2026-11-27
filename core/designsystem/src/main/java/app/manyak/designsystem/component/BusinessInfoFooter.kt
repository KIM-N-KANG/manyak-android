package app.manyak.designsystem.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import app.manyak.designsystem.R
import app.manyak.designsystem.theme.ManyakTheme

private val BusinessInfoItems =
    listOf(
        R.string.business_name_label to R.string.business_name,
        R.string.business_representative_label to R.string.business_representative,
        R.string.business_registration_number_label to R.string.business_registration_number,
        R.string.business_mail_order_number_label to R.string.business_mail_order_number,
        R.string.business_address_label to R.string.business_address,
        R.string.business_phone_label to R.string.business_phone,
        R.string.business_email_label to R.string.business_email,
        R.string.business_hosting_label to R.string.business_hosting,
    )

/** 공정거래위원회 사업자정보 공개 페이지. `business_registration_number` 가 바뀌면 함께 고친다. */
private const val FTC_BUSINESS_INFO_URL = "https://www.ftc.go.kr/bizCommPop.do?wrkr_no=2020455303"

/**
 * 홈과 마이 맨 아래의 사업자 정보와 약관 링크. 라벨과 값은 한 쌍으로만 줄을 넘겨 라벨이 앞줄 끝에
 * 홀로 남지 않게 한다. 링크 줄은 터치 타깃 높이를 확보하느라 위아래 여백이 정보 묶음보다 넓다.
 *
 * 본문과는 여백 안쪽의 가로 구분선으로 나눈다.
 */
@Composable
fun BusinessInfoFooter(
    onOpenTerms: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 위 내용과의 48dp는 부르는 쪽이 간격 없이 붙이고 푸터가 혼자 갖는다. 링크 줄은 48dp 터치 높이 안에
    // 글자 위아래 여백을 품고 있어 맨 아래 여백을 따로 두지 않아도 보이는 여백이 16dp가 된다.
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.block + ManyakTheme.spacing.gutter),
    ) {
        HorizontalDivider(thickness = ManyakTheme.sizes.borderWidth, color = ManyakTheme.colors.border)
        Spacer(modifier = Modifier.height(ManyakTheme.spacing.gutter))
        BusinessInfoPairs()
        FooterLinks(onOpenTerms = onOpenTerms, onOpenPrivacyPolicy = onOpenPrivacyPolicy)
    }
}

@Composable
private fun BusinessInfoPairs() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        BusinessInfoItems.forEach { (labelRes, valueRes) ->
            // 라벨과 값을 한 번에 읽도록 묶는다. 따로 두면 낭독 포커스가 항목마다 두 번씩 멈춘다.
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                FooterText(text = stringResource(labelRes))
                FooterText(text = stringResource(valueRes))
            }
        }
    }
}

@Composable
private fun FooterLinks(
    onOpenTerms: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val context = LocalContext.current
    val openFailed = stringResource(R.string.business_footer_open_failed)
    LinkLine(
        links =
            listOf(
                { FooterLink(labelRes = R.string.business_footer_terms, onClick = onOpenTerms) },
                { FooterLink(labelRes = R.string.business_footer_privacy, onClick = onOpenPrivacyPolicy) },
                {
                    FooterLink(
                        labelRes = R.string.business_footer_ftc,
                        onClick = { openInBrowser(context, FTC_BUSINESS_INFO_URL, openFailed) },
                    )
                },
            ),
    )
}

/**
 * 링크를 줄 바꿈하며 늘어놓고 같은 줄의 링크 사이에만 세로선을 둔다. 줄이 바뀌는 자리의 세로선은 그리지 않아
 * 앞줄 끝이나 다음 줄 맨 앞에 홀로 남지 않는다.
 */
@Composable
private fun LinkLine(links: List<@Composable () -> Unit>) {
    val gap = ManyakTheme.spacing.compact
    Layout(
        contents = listOf({ links.forEach { it() } }, { repeat(links.size - 1) { LinkDivider() } }),
    ) { (linkMeasurables, dividerMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val linkPlaceables = linkMeasurables.map { it.measure(loose) }
        val dividerPlaceables = dividerMeasurables.map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val dividerSpan = dividerPlaceables.firstOrNull()?.let { it.width + gapPx * 2 } ?: 0

        // 줄마다 링크 번호를 모은다. 앞 링크와 같은 줄에 들어갈 때만 세로선 자리를 더한다.
        val lines = mutableListOf(mutableListOf<Int>())
        var x = 0
        linkPlaceables.forEachIndexed { index, link ->
            val needed = if (lines.last().isEmpty()) link.width else dividerSpan + link.width
            if (lines.last().isNotEmpty() && x + needed > constraints.maxWidth) {
                lines += mutableListOf<Int>()
                x = link.width
            } else {
                x += needed
            }
            lines.last() += index
        }
        val lineHeights = lines.map { line -> line.maxOf { linkPlaceables[it].height } }
        layout(constraints.maxWidth, lineHeights.sum()) {
            var y = 0
            lines.forEachIndexed { lineIndex, line ->
                val height = lineHeights[lineIndex]
                var cursor = 0
                line.forEachIndexed { position, linkIndex ->
                    if (position > 0) {
                        val divider = dividerPlaceables[linkIndex - 1]
                        divider.place(cursor + gapPx, y + (height - divider.height) / 2)
                        cursor += dividerSpan
                    }
                    val link = linkPlaceables[linkIndex]
                    link.place(cursor, y + (height - link.height) / 2)
                    cursor += link.width
                }
                y += height
            }
        }
    }
}

private fun openInBrowser(
    context: Context,
    url: String,
    failedMessage: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun LinkDivider() {
    Box(
        modifier =
            Modifier
                .width(ManyakTheme.sizes.borderWidth)
                .height(ManyakTheme.sizes.iconTiny)
                .background(ManyakTheme.colors.border),
    )
}

@Composable
private fun FooterLink(
    @StringRes labelRes: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .heightIn(min = ManyakTheme.sizes.control)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FooterText(text = stringResource(labelRes))
    }
}

@Composable
private fun FooterText(text: String) {
    Text(
        text = text,
        style = ManyakTheme.typography.bodySmall,
        color = ManyakTheme.colors.textSubtle,
    )
}

@Preview(showBackground = true, widthDp = 360, name = "사업자 정보 푸터")
@Composable
private fun BusinessInfoFooterPreview() {
    ManyakTheme(darkTheme = false) {
        BusinessInfoFooter(onOpenTerms = {}, onOpenPrivacyPolicy = {})
    }
}
