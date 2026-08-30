package app.manyak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.manyak.root.ManyakApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 테마 설정을 읽어야 라이트·다크가 정해지므로 ManyakTheme 는 ManyakApp 안에서 두른다.
        setContent {
            ManyakApp()
        }
    }
}
