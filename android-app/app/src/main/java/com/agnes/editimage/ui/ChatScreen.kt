package com.agnes.editimage.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agnes.editimage.ui.theme.AppBackground
import com.agnes.editimage.ui.theme.CardBackground
import com.agnes.editimage.ui.theme.CardBackground2
import com.agnes.editimage.ui.theme.ErrorRed
import com.agnes.editimage.ui.theme.SparkleCyan
import com.agnes.editimage.ui.theme.TealBadge
import com.agnes.editimage.ui.theme.TextMuted
import com.agnes.editimage.ui.theme.TextPrimary
import com.agnes.editimage.util.decodeBitmap
import com.agnes.editimage.util.fetchBytes
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
    var fullscreenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris -> if (uris.isNotEmpty()) viewModel.addImages(uris) }

    fun launchPicker() {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        containerColor = AppBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = state.title,
                onMenu = onOpenSettings,
                onNewChat = viewModel::reset,
            )
        },
        bottomBar = {
            Column {
                ActionPills(mode = state.mode, onMode = viewModel::setMode)
                InputBar(
                    input = state.input,
                    onInput = viewModel::setInput,
                    attachments = state.attachments,
                    onAttach = ::launchPicker,
                    onAddUrl = { showUrlDialog = true },
                    onRemoveAttachment = viewModel::removeAttachment,
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
                    is ChatItem.ResultImage -> ResultImageItem(
                        item = item,
                        onDownload = { bytes ->
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
                        },
                        onContinue = { viewModel.useAsInput(item.bytes) },
                        onZoom = {
                            decodeBitmap(item.bytes)?.let { fullscreenBitmap = it }
                        },
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
            onConfirm = { viewModel.addImageUrl(it); showUrlDialog = false },
            onDismiss = { showUrlDialog = false },
        )
    }
}

@Composable
private fun AppHeader(title: String, onMenu: () -> Unit, onNewChat: () -> Unit) {
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
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNewChat) {
                Icon(Icons.Filled.Add, contentDescription = "Neuer Chat", tint = TextPrimary)
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
            color = Color.White,
            onClick = onPick,
        ) {
            Text(
                "Bild auswählen",
                color = Color.Black,
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
                for (att in item.images) {
                    UserImageThumb(att)
                }
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
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Bild",
                    modifier = modifier,
                    contentScale = contentScale,
                )
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
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Bild",
                    modifier = modifier,
                    contentScale = contentScale,
                )
            } else {
                Box(
                    modifier = modifier.background(CardBackground2),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = TextMuted)
                }
            }
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
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
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
            if (item.active) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SparkleCyan)
            } else {
                Icon(Icons.Filled.Check, contentDescription = null, tint = TealBadge, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(item.text, color = TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ResultImageItem(
    item: ChatItem.ResultImage,
    onDownload: (ByteArray) -> Unit,
    onContinue: () -> Unit,
    onZoom: () -> Unit,
) {
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
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ResultActionButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = "Weiter bearbeiten",
                    onClick = onContinue,
                )
                ResultActionButton(
                    icon = Icons.Filled.Fullscreen,
                    contentDescription = "Vollbild",
                    onClick = onZoom,
                )
                ResultActionButton(
                    icon = Icons.Filled.Download,
                    contentDescription = "Herunterladen",
                    onClick = { onDownload(item.bytes) },
                )
            }
        }
    }
}

@Composable
private fun ResultActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        modifier = Modifier.size(44.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = Color.Black)
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
private fun FullscreenImageDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
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
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Ergebnis Vollbild",
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
                    Icon(Icons.Filled.Close, contentDescription = "Schließen", tint = Color.Black)
                }
            }
        }
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
private fun ActionPills(mode: String, onMode: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(icon = Icons.Filled.Image, label = "Edit Image", selected = mode == "edit", onClick = { onMode("edit") })
        Pill(icon = Icons.Filled.Person, label = "Full Body", selected = mode == "full_body", onClick = { onMode("full_body") })
        Pill(icon = Icons.Filled.Palette, label = "Try-On", selected = false, onClick = null)
        Pill(icon = Icons.Filled.AutoFixHigh, label = "Enhance", selected = mode == "enhance", onClick = { onMode("enhance") })
        Pill(icon = Icons.Filled.DarkMode, label = "Black BG", selected = mode == "black_bg", onClick = { onMode("black_bg") })
    }
}

@Composable
private fun Pill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(18.dp)
    val color = if (selected) Color.White else CardBackground
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.Black else TextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (selected) Color.Black else TextPrimary, fontSize = 13.sp)
        }
    }
    if (onClick != null) {
        Surface(shape = shape, color = color, onClick = onClick) { content() }
    } else {
        Surface(shape = shape, color = color) { content() }
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
                Icon(Icons.Filled.Close, contentDescription = "Entfernen", tint = Color.White, modifier = Modifier.size(12.dp))
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
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((index, att) in attachments.withIndex()) {
                    AttachmentThumb(att = att, onRemove = { onRemoveAttachment(index) })
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
                IconButton(onClick = onAddUrl) {
                    Icon(Icons.Filled.Link, contentDescription = "Bild per URL hinzufügen", tint = TextPrimary)
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                IconButton(onClick = onSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden", tint = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun UrlInputDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardBackground,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Bild per URL hinzufügen",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackground2,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    ) {
                        Text(
                            "Abbrechen",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                    ) {
                        Text(
                            "Hinzufügen",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
