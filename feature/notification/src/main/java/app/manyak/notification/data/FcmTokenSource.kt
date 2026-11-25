package app.manyak.notification.data

/** 현재 설치본의 FCM 등록 토큰. 아직 없거나 Play 서비스가 없어 읽지 못하면 null. */
fun interface FcmTokenSource {
    suspend fun current(): String?
}
