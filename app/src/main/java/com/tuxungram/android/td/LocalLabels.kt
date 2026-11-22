package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One local chat label (v0.9.25, R5): a name plus a color
 *  index into [LocalLabels.PALETTE]. Labels exist only on
 *  this phone — Telegram's own chat labels are a Business
 *  (server) feature; this is the local convenience version,
 *  like local pins: nothing is sent to the server, nothing
 *  syncs to other devices, and the UI says so wherever the
 *  feature is presented. */
data class LocalLabel(
    val id: Long,
    val name: String,
    val colorIndex: Int
)

/**
 * Local chat labels (v0.9.25, R5) — purely local convenience.
 * A user-defined set of labels (name + color), each chat can
 * carry any subset; the home list can be filtered by one
 * label. Storage is per account (the key carries the account
 * slot, as in LocalPins, because chat ids are only meaningful
 * inside one account) in SharedPreferences as one JSON blob.
 * Settings export/import carries the whole structure (see
 * SettingsBackup) so labels survive a device move the user
 * performs deliberately — they still never touch Telegram's
 * servers.
 */
object LocalLabels {
    private const val PREFS = "local_labels"

    /** Label dot colors (ARGB). Index is what gets stored. */
    val PALETTE: List<Long> = listOf(
        0xFFE53935L, 0xFFFB8C00L, 0xFFFDD835L, 0xFF43A047L,
        0xFF00ACC1L, 0xFF1E88E5L, 0xFF8E24AAL, 0xFF6D4C41L
    )

    private val _labels = MutableStateFlow<List<LocalLabel>>(emptyList())
    val labels: StateFlow<List<LocalLabel>> = _labels

    /** chatId -> label ids, in assignment order. */
    private val _assignments =
        MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
    val assignments: StateFlow<Map<Long, List<Long>>> = _assignments

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var slot = 0

    private fun key() = "labels_$slot"

    fun init(context: Context) {
        appContext = context.applicationContext
        slot = AccountStore.currentSlot()
        load()
    }

    private fun load() {
        val ctx = appContext ?: return
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(), "") ?: ""
        if (raw.isEmpty()) {
            _labels.value = emptyList()
            _assignments.value = emptyMap()
            return
        }
        try {
            val root = JSONObject(raw)
            _labels.value = parseLabels(root.optJSONArray("labels"))
            _assignments.value =
                parseAssignments(root.optJSONObject("assignments"))
        } catch (t: Throwable) {
            _labels.value = emptyList()
            _assignments.value = emptyMap()
        }
    }

    private fun parseLabels(arr: JSONArray?): List<LocalLabel> {
        if (arr == null) return emptyList()
        val out = ArrayList<LocalLabel>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            val name = o.optString("name")
            if (id == 0L || name.isEmpty()) continue
            out.add(
                LocalLabel(
                    id, name,
                    o.optInt("color", 0).coerceIn(0, PALETTE.lastIndex)
                )
            )
        }
        return out
    }

    private fun parseAssignments(
        obj: JSONObject?
    ): Map<Long, List<Long>> {
        if (obj == null) return emptyMap()
        val out = LinkedHashMap<Long, List<Long>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val chatId = k.toLongOrNull() ?: continue
            val arr = obj.optJSONArray(k) ?: continue
            val ids = ArrayList<Long>()
            for (i in 0 until arr.length()) {
                val id = arr.optLong(i, 0L)
                if (id != 0L && id !in ids) ids.add(id)
            }
            if (ids.isNotEmpty()) out[chatId] = ids
        }
        return out
    }

    private fun persist() {
        val ctx = appContext ?: return
        val labelsArr = JSONArray()
        for (label in _labels.value) {
            labelsArr.put(JSONObject().apply {
                put("id", label.id)
                put("name", label.name)
                put("color", label.colorIndex)
            })
        }
        val assignObj = JSONObject()
        for ((chatId, ids) in _assignments.value) {
            val arr = JSONArray()
            for (id in ids) arr.put(id)
            assignObj.put(chatId.toString(), arr)
        }
        val root = JSONObject().apply {
            put("labels", labelsArr)
            put("assignments", assignObj)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(), root.toString())
            .apply()
    }

    /** Create a label; returns it, or null for a blank name.
     *  Ids are timestamps — unique enough for a local list. */
    fun create(name: String, colorIndex: Int): LocalLabel? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        var id = System.currentTimeMillis()
        while (_labels.value.any { it.id == id }) id++
        val label = LocalLabel(
            id, trimmed,
            colorIndex.coerceIn(0, PALETTE.lastIndex)
        )
        _labels.value = _labels.value + label
        persist()
        return label
    }

    /** Rename a label (v0.9.28, U4). Assignments are keyed
     *  by id, so a rename touches nothing else; a blank name
     *  is rejected. */
    fun rename(labelId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _labels.value = _labels.value.map {
            if (it.id == labelId) it.copy(name = trimmed) else it
        }
        persist()
    }

    /** Delete a label and every assignment of it. The chats
     *  themselves are untouched — only the local tag goes. */
    fun delete(labelId: Long) {
        _labels.value = _labels.value.filter { it.id != labelId }
        _assignments.value = _assignments.value.mapNotNull { (chat, ids) ->
            val next = ids.filter { it != labelId }
            if (next.isEmpty()) null else chat to next
        }.toMap()
        persist()
    }

    fun labelOf(id: Long): LocalLabel? =
        _labels.value.firstOrNull { it.id == id }

    fun labelsOf(chatId: Long): List<Long> =
        _assignments.value[chatId] ?: emptyList()

    fun has(chatId: Long, labelId: Long): Boolean =
        labelId in labelsOf(chatId)

    /** Chats carrying [labelId] (for the home-list filter). */
    fun chatsWith(labelId: Long): Set<Long> =
        _assignments.value.filterValues { labelId in it }.keys

    fun toggle(chatId: Long, labelId: Long) {
        val current = labelsOf(chatId)
        val next = if (labelId in current) {
            current - labelId
        } else {
            current + labelId
        }
        val map = _assignments.value.toMutableMap()
        if (next.isEmpty()) map.remove(chatId) else map[chatId] = next
        _assignments.value = map
        persist()
    }

    /** Whole-structure export for SettingsBackup. */
    fun exportJson(): JSONObject {
        val labelsArr = JSONArray()
        for (label in _labels.value) {
            labelsArr.put(JSONObject().apply {
                put("id", label.id)
                put("name", label.name)
                put("color", label.colorIndex)
            })
        }
        val assignObj = JSONObject()
        for ((chatId, ids) in _assignments.value) {
            val arr = JSONArray()
            for (id in ids) arr.put(id)
            assignObj.put(chatId.toString(), arr)
        }
        return JSONObject().apply {
            put("labels", labelsArr)
            put("assignments", assignObj)
        }
    }

    /** Replace everything (settings import). Assignments that
     *  point at labels the import does not define are dropped. */
    fun replaceAll(root: JSONObject) {
        val labels = parseLabels(root.optJSONArray("labels"))
        val valid = labels.map { it.id }.toSet()
        _labels.value = labels
        _assignments.value =
            parseAssignments(root.optJSONObject("assignments"))
                .mapNotNull { (chat, ids) ->
                    val kept = ids.filter { it in valid }
                    if (kept.isEmpty()) null else chat to kept
                }.toMap()
        persist()
    }
}
