package app.manyak

import android.app.Application
import app.manyak.core.data.provider.KakaoSdkInitializer
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

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityProvider)
        // 외부 로그인 중 프로세스가 죽으면 리다이렉트 Activity 가 먼저 뜬다. 그전에 SDK 를 준비한다.
        kakaoSdkInitializer.initialize()
        // 저장된 세션을 읽기 전까지 공개 상태는 미확정이며, 그동안 루트는 어느 그래프도 그리지 않는다.
        sessionBootstrapper.start()
    }
}
