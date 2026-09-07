package app.manyak.report.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.network.data.api.emptyBodyApiCall
import app.manyak.report.data.api.ReportApi
import app.manyak.report.data.api.dto.CreateStoryReportRequestDto
import app.manyak.report.domain.ReportRepository
import app.manyak.report.entity.StoryReportReason
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepositoryImpl
    @Inject
    constructor(
        private val storyDetailApi: ReportApi,
    ) : ReportRepository {
        override suspend fun reportStory(
            storyId: String,
            reason: StoryReportReason,
            detail: String?,
        ): DomainResult<Unit> =
            emptyBodyApiCall {
                storyDetailApi.reportStory(
                    storyId = storyId,
                    request =
                        CreateStoryReportRequestDto(
                            reason = reason.name,
                            // 빈 문자열은 미작성과 같은 뜻이라 null 로 보낸다.
                            detail = detail?.takeIf { it.isNotBlank() },
                        ),
                )
            }
    }
