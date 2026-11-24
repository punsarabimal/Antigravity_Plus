package com.example.data

import android.content.Context
import com.example.model.ApiKeyEntry
import com.example.model.IdeUiState
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadApiKeys(): List<ApiKeyEntry> {
        val raw = prefs.getString(KEY_API_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val label = obj.optString("label")
                    val key = obj.optString("key")
                    if (id.isNotBlank() && key.isNotBlank()) {
                        add(ApiKeyEntry(id = id, label = label.ifBlank { "Gemini Key" }, key = key))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveApiKeys(keys: List<ApiKeyEntry>) {
        val array = JSONArray()
        keys.forEach { entry ->
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("label", entry.label)
                put("key", entry.key)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_API_LIST, array.toString()).apply()
    }

    fun loadActiveApiKeyId(): String? = prefs.getString(KEY_ACTIVE_API_ID, null)

    fun saveActiveApiKeyId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_API_ID, id).apply()
    }

    fun loadActiveModel(): String =
        prefs.getString(KEY_ACTIVE_MODEL, IdeUiState.DEFAULT_MODEL) ?: IdeUiState.DEFAULT_MODEL

    fun saveActiveModel(model: String) {
        prefs.edit().putString(KEY_ACTIVE_MODEL, model).apply()
    }

    fun loadSystemInstruction(): String =
        prefs.getString(KEY_SYSTEM_INSTRUCTION, IdeUiState.DEFAULT_SYSTEM_INSTRUCTION)
            ?: IdeUiState.DEFAULT_SYSTEM_INSTRUCTION

    fun saveSystemInstruction(instruction: String) {
        prefs.edit().putString(KEY_SYSTEM_INSTRUCTION, instruction).apply()
    }

    fun loadTermuxWorkdir(): String =
        prefs.getString(KEY_TERMUX_WORKDIR, IdeUiState.DEFAULT_TERMUX_WORKDIR)
            ?: IdeUiState.DEFAULT_TERMUX_WORKDIR

    fun saveTermuxWorkdir(workdir: String) {
        prefs.edit().putString(KEY_TERMUX_WORKDIR, workdir).apply()
    }

    fun loadSafTreeUri(): String? = prefs.getString(KEY_SAF_TREE_URI, null)

    fun saveSafTreeUri(uri: String?) {
        prefs.edit().putString(KEY_SAF_TREE_URI, uri).apply()
    }

    companion object {
        private const val PREFS_NAME = "antigravity_studio_prefs"
        private const val KEY_API_LIST = "gemini_api_keys_json"
        private const val KEY_ACTIVE_API_ID = "active_api_key_id"
        private const val KEY_ACTIVE_MODEL = "active_gemini_model"
        private const val KEY_SYSTEM_INSTRUCTION = "agent_system_instruction"
        private const val KEY_TERMUX_WORKDIR = "default_termux_workdir"
        private const val KEY_SAF_TREE_URI = "saf_tree_uri"
    }
}
