package com.agnes.editimage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agnes.editimage.ui.EditImageScreen
import com.agnes.editimage.ui.EditImageViewModel
import com.agnes.editimage.ui.theme.AgnesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgnesTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    val viewModel: EditImageViewModel = viewModel()
    var showSettings by remember { mutableStateOf(false) }

    EditImageScreen(viewModel = viewModel, onOpenSettings = { showSettings = true })

    if (showSettings) {
        SettingsDialog(viewModel = viewModel, onDismiss = { showSettings = false })
    }
}

@Composable
private fun SettingsDialog(viewModel: EditImageViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var apiKey by remember { mutableStateOf(state.savedApiKey) }
    var baseUrl by remember { mutableStateOf(state.savedBaseUrl) }
    var keyVisible by remember { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Einstellungen") },
        text = {
            Column {
                if (justSaved) {
                    Text(
                        "Gespeichert ✓",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                } else if (state.apiKeyConfigured) {
                    Text(
                        "API-Key ist konfiguriert ✓",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    "Agnes API Key (wird nur auf diesem Gerät gespeichert):",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("sk-…") },
                    visualTransformation = if (keyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (keyVisible) "Key verbergen" else "Key anzeigen",
                            )
                        }
                    },
                )

                Text(
                    "Base URL:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://apihub.agnes-ai.com/v1") },
                )

                if (!state.apiKeyConfigured && apiKey.isBlank()) {
                    Text(
                        "Kein API Key gesetzt. Trage einen Key ein, damit die Bildbearbeitung funktioniert.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (justSaved) {
                    onDismiss()
                } else {
                    viewModel.updateSettings(apiKey.trim(), baseUrl.trim())
                    justSaved = true
                }
            }) { Text(if (justSaved) "Schließen" else "Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}
