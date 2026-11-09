package app.manyak.designsystem.text

import app.manyak.designsystem.component.isAllowedCharacterImageUrl
import app.manyak.designsystem.component.isSceneImageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val IMAGE_URL = "https://cdn.manyak.app/characters/generated/watchmaker.png"

private const val ORIGINAL_IMAGE_URL =
    "https://dev-cdn.manyak.app/characters/originals/cca6358a-0ef3-4709-88d1-100b9faeeca8/오만수_561c77ff.webp"

private const val REALTIME_IMAGE_URL =
    "https://dev-cdn.manyak.app/chat-images/2f1c9a8e-7b3d-4c1a-9e2f-5d6a7b8c9d0e/3-1c2d3e4f-5a6b-7c8d-9e0f-1a2b3c4d5e6f.webp"

private const val SCENE_IMAGE_URL =
    "https://dev-cdn.manyak.app/scenes/originals/cca6358a-0ef3-4709-88d1-100b9faeeca8/platform_1a2b3c4d.webp"

class PassageSegmentsTest {
    @Test
    fun `장면 이미지는 뒤에 대사가 없어도 이미지가 되고 앞뒤 빈 줄을 간격으로 대신한다`() {
        val content = "*플랫폼에 안개가 깔린다.*\n\n[[$SCENE_IMAGE_URL]]\n\n*기차가 들어온다.*"

        assertEquals(
            listOf(
                PassageSegment.Text("*플랫폼에 안개가 깔린다.*"),
                PassageSegment.SceneImage(SCENE_IMAGE_URL),
                PassageSegment.Text("*기차가 들어온다.*"),
            ),
            parsePassageSegments(content),
        )
    }

    @Test
    fun `장면 이미지가 본문 처음이나 끝에 있어도 이미지가 된다`() {
        assertEquals(
            listOf(PassageSegment.SceneImage(SCENE_IMAGE_URL), PassageSegment.Text("문이 열린다.")),
            parsePassageSegments("[[$SCENE_IMAGE_URL]]\n문이 열린다."),
        )
        assertEquals(
            listOf(PassageSegment.Text("문이 열린다."), PassageSegment.SceneImage(SCENE_IMAGE_URL)),
            parsePassageSegments("문이 열린다.\n\n[[$SCENE_IMAGE_URL]]"),
        )
    }

    @Test
    fun `장면 이미지 뒤에 이름 라벨이 와도 인물 이미지로 읽지 않는다`() {
        assertEquals(
            listOf(PassageSegment.SceneImage(SCENE_IMAGE_URL), PassageSegment.Text("시계공: 오셨군요.")),
            parsePassageSegments("[[$SCENE_IMAGE_URL]]\n\n시계공: 오셨군요."),
        )
    }

    @Test
    fun `장면 경로는 운영과 개발 CDN 에서 파일이 있을 때만 허용한다`() {
        assertTrue(isSceneImageUrl(SCENE_IMAGE_URL))
        assertTrue(isSceneImageUrl("https://cdn.manyak.app/scenes/originals/s1/valley.webp"))
        assertFalse(isSceneImageUrl("https://cdn.manyak.app/scenes/originals/"))
        assertFalse(isSceneImageUrl("https://evil.example/scenes/originals/s1/valley.webp"))
        assertFalse(isSceneImageUrl("https://cdn.manyak.app/scenes/generated/s1/valley.webp"))
        assertFalse(isSceneImageUrl(IMAGE_URL))
        assertFalse(isSceneImageUrl("주소가 아님"))

        val content = "[[https://evil.example/scenes/originals/s1/valley.webp]]\n\n문이 열린다."
        assertEquals(listOf(PassageSegment.Text(content)), parsePassageSegments(content))
    }

    @Test
    fun `마커가 없으면 본문 전체가 텍스트 한 조각이다`() {
        assertEquals(
            listOf(PassageSegment.Text("문이 열린다.")),
            parsePassageSegments("문이 열린다."),
        )
        assertEquals(emptyList<PassageSegment>(), parsePassageSegments(""))
    }

    @Test
    fun `세 조건을 만족하면 이미지로 바꾼다`() {
        val content = "문이 열린다.\n[[$IMAGE_URL]]\n\n시계공: 오셨군요."

        assertEquals(
            listOf(
                PassageSegment.Text("문이 열린다."),
                PassageSegment.CharacterImage(name = "시계공", imageUrl = IMAGE_URL),
                PassageSegment.Text("시계공: 오셨군요."),
            ),
            parsePassageSegments(content),
        )
    }

    @Test
    fun `마커 줄에 다른 글자가 섞이면 평문으로 남는다`() {
        // 조용히 지우면 서버가 보낸 본문이 사라진 것을 아무도 알 수 없다.
        val content = "앞 [[$IMAGE_URL]]\n\n시계공: 오셨군요."

        assertEquals(listOf(PassageSegment.Text(content)), parsePassageSegments(content))
    }

    @Test
    fun `마커 뒤에 빈 줄이 없으면 평문으로 남는다`() {
        val content = "[[$IMAGE_URL]]\n시계공: 오셨군요."

        assertEquals(listOf(PassageSegment.Text(content)), parsePassageSegments(content))
    }

    @Test
    fun `빈 줄 다음이 이름 라벨이 아니면 평문으로 남는다`() {
        val content = "[[$IMAGE_URL]]\n\n오셨군요."

        assertEquals(listOf(PassageSegment.Text(content)), parsePassageSegments(content))
    }

    @Test
    fun `허용하지 않는 주소는 평문으로 남는다`() {
        val content = "[[https://evil.example/characters/generated/a.png]]\n\n시계공: 오셨군요."

        assertEquals(listOf(PassageSegment.Text(content)), parsePassageSegments(content))
    }

    @Test
    fun `CRLF 본문도 같게 읽는다`() {
        val content = "문이 열린다.\r\n[[$IMAGE_URL]]\r\n\r\n시계공: 오셨군요."

        assertEquals(
            listOf(
                PassageSegment.Text("문이 열린다."),
                PassageSegment.CharacterImage(name = "시계공", imageUrl = IMAGE_URL),
                PassageSegment.Text("시계공: 오셨군요."),
            ),
            parsePassageSegments(content),
        )
    }

    @Test
    fun `오리지널 스토리의 인물 이미지도 그대로 이미지가 된다`() {
        // 오리지널 스토리는 originals 경로로 오고 파일 이름에 한글이 섞인다.
        val content = "문이 열린다.\n[[$ORIGINAL_IMAGE_URL]]\n\n오만수: 규칙 둘, 창문에 비친 것."

        assertEquals(
            listOf(
                PassageSegment.Text("문이 열린다."),
                PassageSegment.CharacterImage(name = "오만수", imageUrl = ORIGINAL_IMAGE_URL),
                PassageSegment.Text("오만수: 규칙 둘, 창문에 비친 것."),
            ),
            parsePassageSegments(content),
        )
    }

    @Test
    fun `운영과 개발 CDN 의 생성 · 오리지널 · 채팅 실시간 인물 경로만 허용한다`() {
        assertTrue(isAllowedCharacterImageUrl(IMAGE_URL))
        assertTrue(isAllowedCharacterImageUrl("https://dev-cdn.manyak.app/characters/generated/a.png"))
        assertTrue(isAllowedCharacterImageUrl(ORIGINAL_IMAGE_URL))
        assertTrue(isAllowedCharacterImageUrl("https://cdn.manyak.app/characters/originals/a.png"))
        assertTrue(isAllowedCharacterImageUrl(REALTIME_IMAGE_URL))

        // 다른 호스트·평문·포트·자격 증명·다른 경로는 모두 막는다.
        assertFalse(isAllowedCharacterImageUrl("https://cdn.manyak.app.evil.example/characters/generated/a.png"))
        assertFalse(isAllowedCharacterImageUrl("http://cdn.manyak.app/characters/generated/a.png"))
        assertFalse(isAllowedCharacterImageUrl("https://cdn.manyak.app:8443/characters/generated/a.png"))
        assertFalse(isAllowedCharacterImageUrl("https://user:pw@cdn.manyak.app/characters/generated/a.png"))
        assertFalse(isAllowedCharacterImageUrl("https://cdn.manyak.app/covers/a.png"))
        assertFalse(isAllowedCharacterImageUrl("https://cdn.manyak.app/characters/generated/"))
        assertFalse(isAllowedCharacterImageUrl("https://cdn.manyak.app/characters/originals/"))
        assertFalse(isAllowedCharacterImageUrl("https://cdn.manyak.app/chat-images/"))
        assertFalse(isAllowedCharacterImageUrl("주소가 아님"))
    }
}
