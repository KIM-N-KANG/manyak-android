package app.manyak.common.domain.settings

import app.manyak.common.entity.settings.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * 테마 설정의 정본. 기기 귀속 값이라 로그아웃 정리 대상이 아니다.
 *
 * 저장 실패는 삼킨다 — 방금 바꾼 테마가 되돌아가서는 안 되고, 실패의 결과는
 * "다음 실행에서 기본값"뿐이다.
 */
interface ThemePreferenceRepository {
    /** 저장된 테마. 읽기 실패나 저장 전에는 [ThemeMode.SYSTEM] 을 낸다. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
