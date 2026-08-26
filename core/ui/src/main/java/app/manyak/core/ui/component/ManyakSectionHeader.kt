package app.manyak.core.ui.component

import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 로고만 두는 헤더. 홈은 브랜드가 곧 그 자리의 이름이라 "홈" 을 따로 적지 않는다.
 */
@Composable
fun ManyakBrandHeader(modifier: Modifier = Modifier) {
    HeaderBar(modifier = modifier) { ManyakLogo() }
}

/**
 * 현재 섹션 이름만 두는 헤더. 홈이 아닌 탭에서는 로고가 지금 어디인지 알려 주지 않으므로 두지 않는다.
 */
@Composable
fun ManyakSectionHeader(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
) {
    HeaderBar(modifier = modifier) {
        Text(
            text = stringResource(titleRes),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
    }
}

/**
 * 두 헤더가 공유하는 앱 바. 안에 무엇을 두든 높이·여백·색이 갈리지 않게 한 곳에 모은다.
 *
 * M3 [TopAppBar] 를 쓰는 이유는 상단 시스템 바·컷아웃 인셋과 좌우 여백을 직접 계산하지 않기
 * 위해서다. 기본 제목 여백이 16dp 라 이 시스템의 `spacing.gutter` 와 같고, 높이는 64dp 를 최솟값으로
 * 두되 제목이 커지면 함께 늘어나 시스템 글자 크기를 키워도 잘리지 않는다.
 *
 * 하단 탭과 달리 여기서는 M3 컴포넌트가 맞다 — 탭은 끌 수 없는 눌림 피드백과 선택 표시자가
 * 걸림돌이었지만, 앱 바는 색과 제목 슬롯만 지정하면 이 디자인 시스템과 어긋나는 요소가 없다.
 *
 * [TopAppBar] 는 아직 실험 API 라 opt-in 이 필요하다. 사용처가 이 파일뿐이라, 시그니처가 바뀌어도
 * 고칠 곳은 여기 한 곳이다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
) {
    TopAppBar(
        modifier = modifier,
        title = title,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = ManyakTheme.colors.surface,
                titleContentColor = ManyakTheme.colors.text,
            ),
    )
}

@Preview(showBackground = true, name = "홈 헤더 · 라이트")
@Composable
private fun ManyakBrandHeaderPreview() {
    ManyakTheme(darkTheme = false) {
        ManyakBrandHeader()
    }
}

@Preview(showBackground = true, name = "섹션 헤더 · 라이트")
@Composable
private fun ManyakSectionHeaderPreview() {
    ManyakTheme(darkTheme = false) {
        ManyakSectionHeader(titleRes = R.string.main_tab_chat)
    }
}
