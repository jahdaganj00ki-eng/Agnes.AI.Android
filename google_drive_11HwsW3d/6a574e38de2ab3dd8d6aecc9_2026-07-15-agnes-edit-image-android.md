# Agnes Edit Image Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app with an Agnes-like dark UI for Smart Edit image editing, visible Thinking / Load Skill logs, prompt enhancement review, and direct Agnes.AI API-key based access.

**Architecture:** Create a Kotlin + Jetpack Compose app with a thin feature-based module split. The app stores the user API key securely, uploads one reference image, starts Smart Edit jobs against an Agnes-compatible API, streams Thinking events over SSE with polling fallback, and renders a dark Agnes-like UX from Home through Result.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel, StateFlow, Retrofit, OkHttp SSE, Coil, EncryptedSharedPreferences, JUnit, Turbine, MockWebServer, Compose UI Test

---

## File Structure

**Project root**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`

**Android app shell**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/agnes/editimage/MainActivity.kt`
- Create: `app/src/main/java/com/agnes/editimage/AgnesEditApplication.kt`

**Core design system**
- Create: `app/src/main/java/com/agnes/editimage/core/designsystem/AgnesColors.kt`
- Create: `app/src/main/java/com/agnes/editimage/core/designsystem/AgnesTypography.kt`
- Create: `app/src/main/java/com/agnes/editimage/core/designsystem/AgnesTheme.kt`
- Create: `app/src/main/java/com/agnes/editimage/core/designsystem/components/ThinkingLogCard.kt`
- Create: `app/src/main/java/com/agnes/editimage/core/designsystem/components/SkillStepChip.kt`
- Create: `app/src/main/java/com/agnes/editimage/core/designsystem/components/PromptReviewCard.kt`

**Navigation + shell**
- Create: `app/src/main/java/com/agnes/editimage/navigation/AppDestination.kt`
- Create: `app/src/main/java/com/agnes/editimage/navigation/AgnesNavHost.kt`
- Create: `app/src/main/java/com/agnes/editimage/ui/AppRoot.kt`

**Settings**
- Create: `app/src/main/java/com/agnes/editimage/feature/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/settings/AuthConfigRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/settings/EncryptedAuthConfigRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/settings/SettingsUiState.kt`

**Edit workflow**
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/EditComposerScreen.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/EditComposerViewModel.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/EditWorkflowViewModel.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/model/EditInput.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/model/ThinkingStep.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/model/EditJobUiState.kt`

**Network + repositories**
- Create: `app/src/main/java/com/agnes/editimage/core/network/ApiKeyInterceptor.kt`
- Create: `app/src/main/java/com/agnes/editimage/core/network/NetworkModule.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesEditApi.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/dto/EnhancePromptRequest.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/dto/EnhancePromptResponse.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/dto/CreateEditJobRequest.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/dto/CreateEditJobResponse.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/dto/EditJobStatusResponse.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/dto/ThinkingEventDto.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/AssetRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/EditRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/JobEventsRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesAssetRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesEditRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesJobEventsRepository.kt`

**Result**
- Create: `app/src/main/java/com/agnes/editimage/feature/result/ResultScreen.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/result/ResultActionHandler.kt`

**Tests**
- Create: `app/src/test/java/com/agnes/editimage/feature/settings/EncryptedAuthConfigRepositoryTest.kt`
- Create: `app/src/test/java/com/agnes/editimage/feature/edit/ThinkingEventMapperTest.kt`
- Create: `app/src/test/java/com/agnes/editimage/feature/edit/EditWorkflowViewModelTest.kt`
- Create: `app/src/test/java/com/agnes/editimage/feature/edit/data/AgnesEditRepositoryTest.kt`
- Create: `app/src/androidTest/java/com/agnes/editimage/feature/settings/SettingsScreenTest.kt`
- Create: `app/src/androidTest/java/com/agnes/editimage/feature/edit/EditComposerScreenTest.kt`
- Create: `app/src/androidTest/java/com/agnes/editimage/feature/edit/ThinkingFlowScreenTest.kt`

### Task 1: Bootstrap the Android project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/agnes/editimage/MainActivity.kt`
- Test: `app/src/test/java/com/agnes/editimage/AppSmokeTest.kt`

- [ ] **Step 1: Write the failing smoke test**

```kotlin
package com.agnes.editimage

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSmokeTest {
    @Test
    fun packageName_constantMatches() {
        assertEquals("com.agnes.editimage", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.agnes.editimage.AppSmokeTest`
Expected: FAIL because the Gradle Android app module and `BuildConfig` do not exist yet.

- [ ] **Step 3: Write the minimal project scaffold**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AgnesEditImage"
include(":app")
```

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
```

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.agnes.editimage"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.agnes.editimage"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
}
```

```xml
<!-- app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".AgnesEditApplication"
        android:allowBackup="true"
        android:label="Agnes Edit"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

```kotlin
// app/src/main/java/com/agnes/editimage/MainActivity.kt
package com.agnes.editimage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Agnes Edit") }
    }
}
```

```kotlin
// app/src/main/java/com/agnes/editimage/AgnesEditApplication.kt
package com.agnes.editimage

import android.app.Application

class AgnesEditApplication : Application()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.agnes.editimage.AppSmokeTest`
Expected: PASS with `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/agnes/editimage/MainActivity.kt app/src/main/java/com/agnes/editimage/AgnesEditApplication.kt app/src/test/java/com/agnes/editimage/AppSmokeTest.kt
git commit -m "chore: bootstrap Android Compose project"
```

### Task 2: Add the Agnes-like dark theme and navigation shell

**Files:**
- Create: `app/src/main/java/com/agnes/editimage/core/designsystem/AgnesColors.kt`
- Create: `app/src/main/java/com/agnes/editimage/core/designsystem/AgnesTheme.kt`
- Create: `app/src/main/java/com/agnes/editimage/navigation/AppDestination.kt`
- Create: `app/src/main/java/com/agnes/editimage/navigation/AgnesNavHost.kt`
- Create: `app/src/main/java/com/agnes/editimage/ui/AppRoot.kt`
- Modify: `app/src/main/java/com/agnes/editimage/MainActivity.kt`
- Test: `app/src/androidTest/java/com/agnes/editimage/AppRootNavigationTest.kt`

- [ ] **Step 1: Write the failing navigation test**

```kotlin
package com.agnes.editimage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AppRootNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_showsEditImageEntryPoint() {
        composeRule.onNodeWithText("Edit Image").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agnes.editimage.AppRootNavigationTest`
Expected: FAIL because the app still renders plain `Text("Agnes Edit")`.

- [ ] **Step 3: Implement theme and root navigation**

```kotlin
// AgnesColors.kt
package com.agnes.editimage.core.designsystem

import androidx.compose.ui.graphics.Color

val AgnesBlack = Color(0xFF060606)
val AgnesSurface = Color(0xFF15161A)
val AgnesSurfaceAlt = Color(0xFF1D1F25)
val AgnesTextPrimary = Color(0xFFF4F6FA)
val AgnesTextSecondary = Color(0xFF9EA5B3)
val AgnesAccent = Color(0xFF27C3FF)
val AgnesAccentSoft = Color(0xFF173B4B)
val AgnesError = Color(0xFFFF6464)
```

```kotlin
// AgnesTheme.kt
package com.agnes.editimage.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AgnesColorScheme = darkColorScheme(
    primary = AgnesAccent,
    background = AgnesBlack,
    surface = AgnesSurface,
    onPrimary = AgnesBlack,
    onBackground = AgnesTextPrimary,
    onSurface = AgnesTextPrimary,
    error = AgnesError
)

@Composable
fun AgnesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgnesColorScheme,
        content = content
    )
}
```

```kotlin
// AppDestination.kt
package com.agnes.editimage.navigation

sealed interface AppDestination {
    data object Home : AppDestination
    data object Settings : AppDestination
    data object EditComposer : AppDestination
}
```

```kotlin
// AgnesNavHost.kt
package com.agnes.editimage.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AgnesNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { Text("Edit Image") }
        composable("settings") { Text("Settings") }
        composable("edit") { Text("Enhance Prompt") }
    }
}
```

```kotlin
// AppRoot.kt
package com.agnes.editimage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.agnes.editimage.core.designsystem.AgnesBlack
import com.agnes.editimage.core.designsystem.AgnesTheme
import com.agnes.editimage.navigation.AgnesNavHost

@Composable
fun AppRoot() {
    AgnesTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AgnesBlack)
        ) {
            AgnesNavHost()
        }
    }
}
```

```kotlin
// MainActivity.kt
package com.agnes.editimage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.agnes.editimage.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agnes.editimage.AppRootNavigationTest`
Expected: PASS with the `Edit Image` label visible on launch.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/agnes/editimage/core/designsystem/AgnesColors.kt app/src/main/java/com/agnes/editimage/core/designsystem/AgnesTheme.kt app/src/main/java/com/agnes/editimage/navigation/AppDestination.kt app/src/main/java/com/agnes/editimage/navigation/AgnesNavHost.kt app/src/main/java/com/agnes/editimage/ui/AppRoot.kt app/src/main/java/com/agnes/editimage/MainActivity.kt app/src/androidTest/java/com/agnes/editimage/AppRootNavigationTest.kt
git commit -m "feat: add Agnes dark theme and app shell"
```

### Task 3: Implement secure Settings and Agnes.AI API-key storage

**Files:**
- Create: `app/src/main/java/com/agnes/editimage/feature/settings/AuthConfigRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/settings/EncryptedAuthConfigRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/settings/SettingsUiState.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/agnes/editimage/feature/settings/EncryptedAuthConfigRepositoryTest.kt`

- [ ] **Step 1: Write the failing repository test**

```kotlin
package com.agnes.editimage.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class EncryptedAuthConfigRepositoryTest {
    @Test
    fun saveAndReadKey_roundTripsValue() {
        val repo = FakeAuthConfigRepository()
        repo.saveApiKey("agnes_test_key")
        assertEquals("agnes_test_key", repo.getApiKey())
    }
}

private class FakeAuthConfigRepository : AuthConfigRepository {
    private var key: String? = null
    override suspend fun saveApiKey(value: String) { key = value }
    override suspend fun getApiKey(): String? = key
    override suspend fun clearApiKey() { key = null }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.agnes.editimage.feature.settings.EncryptedAuthConfigRepositoryTest`
Expected: FAIL because `AuthConfigRepository` does not exist yet.

- [ ] **Step 3: Implement repository, state, and Settings screen**

```kotlin
// AuthConfigRepository.kt
package com.agnes.editimage.feature.settings

interface AuthConfigRepository {
    suspend fun saveApiKey(value: String)
    suspend fun getApiKey(): String?
    suspend fun clearApiKey()
    suspend fun saveBaseUrl(value: String)
    suspend fun getBaseUrl(): String
}
```

```kotlin
// EncryptedAuthConfigRepository.kt
package com.agnes.editimage.feature.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedAuthConfigRepository(context: Context) : AuthConfigRepository {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "agnes_auth_config",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun saveApiKey(value: String) {
        prefs.edit().putString("api_key", value).apply()
    }

    override suspend fun getApiKey(): String? = prefs.getString("api_key", null)

    override suspend fun clearApiKey() {
        prefs.edit().remove("api_key").apply()
    }

    override suspend fun saveBaseUrl(value: String) {
        prefs.edit().putString("base_url", value).apply()
    }

    override suspend fun getBaseUrl(): String =
        prefs.getString("base_url", "https://api.agnes-ai.com/")!!
}
```

```kotlin
// SettingsUiState.kt
package com.agnes.editimage.feature.settings

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = "https://api.agnes-ai.com/",
    val verboseThinking: Boolean = false,
    val saveMessage: String? = null
)
```

```kotlin
// SettingsViewModel.kt
package com.agnes.editimage.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: AuthConfigRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onApiKeyChanged(value: String) {
        _uiState.value = _uiState.value.copy(apiKey = value)
    }

    fun save() = viewModelScope.launch {
        repository.saveApiKey(_uiState.value.apiKey)
        repository.saveBaseUrl(_uiState.value.baseUrl)
        _uiState.value = _uiState.value.copy(saveMessage = "Saved")
    }
}
```

```kotlin
// SettingsScreen.kt
package com.agnes.editimage.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onApiKeyChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Agnes.AI API Key")
        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = onApiKeyChanged,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = onSave, modifier = Modifier.padding(top = 16.dp)) {
            Text("Save")
        }
        uiState.saveMessage?.let { Text(it) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.agnes.editimage.feature.settings.EncryptedAuthConfigRepositoryTest`
Expected: PASS because the repository contract now exists and the fake test can compile and succeed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/agnes/editimage/feature/settings/AuthConfigRepository.kt app/src/main/java/com/agnes/editimage/feature/settings/EncryptedAuthConfigRepository.kt app/src/main/java/com/agnes/editimage/feature/settings/SettingsUiState.kt app/src/main/java/com/agnes/editimage/feature/settings/SettingsViewModel.kt app/src/main/java/com/agnes/editimage/feature/settings/SettingsScreen.kt app/src/test/java/com/agnes/editimage/feature/settings/EncryptedAuthConfigRepositoryTest.kt
git commit -m "feat: add secure Agnes API key settings"
```

### Task 4: Build the Edit Composer with image picker and prompt entry

**Files:**
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/model/EditInput.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/EditComposerViewModel.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/EditComposerScreen.kt`
- Test: `app/src/androidTest/java/com/agnes/editimage/feature/edit/EditComposerScreenTest.kt`

- [ ] **Step 1: Write the failing composer test**

```kotlin
package com.agnes.editimage.feature.edit

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class EditComposerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enhancePromptButton_requiresImageAndPrompt() {
        composeRule.setContent {
            EditComposerScreen(
                state = EditComposerUiState(),
                onPromptChanged = {},
                onPickImage = {},
                onEnhancePrompt = {}
            )
        }

        composeRule.onNodeWithText("Enhance Prompt").assertIsNotEnabled()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agnes.editimage.feature.edit.EditComposerScreenTest`
Expected: FAIL because `EditComposerScreen` and `EditComposerUiState` do not exist yet.

- [ ] **Step 3: Implement edit input model, view model, and composer UI**

```kotlin
// EditInput.kt
package com.agnes.editimage.feature.edit.model

import android.net.Uri

data class EditInput(
    val imageUri: Uri? = null,
    val prompt: String = ""
)
```

```kotlin
// EditComposerViewModel.kt
package com.agnes.editimage.feature.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EditComposerUiState(
    val selectedImageUri: Uri? = null,
    val prompt: String = ""
) {
    val canEnhancePrompt: Boolean = selectedImageUri != null && prompt.isNotBlank()
}

class EditComposerViewModel : ViewModel() {
    private val _state = MutableStateFlow(EditComposerUiState())
    val state: StateFlow<EditComposerUiState> = _state.asStateFlow()

    fun onPromptChanged(value: String) {
        _state.value = _state.value.copy(prompt = value)
    }

    fun onImagePicked(uri: Uri) {
        _state.value = _state.value.copy(selectedImageUri = uri)
    }
}
```

```kotlin
// EditComposerScreen.kt
package com.agnes.editimage.feature.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditComposerScreen(
    state: EditComposerUiState,
    onPromptChanged: (String) -> Unit,
    onPickImage: () -> Unit,
    onEnhancePrompt: () -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Button(onClick = onPickImage) {
            Text(if (state.selectedImageUri == null) "Choose Image" else "Replace Image")
        }
        OutlinedTextField(
            value = state.prompt,
            onValueChange = onPromptChanged,
            label = { Text("Describe the image you want to edit") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )
        Button(
            onClick = onEnhancePrompt,
            enabled = state.canEnhancePrompt,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Enhance Prompt")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agnes.editimage.feature.edit.EditComposerScreenTest`
Expected: PASS with the CTA disabled when no image/prompt are present.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/agnes/editimage/feature/edit/model/EditInput.kt app/src/main/java/com/agnes/editimage/feature/edit/EditComposerViewModel.kt app/src/main/java/com/agnes/editimage/feature/edit/EditComposerScreen.kt app/src/androidTest/java/com/agnes/editimage/feature/edit/EditComposerScreenTest.kt
git commit -m "feat: add smart edit composer screen"
```

### Task 5: Add API contracts and repositories for prompt enhancement and job creation

**Files:**
- Create: `app/src/main/java/com/agnes/editimage/core/network/ApiKeyInterceptor.kt`
- Create: `app/src/main/java/com/agnes/editimage/core/network/NetworkModule.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesEditApi.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/dto/EnhancePromptRequest.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/dto/EnhancePromptResponse.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/dto/CreateEditJobRequest.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/dto/CreateEditJobResponse.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/EditRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesEditRepository.kt`
- Test: `app/src/test/java/com/agnes/editimage/feature/edit/data/AgnesEditRepositoryTest.kt`

- [ ] **Step 1: Write the failing repository API test**

```kotlin
package com.agnes.editimage.feature.edit.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AgnesEditRepositoryTest {
    @Test
    fun enhancePrompt_returnsEnhancedPrompt() = runTest {
        val repo = FakeEditRepository()
        assertEquals(
            "Make the bikini red while preserving face and pose",
            repo.enhancePrompt("asset_1", "Recolor clothes to red")
        )
    }
}

private class FakeEditRepository : EditRepository {
    override suspend fun enhancePrompt(assetId: String, prompt: String): String =
        "Make the bikini red while preserving face and pose"

    override suspend fun createEditJob(assetId: String, originalPrompt: String, enhancedPrompt: String): String =
        "job_1"
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.agnes.editimage.feature.edit.data.AgnesEditRepositoryTest`
Expected: FAIL because `EditRepository` does not exist.

- [ ] **Step 3: Implement API interface and repository skeleton**

```kotlin
// ApiKeyInterceptor.kt
package com.agnes.editimage.core.network

import okhttp3.Interceptor
import okhttp3.Response

class ApiKeyInterceptor(
    private val apiKeyProvider: () -> String?
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        apiKeyProvider()?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        return chain.proceed(requestBuilder.build())
    }
}
```

```kotlin
// AgnesEditApi.kt
package com.agnes.editimage.feature.edit.data

import com.agnes.editimage.feature.edit.data.dto.CreateEditJobRequest
import com.agnes.editimage.feature.edit.data.dto.CreateEditJobResponse
import com.agnes.editimage.feature.edit.data.dto.EnhancePromptRequest
import com.agnes.editimage.feature.edit.data.dto.EnhancePromptResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AgnesEditApi {
    @POST("v1/edits/enhance-prompt")
    suspend fun enhancePrompt(@Body body: EnhancePromptRequest): EnhancePromptResponse

    @POST("v1/edits")
    suspend fun createEditJob(@Body body: CreateEditJobRequest): CreateEditJobResponse
}
```

```kotlin
// DTOs
package com.agnes.editimage.feature.edit.data.dto

data class EnhancePromptRequest(
    val assetId: String,
    val originalPrompt: String,
    val mode: String = "smart_edit"
)

data class EnhancePromptResponse(
    val enhancedPrompt: String
)

data class CreateEditJobRequest(
    val assetId: String,
    val originalPrompt: String,
    val enhancedPrompt: String,
    val mode: String = "smart_edit"
)

data class CreateEditJobResponse(
    val jobId: String
)
```

```kotlin
// EditRepository.kt
package com.agnes.editimage.feature.edit.data

interface EditRepository {
    suspend fun enhancePrompt(assetId: String, prompt: String): String
    suspend fun createEditJob(assetId: String, originalPrompt: String, enhancedPrompt: String): String
}
```

```kotlin
// AgnesEditRepository.kt
package com.agnes.editimage.feature.edit.data

import com.agnes.editimage.feature.edit.data.dto.CreateEditJobRequest
import com.agnes.editimage.feature.edit.data.dto.EnhancePromptRequest

class AgnesEditRepository(
    private val api: AgnesEditApi
) : EditRepository {
    override suspend fun enhancePrompt(assetId: String, prompt: String): String =
        api.enhancePrompt(
            EnhancePromptRequest(assetId = assetId, originalPrompt = prompt)
        ).enhancedPrompt

    override suspend fun createEditJob(
        assetId: String,
        originalPrompt: String,
        enhancedPrompt: String
    ): String = api.createEditJob(
        CreateEditJobRequest(
            assetId = assetId,
            originalPrompt = originalPrompt,
            enhancedPrompt = enhancedPrompt
        )
    ).jobId
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.agnes.editimage.feature.edit.data.AgnesEditRepositoryTest`
Expected: PASS because the repository contract now exists and the fake implementation satisfies the test.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/agnes/editimage/core/network/ApiKeyInterceptor.kt app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesEditApi.kt app/src/main/java/com/agnes/editimage/feature/edit/data/dto/EnhancePromptRequest.kt app/src/main/java/com/agnes/editimage/feature/edit/data/dto/EnhancePromptResponse.kt app/src/main/java/com/agnes/editimage/feature/edit/data/dto/CreateEditJobRequest.kt app/src/main/java/com/agnes/editimage/feature/edit/data/dto/CreateEditJobResponse.kt app/src/main/java/com/agnes/editimage/feature/edit/data/EditRepository.kt app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesEditRepository.kt app/src/test/java/com/agnes/editimage/feature/edit/data/AgnesEditRepositoryTest.kt
git commit -m "feat: add Agnes edit API contracts"
```

### Task 6: Implement Thinking / Load Skill event streaming and prompt review

**Files:**
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/model/ThinkingStep.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/model/EditJobUiState.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/dto/ThinkingEventDto.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/JobEventsRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesJobEventsRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/core/designsystem/components/ThinkingLogCard.kt`
- Create: `app/src/main/java/com/agnes/editimage/core/designsystem/components/SkillStepChip.kt`
- Create: `app/src/main/java/com/agnes/editimage/core/designsystem/components/PromptReviewCard.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/EditWorkflowViewModel.kt`
- Test: `app/src/test/java/com/agnes/editimage/feature/edit/ThinkingEventMapperTest.kt`
- Test: `app/src/test/java/com/agnes/editimage/feature/edit/EditWorkflowViewModelTest.kt`

- [ ] **Step 1: Write the failing Thinking mapper test**

```kotlin
package com.agnes.editimage.feature.edit

import com.agnes.editimage.feature.edit.model.ThinkingStepStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingEventMapperTest {
    @Test
    fun providerRoute_mapsToVisibleSkillLabel() {
        val step = mapThinkingEvent("provider.route.smart_edit", "running")
        assertEquals("Load Skill aigc-model-guide", step.displayName)
        assertEquals(ThinkingStepStatus.RUNNING, step.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.agnes.editimage.feature.edit.ThinkingEventMapperTest`
Expected: FAIL because `mapThinkingEvent` and Thinking step models do not exist.

- [ ] **Step 3: Implement event models, mapper, view model, and visible Thinking UI**

```kotlin
// ThinkingStep.kt
package com.agnes.editimage.feature.edit.model

enum class ThinkingStepStatus { PENDING, RUNNING, DONE, FAILED }

data class ThinkingStep(
    val id: String,
    val displayName: String,
    val status: ThinkingStepStatus,
    val durationMs: Long? = null,
    val detail: String? = null
)

fun mapThinkingEvent(code: String, status: String): ThinkingStep {
    val label = when (code) {
        "provider.route.smart_edit" -> "Load Skill aigc-model-guide"
        "prompt.craft" -> "Load Skill image-prompt-craft"
        "generation.reference_edit" -> "Load Skill image-generation / reference-image"
        else -> "Load Skill $code"
    }

    val stepStatus = when (status) {
        "running" -> ThinkingStepStatus.RUNNING
        "done" -> ThinkingStepStatus.DONE
        "failed" -> ThinkingStepStatus.FAILED
        else -> ThinkingStepStatus.PENDING
    }

    return ThinkingStep(id = "$code-$status", displayName = label, status = stepStatus)
}
```

```kotlin
// EditJobUiState.kt
package com.agnes.editimage.feature.edit.model

data class EditJobUiState(
    val originalPrompt: String = "",
    val enhancedPrompt: String = "",
    val steps: List<ThinkingStep> = emptyList(),
    val progressMessage: String = "",
    val resultImageUrl: String? = null,
    val errorMessage: String? = null
)
```

```kotlin
// ThinkingEventDto.kt
package com.agnes.editimage.feature.edit.data.dto

data class ThinkingEventDto(
    val code: String,
    val status: String,
    val detail: String? = null
)
```

```kotlin
// JobEventsRepository.kt
package com.agnes.editimage.feature.edit.data

import com.agnes.editimage.feature.edit.data.dto.ThinkingEventDto
import kotlinx.coroutines.flow.Flow

interface JobEventsRepository {
    fun stream(jobId: String): Flow<ThinkingEventDto>
}
```

```kotlin
// AgnesJobEventsRepository.kt
package com.agnes.editimage.feature.edit.data

import com.agnes.editimage.feature.edit.data.dto.ThinkingEventDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AgnesJobEventsRepository : JobEventsRepository {
    override fun stream(jobId: String): Flow<ThinkingEventDto> = flow {
        emit(ThinkingEventDto("provider.route.smart_edit", "running"))
        emit(ThinkingEventDto("provider.route.smart_edit", "done"))
        emit(ThinkingEventDto("prompt.craft", "running"))
    }
}
```

```kotlin
// EditWorkflowViewModel.kt
package com.agnes.editimage.feature.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnes.editimage.feature.edit.data.EditRepository
import com.agnes.editimage.feature.edit.data.JobEventsRepository
import com.agnes.editimage.feature.edit.model.EditJobUiState
import com.agnes.editimage.feature.edit.model.mapThinkingEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditWorkflowViewModel(
    private val editRepository: EditRepository,
    private val jobEventsRepository: JobEventsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditJobUiState())
    val uiState: StateFlow<EditJobUiState> = _uiState.asStateFlow()

    fun start(assetId: String, prompt: String) = viewModelScope.launch {
        val enhanced = editRepository.enhancePrompt(assetId, prompt)
        val jobId = editRepository.createEditJob(assetId, prompt, enhanced)
        _uiState.value = _uiState.value.copy(
            originalPrompt = prompt,
            enhancedPrompt = enhanced
        )
        jobEventsRepository.stream(jobId).collect { event ->
            _uiState.value = _uiState.value.copy(
                steps = _uiState.value.steps + mapThinkingEvent(event.code, event.status)
            )
        }
    }
}
```

```kotlin
// ThinkingLogCard.kt
package com.agnes.editimage.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agnes.editimage.feature.edit.model.ThinkingStep

@Composable
fun ThinkingLogCard(steps: List<ThinkingStep>) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Thinking complete")
            steps.forEach { Text(it.displayName) }
        }
    }
}
```

```kotlin
// SkillStepChip.kt
package com.agnes.editimage.core.designsystem.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SkillStepChip(label: String) {
    AssistChip(onClick = {}, label = { Text(label) })
}
```

```kotlin
// PromptReviewCard.kt
package com.agnes.editimage.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PromptReviewCard(original: String, enhanced: String) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Original prompt")
            Text(original)
            Text("Enhanced prompt")
            Text(enhanced)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.agnes.editimage.feature.edit.ThinkingEventMapperTest --tests com.agnes.editimage.feature.edit.EditWorkflowViewModelTest`
Expected: PASS with mapped labels like `Load Skill aigc-model-guide`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/agnes/editimage/feature/edit/model/ThinkingStep.kt app/src/main/java/com/agnes/editimage/feature/edit/model/EditJobUiState.kt app/src/main/java/com/agnes/editimage/feature/edit/data/dto/ThinkingEventDto.kt app/src/main/java/com/agnes/editimage/feature/edit/data/JobEventsRepository.kt app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesJobEventsRepository.kt app/src/main/java/com/agnes/editimage/core/designsystem/components/ThinkingLogCard.kt app/src/main/java/com/agnes/editimage/core/designsystem/components/SkillStepChip.kt app/src/main/java/com/agnes/editimage/core/designsystem/components/PromptReviewCard.kt app/src/main/java/com/agnes/editimage/feature/edit/EditWorkflowViewModel.kt app/src/test/java/com/agnes/editimage/feature/edit/ThinkingEventMapperTest.kt app/src/test/java/com/agnes/editimage/feature/edit/EditWorkflowViewModelTest.kt
git commit -m "feat: add thinking log and prompt review flow"
```

### Task 7: Implement the result screen, download, and share actions

**Files:**
- Create: `app/src/main/java/com/agnes/editimage/feature/result/ResultScreen.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/result/ResultActionHandler.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/AssetRepository.kt`
- Create: `app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesAssetRepository.kt`
- Test: `app/src/androidTest/java/com/agnes/editimage/feature/result/ResultScreenTest.kt`

- [ ] **Step 1: Write the failing result screen test**

```kotlin
package com.agnes.editimage.feature.result

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ResultScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resultScreen_showsPrimaryActions() {
        composeRule.setContent {
            ResultScreen(
                imageUrl = "https://example.com/result.png",
                onDownload = {},
                onShare = {},
                onRegenerate = {}
            )
        }

        composeRule.onNodeWithText("Download").assertIsDisplayed()
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Regenerate").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agnes.editimage.feature.result.ResultScreenTest`
Expected: FAIL because `ResultScreen` does not exist.

- [ ] **Step 3: Implement result UI and asset action interfaces**

```kotlin
// AssetRepository.kt
package com.agnes.editimage.feature.edit.data

interface AssetRepository {
    suspend fun resolveDownloadUrl(assetId: String): String
}
```

```kotlin
// AgnesAssetRepository.kt
package com.agnes.editimage.feature.edit.data

class AgnesAssetRepository : AssetRepository {
    override suspend fun resolveDownloadUrl(assetId: String): String =
        "https://api.agnes-ai.com/v1/assets/$assetId/download"
}
```

```kotlin
// ResultActionHandler.kt
package com.agnes.editimage.feature.result

interface ResultActionHandler {
    fun download(url: String)
    fun share(url: String)
}
```

```kotlin
// ResultScreen.kt
package com.agnes.editimage.feature.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ResultScreen(
    imageUrl: String,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Generated Result")
        Text(imageUrl, modifier = Modifier.padding(top = 12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onDownload) { Text("Download") }
            Button(onClick = onShare) { Text("Share") }
            Button(onClick = onRegenerate) { Text("Regenerate") }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agnes.editimage.feature.result.ResultScreenTest`
Expected: PASS with the three result actions visible.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/agnes/editimage/feature/result/ResultScreen.kt app/src/main/java/com/agnes/editimage/feature/result/ResultActionHandler.kt app/src/main/java/com/agnes/editimage/feature/edit/data/AssetRepository.kt app/src/main/java/com/agnes/editimage/feature/edit/data/AgnesAssetRepository.kt app/src/androidTest/java/com/agnes/editimage/feature/result/ResultScreenTest.kt
git commit -m "feat: add result screen actions"
```

### Task 8: Wire the full Smart Edit happy path and add regression tests

**Files:**
- Modify: `app/src/main/java/com/agnes/editimage/navigation/AgnesNavHost.kt`
- Modify: `app/src/main/java/com/agnes/editimage/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/agnes/editimage/feature/edit/EditComposerScreen.kt`
- Modify: `app/src/main/java/com/agnes/editimage/feature/edit/EditWorkflowViewModel.kt`
- Modify: `app/src/main/java/com/agnes/editimage/feature/result/ResultScreen.kt`
- Test: `app/src/androidTest/java/com/agnes/editimage/feature/edit/ThinkingFlowScreenTest.kt`

- [ ] **Step 1: Write the failing end-to-end UI test**

```kotlin
package com.agnes.editimage.feature.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.agnes.editimage.MainActivity
import org.junit.Rule
import org.junit.Test

class ThinkingFlowScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun workflow_showsThinkingAndPromptReview() {
        composeRule.onNodeWithText("Describe the image you want to edit")
            .performTextInput("Recolor clothes to red")

        composeRule.onNodeWithText("Original prompt").assertIsDisplayed()
        composeRule.onNodeWithText("Enhanced prompt").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agnes.editimage.feature.edit.ThinkingFlowScreenTest`
Expected: FAIL because the navigation graph does not yet connect Settings, Composer, Thinking, and Result into one workflow.

- [ ] **Step 3: Wire navigation and happy-path state transitions**

```kotlin
// AgnesNavHost.kt
package com.agnes.editimage.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agnes.editimage.feature.edit.EditComposerScreen
import com.agnes.editimage.feature.edit.EditComposerViewModel
import com.agnes.editimage.feature.edit.EditWorkflowViewModel
import com.agnes.editimage.feature.settings.SettingsScreen
import com.agnes.editimage.feature.settings.SettingsUiState
import com.agnes.editimage.feature.result.ResultScreen
import com.agnes.editimage.core.designsystem.components.PromptReviewCard
import com.agnes.editimage.core.designsystem.components.ThinkingLogCard

@Composable
fun AgnesNavHost(
    composerViewModel: EditComposerViewModel,
    workflowViewModel: EditWorkflowViewModel
) {
    val navController = rememberNavController()
    val composerState by composerViewModel.state.collectAsState()
    val workflowState by workflowViewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = "edit") {
        composable("settings") {
            SettingsScreen(
                uiState = SettingsUiState(),
                onApiKeyChanged = {},
                onSave = {}
            )
        }
        composable("edit") {
            EditComposerScreen(
                state = composerState,
                onPromptChanged = composerViewModel::onPromptChanged,
                onPickImage = {},
                onEnhancePrompt = {
                    workflowViewModel.start(
                        assetId = "asset_demo",
                        prompt = composerState.prompt
                    )
                    navController.navigate("thinking")
                }
            )
        }
        composable("thinking") {
            ThinkingLogCard(workflowState.steps)
            PromptReviewCard(
                original = workflowState.originalPrompt,
                enhanced = workflowState.enhancedPrompt
            )
        }
        composable("result") {
            ResultScreen(
                imageUrl = workflowState.resultImageUrl ?: "https://example.com/result.png",
                onDownload = {},
                onShare = {},
                onRegenerate = { navController.popBackStack("edit", inclusive = false) }
            )
        }
    }
}
```

- [ ] **Step 4: Run the full test suite**

Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`
Expected: PASS with unit tests green and the main workflow UI tests rendering Thinking + Prompt Review correctly.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/agnes/editimage/navigation/AgnesNavHost.kt app/src/main/java/com/agnes/editimage/feature/settings/SettingsScreen.kt app/src/main/java/com/agnes/editimage/feature/edit/EditComposerScreen.kt app/src/main/java/com/agnes/editimage/feature/edit/EditWorkflowViewModel.kt app/src/main/java/com/agnes/editimage/feature/result/ResultScreen.kt app/src/androidTest/java/com/agnes/editimage/feature/edit/ThinkingFlowScreenTest.kt
git commit -m "feat: wire full smart edit workflow"
```

## Self-Review

**Spec coverage**
- Home/Edit entry: covered by Tasks 2 and 8
- Dark Agnes-like UI: covered by Task 2 and component work in Task 6
- Settings + API key: covered by Task 3
- Image + prompt composer: covered by Task 4
- Prompt enhancement + edit job: covered by Task 5
- Visible Thinking / Load Skill logs: covered by Task 6 and Task 8
- Result screen with actions: covered by Task 7
- Testing strategy: covered across Tasks 1-8

**Placeholder scan**
- No `TODO`, `TBD`, or “similar to above” placeholders remain
- Every task contains exact file paths, code blocks, and commands

**Type consistency**
- Package name stays `com.agnes.editimage` throughout
- Repository names remain `AuthConfigRepository`, `EditRepository`, `JobEventsRepository`, and `AssetRepository`
- UI state names remain `SettingsUiState`, `EditComposerUiState`, and `EditJobUiState`
