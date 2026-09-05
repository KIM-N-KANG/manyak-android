package app.manyak.feature.my

import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.manyak.core.ui.R
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.theme.ManyakTheme
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.manyak.designsystem.R as DesignsystemR

/**
 * 오픈소스 고지. 목록은 빌드가 실제 의존성 그래프에서 뽑아 둔 것을 읽는다 — 손으로 적으면
 * 의존성이 바뀔 때마다 어긋나고, 실제로 그래야 할 라이선스(Apache 아닌 것들)를 놓친다.
 *
 * @param librariesRes 빌드가 만든 목록 자산. 조립처인 `:app` 이 자기 리소스를 넘긴다.
 */
@Composable
fun OpenSourceLicenseScreen(
    @RawRes librariesRes: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val libraries by produceLibraries {
        withContext(Dispatchers.IO) {
            context.resources
                .openRawResource(librariesRes)
                .bufferedReader()
                .use { it.readText() }
        }
    }
    val groups = remember(libraries) { libraries?.toLicenseGroups().orEmpty() }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        MyDetailHeader(titleRes = R.string.my_open_source_license, onBack = onBack)
        if (groups.isEmpty()) {
            LoadingContent()
        } else {
            LicenseGroupList(groups = groups, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ManyakProgressIndicator(modifier = Modifier.size(ManyakTheme.sizes.icon))
    }
}

@Composable
private fun LicenseGroupList(
    groups: List<LicenseGroup>,
    modifier: Modifier = Modifier,
) {
    // 전문은 하나만 편다. 목록이 길어 여럿을 동시에 펴면 어디를 보고 있었는지 잃는다.
    var expandedHash by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = ManyakTheme.spacing.gutter,
                end = ManyakTheme.spacing.gutter,
                top = ManyakTheme.spacing.compact,
                bottom = ManyakTheme.spacing.screenBottom,
            ),
    ) {
        item(key = "description") {
            Text(
                modifier = Modifier.padding(bottom = ManyakTheme.spacing.section),
                text = stringResource(R.string.open_source_description),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        }
        groups.forEach { group ->
            item(key = "${group.license.hash}-title") {
                Text(
                    modifier = Modifier.padding(bottom = ManyakTheme.spacing.compact),
                    text = group.license.name,
                    style = ManyakTheme.typography.titleMediumStrong,
                    color = ManyakTheme.colors.text,
                )
            }
            items(items = group.libraryNames, key = { "${group.license.hash}-$it" }) { name ->
                Text(
                    modifier = Modifier.padding(bottom = ManyakTheme.spacing.inline),
                    text = name,
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.text,
                )
            }
            item(key = "${group.license.hash}-text") {
                LicenseText(
                    license = group.license,
                    isExpanded = expandedHash == group.license.hash,
                    onToggle = {
                        expandedHash = if (expandedHash == group.license.hash) null else group.license.hash
                    },
                )
            }
        }
    }
}

/**
 * 라이선스 전문. 빌드가 받아 둔 본문이 있으면 앱 안에서 펼치고, 없으면 원문 주소를 연다 —
 * 전문은 한 글자도 바뀌면 안 되므로 어느 쪽이든 앱이 옮겨 적지 않는다.
 */
@Composable
private fun LicenseText(
    license: License,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val content = license.licenseContent
    val url = license.url
    if (content == null && url.isNullOrBlank()) return

    Column(modifier = modifier.padding(top = ManyakTheme.spacing.dense, bottom = ManyakTheme.spacing.section)) {
        Row(
            modifier =
                Modifier
                    // 브라우저가 없는 기기에서도 화면이 죽지 않게 한다.
                    .clickable { if (content != null) onToggle() else runCatching { uriHandler.openUri(url!!) } }
                    .padding(vertical = ManyakTheme.spacing.hairline),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            Text(
                text =
                    stringResource(
                        if (isExpanded) R.string.open_source_license_collapse else R.string.open_source_license_expand,
                    ),
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.textSubtle,
            )
            Icon(
                painter =
                    painterResource(
                        when {
                            content == null -> DesignsystemR.drawable.ic_external_link
                            isExpanded -> DesignsystemR.drawable.ic_angle_up
                            else -> DesignsystemR.drawable.ic_angle_down
                        },
                    ),
                contentDescription = null,
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                tint = ManyakTheme.colors.textSubtlest,
            )
        }
        if (isExpanded && content != null) {
            Text(
                modifier = Modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.compact),
                text = content.normalizeLicenseText(),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

/**
 * 받아 온 본문을 그대로 읽을 수 있게만 다듬는다. 문장은 건드리지 않는다.
 *
 * SPDX 본문에는 저작권자 자리를 뜻하는 치환 표기(`<<var;…;original=…;match=…>>`)가 섞여 있어,
 * 그 표기가 가리키는 원문 조각으로 되돌린다. 줄바꿈이 `<br />` 로도 들어오는 본문은 태그만 걷어낸다.
 */
private fun String.normalizeLicenseText(): String =
    replace(SpdxVariable) { match -> match.groupValues[1] }
        .replace(HtmlBreak, "")
        .trim()

private val SpdxVariable = Regex("""<<var;name=[^;]*;original=(.*?);match=.*?>>""", RegexOption.DOT_MATCHES_ALL)

private val HtmlBreak = Regex("""<br\s*/?>""")

private data class LicenseGroup(
    val license: License,
    val libraryNames: List<String>,
)

private fun Libs.toLicenseGroups(): List<LicenseGroup> =
    libraries
        .flatMap { library -> library.licenses.map { license -> license to library } }
        .groupBy({ (license, _) -> license }, { (_, library) -> library })
        .map { (license, grouped) ->
            LicenseGroup(license = license, libraryNames = grouped.map { it.name }.distinct().sorted())
        }.sortedWith(compareByDescending<LicenseGroup> { it.libraryNames.size }.thenBy { it.license.name })
