package app.manyak.notification.consent.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.designsystem.component.ManyakDialog
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.notification.consent.entity.ConsentChange
import app.manyak.notification.consent.entity.ConsentNotice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.manyak.notification.R as NotificationR

/**
 * 동의·철회 처리 결과 통지. 전송자·일시·처리 내용 세 줄이 법이 요구하는 항목이라 문구를 줄이지 않는다.
 * 첫 진입 시트와 알림 설정 토글이 같은 창을 쓴다.
 */
@Composable
internal fun ConsentNoticeDialog(
    notice: ConsentNotice,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // SimpleDateFormat 은 스레드 안전하지 않아 통지마다 새로 만든다.
    val at = remember(notice) { SimpleDateFormat(AT_PATTERN, Locale.KOREA).format(Date(notice.atEpochMillis)) }
    ManyakDialog(modifier = modifier, onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(ManyakTheme.spacing.section)) {
            Text(
                text = stringResource(NotificationR.string.notification_consent_notice_title),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
            Column(
                modifier = Modifier.padding(top = ManyakTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                NoticeLine(
                    NotificationR.string.notification_consent_sender_line,
                    stringResource(NotificationR.string.notification_consent_sender),
                )
                NoticeLine(NotificationR.string.notification_consent_notice_at, at)
                NoticeLine(
                    NotificationR.string.notification_consent_notice_result,
                    stringResource(notice.change.resultRes()),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.section),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDismiss,
                    shape = ManyakTheme.shapes.control,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = ManyakTheme.colors.brand,
                            contentColor = ManyakTheme.colors.textInverse,
                        ),
                ) {
                    Text(
                        text = stringResource(NotificationR.string.notification_consent_notice_confirm),
                        style = ManyakTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeLine(
    @StringRes formatRes: Int,
    value: String,
) {
    Text(
        text = stringResource(formatRes, value),
        style = ManyakTheme.typography.bodyMedium,
        color = ManyakTheme.colors.textSubtle,
    )
}

@StringRes
private fun ConsentChange.resultRes(): Int =
    when (this) {
        ConsentChange.MARKETING_ON -> NotificationR.string.notification_consent_notice_result_marketing_on
        ConsentChange.MARKETING_OFF -> NotificationR.string.notification_consent_notice_result_marketing_off
        ConsentChange.NIGHT_ON -> NotificationR.string.notification_consent_notice_result_night_on
        ConsentChange.NIGHT_OFF -> NotificationR.string.notification_consent_notice_result_night_off
    }

private const val AT_PATTERN = "yyyy.MM.dd HH:mm"

@Preview(name = "동의 처리 안내 · 라이트")
@Composable
private fun ConsentNoticeDialogPreview() {
    ManyakTheme(darkTheme = false) {
        ConsentNoticeDialog(
            notice = ConsentNotice(ConsentChange.MARKETING_ON, atEpochMillis = 1_788_000_000_000L),
            onDismiss = {},
        )
    }
}
