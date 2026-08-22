package app.manyak.session

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.manyak.core.data.provider.ActivityProvider
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 제공자 SDK 는 화면을 띄우므로 Activity 가 필요하다. 현재 화면을 아는 것은 `:app` 뿐이라 여기서 추적한다.
 *
 * 참조를 약하게 잡아 화면이 사라진 뒤에도 붙들지 않는다.
 */
@Singleton
class CurrentActivityProvider
    @Inject
    constructor() :
    ActivityProvider,
        Application.ActivityLifecycleCallbacks {
        private var current: WeakReference<Activity>? = null

        override fun currentActivity(): Activity? = current?.get()?.takeIf { !it.isFinishing }

        override fun onActivityResumed(activity: Activity) {
            current = WeakReference(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (current?.get() === activity) current = null
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }
