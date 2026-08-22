package app.manyak

import android.app.Application
import app.manyak.session.CurrentActivityProvider
import app.manyak.session.SessionBootstrapper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ManyakApplication : Application() {
    @Inject
    lateinit var activityProvider: CurrentActivityProvider

    @Inject
    lateinit var sessionBootstrapper: SessionBootstrapper

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityProvider)
        // 저장된 세션을 읽기 전까지 공개 상태는 미확정이며, 그동안 루트는 어느 그래프도 그리지 않는다.
        sessionBootstrapper.start()
    }
}
