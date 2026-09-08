package app.manyak.notification.data

import app.manyak.notification.domain.PushTokenRegistrar
import com.google.firebase.messaging.FirebaseMessagingService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 토큰 회전만 등록기에 넘긴다. 메시지 수신·표시는 아직 다루지 않는다. */
@AndroidEntryPoint
class ManyakFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var registrar: PushTokenRegistrar

    override fun onNewToken(token: String) {
        // 토큰 값은 쓰지 않는다 — 등록기가 필요할 때 SDK 에서 다시 읽는다.
        registrar.onTokenRefreshed()
    }
}
