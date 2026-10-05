package io.github.krank56.webmote.ui.remote

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TextMirrorTest {
    private val sent = mutableListOf<String>()
    private val mirror = TextMirror(insert = { sent += "insert $it" }, delete = { sent += "delete $it" })

    private fun type(vararg states: String) = states.forEach(mirror::onTextChanged)

    @Test
    fun `each typed character is inserted as it's typed`() {
        type("n", "ne", "net")

        assertEquals(listOf("insert n", "insert e", "insert t"), sent)
    }

    @Test
    fun `backspace deletes one character`() {
        type("net", "ne")

        assertEquals(listOf("insert net", "delete 1"), sent)
    }

    @Test
    fun `an autocorrected word is deleted back to where it differs and retyped`() {
        type("helo", "hello ")

        assertEquals(listOf("insert helo", "delete 1", "insert lo "), sent)
    }

    @Test
    fun `an edit in the middle retypes everything after it`() {
        type("abcd", "aXcd")

        assertEquals(listOf("insert abcd", "delete 3", "insert Xcd"), sent)
    }

    @Test
    fun `deleting an emoji deletes one character, not two UTF-16 units`() {
        type("a😀", "a")

        assertEquals(listOf("insert a😀", "delete 1"), sent)
    }

    @Test
    fun `an unchanged text sends nothing`() {
        type("a", "a")

        assertEquals(listOf("insert a"), sent)
    }

    @Test
    fun `after a reset the field starts empty without deleting on the TV`() {
        type("search")
        mirror.reset()
        type("s")

        assertEquals(listOf("insert search", "insert s"), sent)
    }
}
