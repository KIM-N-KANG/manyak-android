package app.manyak.notification.data

import app.manyak.notification.domain.PushTokenRegistrar
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** 토큰 회전은 등록기에, 메시지는 트레이에 넘긴다. 서버 메시지는 data 전용이라 항상 여기로 온다. */
@AndroidEntryPoint
class ManyakFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var registrar: PushTokenRegistrar

    @Inject
    lateinit var tray: PushNotificationTray

    override fun onNewToken(token: String) {
        // 토큰 값은 쓰지 않는다 — 등록기가 필요할 때 SDK 에서 다시 읽는다.
        registrar.onTokenRefreshed()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // 이미 백그라운드 스레드이고, 콜백이 끝나면 프로세스가 죽을 수 있어 여기서 끝까지 기다린다.
        runBlocking { tray.show(message.data) }
    }
}
