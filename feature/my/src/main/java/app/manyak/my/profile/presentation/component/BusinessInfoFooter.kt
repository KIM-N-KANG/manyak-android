package app.manyak.my.profile.presentation.component

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.my.R as MyR

private val BusinessInfoItems =
    listOf(
        MyR.string.my_business_name_label to MyR.string.my_business_name,
        MyR.string.my_business_representative_label to MyR.string.my_business_representative,
        MyR.string.my_business_registration_number_label to MyR.string.my_business_registration_number,
        MyR.string.my_business_mail_order_number_label to MyR.string.my_business_mail_order_number,
        MyR.string.my_business_address_label to MyR.string.my_business_address,
        MyR.string.my_business_phone_label to MyR.string.my_business_phone,
        MyR.string.my_business_email_label to MyR.string.my_business_email,
    )

/**
 * 마이 맨 아래의 사업자 정보와 약관 링크. 라벨과 값은 한 쌍으로만 줄을 넘겨 라벨이 앞줄 끝에 홀로
 * 남지 않게 한다. 링크 줄은 터치 타깃 높이를 확보하느라 위아래 여백이 정보 묶음보다 넓다.
 */
@Composable
internal fun BusinessInfoFooter(
    onOpenTerms: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = ManyakTheme.spacing.gutter)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            BusinessInfoItems.forEach { (labelRes, valueRes) ->
                Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline)) {
                    FooterText(text = stringResource(labelRes))
                    FooterText(text = stringResource(valueRes))
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            FooterLink(labelRes = MyR.string.my_footer_terms, onClick = onOpenTerms)
            Box(
                modifier =
                    Modifier
                        .width(ManyakTheme.sizes.borderWidth)
                        .height(ManyakTheme.sizes.iconTiny)
                        .background(ManyakTheme.colors.border),
            )
            FooterLink(labelRes = MyR.string.my_footer_privacy, onClick = onOpenPrivacyPolicy)
        }
    }
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
