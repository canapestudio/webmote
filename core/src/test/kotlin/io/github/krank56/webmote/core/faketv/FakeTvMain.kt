package io.github.krank56.webmote.core.faketv

/**
 * Serves a fake TV on 127.0.0.1:3001 for trying the app without a TV: `./gradlew :core:runFakeTv`,
 * then `adb reverse tcp:3001 tcp:3001` and pair with 127.0.0.1 in the app.
 *
 * Type a command and Enter: `accept` / `decline` (the pairing prompt), `off` / `on`, `quit`.
 */
fun main() {
    val tv = FakeTv(port = 3001)
    tv.promptAnswer = PromptAnswer.Wait
    tv.powerOn()
    println("Fake TV on wss://${tv.host}:${tv.port}. Commands: accept, decline, off, on, quit")
    while (true) {
        val line = readlnOrNull()?.trim() ?: break
        val words = line.split(' ')
        when (words.first()) {
            "accept" -> tv.acceptPrompt()
            "decline" -> tv.declinePrompt()
            "off" -> tv.powerOff()
            "on" -> tv.powerOn()
            "quit" -> break
            else -> Unit
        }
        println("ok; requests so far: ${tv.requests.size}")
    }
    tv.close()
}
