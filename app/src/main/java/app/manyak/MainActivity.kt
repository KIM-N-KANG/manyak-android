package app.manyak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.core.ui.theme.ManyakTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ManyakTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = ManyakTheme.colors.surface,
                ) { innerPadding ->
                    Greeting(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = "만약, 그날 다른 선택을 했다면",
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = "이야기는 거기서 갈라집니다.",
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
        Text(
            text = "그는 문을 열었다. 복도의 공기는 어제와 같았지만, 그 사실이 오히려 낯설었다.",
            style = ManyakTheme.typography.bodyReading,
            color = ManyakTheme.colors.text,
        )
    }
}

@Preview(showBackground = true, name = "라이트")
@Composable
fun GreetingPreview() {
    ManyakTheme(darkTheme = false) {
        Greeting()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131313, name = "다크")
@Composable
fun GreetingDarkPreview() {
    ManyakTheme(darkTheme = true) {
        Greeting()
    }
}
