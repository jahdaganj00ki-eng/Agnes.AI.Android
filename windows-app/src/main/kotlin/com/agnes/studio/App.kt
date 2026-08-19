package com.agnes.studio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.Base64
import java.util.Locale
import java.util.Properties
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private const val DEFAULT_BASE_URL = "https://apihub.agnes-ai.com/v1"

private val AppBackground = Color(0xFF121212)
private val CardBackground = Color(0xFF1E1E1E)
private val CardBackground2 = Color(0xFF2A2A2E)
private val TextPrimary = Color(0xFFF5F5F5)
private val TextMuted = Color(0xFF9E9E9E)
private val ErrorRed = Color(0xFFEF6A6A)
private val SparkleCyan = Color(0xFF8AD8F0)
private val TealBadge = Color(0xFF2DD4BF)

private val DarkColors = darkColorScheme(
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground2,
    onSurfaceVariant = TextMuted,
)

data class LoadedSkill(val badge: String, val chars: Int)

sealed interface Attachment {
    data class Local(val bytes: ByteArray, val mime: String) : Attachment
    data class Remote(val url: String) : Attachment
}

sealed interface ChatItem {
    data class UserMessage(val text: String, val images: List<Attachment>) : ChatItem
    data class ThoughtGroup(val durationSeconds: String, val skills: List<LoadedSkill>, val expanded: Boolean) : ChatItem
    data class AssistantText(val text: String) : ChatItem
    data class PromptEnhancement(val original: String, val enhanced: String) : ChatItem
    data class StatusBanner(val text: String, val active: Boolean = true) : ChatItem
    data class ResultImage(val bytes: ByteArray) : ChatItem
    data class Error(val message: String) : ChatItem
}

data class Settings(val apiKey: String, val baseUrl: String)

private fun configDir(): File {
    // jpackage setzt diese Property in der gebauten App (App-Image).
    val jpkg = System.getProperty("jpackage.app-path")
    if (!jpkg.isNullOrBlank()) {
        var dir = File(jpkg)
        // Falls jpackage auf den internen "app"-Unterordner zeigt, eine Ebene
        // hoch, damit die Config wirklich neben der .exe liegt.
        if (dir.name.equals("app", ignoreCase = true)) {
            dir.parentFile?.let { dir = it }
        }
        return dir
    }
    // Entwicklung (gradlew run): Config im Projektordner.
    return File(System.getProperty("user.dir"))
}

private fun configFile(): File = File(configDir(), "agnes-image-studio.properties")

private fun loadSettings(): Settings = try {
    val f = configFile()
    val p = Properties()
    if (f.exists()) f.inputStream().use { p.load(it) }
    Settings(
        apiKey = p.getProperty("api_key").orEmpty(),
        baseUrl = p.getProperty("base_url").orEmpty().ifBlank { System.getenv("AGNES_BASE_URL") ?: DEFAULT_BASE_URL },
    )
} catch (_: Exception) {
    Settings(System.getenv("AGNES_API_KEY").orEmpty(), System.getenv("AGNES_BASE_URL") ?: DEFAULT_BASE_URL)
}

private fun saveSettings(key: String, base: String) {
    val p = Properties()
    p.setProperty("api_key", key)
    p.setProperty("base_url", base)
    try {
        configFile().outputStream().use { p.store(it, "Agnes AI Image Studio") }
    } catch (_: Exception) {
    }
}

class StudioState(
    private val scope: CoroutineScope,
    initialKey: String,
    initialBaseUrl: String,
) {
    var api by mutableStateOf(AgnesApi(initialKey, initialBaseUrl))
        private set

    var apiKey by mutableStateOf(initialKey)
        private set
    var baseUrl by mutableStateOf(initialBaseUrl)
        private set

    var items by mutableStateOf<List<ChatItem>>(emptyList())
    var busy by mutableStateOf(false)
    var input by mutableStateOf("")
    var attachments by mutableStateOf<List<Attachment>>(emptyList())
    var mode by mutableStateOf("edit")
    var title by mutableStateOf("Agnes AI Image Studio")

    fun addFiles(files: List<File>) {
        val added = files.mapNotNull { f ->
            val bytes = try { f.readBytes() } catch (_: Exception) { return@mapNotNull null }
            if (bytes.isEmpty()) return@mapNotNull null
            Attachment.Local(bytes, mimeOf(f.name))
        }
        if (added.isNotEmpty()) attachments = attachments + added
    }

    fun addUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        attachments = attachments + Attachment.Remote(trimmed)
    }

    fun removeAttachment(index: Int) {
        if (index !in attachments.indices) return
        attachments = attachments.toMutableList().also { it.removeAt(index) }
    }

    fun useAsInput(bytes: ByteArray) {
        attachments = listOf(Attachment.Local(bytes, "image/png"))
    }

    fun setMode(m: String) {
        mode = m
    }

    fun reset() {
        items = emptyList()
        busy = false
        input = ""
        attachments = emptyList()
        mode = "edit"
        title = "Agnes AI Image Studio"
    }

    fun updateSettings(key: String, base: String) {
        apiKey = key
        baseUrl = base.ifBlank { DEFAULT_BASE_URL }
        api = AgnesApi(apiKey, baseUrl)
        saveSettings(apiKey, baseUrl)
    }

    fun submit() {
        if (attachments.isEmpty()) return
        val prompt = input.trim()
        if (prompt.isEmpty() || busy) return

        val images = attachments.toList()
        val base = items.size
        val skillLoads = SKILLS.map { LoadedSkill(it.badge, it.content.length) }
        val newItems = items.toMutableList()
        newItems += ChatItem.UserMessage(prompt, images)
        newItems += ChatItem.ThoughtGroup("…", emptyList(), expanded = false)
        newItems += ChatItem.ThoughtGroup("…", skillLoads, expanded = true)
        newItems += ChatItem.AssistantText("")
        newItems += ChatItem.PromptEnhancement(prompt, "")
        newItems += ChatItem.StatusBanner("Das dauert etwa 15–45 Sekunden, bitte habe einen Moment Geduld.", active = true)

        items = newItems
        busy = true
        input = ""
        attachments = emptyList()
        title = prompt

        scope.launch {
            try {
                val imageDataUris = images.map { att ->
                    when (att) {
                        is Attachment.Local -> "data:${att.mime};base64," + Base64.getEncoder().encodeToString(att.bytes)
                        is Attachment.Remote -> att.url
                    }
                }

                val t0 = System.currentTimeMillis()
                val analysis = analyzeAndEnhance(api, imageDataUris, prompt)
                val analysisSeconds = (System.currentTimeMillis() - t0) / 1000.0

                val mid = items.toMutableList()
                if (mid.size >= base + 5) {
                    mid[base + 1] = ChatItem.ThoughtGroup(String.format(Locale.US, "%.2f", analysisSeconds), emptyList(), expanded = false)
                    mid[base + 3] = ChatItem.AssistantText(analysis.replyDe)
                    mid[base + 4] = ChatItem.PromptEnhancement(prompt, analysis.editPrompt)
                    items = mid
                }

                val t1 = System.currentTimeMillis()
                val firstLocal = images.filterIsInstance<Attachment.Local>().firstOrNull()?.bytes
                val dims = firstLocal?.let { imageDimensions(it) }
                val ratio = dims?.let { pickRatio(it.first, it.second) } ?: "3:4"
                val resultBytes = generateEdit(api, imageDataUris, analysis, ratio, "2K", mode)
                val genSeconds = (System.currentTimeMillis() - t1) / 1000.0

                val end = items.toMutableList()
                if (end.size >= base + 6) {
                    end[base + 2] = ChatItem.ThoughtGroup(String.format(Locale.US, "%.2f", genSeconds), skillLoads, expanded = true)
                    end[base + 5] = ChatItem.StatusBanner("Bearbeitung abgeschlossen.", active = false)
                    end += ChatItem.AssistantText("Ich habe die gewünschte Änderung vorgenommen.")
                    end += ChatItem.ResultImage(resultBytes)
                    items = end
                    busy = false
                }
            } catch (e: Exception) {
                val err = items.toMutableList()
                if (err.size >= base + 6) {
                    err[base + 5] = ChatItem.StatusBanner("Bearbeitung fehlgeschlagen.", active = false)
                    err += ChatItem.Error(e.message ?: "Unbekannter Fehler")
                    items = err
                    busy = false
                }
            }
        }
    }
}

private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/png"
}

private fun chooseImageFiles(): List<File> {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = true
        fileFilter = FileNameExtensionFilter("Bilder", "png", "jpg", "jpeg", "webp", "gif")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.toList()
    } else {
        emptyList()
    }
}

private fun chooseSaveFile(): File? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        selectedFile = File("agnes-edit.png")
    }
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

@Composable
private fun rememberBitmap(bytes: ByteArray?): ImageBitmap? = remember(bytes) {
    bytes?.let { bytesToImageBitmap(it) }
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val initial = remember { loadSettings() }
    val state = remember { StudioState(scope, initial.apiKey, initial.baseUrl) }

    var showSettings by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var fullscreenBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    MaterialTheme(colorScheme = DarkColors) {
        Scaffold(
            containerColor = AppBackground,
            topBar = {
                TopBar(
                    title = state.title,
                    onSettings = { showSettings = true },
                    onNewChat = { state.reset() },
                )
            },
            bottomBar = {
                Column {
                    ModePills(mode = state.mode, onMode = { state.setMode(it) })
                    InputBar(
                        input = state.input,
                        onInput = { state.input = it },
                        attachments = state.attachments,
                        onAttach = { chooseImageFiles().let { if (it.isNotEmpty()) state.addFiles(it) } },
                        onAddUrl = { showUrlDialog = true },
                        onRemoveAttachment = { state.removeAttachment(it) },
                        onSend = { state.submit() },
                    )
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.items.isEmpty()) {
                    item {
                        EmptyState(
                            missingKey = state.apiKey.isBlank(),
                            onPick = { chooseImageFiles().let { if (it.isNotEmpty()) state.addFiles(it) } },
                        )
                    }
                }
                items(state.items) { item ->
                    when (item) {
                        is ChatItem.UserMessage -> UserMessageItem(item)
                        is ChatItem.ThoughtGroup -> ThoughtGroupItem(item)
                        is ChatItem.AssistantText -> AssistantTextItem(item)
                        is ChatItem.PromptEnhancement -> PromptEnhancementItem(item)
                        is ChatItem.StatusBanner -> StatusBannerItem(item)
                        is ChatItem.ResultImage -> ResultImageItem(
                            item = item,
                            onSave = { chooseSaveFile()?.let { f -> saveBytes(item.bytes, f) } },
                            onContinue = { state.useAsInput(item.bytes) },
                            onZoom = { bytesToImageBitmap(item.bytes)?.let { fullscreenBitmap = it } },
                        )
                        is ChatItem.Error -> ErrorItem(item)
                    }
                }
                if (state.busy) {
                    item { BusyRow() }
                }
            }
        }

        fullscreenBitmap?.let { bmp ->
            FullscreenImageDialog(bitmap = bmp, onDismiss = { fullscreenBitmap = null })
        }
        if (showUrlDialog) {
            UrlInputDialog(
                onConfirm = { state.addUrl(it); showUrlDialog = false },
                onDismiss = { showUrlDialog = false },
            )
        }
        if (showSettings) {
            SettingsDialog(
                apiKey = state.apiKey,
                baseUrl = state.baseUrl,
                onSave = { k, b -> state.updateSettings(k, b); showSettings = false },
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun TopBar(title: String, onSettings: () -> Unit, onNewChat: () -> Unit) {
    Surface(color = AppBackground) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Menu, "Einstellungen", tint = TextPrimary)
            }
            Text(
                title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNewChat) {
                Icon(Icons.Filled.Add, "Neuer Chat", tint = TextPrimary)
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit, missingKey: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = CardBackground, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, tint = TextPrimary, modifier = Modifier.size(40.dp))
            }
        }
        Text("Agnes AI Image Studio", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Wähle ein Bild und beschreibe, was geändert werden soll.", color = TextMuted, fontSize = 14.sp)
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, onClick = onPick) {
            Text(
                "Bilder auswählen",
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
        if (missingKey) {
            Text(
                "Kein API-Key gesetzt — öffne oben das Menü (≡) und trage ihn ein.",
                color = ErrorRed,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun UserMessageItem(item: ChatItem.UserMessage) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = Color.White,
        ) {
            Text(
                item.text,
                color = Color.Black,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (item.images.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.images.forEach { att -> UserImageThumb(att) }
            }
        }
    }
}

@Composable
private fun UserImageThumb(att: Attachment) {
    AttachmentImage(
        att = att,
        modifier = Modifier
            .size(width = 140.dp, height = 180.dp)
            .clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun AttachmentImage(
    att: Attachment,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    when (att) {
        is Attachment.Local -> {
            val bmp = rememberBitmap(att.bytes)
            if (bmp != null) {
                Image(bmp, "Bild", modifier = modifier, contentScale = contentScale)
            }
        }
        is Attachment.Remote -> {
            var bytes by remember(att.url) { mutableStateOf<ByteArray?>(null) }
            LaunchedEffect(att.url) {
                bytes = try {
                    fetchBytes(att.url)
                } catch (_: Exception) {
                    null
                }
            }
            val bmp = rememberBitmap(bytes)
            if (bmp != null) {
                Image(bmp, "Bild", modifier = modifier, contentScale = contentScale)
            } else {
                Box(modifier = modifier.background(CardBackground2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Image, null, tint = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun ThoughtGroupItem(item: ChatItem.ThoughtGroup) {
    var expanded by remember { mutableStateOf(item.expanded) }
    Surface(shape = RoundedCornerShape(16.dp), color = CardBackground, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Memory, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Thoughts complete (Takes ${item.durationSeconds} seconds)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                        null,
                        tint = TextMuted,
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                item.skills.forEach { skill ->
                    Spacer(Modifier.height(6.dp))
                    SkillRow(skill)
                }
            }
        }
    }
}

@Composable
private fun SkillRow(skill: LoadedSkill) {
    Surface(shape = RoundedCornerShape(12.dp), color = CardBackground2, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoStories, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Load Skill", color = TextPrimary, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = Color.White) {
                    Text(
                        skill.badge,
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Loaded ${skill.chars} Chars", color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AssistantTextItem(item: ChatItem.AssistantText) {
    Surface(shape = RoundedCornerShape(16.dp), color = CardBackground, modifier = Modifier.fillMaxWidth()) {
        Text(item.text, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun PromptEnhancementItem(item: ChatItem.PromptEnhancement) {
    Surface(shape = RoundedCornerShape(16.dp), color = CardBackground, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.AutoAwesome, null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("Prompt Enhancement", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(2.dp))
            Text("Review the enhanced prompt before designing", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            PromptBlock("Original prompt", item.original, Icons.Filled.PhotoLibrary)
            Spacer(Modifier.height(8.dp))
            PromptBlock("Enhanced prompt", item.enhanced.ifBlank { "…" }, Icons.Filled.AutoAwesome)
        }
    }
}

@Composable
private fun PromptBlock(label: String, text: String, icon: ImageVector) {
    Surface(shape = RoundedCornerShape(12.dp), color = CardBackground2, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = TextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(text, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun StatusBannerItem(item: ChatItem.StatusBanner) {
    Surface(shape = RoundedCornerShape(14.dp), color = CardBackground2, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (item.active) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SparkleCyan)
            } else {
                Icon(Icons.Filled.Check, null, tint = TealBadge, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(item.text, color = TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ResultImageItem(
    item: ChatItem.ResultImage,
    onSave: () -> Unit,
    onContinue: () -> Unit,
    onZoom: () -> Unit,
) {
    val bmp = rememberBitmap(item.bytes)
    if (bmp != null) {
        Box {
            Image(
                bmp,
                "Ergebnis",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ResultActionButton(Icons.Filled.Edit, "Weiter bearbeiten", onContinue)
                ResultActionButton(Icons.Filled.Fullscreen, "Vollbild", onZoom)
                ResultActionButton(Icons.Filled.Download, "Herunterladen", onSave)
            }
        }
    }
}

@Composable
private fun ResultActionButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(44.dp), onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = Color.Black)
        }
    }
}

@Composable
private fun ErrorItem(item: ChatItem.Error) {
    Surface(shape = RoundedCornerShape(16.dp), color = CardBackground, modifier = Modifier.fillMaxWidth()) {
        Text(item.message, color = ErrorRed, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun FullscreenImageDialog(bitmap: ImageBitmap, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 6f)
        scale = newScale
        offset = if (newScale <= 1f) Offset.Zero else offset + panChange
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Image(
            bitmap,
            "Ergebnis Vollbild",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                )
                .transformable(transformState),
            contentScale = ContentScale.Fit,
        )
        Surface(
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(44.dp),
            onClick = onDismiss,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Schließen", tint = Color.Black)
            }
        }
    }
}

@Composable
private fun BusyRow() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SparkleCyan)
        Spacer(Modifier.width(10.dp))
        Text("Agnes arbeitet …", color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun ModePills(mode: String, onMode: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(Icons.Filled.Image, "Edit Image", mode == "edit") { onMode("edit") }
        Pill(Icons.Filled.Person, "Full Body", mode == "full_body") { onMode("full_body") }
        Pill(Icons.Filled.Palette, "Try-On", false) {}
        Pill(Icons.Filled.AutoFixHigh, "Enhance", mode == "enhance") { onMode("enhance") }
        Pill(Icons.Filled.DarkMode, "Black BG", mode == "black_bg") { onMode("black_bg") }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val color = if (selected) Color.White else CardBackground
    Surface(shape = shape, color = color, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(icon, null, tint = if (selected) Color.Black else TextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (selected) Color.Black else TextPrimary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AttachmentThumb(att: Attachment, onRemove: () -> Unit) {
    Box {
        AttachmentImage(
            att = att,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp),
            onClick = onRemove,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Entfernen", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    onInput: (String) -> Unit,
    attachments: List<Attachment>,
    onAttach: () -> Unit,
    onAddUrl: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onSend: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEachIndexed { index, att ->
                    AttachmentThumb(att) { onRemoveAttachment(index) }
                }
            }
        }

        Surface(shape = RoundedCornerShape(26.dp), color = CardBackground, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAttach) {
                    Icon(Icons.Filled.Add, "Bilder hinzufügen", tint = TextPrimary)
                }
                IconButton(onClick = onAddUrl) {
                    Icon(Icons.Filled.Link, "Bild per URL", tint = TextPrimary)
                }
                TextField(
                    value = input,
                    onValueChange = onInput,
                    placeholder = { Text("Prompt eingeben…", color = TextMuted) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = TextPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                IconButton(onClick = onSend) {
                    Icon(Icons.Filled.Send, "Senden", tint = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun UrlInputDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = CardBackground, modifier = Modifier.width(360.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bild per URL hinzufügen", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("https://…", color = TextMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBackground2,
                        unfocusedContainerColor = CardBackground2,
                        disabledContainerColor = CardBackground2,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = TextPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = CardBackground2, modifier = Modifier.weight(1f), onClick = onDismiss) {
                        Text("Abbrechen", color = TextPrimary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.weight(1f), onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
                        Text("Hinzufügen", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    apiKey: String,
    baseUrl: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf(apiKey) }
    var base by remember { mutableStateOf(baseUrl) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = CardBackground, modifier = Modifier.width(360.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Einstellungen", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextField(
                    value = key,
                    onValueChange = { key = it },
                    placeholder = { Text("AGNES_API_KEY", color = TextMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBackground2,
                        unfocusedContainerColor = CardBackground2,
                        disabledContainerColor = CardBackground2,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = TextPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )
                TextField(
                    value = base,
                    onValueChange = { base = it },
                    placeholder = { Text(DEFAULT_BASE_URL, color = TextMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBackground2,
                        unfocusedContainerColor = CardBackground2,
                        disabledContainerColor = CardBackground2,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = TextPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = CardBackground2, modifier = Modifier.weight(1f), onClick = onDismiss) {
                        Text("Abbrechen", color = TextPrimary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.weight(1f), onClick = { onSave(key.trim(), base.trim()) }) {
                        Text("Speichern", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}
