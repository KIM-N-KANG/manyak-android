package app.manyak.core.data.datastore

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 진행 레코드가 Room 으로 옮겨 가기 전에 쓰던 DataStore 파일을 지운다.
 *
 * 레코드는 재생성 가능한 스냅숏이라 이관하지 않는다. 그렇다고 파일을 남겨 두면 읽는 곳 없는
 * 사용자 귀속 데이터가 기기에 남으므로, 앱 시작 시 한 번 지운다. 파일이 없으면 아무 일도 없다.
 */
@Singleton
class LegacyPendingCreationFile
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        fun delete() {
            runCatching { context.preferencesDataStoreFile(LEGACY_STORE_NAME).delete() }
        }

        private companion object {
            const val LEGACY_STORE_NAME = "pending_creation"
        }
    }
