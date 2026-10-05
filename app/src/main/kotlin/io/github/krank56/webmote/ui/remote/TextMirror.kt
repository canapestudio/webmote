package io.github.krank56.webmote.ui.remote

/**
 * Mirrors the phone's text field onto the TV's focused text field, which can only type and delete
 * at its cursor.
 *
 * Each change is turned into "delete back to where the texts differ, then type the rest". That covers
 * typing, backspace, and IME composition and autocorrect rewriting the current word; an edit in the
 * middle retypes everything after it.
 */
class TextMirror(
    private val insert: (String) -> Unit,
    private val delete: (Int) -> Unit,
) {
    private var mirrored = ""

    fun onTextChanged(text: String) {
        if (text == mirrored) return
        val common = mirrored.commonPrefixWith(text).length
        val deleted = mirrored.codePointCount(common, mirrored.length)
        if (deleted > 0) delete(deleted)
        val inserted = text.substring(common)
        if (inserted.isNotEmpty()) insert(inserted)
        mirrored = text
    }

    /** Forgets the mirrored text, e.g. after Enter, without touching the TV's field. */
    fun reset() {
        mirrored = ""
    }
}
