package com.agnes.editimage.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agnes.editimage.ui.theme.AppBackground
import com.agnes.editimage.ui.theme.CardBackground
import com.agnes.editimage.ui.theme.CardBackground2
import com.agnes.editimage.ui.theme.ErrorRed
import com.agnes.editimage.ui.theme.SparkleCyan
import com.agnes.editimage.ui.theme.TealBadge
import com.agnes.editimage.ui.theme.TextMuted
import com.agnes.editimage.ui.theme.TextPrimary
import com.agnes.editimage.ui.theme.UserBubble
import com.agnes.editimage.util.decodeBitmap
import com.agnes.editimage.util.saveToGallery
import kotlinx.coroutines.launch

@Composable
fun rememberBitmap(bytes: ByteArray?): Bitmap? = remember(bytes) {
    bytes?.let { decodeBitmap(it) }
}

@Composable
fun EditImageScreen(viewModel: EditImageViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.attachImage(uri) }

    fun launchPicker() {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        containerColor = AppBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AppHeader(title = state.title, onMenu = onOpenSettings) },
        bottomBar = {
            Column {
                ActionPills()
                InputBar(
                    input = state.input,
                    onInput = viewModel::setInput,
                    attachedBytes = state.attachedImage,
                    onAttach = ::launchPicker,
                    onClearAttachment = viewModel::clearAttachment,
                    onSend = viewModel::submit,
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
                    EmptyState(onPick = ::launchPicker)
                }
            }
            items(state.items) { item ->
                when (item) {
                    is ChatItem.UserMessage -> UserMessageItem(item)
                    is ChatItem.ThoughtGroup -> ThoughtGroupItem(item)
                    is ChatItem.AssistantText -> AssistantTextItem(item)
                    is ChatItem.PromptEnhancement -> PromptEnhancementItem(item)
                    is ChatItem.StatusBanner -> StatusBannerItem(item)
                    is ChatItem.ResultImage -> ResultImageItem(item, onDownload = { bytes ->
                        val bmp = decodeBitmap(bytes)
                        if (bmp != null) {
                            val saved = saveToGallery(context, bmp)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (saved) "Bild gespeichert" else "Speichern fehlgeschlagen"
                                )
                            }
                            viewModel.markSaved()
                        }
                    })
                    is ChatItem.Error -> ErrorItem(item)
                }
            }
            if (state.busy) {
                item { BusyRow() }
            }
        }
    }
}

@Composable
private fun AppHeader(title: String, onMenu: () -> Unit) {
    Surface(color = AppBackground) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Menü", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Generated by AI",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = CardBackground,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SparkleCyan, modifier = Modifier.size(40.dp))
            }
        }
        Text("Edit Image", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Wähle ein Bild und beschreibe, was geändert werden soll.",
            color = TextMuted,
            fontSize = 14.sp,
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = UserBubble,
            onClick = onPick,
        ) {
            Text(
                "Bild auswählen",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun UserMessageItem(item: ChatItem.UserMessage) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
            color = UserBubble,
        ) {
            Text(
                item.text,
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        val bmp = rememberBitmap(item.imageBytes)
        if (bmp != null) {
            Spacer(Modifier.height(8.dp))
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Bild",
                modifier = Modifier
                    .size(width = 140.dp, height = 180.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun ThoughtGroupItem(item: ChatItem.ThoughtGroup) {
    var expanded by remember { mutableStateOf(item.expanded) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Memory, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Thoughts complete (Takes ${item.durationSeconds} seconds)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
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
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardBackground2,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoStories, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Load Skill", color = TextPrimary, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = TealBadge) {
                    Text(
                        skill.badge,
                        color = Color(0xFF00332E),
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            item.text,
            color = TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun PromptEnhancementItem(item: ChatItem.PromptEnhancement) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SparkleCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SparkleCyan, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("Prompt Enhancement", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(2.dp))
            Text("Review the enhanced prompt before designing", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            PromptBlock(label = "Original prompt", text = item.original, icon = Icons.Filled.PhotoLibrary)
            Spacer(Modifier.height(8.dp))
            PromptBlock(label = "Enhanced prompt", text = item.enhanced.ifBlank { "…" }, icon = Icons.Filled.AutoAwesome)
        }
    }
}

@Composable
private fun PromptBlock(label: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardBackground2,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
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
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardBackground2,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SparkleCyan)
            Spacer(Modifier.width(10.dp))
            Text(item.text, color = TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ResultImageItem(item: ChatItem.ResultImage, onDownload: (ByteArray) -> Unit) {
    val bmp = rememberBitmap(item.bytes)
    if (bmp != null) {
        Box {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Ergebnis",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
            )
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(44.dp),
                onClick = { onDownload(item.bytes) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Download, contentDescription = "Herunterladen", tint = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun ErrorItem(item: ChatItem.Error) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            item.message,
            color = ErrorRed,
            fontSize = 13.sp,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun BusyRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SparkleCyan)
        Spacer(Modifier.width(10.dp))
        Text("Agnes arbeitet …", color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun ActionPills() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(icon = Icons.Filled.Image, label = "to Video", selected = false)
        Pill(icon = Icons.Filled.Image, label = "Edit Image", selected = true)
        Pill(icon = Icons.Filled.Palette, label = "Photo design", selected = false)
    }
}

@Composable
private fun Pill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) UserBubble else CardBackground,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else TextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (selected) Color.White else TextPrimary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    onInput: (String) -> Unit,
    attachedBytes: ByteArray?,
    onAttach: () -> Unit,
    onClearAttachment: () -> Unit,
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        val attachedBmp = rememberBitmap(attachedBytes)
        if (attachedBmp != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Image(
                    bitmap = attachedBmp.asImageBitmap(),
                    contentDescription = "Anhang",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(8.dp))
                Text("Bild angehängt – beschreibe die Änderung.", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClearAttachment, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Entfernen", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = CardBackground,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAttach) {
                    Icon(Icons.Filled.Add, contentDescription = "Bild hinzufügen", tint = TextPrimary)
                }
                TextField(
                    value = input,
                    onValueChange = onInput,
                    placeholder = { Text("Type or hold to talk", color = TextMuted) },
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                IconButton(onClick = onSend) {
                    Icon(Icons.Filled.Mic, contentDescription = "Senden", tint = TextPrimary)
                }
            }
        }
    }
}
