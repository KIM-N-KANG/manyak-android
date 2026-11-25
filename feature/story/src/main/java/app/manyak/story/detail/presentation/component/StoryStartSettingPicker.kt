package app.manyak.story.detail.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.common.entity.persona.Persona
import app.manyak.designsystem.component.ManyakSelectAction
import app.manyak.designsystem.component.ManyakSelectField
import app.manyak.designsystem.component.ManyakSelectOption
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.story.entity.StoryStartSetting
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.story.R as StoryR

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
        onClickLabel = stringResource(StoryR.string.story_detail_start_setting_select),
    )
}

/**
 * 페르소나 셀렉트. 맨 위는 기본 주인공이고 내 페르소나가 응답 순서로 뒤따른다. 맨 아래 "페르소나 생성하기"는
 * 고를 수 있는 값이 아니라 생성 화면으로 가는 동작이라 눌러도 선택이 바뀌지 않는다.
 *
 * @param selectedId null 이면 기본 주인공이다.
 */
@Composable
internal fun PersonaSelect(
    personas: List<Persona>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        listOf(
            ManyakSelectOption<String?>(
                value = null,
                label = stringResource(StoryR.string.story_detail_persona_default),
            ),
        ) +
            personas.map { persona -> ManyakSelectOption<String?>(value = persona.id, label = persona.name) }

    ManyakSelectField(
        modifier = modifier,
        options = options,
        selected = selectedId,
        onSelect = onSelect,
        onClickLabel = stringResource(StoryR.string.story_detail_persona_select),
        action =
            ManyakSelectAction(
                iconRes = DesignsystemR.drawable.ic_add,
                label = stringResource(StoryR.string.story_detail_persona_create),
                onClick = onCreate,
            ),
    )
}

@Preview(showBackground = true, name = "시작 상황 셀렉트")
@Composable
private fun StartSettingSelectPreview() {
    ManyakTheme(darkTheme = false) {
        StartSettingSelect(
            startSettings =
                listOf(
                    StoryStartSetting(id = "a", name = "첫 표행의 아침", startSituation = "", endings = emptyList()),
                    StoryStartSetting(id = "b", name = "시계공의 작업실", startSituation = "", endings = emptyList()),
                ),
            selectedId = "a",
            onSelect = {},
        )
    }
}
