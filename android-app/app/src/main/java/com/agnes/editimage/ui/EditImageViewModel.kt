package com.agnes.editimage.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agnes.editimage.BuildConfig
import com.agnes.editimage.data.AgnesApi
import com.agnes.editimage.data.SKILLS
import com.agnes.editimage.data.analyzeAndEnhance
import com.agnes.editimage.data.generateEdit
import com.agnes.editimage.data.pickRatio
import com.agnes.editimage.util.imageDimensions
import com.agnes.editimage.util.readBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoadedSkill(val badge: String, val chars: Int)

sealed interface ChatItem {
    data class UserMessage(val text: String, val imageBytes: ByteArray?) : ChatItem
    data class ThoughtGroup(val durationSeconds: String, val skills: List<LoadedSkill>, val expanded: Boolean) : ChatItem
    data class AssistantText(val text: String) : ChatItem
    data class PromptEnhancement(val original: String, val enhanced: String) : ChatItem
    data class StatusBanner(val text: String) : ChatItem
    data class ResultImage(val bytes: ByteArray) : ChatItem
    data class Error(val message: String) : ChatItem
}

data class UiState(
    val items: List<ChatItem> = emptyList(),
    val busy: Boolean = false,
    val input: String = "",
    val attachedImage: ByteArray? = null,
    val attachedMime: String = "image/jpeg",
    val title: String = "Edit Image",
    val apiKeyConfigured: Boolean = false,
    val savedApiKey: String = "",
    val savedBaseUrl: String = "",
    val lastSaved: Boolean = false,
)

class EditImageViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("agnes_settings", Application.MODE_PRIVATE)

    private var apiKey: String = prefs.getString("api_key", null) ?: BuildConfig.AGNES_API_KEY
    private var baseUrl: String =
        prefs.getString("base_url", null) ?: BuildConfig.AGNES_BASE_URL

    private var api = AgnesApi(apiKey, baseUrl)

    private val _state = MutableStateFlow(
        UiState(
            apiKeyConfigured = apiKey.isNotBlank(),
            savedApiKey = apiKey,
            savedBaseUrl = baseUrl,
        )
    )
    val state: StateFlow<UiState> = _state

    fun attachImage(uri: Uri) {
        val bytes = readBytes(getApplication(), uri)
        if (bytes.isNotEmpty()) {
            val mime = getApplication<Application>().contentResolver.getType(uri)
                ?: "image/jpeg"
            _state.update { it.copy(attachedImage = bytes, attachedMime = mime) }
        }
    }

    fun setInput(text: String) {
        _state.update { it.copy(input = text) }
    }

    fun clearAttachment() {
        _state.update { it.copy(attachedImage = null) }
    }

    fun reset() {
        _state.value = UiState(
            apiKeyConfigured = apiKey.isNotBlank(),
            savedApiKey = apiKey,
            savedBaseUrl = baseUrl,
        )
    }

    fun submit() {
        val s = _state.value
        val image = s.attachedImage ?: return
        val prompt = s.input.trim()
        if (prompt.isEmpty() || s.busy) return

        val base = s.items.size
        val items = s.items.toMutableList()
        val skillLoads = SKILLS.map { LoadedSkill(it.badge, it.content.length) }

        items += ChatItem.UserMessage(prompt, image)                 // base + 0
        items += ChatItem.ThoughtGroup("…", emptyList(), expanded = false)  // base + 1
        items += ChatItem.AssistantText("")                          // base + 2
        items += ChatItem.ThoughtGroup("…", skillLoads, expanded = true)    // base + 3
        items += ChatItem.PromptEnhancement(prompt, "")              // base + 4
        items += ChatItem.StatusBanner("Das dauert etwa 15–45 Sekunden, bitte habe einen Moment Geduld.") // base + 5

        _state.update {
            it.copy(
                items = items,
                busy = true,
                input = "",
                attachedImage = null,
                title = prompt,
                lastSaved = false,
            )
        }

        viewModelScope.launch {
            try {
                val imageDataUri = "data:${s.attachedMime};base64," +
                    Base64.encodeToString(image, Base64.NO_WRAP)

                val t0 = SystemClock.elapsedRealtime()
                val analysis = analyzeAndEnhance(api, imageDataUri, prompt)
                val analysisSeconds = (SystemClock.elapsedRealtime() - t0) / 1000.0

                _state.update { st ->
                    val newItems = st.items.toMutableList()
                    newItems[base + 1] = ChatItem.ThoughtGroup(
                        durationSeconds = String.format("%.2f", analysisSeconds),
                        skills = emptyList(),
                        expanded = false,
                    )
                    newItems[base + 2] = ChatItem.AssistantText(analysis.replyDe)
                    newItems[base + 4] = ChatItem.PromptEnhancement(prompt, analysis.editPrompt)
                    st.copy(items = newItems)
                }

                val t1 = SystemClock.elapsedRealtime()
                val dims = imageDimensions(image)
                val ratio = dims?.let { pickRatio(it.first, it.second) } ?: "3:4"
                val resultBytes = generateEdit(api, imageDataUri, analysis, ratio, "2K")
                val genSeconds = (SystemClock.elapsedRealtime() - t1) / 1000.0

                _state.update { st ->
                    val newItems = st.items.toMutableList()
                    newItems[base + 3] = ChatItem.ThoughtGroup(
                        durationSeconds = String.format("%.2f", genSeconds),
                        skills = skillLoads,
                        expanded = true,
                    )
                    newItems += ChatItem.AssistantText("Ich habe die gewünschte Änderung vorgenommen.")
                    newItems += ChatItem.ResultImage(resultBytes)
                    st.copy(items = newItems, busy = false)
                }
            } catch (e: Exception) {
                _state.update { st ->
                    val newItems = st.items.toMutableList()
                    newItems += ChatItem.Error(e.message ?: "Unbekannter Fehler")
                    st.copy(items = newItems, busy = false)
                }
            }
        }
    }

    fun updateSettings(apiKey: String, baseUrl: String) {
        val newKey = apiKey.trim()
        val newBase = baseUrl.trim().ifBlank { BuildConfig.AGNES_BASE_URL }
        this.apiKey = newKey
        this.baseUrl = newBase
        prefs.edit()
            .putString("api_key", newKey)
            .putString("base_url", newBase)
            .apply()
        api = AgnesApi(newKey, newBase)
        _state.update {
            it.copy(
                apiKeyConfigured = newKey.isNotBlank(),
                savedApiKey = newKey,
                savedBaseUrl = newBase,
            )
        }
    }

    fun markSaved() {
        _state.update { it.copy(lastSaved = true) }
    }
}
