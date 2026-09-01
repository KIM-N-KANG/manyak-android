package app.manyak.feature.create

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.ActivityRetainedLifecycle
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Qualifier

/**
 * 퍼널 수명(Activity 보존) 코루틴 스코프. 스토리라인 단계가 키워드 목적지를 대체하는 구조라
 * 생성 실행이 요청을 시작한 화면의 ViewModel 수명을 넘어야 하고, 그 실행을 이 스코프가 담는다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FunnelScope

@Module
@InstallIn(ActivityRetainedComponent::class)
object CreateFunnelModule {
    @Provides
    @ActivityRetainedScoped
    @FunnelScope
    fun provideFunnelScope(lifecycle: ActivityRetainedLifecycle): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            lifecycle.addOnClearedListener { scope.cancel() }
        }
}
