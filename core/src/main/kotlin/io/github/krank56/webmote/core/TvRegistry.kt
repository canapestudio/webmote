package io.github.krank56.webmote.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A TV the user has paired, as persisted by [TvRegistry]. */
@Serializable
public data class SavedTv(
    /** The TV's unique ID. */
    val id: String,
    val name: String,
    /** The last-known IP address or host name. */
    val host: String,
)

/**
 * Persists the saved TVs and which one is the active TV, as one JSON file in [directory].
 *
 * Safe to call from any thread. Every change is written to disk before the call returns.
 */
public class TvRegistry(directory: File) {
    private val file = File(directory, FILE_NAME)
    private val lock = Any()

    private val _tvs: MutableStateFlow<List<SavedTv>>
    private val _activeTvId: MutableStateFlow<String?>

    init {
        directory.mkdirs()
        val stored = load()
        _tvs = MutableStateFlow(stored.tvs)
        _activeTvId = MutableStateFlow(stored.activeTvId?.takeIf { id -> stored.tvs.any { it.id == id } })
    }

    public val tvs: StateFlow<List<SavedTv>> = _tvs.asStateFlow()
    public val activeTvId: StateFlow<String?> = _activeTvId.asStateFlow()

    public val activeTv: SavedTv? get() = activeTvId.value?.let(::get)

    public operator fun get(id: String): SavedTv? = tvs.value.firstOrNull { it.id == id }

    /** Adds [tv], or replaces the saved TV with the same ID. */
    public fun save(tv: SavedTv): Unit = mutate { tvs, active ->
        val replaced = tvs.indexOfFirst { it.id == tv.id }
        val updated = if (replaced >= 0) tvs.toMutableList().also { it[replaced] = tv } else tvs + tv
        updated to active
    }

    public fun setActive(id: String?): Unit = mutate { tvs, active ->
        tvs to (if (id == null || tvs.any { it.id == id }) id else active)
    }

    private fun mutate(change: (List<SavedTv>, String?) -> Pair<List<SavedTv>, String?>) {
        synchronized(lock) {
            val (tvs, active) = change(_tvs.value, _activeTvId.value)
            if (tvs == _tvs.value && active == _activeTvId.value) return
            write(Stored(tvs, active))
            _tvs.value = tvs
            _activeTvId.value = active
        }
    }

    private fun load(): Stored =
        if (file.exists()) runCatching { json.decodeFromString<Stored>(file.readText()) }.getOrDefault(Stored()) else Stored()

    private fun write(stored: Stored) {
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(json.encodeToString(stored))
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Couldn't write $file" }
        }
    }

    @Serializable
    private data class Stored(val tvs: List<SavedTv> = emptyList(), val activeTvId: String? = null)

    private companion object {
        const val FILE_NAME = "tvs.json"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    }
}
