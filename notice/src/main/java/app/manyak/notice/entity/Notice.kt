package app.manyak.notice.entity

/** 목록 한 줄. [publishedAtMillis] 는 서버 시각을 해석하지 못하면 null 이고, 그 공지는 게시일을 그리지 않고 안 읽음으로도 세지 않는다. */
data class NoticeSummary(
    val id: String,
    val title: String,
    val publishedAtMillis: Long?,
    val tag: String? = null,
)

data class NoticePage(
    val items: List<NoticeSummary>,
    val nextCursor: String?,
)

/** [body] 는 서버가 가공하지 않는 평문이다. 마크다운·HTML 로 해석하지 않고 줄바꿈만 살린다. */
data class NoticeDetail(
    val id: String,
    val title: String,
    val body: String,
    val publishedAtMillis: Long?,
    val updatedAtMillis: Long?,
    val tag: String? = null,
)
