package app.manyak.create.presentation.component

import org.junit.Assert.assertEquals
import org.junit.Test

class EuroParticleTest {
    @Test
    fun `받침이 없거나 ㄹ이면 로, 그 밖의 받침이면 으로를 붙인다`() {
        assertEquals("로", euroParticle("판타지, 로맨스"))
        assertEquals("로", euroParticle("무협, 학원물"))
        assertEquals("으로", euroParticle("괴담, 생존"))
        assertEquals("로", euroParticle("SF"))
        assertEquals("로", euroParticle(""))
    }
}
