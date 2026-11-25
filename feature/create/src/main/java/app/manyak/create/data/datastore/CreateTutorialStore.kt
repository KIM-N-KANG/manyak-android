package app.manyak.create.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.common.data.di.DeviceDataStore
import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.story.CreateTutorialProgress
import app.manyak.common.entity.story.CreateTutorialStage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 첫 진입 안내의 마지막으로 마친 단계. 기기 귀속 값이라 `device_id` 와 같은 DataStore 파일을 쓰고
 * `UserScopedStore` 를 구현하지 않는다.
 *
 * 읽지 못하면 처음부터 보이고 쓰지 못하면 다음 진입에 같은 단계를 다시 연다. 안내가 한 번 더 뜨는 편이 영영 못 보는
 * 것보다 낫다. 닫자마자 화면을 떠나도 기록이 남도록 쓰기는 취소하지 않는다.
 */
@Singleton
class CreateTutorialStore
    @Inject
    constructor(
        @param:DeviceDataStore private val dataStore: DataStore<Preferences>,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : CreateTutorialProgress {
        override suspend fun next(): CreateTutorialStage? =
            withContext(ioDispatcher) {
                CreateTutorialStage.nextAfter(runCatching { dataStore.data.first()[STAGE_KEY] }.getOrNull())
            }

        override suspend fun finish(stage: CreateTutorialStage) = write(stage.wire)

        override suspend fun finishAll() = write(CreateTutorialStage.DONE)

        private suspend fun write(value: String) {
            withContext(ioDispatcher + NonCancellable) {
                runCatching { dataStore.edit { it[STAGE_KEY] = value } }
            }
        }

        private companion object {
            val STAGE_KEY = stringPreferencesKey("create_tutorial_stage")
        }
    }
