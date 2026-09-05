package app.manyak.core.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.feedback.FeedbackRepository
import app.manyak.core.data.api.FeedbackApi
import app.manyak.core.data.api.dto.CreateFeedbackRequestDto
import app.manyak.network.data.api.emptyBodyApiCall
import app.manyak.network.data.di.DataLayerConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepositoryImpl
    @Inject
    constructor(
        private val feedbackApi: FeedbackApi,
        private val config: DataLayerConfig,
    ) : FeedbackRepository {
        override suspend fun submitFeedback(
            body: String,
            email: String?,
        ): DomainResult<Unit> =
            emptyBodyApiCall {
                feedbackApi.createFeedback(
                    CreateFeedbackRequestDto(
                        body = body,
                        // 빈 문자열은 익명과 같은 뜻이라 null 로 보낸다.
                        email = email?.takeIf { it.isNotBlank() },
                        platform = PLATFORM_ANDROID,
                        appVersion = config.appVersion,
                    ),
                )
            }

        private companion object {
            const val PLATFORM_ANDROID = "ANDROID"
        }
    }
