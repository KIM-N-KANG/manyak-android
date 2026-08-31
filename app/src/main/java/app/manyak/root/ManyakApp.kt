package app.manyak.root

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.view.ViewCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.manyak.core.domain.session.SessionState
import app.manyak.core.domain.settings.ThemeMode
import app.manyak.core.domain.story.CreationResumePoint
import app.manyak.core.navigation.ChatRoomRoute
import app.manyak.core.navigation.CreateAdditionalInfoRoute
import app.manyak.core.navigation.CreateKeywordRoute
import app.manyak.core.navigation.CreateStorylineRoute
import app.manyak.core.navigation.LegalDocument
import app.manyak.core.navigation.LegalRoute
import app.manyak.core.navigation.LoginRoute
import app.manyak.core.navigation.MainTabsRoute
import app.manyak.core.navigation.MyCreditHistoryRoute
import app.manyak.core.navigation.MyFeedbackRoute
import app.manyak.core.navigation.MyInviteRoute
import app.manyak.core.navigation.MyOpenSourceLicenseRoute
import app.manyak.core.navigation.StoryDetailRoute
import app.manyak.core.navigation.WithdrawalRoute
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.component.rememberDelayedProgressVisibility
import app.manyak.core.ui.error.messageResOrNull
import app.manyak.core.ui.theme.ManyakTheme
import app.manyak.feature.chat.ChatRoomScreen
import app.manyak.feature.create.CreateAdditionalInfoScreen
import app.manyak.feature.create.CreateKeywordScreen
import app.manyak.feature.create.CreateStorylineScreen
import app.manyak.feature.legal.LegalDocumentScreen
import app.manyak.feature.login.LoginScreen
import app.manyak.feature.my.CreditHistoryScreen
import app.manyak.feature.my.FeedbackScreen
import app.manyak.feature.my.InviteOnboardingSheet
import app.manyak.feature.my.InviteScreen
import app.manyak.feature.my.OpenSourceLicenseScreen
import app.manyak.feature.my.WithdrawalScreen
import app.manyak.feature.story.StoryDetailScreen

/**
 * 세션 상태가 어느 그래프를 띄울지 결정한다. 그래프 안에서 가드로 막지 않는다 —
 * 미로그인 상태에서 **메인 목적지가 백스택에 존재할 수 없게** 만들어 가드 누락이 사고가 되지 않게 한다.
 *
 * 상태가 미확정인 동안에는 어느 그래프도 그리지 않는다. 로그인 화면이 잠깐 스쳤다가 메인으로 바뀌는
 * 깜빡임을 막기 위해서다.
 */
@Composable
fun ManyakApp(
    modifier: Modifier = Modifier,
    viewModel: RootViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val showSessionProgress = rememberDelayedProgressVisibility(sessionState == SessionState.Undetermined)
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    ManyakTheme(darkTheme = darkTheme) {
        SystemBarIconAppearance(darkTheme = darkTheme)
        Surface(modifier = modifier.fillMaxSize(), color = ManyakTheme.colors.surface) {
            when (val state = sessionState) {
                SessionState.Undetermined -> if (showSessionProgress) SessionProgress()
                is SessionState.SignedOut -> AuthNavDisplay()
                SessionState.Member -> {
                    MainNavDisplay()
                    // 신규 가입 안내는 어느 탭에 있든 회원 그래프 위에 뜬다. 로그인 화면에 두면
                    // 로그인 성공과 동시에 인증 백스택이 사라져 안내도 함께 걷힌다.
                    InviteOnboardingSheet()
                }
                // 이전 사용자의 데이터가 남아 있다. 정리가 끝날 때까지 어느 그래프도 열지 않는다.
                is SessionState.CleanupFailed -> CleanupFailed(state, onRetry = viewModel::onRetryCleanup)
            }
        }
    }
}

/**
 * 시스템 바 아이콘 밝기를 앱 테마에 맞춘다. edge-to-edge 기본값은 **시스템** 다크 모드를 따르므로,
 * 앱에서 테마를 강제하면 상태 바 시계·배터리가 배경과 같은 밝기가 되어 읽히지 않는다.
 */
@Composable
private fun SystemBarIconAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    LaunchedEffect(view, darkTheme) {
        val controller = ViewCompat.getWindowInsetsController(view) ?: return@LaunchedEffect
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}

/**
 * 종료 정리가 재시도를 소진했을 때의 차단 화면.
 *
 * 로그인 화면을 대신 열면 새 계정이 이전 사용자의 잔여 데이터 위에서 시작되고, 남은 저널 때문에
 * 다음 실행의 재개가 그 새 계정의 데이터를 지운다. 그래서 여기서는 다시 시도만 제공한다.
 */
@Composable
private fun CleanupFailed(
    state: SessionState.CleanupFailed,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.notice.messageResOrNull()?.let { noticeRes ->
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(noticeRes),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.text,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.session_cleanup_failed),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textDanger,
            textAlign = TextAlign.Center,
        )
        Button(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ManyakTheme.sizes.control),
            onClick = onRetry,
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.brand,
                    contentColor = ManyakTheme.colors.textInverse,
                ),
        ) {
            Text(text = stringResource(R.string.common_retry), style = ManyakTheme.typography.labelLarge)
        }
    }
}

/**
 * 상태가 미확정인 동안 보여 주는 표시.
 *
 * 로그아웃 중에는 정리가 끝날 때까지 유지된다. 종료 정리는 서버 호출과 저장소 삭제를 포함해 눈에 띄는
 * 시간이 걸리므로, 빈 화면 대신 진행 중임을 알린다. 반대로 앱 시작 직후처럼 상태가 금방 확정되는
 * 경우에는 호출부가 아예 그리지 않는다 — 스피너가 스쳐 가는 깜빡임을 만들지 않는다.
 */
@Composable
private fun SessionProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ManyakProgressIndicator()
    }
}

/**
 * 인증 백스택. 로그인 성공 시 세션 상태가 바뀌며 이 백스택이 통째로 사라지므로,
 * 메인에서 뒤로가기로 로그인 화면에 돌아갈 수 없다.
 */
@Composable
private fun AuthNavDisplay() {
    val backStack = rememberNavBackStack(LoginRoute)
    val screenTransition = rememberScreenTransition()
    NavDisplay(
        backStack = backStack,
        entryDecorators = rememberManyakEntryDecorators(),
        transitionSpec = screenTransition,
        popTransitionSpec = screenTransition,
        entryProvider =
            entryProvider<NavKey> {
                entry<LoginRoute> {
                    LoginScreen(onOpenLegalDocument = { document -> backStack.add(LegalRoute(document)) })
                }
                legalEntry()
            },
    )
}

/**
 * 메인 백스택. 하단 탭 넷은 셸이 소유한 탭별 백스택 안에 있고, 셸을 두르지 않는 화면은
 * 셸 키 위에 쌓여 헤더도 하단 탭도 없이 전체 화면으로 그려진다.
 */
@Composable
private fun MainNavDisplay() {
    val backStack = rememberNavBackStack(MainTabsRoute)
    // 셸 밖에서도 탭을 바꿀 수 있어야 한다 — 채팅을 지우면 방을 걷어내고 채팅 탭을 편다.
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val screenTransition = rememberScreenTransition()
    NavDisplay(
        backStack = backStack,
        entryDecorators = rememberManyakEntryDecorators(),
        transitionSpec = screenTransition,
        popTransitionSpec = screenTransition,
        entryProvider =
            entryProvider<NavKey> {
                entry<MainTabsRoute> {
                    MainTabsScreen(
                        selectedTab = selectedTab,
                        onSelectTab = { tab -> selectedTab = tab },
                        // 상세는 셸 위에 쌓여 헤더도 하단 탭도 없는 전체 화면이 되고, 뒤로가기는
                        // 셸이 든 선택 탭으로 그대로 돌아온다.
                        onOpenStory = { storyId -> backStack.add(StoryDetailRoute(storyId)) },
                        // 채팅 목록에서 이어가기 — 상세에서 시작한 채팅과 같은 목적지를 쌓고,
                        // 뒤로가기는 채팅 탭으로 돌아온다.
                        onOpenChat = { chatId -> backStack.add(ChatRoomRoute(chatId)) },
                        onCreateStory = { backStack.add(CreateKeywordRoute) },
                        // 재개·복구 진입 — 레코드가 가리키는 단계까지 체인을 쌓는다.
                        onResumeCreation = { resumePoint -> backStack.addCreationResumeChain(resumePoint) },
                        // 마이 하위 목적지들 — 셸 위에 쌓이는 전체 화면이고 뒤로가기는 마이 탭으로 돌아온다.
                        onOpenInvite = { backStack.add(MyInviteRoute) },
                        onOpenServiceInfo = { backStack.add(LegalRoute(LegalDocument.ABOUT)) },
                        onOpenFeedback = { backStack.add(MyFeedbackRoute) },
                        onOpenOpenSourceLicense = { backStack.add(MyOpenSourceLicenseRoute) },
                        onOpenWithdrawal = { backStack.add(WithdrawalRoute) },
                        onOpenCreditHistory = { backStack.add(MyCreditHistoryRoute) },
                    )
                }
                myDestinationEntries(backStack)
                entry<StoryDetailRoute> { route ->
                    StoryDetailScreen(
                        storyId = route.storyId,
                        onBack = { backStack.removeLastOrNull() },
                        // 상세를 걷어내지 않고 그 위에 쌓는다 — 채팅방 뒤로가기가 방금 보던
                        // 스토리로 돌아온다(웹 `replace` 와 갈리는 앱 전용 차이).
                        onEnterChat = { chatId -> backStack.add(ChatRoomRoute(chatId)) },
                    )
                }
                creationFunnelEntries(backStack)
                entry<ChatRoomRoute> { route ->
                    ChatRoomScreen(
                        chatId = route.chatId,
                        onBack = { backStack.removeLastOrNull() },
                        // 지운 방이 뒤로가기로 되살아나면 안 되므로 셸까지 걷어내고 채팅 탭을 편다.
                        // 상세에서 시작한 채팅이면 상세도 함께 걷힌다.
                        onDeleted = {
                            backStack.popToMainTabs()
                            selectedTab = MainTab.CHAT
                        },
                    )
                }
                legalEntry()
            },
    )
}

/** 마이 탭의 하위 목적지들. 셸 없이 전체 화면으로 열리고 뒤로가기는 마이 탭으로 돌아온다. */
private fun EntryProviderScope<NavKey>.myDestinationEntries(backStack: MutableList<NavKey>) {
    entry<MyInviteRoute> {
        InviteScreen(onBack = { backStack.removeLastOrNull() })
    }
    entry<MyCreditHistoryRoute> {
        CreditHistoryScreen(onBack = { backStack.removeLastOrNull() })
    }
    entry<MyFeedbackRoute> {
        FeedbackScreen(onBack = { backStack.removeLastOrNull() })
    }
    entry<MyOpenSourceLicenseRoute> {
        OpenSourceLicenseScreen(
            // 이 파일의 R 은 :core:ui 의 것이다. 빌드가 만든 목록은 :app 자기 리소스라 온전한 이름으로 가리킨다.
            librariesRes = app.manyak.R.raw.aboutlibraries,
            onBack = { backStack.removeLastOrNull() },
        )
    }
    entry<WithdrawalRoute> {
        WithdrawalScreen(onBack = { backStack.removeLastOrNull() })
    }
}

private fun MutableList<NavKey>.addCreationResumeChain(resumePoint: CreationResumePoint) {
    when (resumePoint) {
        CreationResumePoint.KeywordStep -> add(CreateKeywordRoute)
        CreationResumePoint.StorylineStep -> add(CreateStorylineRoute)
        is CreationResumePoint.AdditionalInfoStep -> {
            add(CreateStorylineRoute)
            add(CreateAdditionalInfoRoute(resumePoint.storylineIndex))
        }
    }
}

/**
 * 간편 제작 퍼널 세 단계. 셸을 두르지 않는 전체 화면이며 백스택 구조가 단계 관계를 그대로 드러낸다.
 */
private fun EntryProviderScope<NavKey>.creationFunnelEntries(backStack: MutableList<NavKey>) {
    entry<CreateKeywordRoute> {
        CreateKeywordScreen(
            onLeaveFunnel = { backStack.removeLastOrNull() },
            // 스토리라인 단계는 키워드 목적지를 대체한다 — 그 화면의 뒤로가기가
            // 홈 복귀(퍼널 이탈)가 되도록 한다.
            onOpenStorylineStep = {
                backStack.removeLastOrNull()
                backStack.add(CreateStorylineRoute)
            },
        )
    }
    entry<CreateStorylineRoute> {
        CreateStorylineScreen(
            onLeaveFunnel = { backStack.removeLastOrNull() },
            onOpenAdditionalInfoStep = { storylineIndex ->
                backStack.add(CreateAdditionalInfoRoute(storylineIndex))
            },
        )
    }
    entry<CreateAdditionalInfoRoute> { route ->
        CreateAdditionalInfoScreen(
            storylineIndex = route.storylineIndex,
            // 이탈은 퍼널 단계를 전부 걷어내고 홈으로 돌아간다. 스토리라인 단계만 pop 하면
            // 홈으로 나가려던 조작이 한 단계 뒤로 가기로 보인다.
            onLeaveFunnel = { backStack.popToMainTabs() },
            onBackToStoryline = { backStack.removeLastOrNull() },
            // 완성 성공 — 퍼널 단계를 모두 걷어내고 생성된 채팅방을 쌓는다(웹의 채팅 화면
            // `replace` 대응). 상세에서 시작한 채팅과 달리 돌아갈 단계가 남지 않는다.
            onEnterChat = { chatId ->
                backStack.popToMainTabs()
                backStack.add(ChatRoomRoute(chatId))
            },
        )
    }
}

/** 한 번 쓰고 끝나는 퍼널 단계를 모두 걷어내 셸만 남긴다. */
private fun MutableList<NavKey>.popToMainTabs() {
    while (size > 1 && lastOrNull() != MainTabsRoute) removeLastOrNull()
}

/**
 * 웹이 정본인 공용 문서. 제품 백스택 양쪽에 등록해 뒤로가기가 진입한 화면으로 돌아간다.
 * 여기서 임의의 메인 목적지로 이동하는 경로는 두지 않는다.
 */
private fun EntryProviderScope<NavKey>.legalEntry() {
    entry<LegalRoute> { route ->
        LegalDocumentScreen(document = route.document)
    }
}
