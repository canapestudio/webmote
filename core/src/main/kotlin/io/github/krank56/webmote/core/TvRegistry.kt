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
    /** The TV's `deviceUUID`, from its hello. */
    val id: String,
    val name: String,
    /** The last-known IP address or host name. */
    val host: String,
    val model: String? = null,
    val webOsVersion: String? = null,
    val clientKey: String? = null,
    /** SHA-256 fingerprint of the TV's certificate, recorded at pairing (trust on first use). */
    val certificatePin: String? = null,
    val capabilities: LearnedCapabilities = LearnedCapabilities(),
)

@Serializable
public data class LearnedCapabilities(
    val pointer: Capability = Capability.Unknown,
    val volumeLevel: Capability = Capability.Unknown,
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

    public fun findByHost(host: String): SavedTv? = tvs.value.firstOrNull { it.host == host }

    /** Adds [tv], or replaces the saved TV with the same ID. */
    public fun save(tv: SavedTv): Unit = mutate { tvs, active ->
        val replaced = tvs.indexOfFirst { it.id == tv.id }
        val updated = if (replaced >= 0) tvs.toMutableList().also { it[replaced] = tv } else tvs + tv
        updated to active
    }

    /** Applies [change] to the saved TV with [id]. Does nothing if there's none. */
    public fun update(id: String, change: (SavedTv) -> SavedTv): Unit = mutate { tvs, active ->
        tvs.map { if (it.id == id) change(it) else it } to active
    }

    public fun rename(id: String, name: String): Unit = update(id) { it.copy(name = name) }

    public fun setActive(id: String?): Unit = mutate { tvs, active ->
        tvs to (if (id == null || tvs.any { it.id == id }) id else active)
    }

    /**
     * Removes the TV with [id] and everything stored with it. If it was the active TV, the first
     * remaining TV becomes active, or none if it was the last.
     */
    public fun forget(id: String): Unit = mutate { tvs, active ->
        val remaining = tvs.filterNot { it.id == id }
        remaining to (if (active == id) remaining.firstOrNull()?.id else active)
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
