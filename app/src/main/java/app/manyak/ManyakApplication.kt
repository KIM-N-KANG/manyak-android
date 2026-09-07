package app.manyak

import android.app.Application
import app.manyak.auth.data.provider.KakaoSdkInitializer
import app.manyak.create.data.datastore.LegacyPendingCreationFile
import app.manyak.notification.domain.PushTokenRegistrar
import app.manyak.session.AnalyticsSessionBinder
import app.manyak.session.CurrentActivityProvider
import app.manyak.session.SessionBootstrapper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ManyakApplication : Application() {
    @Inject
    lateinit var activityProvider: CurrentActivityProvider

    @Inject
    lateinit var kakaoSdkInitializer: KakaoSdkInitializer

    @Inject
    lateinit var sessionBootstrapper: SessionBootstrapper

    @Inject
    lateinit var legacyPendingCreationFile: LegacyPendingCreationFile

    @Inject
    lateinit var analyticsSessionBinder: AnalyticsSessionBinder

    @Inject
    lateinit var pushTokenRegistrar: PushTokenRegistrar

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityProvider)
        // 외부 로그인 중 프로세스가 죽으면 리다이렉트 Activity 가 먼저 뜬다. 그전에 SDK 를 준비한다.
        kakaoSdkInitializer.initialize()
        // 저장된 세션을 읽기 전까지 공개 상태는 미확정이며, 그동안 루트는 어느 그래프도 그리지 않는다.
        sessionBootstrapper.start()
        // device_id 를 SDK 에 넣기 전까지 이벤트가 나가지 않으므로 세션 복원과 나란히 시작해도 된다.
        analyticsSessionBinder.start()
        // 회원이 되는 순간마다 FCM 토큰을 서버에 맡긴다. 세션 복원 결과를 관찰하므로 순서는 상관없다.
        pushTokenRegistrar.start()
        // 진행 레코드가 Room 으로 옮겨 가기 전 쓰던 파일을 치운다. 읽는 곳이 없어진 사용자
        // 귀속 데이터를 기기에 남기지 않는다.
        legacyPendingCreationFile.delete()
    }
}
