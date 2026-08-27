package app.manyak.feature.story

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.core.domain.story.StoryStartSetting
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakSelectField
import app.manyak.core.ui.component.ManyakSelectOption
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 시작 상황 셀렉트. 고를 것이 하나뿐이어도 그린다 — 스토리마다 이 자리의 모양이 달라지면
 * 무엇을 바꿀 수 있는 화면인지 매번 다시 읽어야 한다.
 *
 * 앵커·메뉴 모양은 공용 [ManyakSelectField] 가 소유한다(제작 퍼널의 성별 셀렉트와 같은 컨트롤).
 */
@Composable
internal fun StartSettingSelect(
    startSettings: List<StoryStartSetting>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 아직 고른 값이 없을 수 있어 항목 타입도 nullable 로 맞춘다.
    val options =
        startSettings.map { setting ->
            ManyakSelectOption<String?>(value = setting.id, label = setting.name)
        }

    ManyakSelectField(
        modifier = modifier,
        options = options,
        selected = selectedId,
        onSelect = { id -> id?.let(onSelect) },
        onClickLabel = stringResource(R.string.story_detail_start_setting_select),
    )
}

@Preview(showBackground = true, name = "시작 상황 셀렉트")
@Composable
private fun StartSettingSelectPreview() {
    ManyakTheme(darkTheme = false) {
        StartSettingSelect(
            startSettings =
                listOf(
                    StoryStartSetting(id = "a", name = "첫 표행의 아침", startSituation = ""),
                    StoryStartSetting(id = "b", name = "시계공의 작업실", startSituation = ""),
                ),
            selectedId = "a",
            onSelect = {},
        )
    }
}
