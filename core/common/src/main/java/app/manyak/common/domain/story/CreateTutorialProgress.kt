package app.manyak.common.domain.story

import app.manyak.common.entity.story.CreateTutorialStage

/**
 * 간편 제작 첫 진입 안내를 이 기기에서 어디까지 마쳤는지. 제작 기능이 구현하고 제작 탭이 마지막 단계에 함께 쓴다.
 *
 * 기기 귀속 기록이라 로그인과 제작 이력을 보지 않고 로그아웃 정리 대상도 아니다.
 */
interface CreateTutorialProgress {
    /** 다음에 보일 단계. 모두 마쳤으면 null 이고, 기록을 읽지 못하면 첫 단계다. */
    suspend fun next(): CreateTutorialStage?

    /** [stage] 를 마쳤다. 기록에 실패하면 다음 진입에 같은 단계를 다시 연다. */
    suspend fun finish(stage: CreateTutorialStage)

    /** 건너뛰었거나 마지막 단계를 마쳤다. 이후 어느 단계도 열지 않는다. */
    suspend fun finishAll()
}
