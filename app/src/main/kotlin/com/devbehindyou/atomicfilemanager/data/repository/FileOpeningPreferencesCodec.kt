package com.devbehindyou.atomicfilemanager.data.repository

import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleKey
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleScope
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenTarget
import com.devbehindyou.atomicfilemanager.domain.model.FileOpeningPreferences
import com.devbehindyou.atomicfilemanager.domain.model.MAX_FILE_OPENING_RULES
import com.devbehindyou.atomicfilemanager.domain.model.normalizedFileMime
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

internal object FileOpeningPreferencesCodec {
    fun decode(raw: String?): FileOpeningPreferences {
        if (raw == null || raw.length > MAX_CHARACTERS) return FileOpeningPreferences()
        return try {
            val json = JSONObject(raw)
            if (json.optInt("version") != 1) return FileOpeningPreferences()
            val defaultMode = enumValues<FileOpenMode>().firstOrNull { it.name == json.optString("default") }
                ?: FileOpenMode.BUILT_IN
            val rows = json.optJSONArray("rules") ?: JSONArray()
            val rules = buildMap {
                for (i in 0 until minOf(rows.length(), MAX_FILE_OPENING_RULES)) {
                    val row = rows.optJSONObject(i) ?: continue
                    val scope =
                        enumValues<FileOpenRuleScope>().firstOrNull { it.name == row.optString("scope") } ?: continue
                    val key = normalizedKey(scope, row.optString("key")) ?: continue
                    val mode = enumValues<FileOpenMode>().firstOrNull { it.name == row.optString("mode") } ?: continue
                    val component = row.optString("component").takeIf { it.isNotBlank() }
                    if (component != null &&
                        (mode != FileOpenMode.EXTERNAL || component.length !in 3..512 || '/' !in component)
                    ) continue
                    val label = row.optString("label").takeIf { it.isNotBlank() }?.take(128)
                    put(FileOpenRuleKey(scope, key), FileOpenTarget(mode, component, label))
                }
            }
            FileOpeningPreferences(defaultMode, rules)
        } catch (_: JSONException) {
            FileOpeningPreferences()
        }
    }

    fun encode(settings: FileOpeningPreferences): String =
        JSONObject().apply {
            put("version", 1)
            put("default", settings.defaultMode.name)
            put("rules", JSONArray().apply {
                settings.rules.entries.forEach { (key, target) ->
                    put(JSONObject().apply {
                        put("scope", key.scope.name)
                        put("key", key.value)
                        put("mode", target.mode.name)
                        target.component?.let { put("component", it) }
                        target.appLabel?.let { put("label", it.take(128)) }
                    })
                }
            })
        }.toString()

    private fun normalizedKey(scope: FileOpenRuleScope, raw: String): String? =
        when (scope) {
            FileOpenRuleScope.EXTENSION -> raw.lowercase(Locale.ROOT).takeIf {
                it.length in 1..32 && it.all { c -> c.isLetterOrDigit() || c in "_+-" }
            }
            FileOpenRuleScope.MIME -> normalizedFileMime(raw)
            FileOpenRuleScope.FAMILY -> enumValues<com.devbehindyou.atomicfilemanager.domain.model.FileOpenFamily>()
                .firstOrNull { it.name == raw }?.name
        }

    private const val MAX_CHARACTERS = 262_144
}
