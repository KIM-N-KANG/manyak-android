package app.manyak.designsystem.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

/** 공정거래위원회 사업자정보 공개 페이지. 사업자등록번호가 바뀌면 함께 고친다. */
private const val FTC_BUSINESS_INFO_URL = "https://www.ftc.go.kr/bizCommPop.do?wrkr_no=2020455303"

/**
 * 홈과 마이 맨 아래의 사업자 정보와 약관 링크. 라벨과 값은 한 쌍으로만 줄을 넘겨 라벨이 앞줄 끝에
 * 홀로 남지 않게 한다. 링크 줄은 터치 타깃 높이를 확보하느라 위아래 여백이 정보 묶음보다 넓다.
 *
 * [collapsible] 이면 사업자 정보를 "사업자 정보" 버튼 뒤에 접어 두고, 문서 링크는 접혀 있어도 보인다.
 * 회색 바탕이 화면 폭을 채워 본문과 나뉘므로 화면의 맨 아래에 여백 없이 붙여 둔다.
 */
@Composable
fun BusinessInfoFooter(
    onOpenTerms: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(!collapsible) }
    // 접기 버튼과 링크 줄은 48dp 터치 높이 안에 글자 위아래 여백을 품고 있다. 바탕의 여백은 그만큼 덜어
    // 보이는 여백이 위 20dp, 아래 24dp가 되게 한다.
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ManyakTheme.colors.backgroundNeutral)
                .padding(
                    start = ManyakTheme.spacing.gutter,
                    end = ManyakTheme.spacing.gutter,
                    top = if (collapsible) ManyakTheme.spacing.inline else ManyakTheme.spacing.passage,
                    bottom = ManyakTheme.spacing.compact,
                ),
    ) {
        if (collapsible) {
            BusinessInfoToggle(expanded = expanded, onToggle = { expanded = !expanded })
        }
        AnimatedVisibility(
            visible = expanded || !collapsible,
            enter = expandVertically(tween(ManyakTheme.motion.elementEnterMillis)) + fadeIn(),
            exit = shrinkVertically(tween(ManyakTheme.motion.elementExitMillis)) + fadeOut(),
        ) {
            BusinessInfoPairs()
        }
        FooterLinks(onOpenTerms = onOpenTerms, onOpenPrivacyPolicy = onOpenPrivacyPolicy)
    }
}

@Composable
private fun BusinessInfoToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val state = stringResource(if (expanded) R.string.business_info_expanded else R.string.business_info_collapsed)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(ManyakTheme.motion.elementEnterMillis),
        label = "businessInfoChevron",
    )
    Row(
        modifier =
            Modifier
                .heightIn(min = ManyakTheme.sizes.control)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
    ) {
        FooterText(text = stringResource(R.string.business_info_toggle))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = null,
            modifier = Modifier.size(ManyakTheme.sizes.iconSmall).rotate(rotation),
            tint = ManyakTheme.colors.textSubtle,
        )
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
    FlowRow(
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        FooterLink(labelRes = R.string.business_footer_terms, onClick = onOpenTerms)
        LinkDivider()
        FooterLink(labelRes = R.string.business_footer_privacy, onClick = onOpenPrivacyPolicy)
        LinkDivider()
        FooterLink(
            labelRes = R.string.business_footer_ftc,
            onClick = { openInBrowser(context, FTC_BUSINESS_INFO_URL, openFailed) },
        )
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
        Column {
            BusinessInfoFooter(onOpenTerms = {}, onOpenPrivacyPolicy = {})
            BusinessInfoFooter(onOpenTerms = {}, onOpenPrivacyPolicy = {}, collapsible = true)
        }
    }
}
