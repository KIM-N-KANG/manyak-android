package app.manyak.notice.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakNeutralButton
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.notice.entity.NoticeDetail
import app.manyak.notice.R as NoticeR

/**
 * 공지 상세. 제목, 게시일(고쳐 썼으면 수정일), 평문 본문을 줄바꿈만 살려 보인다.
 * 뒤로가기는 목록으로 — 푸시로 바로 열었을 때도 `app` 이 목록을 먼저 쌓아 둔다.
 */
@Composable
fun NoticeDetailScreen(
    noticeId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoticeDetailViewModel =
        hiltViewModel<NoticeDetailViewModel, NoticeDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(noticeId) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        NoticeTopBar(onBack = onBack)
        NoticeDetailContent(
            state = state,
            onBackToList = onBack,
            onIntent = viewModel::onIntent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NoticeDetailContent(
    state: NoticeDetailUiState,
    onBackToList: () -> Unit,
    onIntent: (NoticeDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSkeleton = rememberDelayedProgressVisibility(state.isLoading)
    when {
        state.isLoading -> if (showSkeleton) NoticeDetailSkeleton(modifier = modifier.fillMaxWidth())

        state.notFound -> NoticeNotFound(onBackToList = onBackToList, modifier = modifier.fillMaxWidth())

        state.loadFailed ->
            LoadFailedContent(
                message = stringResource(NoticeR.string.notice_load_failed),
                onRetry = { onIntent(NoticeDetailIntent.Retry) },
                modifier = modifier.fillMaxWidth().padding(ManyakTheme.spacing.gutter),
            )

        else -> state.notice?.let { NoticeBody(notice = it, modifier = modifier.fillMaxWidth()) }
    }
}

@Composable
private fun NoticeBody(
    notice: NoticeDetail,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.gutter, bottom = ManyakTheme.spacing.screenBottom),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.passage),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            NoticeTagBadge(notice.tag)
            Text(text = notice.title, style = ManyakTheme.typography.titleLarge, color = ManyakTheme.colors.text)
            NoticeDates(notice)
        }
        // 서버가 가공하지 않는 평문이라 줄바꿈만 살려 그대로 그린다. 서사 서체(bodyReading)는 스토리 본문
        // 전용이라 운영 안내문에는 UI 서체를 쓴다.
        Text(text = notice.body, style = ManyakTheme.typography.bodyLarge, color = ManyakTheme.colors.text)
    }
}

@Composable
private fun NoticeDates(
    notice: NoticeDetail,
    modifier: Modifier = Modifier,
) {
    val published = notice.publishedAtMillis ?: return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component)) {
        Text(
            text = stringResource(NoticeR.string.notice_published_at, formatNoticeDate(published)),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
        notice.updatedDateOrNull()?.let { updated ->
            Text(
                text = stringResource(NoticeR.string.notice_updated_at, updated),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

@Composable
private fun NoticeNotFound(
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(NoticeR.string.notice_not_found),
            style = ManyakTheme.typography.bodyLargeStrong,
            color = ManyakTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(NoticeR.string.notice_not_found_description),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
            textAlign = TextAlign.Center,
        )
        ManyakNeutralButton(label = stringResource(NoticeR.string.notice_back_to_list), onClick = onBackToList)
    }
}

@Composable
private fun NoticeDetailSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonPulseAlpha()
    val loading = stringResource(NoticeR.string.notice_list_loading)
    Column(
        modifier =
            modifier
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.gutter)
                .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.passage),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            SkeletonPlaceholder(alpha, Modifier.fillMaxWidth(SKELETON_TITLE_WIDTH).height(ManyakTheme.sizes.tabIcon))
            SkeletonPlaceholder(alpha, Modifier.fillMaxWidth(SKELETON_DATE_WIDTH).height(ManyakTheme.sizes.iconSmall))
        }
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            SkeletonBodyWidths.forEach { width ->
                SkeletonPlaceholder(alpha, Modifier.fillMaxWidth(width).height(ManyakTheme.sizes.iconSmall))
            }
        }
    }
}

private const val SKELETON_TITLE_WIDTH = 0.8f
private const val SKELETON_DATE_WIDTH = 0.3f
private val SkeletonBodyWidths = listOf(1f, 1f, 0.9f, 0.65f)

@Preview(showBackground = true)
@Composable
private fun NoticeDetailPreview() {
    ManyakTheme(darkTheme = false) {
        NoticeDetailContent(
            state =
                NoticeDetailUiState(
                    isLoading = false,
                    notice =
                        NoticeDetail(
                            id = "1",
                            title = "서비스 점검 안내",
                            body = "10월 27일 02:00~04:00 점검이 있어요.\n점검 중에는 채팅을 이용할 수 없어요.",
                            publishedAtMillis = 1_792_000_000_000L,
                            updatedAtMillis = 1_792_100_000_000L,
                        ),
                ),
            onBackToList = {},
            onIntent = {},
        )
    }
}
