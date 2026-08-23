package app.manyak.feature.my

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 마이 탭. 화면 제목은 셸 헤더가 표시하므로 여기서 다시 그리지 않는다.
 */
@Composable
fun MyScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: MyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MyContent(
        state = state,
        onLogOut = { viewModel.onIntent(MyIntent.LogOut) },
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
private fun MyContent(
    state: MyUiState,
    onLogOut: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        Text(
            text = state.profile?.nickname.orEmpty(),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.text,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onLogOut,
            enabled = !state.isLoggingOut,
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.backgroundNeutral,
                    contentColor = ManyakTheme.colors.text,
                ),
        ) {
            val labelRes = if (state.isLoggingOut) R.string.my_logout_in_progress else R.string.my_logout
            Text(text = stringResource(labelRes), style = ManyakTheme.typography.labelLarge)
        }
    }
}

@Preview(showBackground = true, name = "마이 · 라이트")
@Composable
private fun MyScreenPreview() {
    ManyakTheme(darkTheme = false) {
        MyContent(state = MyUiState(), onLogOut = {}, contentPadding = PaddingValues(0.dp))
    }
}
