package app.manyak.legal.consent.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConsentStatusDto(
    val requiredVersion: String? = null,
    val needsConsent: Boolean? = null,
)

@Serializable
data class UserConsentResponseDto(
    val terms: ConsentStatusDto? = null,
    val privacy: ConsentStatusDto? = null,
    val age14: ConsentStatusDto? = null,
)

/** 수락한 버전만 싣는다. null 필드는 직렬화에서 빠지고 서버는 미제출로 본다. */
@Serializable
data class UserConsentRequestDto(
    val terms: String? = null,
    val privacy: String? = null,
    val age14: String? = null,
)
