package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv.Companion.ok
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class CatalogTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    // Inputs

    @Test
    fun `the TV's inputs are listed`() {
        h.pair()

        h.eventually { h.state.inputs.isNotEmpty() }
        assertEquals(
            listOf(TvInput("HDMI_1", "HDMI 1"), TvInput("HDMI_2", "HDMI 2"), TvInput("HDMI_3", "HDMI 3")),
            h.state.inputs,
        )
    }

    @Test
    fun `inputs without an id are skipped, and an input without a label is shown by its id`() {
        h.tv.on(INPUT_LIST) {
            ok {
                putJsonArray("devices") {
                    addJsonObject { put("label", "Mystery") }
                    addJsonObject { put("id", "AV_1") }
                    addJsonObject { put("id", "HDMI_2"); put("label", "THE-POWERHOUSE"); put("appId", "com.webos.app.hdmi2") }
                }
            }
        }
        h.pair()

        h.eventually { h.state.inputs.isNotEmpty() }
        assertEquals(listOf(TvInput("AV_1", "AV_1"), TvInput("HDMI_2", "THE-POWERHOUSE")), h.state.inputs)
    }

    @Test
    fun `switching input sends the input's id`() {
        h.pair()

        h.session.switchInput("HDMI_2")

        assertEquals("HDMI_2", h.awaitRequests("ssap://tv/switchInput").single().string("inputId"))
    }

    private companion object {
        const val INPUT_LIST = "ssap://tv/getExternalInputList"
    }
}
