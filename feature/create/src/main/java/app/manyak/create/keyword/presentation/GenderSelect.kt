package app.manyak.create.keyword.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.create.entity.CharacterGender
import app.manyak.designsystem.component.ManyakSelectField
import app.manyak.designsystem.component.ManyakSelectOption
import app.manyak.create.R as CreateR

/**
 * 인물 성별 셀렉트. 고르지 않은 상태(무작위)가 기본이라 그때는 값을 placeholder 로 흐리게 둔다.
 *
 * 앵커·메뉴 모양은 공용 [ManyakSelectField] 가 소유한다 — 스토리 상세의 시작 상황 셀렉트와 같은
 * 컨트롤이라, 두 화면이 각자 만들면 모양이 갈린다.
 */
@Composable
internal fun GenderSelectField(
    gender: CharacterGender?,
    onGenderChange: (CharacterGender?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // null(무작위)과 enum 이 섞여 있어 타입을 명시한다 — 추론에 맡기면 out 으로 좁혀진다.
    val options =
        listOf<ManyakSelectOption<CharacterGender?>>(
            ManyakSelectOption(value = null, label = stringResource(CreateR.string.create_gender_random)),
            ManyakSelectOption(value = CharacterGender.MALE, label = stringResource(CreateR.string.create_gender_male)),
            ManyakSelectOption(
                value = CharacterGender.FEMALE,
                label = stringResource(CreateR.string.create_gender_female),
            ),
        )

    ManyakSelectField(
        modifier = modifier,
        options = options,
        selected = gender,
        onSelect = onGenderChange,
        isPlaceholder = gender == null,
    )
}
