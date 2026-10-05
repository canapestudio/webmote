package io.github.krank56.webmote.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class UnsupportedTvTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `a TV that only answers on the plain port is reported as unsupported`() {
        h.tv.legacyOnly = true
        h.tv.powerOn()

        h.session.connect(h.tv.host)

        h.awaitConnection(ConnectionState.Unsupported)
        assertTrue(h.registry.tvs.value.isEmpty())
    }

    @Test
    fun `a TV that answers on neither port is reported as off`() {
        h.session.connect(h.tv.host)

        h.awaitConnection(ConnectionState.Off)
    }

    @Test
    fun `a paired TV that only answers on the plain port for now is reported as off, not unsupported`() {
        h.pair()
        h.tv.powerOff()
        h.tv.legacyOnly = true
        h.tv.powerOn()

        h.reopen()
        h.session.connect()

        h.awaitConnection(ConnectionState.Off)
    }
}
