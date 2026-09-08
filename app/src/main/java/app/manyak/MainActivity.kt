package app.manyak

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.manyak.core.navigation.PushEntry
import app.manyak.root.ManyakApp
import app.manyak.root.RootViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 재생성이 돌려주는 원래 인텐트는 다시 해석하지 않는다 — 백스택과 보류 진입이 저장 상태에 있다.
        if (savedInstanceState == null) acceptExternalEntry(intent)
        // 테마 설정을 읽어야 라이트·다크가 정해지므로 ManyakTheme 는 ManyakApp 안에서 두른다.
        setContent {
            ManyakApp(viewModel = rootViewModel)
        }
    }

    /** singleTop 이라 실행 중에 알림을 탭하면 여기로 온다. 종료 상태의 진입과 같은 보류 경로로 모은다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptExternalEntry(intent)
    }

    private fun acceptExternalEntry(intent: Intent?) {
        PushEntry.readFrom(intent)?.let(rootViewModel::onExternalEntry)
    }
}
