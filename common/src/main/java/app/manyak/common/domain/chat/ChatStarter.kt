package app.manyak.common.domain.chat

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.chat.CreatedChat

/** 다른 기능에는 채팅을 시작하는 동작과 생성된 식별자만 공개한다. */
interface ChatStarter {
    /**
     * 스토리로 채팅을 생성한다(플레이 시작).
     *
     * @param startSettingId 상세에서 고른 시작 설정. `null` 이면 서버가 스토리의 첫 시작 설정으로
     *  폴백한다 — 간편 제작 완성 직후 진입처럼 고를 것이 하나뿐인 경로가 그렇다.
     */
    suspend fun createChat(
        storyId: String,
        startSettingId: String? = null,
    ): DomainResult<CreatedChat>
}
