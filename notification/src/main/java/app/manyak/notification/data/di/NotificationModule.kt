package app.manyak.notification.data.di

import app.manyak.network.data.di.AuthenticatedClient
import app.manyak.network.data.di.DataLayerConfig
import app.manyak.network.data.retrofit
import app.manyak.notification.data.FcmTokenSource
import app.manyak.notification.data.NotificationPermissionPromptStore
import app.manyak.notification.data.PushTokenRegistrarImpl
import app.manyak.notification.data.api.PushTokenApi
import app.manyak.notification.domain.NotificationPermissionPromptRepository
import app.manyak.notification.domain.PushTokenRegistrar
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton
import kotlin.coroutines.resume

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds
    @Singleton
    abstract fun bindPushTokenRegistrar(impl: PushTokenRegistrarImpl): PushTokenRegistrar

    @Binds
    @Singleton
    abstract fun bindNotificationPermissionPromptRepository(
        impl: NotificationPermissionPromptStore,
    ): NotificationPermissionPromptRepository

    companion object {
        @Provides
        @Singleton
        fun providePushTokenApi(
            @AuthenticatedClient client: OkHttpClient,
            config: DataLayerConfig,
            json: Json,
        ): PushTokenApi = retrofit(client, config, json).create(PushTokenApi::class.java)

        @Provides
        @Singleton
        fun provideFcmTokenSource(): FcmTokenSource = FcmTokenSource { firebaseToken() }

        /** Task 를 기다리는 데 코루틴 어댑터 의존성을 더하지 않는다. 실패는 "토큰 없음" 으로 본다. */
        private suspend fun firebaseToken(): String? =
            suspendCancellableCoroutine { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    val token = if (task.isSuccessful) task.result?.takeIf { it.isNotBlank() } else null
                    continuation.resume(token)
                }
            }
    }
}
