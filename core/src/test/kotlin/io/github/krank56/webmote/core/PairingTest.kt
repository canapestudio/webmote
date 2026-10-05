package io.github.krank56.webmote.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class PairingTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `a saved TV is still the active TV after reopening the app`() {
        h.registry.save(SavedTv(id = "living-room", name = "Living room", host = h.tv.host))
        h.registry.setActive("living-room")

        h.reopen()

        assertEquals(listOf("Living room"), h.registry.tvs.value.map { it.name })
        assertEquals("living-room", h.registry.activeTvId.value)
        assertEquals("living-room", h.state.tvId)
    }
}
