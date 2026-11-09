package app.manyak.create.entity

/** 제공 화풍 하나. 목록 순서와 이름은 서버 카탈로그가 정하므로 앱은 받은 그대로 그린다. */
data class ArtStyle(
    /** 완성 요청의 `artStyle` 에 싣는 값. */
    val code: String,
    val name: String,
    /** 3:4 미리보기. 인물 이미지를 만들지 않는 화풍은 없다. */
    val previewImageUrl: String?,
)
