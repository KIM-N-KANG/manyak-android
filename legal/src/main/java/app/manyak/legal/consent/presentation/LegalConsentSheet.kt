package app.manyak.legal.consent.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.core.navigation.LegalDocument
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.component.ManyakCheckbox
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.legal.consent.entity.ConsentItem
import app.manyak.legal.consent.entity.RequiredConsent
import app.manyak.legal.presentation.titleRes
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.legal.R as LegalR

/**
 * 로그인 직후 필수 동의를 받는 시트. 회원 그래프 위에 얹는다.
 *
 * 닫을 수 없다 — 끌어내리기·스크림 탭은 막고, 뒤로가기만 "동의하지 않음" 으로 보고 로그아웃한다.
 * 전문은 시트 위에 전체 화면 창으로 연다. 모달 시트는 아래 화면을 덮으므로 백스택에 문서를 쌓으면 보이지 않는다.
 * 선택 항목(광고 알림)은 OS 권한과 별개의 법적 동의라 권한을 거부했어도 싣는다.
 *
 * @param enabled 앞선 안내(알림 권한 응답)가 끝났는가. 참이 되기 전에는 판정은 하되 시트를 그리지 않는다.
 */
@Composable
fun LegalConsentSheet(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    viewModel: LegalConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (enabled && state.isSheetVisible) {
        LegalConsentContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
    }
    state.viewingDocument?.let { document ->
        LegalDocumentDialog(document = document, onClose = { viewModel.onIntent(LegalConsentIntent.CloseDocument) })
    }
}

@Composable
private fun LegalConsentContent(
    state: LegalConsentUiState,
    onIntent: (LegalConsentIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManyakBottomSheet(
        modifier = modifier,
        onDismissRequest = { onIntent(LegalConsentIntent.Abandon) },
        dismissEnabled = false,
        // 저장·로그아웃 중에는 뒤로가기도 받지 않는다 — 결과가 나오기 전에 두 번째 종료를 시작하지 않는다.
        dismissOnBackPress = !state.isLocked,
        // 끌어내려 닫을 수 없는 시트라 핸들을 두지 않는다.
        dragHandleVisible = false,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        when (state.phase) {
            LegalConsentPhase.REQUIRED -> {
                Text(
                    text = stringResource(LegalR.string.consent_title),
                    style = ManyakTheme.typography.titleLarge,
                    color = ManyakTheme.colors.text,
                )
                ConsentItems(state = state, onIntent = onIntent)
                Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                    state.notice?.let { notice ->
                        Text(
                            text = stringResource(notice.messageRes()),
                            style = ManyakTheme.typography.bodySmall,
                            color = ManyakTheme.colors.textDanger,
                        )
                    }
                    SubmitButton(state = state, onClick = { onIntent(LegalConsentIntent.Submit) })
                }
            }

            else -> LoadFailedContent(onRetry = { onIntent(LegalConsentIntent.Retry) })
        }
    }
}

@Composable
private fun ConsentItems(
    state: LegalConsentUiState,
    onIntent: (LegalConsentIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        ConsentRow(
            label = stringResource(LegalR.string.consent_agree_all),
            labelStyle = ManyakTheme.typography.bodyLargeStrong,
            isChecked = state.isAllChecked,
            enabled = !state.isLocked,
            onToggle = { onIntent(LegalConsentIntent.ToggleAll) },
        )
        HorizontalDivider(thickness = 1.dp, color = ManyakTheme.colors.border)
        state.required.forEach { required ->
            ConsentRow(
                label = stringResource(required.item.labelRes()),
                isChecked = required.item in state.checked,
                enabled = !state.isLocked,
                onToggle = { onIntent(LegalConsentIntent.Toggle(required.item)) },
                trailing = {
                    required.item.document()?.let { document ->
                        ViewDocumentButton(
                            document = document,
                            enabled = !state.isLocked,
                            onClick = { onIntent(LegalConsentIntent.OpenDocument(document)) },
                        )
                    }
                },
            )
        }
        ConsentRow(
            label = stringResource(LegalR.string.consent_item_marketing),
            description = stringResource(LegalR.string.consent_item_marketing_description),
            isChecked = state.marketingOptIn,
            enabled = !state.isLocked,
            onToggle = { onIntent(LegalConsentIntent.ToggleMarketing) },
        )
    }
}

/** 항목 한 줄. 체크박스만이 아니라 줄 전체가 토글이라 문구를 눌러도 켜진다. 오른쪽 "보기" 는 줄과 별개의 버튼이다. */
@Composable
private fun ConsentRow(
    label: String,
    isChecked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = ManyakTheme.typography.bodyLarge,
    description: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .toggleable(
                    value = isChecked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    interactionSource = null,
                    indication = null,
                    onValueChange = { onToggle() },
                ),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManyakCheckbox(isChecked = isChecked)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            Text(text = label, style = labelStyle, color = ManyakTheme.colors.text)
            description?.let {
                Text(text = it, style = ManyakTheme.typography.bodySmall, color = ManyakTheme.colors.textSubtle)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun ViewDocumentButton(
    document: LegalDocument,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        stringResource(LegalR.string.consent_view_document_description, stringResource(document.titleRes()))
    ManyakTextButton(
        modifier = modifier.semantics { contentDescription = description },
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(LegalR.string.consent_view_document),
            style = ManyakTheme.typography.labelSmall.copy(textDecoration = TextDecoration.Underline),
            color = if (enabled) ManyakTheme.colors.textSubtle else ManyakTheme.colors.textDisabled,
        )
    }
}

@Composable
private fun SubmitButton(
    state: LegalConsentUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
        onClick = onClick,
        enabled = state.isEveryRequiredChecked && !state.isLocked,
        shape = ManyakTheme.shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.brand,
                contentColor = ManyakTheme.colors.textInverse,
                // 저장 중에는 채움을 유지해 스피너가 브랜드 버튼 위에 돈다. 체크가 모자란 비활성만 흐리다.
                disabledContainerColor =
                    if (state.isLocked) ManyakTheme.colors.brand else ManyakTheme.colors.backgroundDisabled,
                disabledContentColor =
                    if (state.isLocked) ManyakTheme.colors.textInverse else ManyakTheme.colors.textDisabled,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 진행 중에도 라벨 자리를 유지해 버튼 폭이 스피너 폭으로 줄지 않게 한다.
            Text(
                modifier = Modifier.alpha(if (state.isSubmitting) 0f else 1f),
                text = stringResource(LegalR.string.consent_submit),
                style = ManyakTheme.typography.labelLarge,
            )
            if (state.isSubmitting) {
                ManyakProgressIndicator(
                    modifier = Modifier.size(ManyakTheme.sizes.icon),
                    color = ManyakTheme.colors.textInverse,
                )
            }
        }
    }
}

@Composable
private fun LoadFailedContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        Text(
            text = stringResource(LegalR.string.consent_load_failed_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(LegalR.string.consent_load_failed_description),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
    Button(
        modifier = Modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
        onClick = onRetry,
        shape = ManyakTheme.shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.brand,
                contentColor = ManyakTheme.colors.textInverse,
            ),
    ) {
        Text(text = stringResource(DesignsystemR.string.common_retry), style = ManyakTheme.typography.labelLarge)
    }
}

@StringRes
private fun ConsentItem.labelRes(): Int =
    when (this) {
        ConsentItem.TERMS -> LegalR.string.consent_item_terms
        ConsentItem.PRIVACY -> LegalR.string.consent_item_privacy
        ConsentItem.AGE14 -> LegalR.string.consent_item_age14
    }

private fun ConsentItem.document(): LegalDocument? =
    when (this) {
        ConsentItem.TERMS -> LegalDocument.TERMS
        ConsentItem.PRIVACY -> LegalDocument.PRIVACY
        ConsentItem.AGE14 -> null
    }

@StringRes
private fun LegalConsentNotice.messageRes(): Int =
    when (this) {
        LegalConsentNotice.RETRYABLE -> LegalR.string.consent_error_retryable
        LegalConsentNotice.VERSION_MISMATCH -> LegalR.string.consent_error_version_mismatch
    }

@Preview(name = "필수 동의 · 라이트")
@Composable
private fun LegalConsentSheetPreview() {
    ManyakTheme(darkTheme = false) {
        LegalConsentContent(
            state =
                LegalConsentUiState(
                    phase = LegalConsentPhase.REQUIRED,
                    required =
                        listOf(
                            RequiredConsent(ConsentItem.AGE14, "1"),
                            RequiredConsent(ConsentItem.TERMS, "v1"),
                            RequiredConsent(ConsentItem.PRIVACY, "v1"),
                        ),
                    checked = setOf(ConsentItem.TERMS),
                ),
            onIntent = {},
        )
    }
}
