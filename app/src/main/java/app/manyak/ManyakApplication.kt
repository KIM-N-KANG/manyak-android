package app.manyak

import android.app.Application
import app.manyak.core.data.datastore.LegacyPendingCreationFile
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

    @Inject
    lateinit var legacyPendingCreationFile: LegacyPendingCreationFile

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityProvider)
        // 외부 로그인 중 프로세스가 죽으면 리다이렉트 Activity 가 먼저 뜬다. 그전에 SDK 를 준비한다.
        kakaoSdkInitializer.initialize()
        // 저장된 세션을 읽기 전까지 공개 상태는 미확정이며, 그동안 루트는 어느 그래프도 그리지 않는다.
        sessionBootstrapper.start()
        // 진행 레코드가 Room 으로 옮겨 가기 전 쓰던 파일을 치운다. 읽는 곳이 없어진 사용자
        // 귀속 데이터를 기기에 남기지 않는다.
        legacyPendingCreationFile.delete()
    }
}
