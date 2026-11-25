package app.manyak.create.testing

import app.manyak.common.domain.story.CreateTutorialProgress
import app.manyak.common.entity.story.CreateTutorialStage

/** 기본은 안내를 모두 마친 기기라 다른 화면 테스트에 대화창이 끼어들지 않는다. */
class FakeCreateTutorialProgress(
    private var next: CreateTutorialStage? = null,
) : CreateTutorialProgress {
    override suspend fun next(): CreateTutorialStage? = next

    override suspend fun finish(stage: CreateTutorialStage) {
        next = stage.following
    }

    override suspend fun finishAll() {
        next = null
    }
}
