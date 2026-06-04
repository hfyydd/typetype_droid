package com.typetype.droid.dictionary

import android.content.Context
import com.typetype.droid.settings.Android031Settings
import org.json.JSONArray
import java.util.Locale

data class DictionaryEntry(
    val term: String,
    val aliases: List<String> = emptyList(),
    val replacement: String = "",
    val enabled: Boolean = true,
    val autoLearned: Boolean = false,
)

class DictionaryStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("typetype_dictionary", Context.MODE_PRIVATE)
    private val loadedSystemTerms = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadBundledTerms(limit = 120_000)
    }

    fun loadEntries(): List<DictionaryEntry> {
        val raw = preferences.getString(KEY_ENTRIES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val aliases = item.optJSONArray("aliases")
                DictionaryEntry(
                    term = item.optString("term").trim(),
                    aliases = if (aliases == null) emptyList() else List(aliases.length()) { aliases.optString(it).trim() }.filter { it.isNotEmpty() },
                    replacement = item.optString("replacement").trim(),
                    enabled = item.optBoolean("enabled", true),
                    autoLearned = item.optBoolean("autoLearned", false),
                )
            }.filter { it.term.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    fun addTerm(term: String, aliases: List<String> = emptyList(), autoLearned: Boolean = false) {
        val normalized = term.trim()
        if (normalized.length < 2 || normalized.length > 40) return
        val current = loadEntries().toMutableList()
        if (current.any { it.term.equals(normalized, ignoreCase = true) }) return
        current += DictionaryEntry(normalized, aliases, autoLearned = autoLearned)
        saveEntries(current.takeLast(MAX_PERSONAL_ENTRIES))
    }

    fun addReplacement(from: String, to: String) {
        val source = from.trim()
        val target = to.trim()
        if (source.isEmpty() || target.isEmpty()) return
        val current = loadEntries().filterNot { it.term == target && it.aliases.contains(source) }.toMutableList()
        current += DictionaryEntry(term = target, aliases = listOf(source), replacement = target)
        saveEntries(current.takeLast(MAX_PERSONAL_ENTRIES))
    }

    fun applyReplacements(text: String): String {
        var output = text
        loadEntries()
            .filter { it.enabled && it.replacement.isNotBlank() }
            .forEach { entry ->
                entry.aliases.forEach { alias ->
                    if (alias.isNotBlank()) {
                        output = output.replace(alias, entry.replacement)
                    }
                }
            }
        return output
    }

    fun preserveTermsFor(text: String, settings: Android031Settings): List<String> {
        val normalized = text.lowercase(Locale.ROOT)
        val personal = loadEntries()
            .filter { it.enabled }
            .flatMap { listOf(it.term) + it.aliases }
            .filter { it.length >= 2 && normalized.contains(it.lowercase(Locale.ROOT)) }
        val bundled = if (settings.systemLexiconEnabled) {
            loadedSystemTerms.value.filter { normalized.contains(it.lowercase(Locale.ROOT)) }.take(80)
        } else {
            emptyList()
        }
        return (personal + bundled).distinct().take(120)
    }

    fun autoLearnFromText(text: String, enabled: Boolean) {
        if (!enabled) return
        val candidates = AUTO_LEARN_PATTERN.findAll(text)
            .map { it.value.trim('，', '。', '！', '？', '；', '：', ',', '.', '!', '?') }
            .filter { it.length in 2..24 && !PRIVACY_PATTERN.containsMatchIn(it) }
            .distinct()
            .take(12)
            .toList()
        candidates.forEach { addTerm(it, autoLearned = true) }
    }

    private fun saveEntries(entries: List<DictionaryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val aliases = JSONArray()
            entry.aliases.forEach(aliases::put)
            array.put(
                org.json.JSONObject()
                    .put("term", entry.term)
                    .put("aliases", aliases)
                    .put("replacement", entry.replacement)
                    .put("enabled", entry.enabled)
                    .put("autoLearned", entry.autoLearned),
            )
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun loadBundledTerms(limit: Int): List<String> {
        val output = linkedSetOf<String>()
        listOf("lexicons/code-switch-lexicon.json", "lexicons/system-lexicon.json").forEach { asset ->
            runCatching {
                appContext.assets.open(asset).bufferedReader(Charsets.UTF_8).useLines { lines ->
                    val pattern = Regex("\"term\"\\s*:\\s*\"([^\"]{2,40})\"")
                    for (line in lines) {
                        val term = pattern.find(line)?.groupValues?.getOrNull(1)
                        if (!term.isNullOrBlank()) {
                            output += term
                            if (output.size >= limit) return@useLines
                        }
                    }
                }
            }
        }
        return output.toList()
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val MAX_PERSONAL_ENTRIES = 4_000
        private val AUTO_LEARN_PATTERN = Regex("[A-Za-z][A-Za-z0-9.+#_-]{2,}|[\\u4e00-\\u9fa5]{2,}(?:项目|平台|系统|模型|公司|客户|品牌|会议|方案)")
        private val PRIVACY_PATTERN = Regex("(\\d{6,}|@|key|token|secret)", RegexOption.IGNORE_CASE)
    }
}
