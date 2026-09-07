package app.manyak.di

import app.manyak.BuildConfig
import app.manyak.core.navigation.LegalDocument
import app.manyak.legal.domain.LegalUrlProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 법적 문서 주소. 본문 정본이 웹 한 곳이라 앱은 복제하지 않고 그 페이지를 연다.
 * 시행일·버전이 갈라질 수 없는 대신 오프라인에서는 열리지 않는다.
 */
@Module
@InstallIn(SingletonComponent::class)
object LegalModule {
    @Provides
    @Singleton
    fun provideLegalUrlProvider(): LegalUrlProvider =
        object : LegalUrlProvider {
            override fun urlFor(document: LegalDocument): String =
                when (document) {
                    LegalDocument.TERMS -> "${BuildConfig.WEB_BASE_URL}/terms"
                    LegalDocument.PRIVACY -> "${BuildConfig.WEB_BASE_URL}/privacy"
                    LegalDocument.ABOUT -> "${BuildConfig.WEB_BASE_URL}/about"
                }
        }
}
