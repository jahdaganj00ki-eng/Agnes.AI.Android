# Native Android App: Edit Image Workflow Inspired by Agnes

## 1. Summary

This document defines the V1 product and technical design for a native Android app focused on AI-powered image editing with a workflow inspired by the Agnes Android app screenshots and APK analysis in this repository.

The V1 app supports a single core capability:

- Smart Edit: prompt-based image editing using one uploaded reference image

The design intentionally mirrors the observed Agnes interaction pattern:

1. User opens `Edit Image`
2. User uploads an image and enters a short prompt
3. App shows visible "Thinking" / "Load Skill" steps
4. App shows prompt enhancement review
5. App runs a server-side generation job
6. App shows the resulting image with download/share/regenerate actions

The app uses a user-provided Agnes.AI API key entered in Settings. The app is native Android, uses a dark Agnes-like theme, and relies on server-side inference rather than on-device image generation models.

## 2. Goals

- Recreate the Agnes-like `Edit Image` experience as a focused native Android app
- Support Smart Edit in V1 with one reference image and one edit prompt
- Show visible Thinking / Load Skill logs during processing
- Show original prompt and enhanced prompt before generation
- Use a dark premium UI inspired by the Agnes screenshots in this repository
- Allow the user to configure an Agnes.AI API key in Settings
- Keep architecture extensible for later support of additional edit tools such as Remove BG, Split Layers, or Text Edit

## 3. Non-Goals

- No on-device generative model inference in V1
- No end-user billing, subscription, or credits system in V1
- No multi-image composition workflow in V1
- No video generation in V1
- No full chat assistant surface in V1
- No template marketplace or template library UI in V1, although the architecture should not block adding it later

## 4. Inputs from APK and Screenshots

The design is based on observed product and technical clues from the repository:

- Screenshots show tabs and actions such as `Edit Image`, `Photo design`, prompt enhancement, and visible `Load Skill` steps
- APK strings indicate tracked design edit actions:
  - `design_edit_smartedit`
  - `design_edit_removebg`
  - `design_edit_splitlayers`
  - `design_edit_textediting`
- APK strings indicate job lifecycle states:
  - `template_generate_pending`
  - `template_generate_failed`
  - `template_generate_completed`
- APK strings indicate server-oriented dynamic mode/model selection:
  - `ModelCapability`
  - `ModelsAccessRequest`
  - `ModelsCostRequest`
  - `PixaModeItem`
  - `PixaFeaturesItem`
- No packaged local ML model files were found in the APK, which supports a server-side inference design

These signals indicate a workflow where the Android client orchestrates upload, prompting, visible steps, and result retrieval while the backend performs routing, prompt crafting, generation, and asset delivery.

## 5. Product Scope

### 5.1 V1 User Story

As a user, I want to upload a photo, describe a change in a short prompt, watch the app think through the edit steps, and receive a generated edited image that I can save or share.

### 5.2 V1 Supported Feature

- Smart Edit with one image input and one prompt input

### 5.3 V1 Required User Actions

- Add Agnes.AI API key in Settings
- Select one local image from device storage
- Enter a short natural-language edit instruction
- Review the enhanced prompt
- Start generation
- Save, share, or regenerate the output

## 6. UX Flow

### 6.1 Screen 1: Home

Purpose:
- Provide a simple entry point into the image editing workflow

Primary UI:
- Dark hero area or simple branded header
- Main action card/button: `Edit Image`
- Optional recent result preview if cached locally
- Entry to Settings

### 6.2 Screen 2: Edit Composer

Purpose:
- Collect the input image and the user edit prompt

Primary UI:
- Selected image preview card
- Button to choose or replace image
- Prompt input field
- CTA: `Enhance Prompt`

Behavior:
- CTA disabled until image and prompt are both present
- If API key is missing, redirect or inline prompt to Settings

### 6.3 Screen 3: Thinking + Prompt Review

Purpose:
- Show visible system reasoning steps in a productized form
- Present prompt enhancement before final generation

Primary UI:
- Expandable dark card for Thinking log
- Ordered visible steps, for example:
  - `Load Skill aigc-model-guide`
  - `Load Skill image-prompt-craft`
  - `Load Skill image-generation / reference-image`
- Status per step:
  - pending
  - running
  - done
  - failed
- Optional step duration
- Prompt review card:
  - `Original prompt`
  - `Enhanced prompt`
- CTA: `Generate`

Important product requirement:
- The Thinking / Load Skill log is visible to the user in V1, similar to Agnes
- This is product UI, not a hidden debug console

### 6.4 Screen 4: Generation Progress

Purpose:
- Show that the server-side image generation is still running

Primary UI:
- Progress indicator
- Estimated time text such as `This will take about 30-60 seconds`
- Ongoing Thinking / Load Skill entries if additional events arrive
- Retry or cancel behavior only if supported by the API

### 6.5 Screen 5: Result

Purpose:
- Display the generated image artifact and output actions

Primary UI:
- Large edited image preview
- Action buttons:
  - Download
  - Share
  - Regenerate

Optional later additions:
- Before/after comparison
- Variation generation
- History

## 7. Visual Design

### 7.1 Style Direction

The app uses an Agnes-like dark visual language based on the screenshots in this repository.

Design qualities:
- Dark, premium, AI-first
- Minimal and calm
- High-contrast typography
- Rounded panels and cards
- Light use of accent colors for active states and skill chips

### 7.2 Theme Tokens

Core theme expectations:
- Background: near-black or deep charcoal
- Surface cards: slightly elevated dark gray
- Primary text: off-white
- Secondary text: cool gray
- Accent: blue/cyan family for skill chips, active controls, and progress emphasis
- Error: warm red with readable contrast

### 7.3 Component Styles

- ThinkingLogCard: dark rounded expandable panel
- SkillStepChip: pill-style highlighted label
- PromptReviewCard: two stacked dark containers for original and enhanced prompt
- Primary button: bright accent or high-contrast filled dark variant
- Asset preview card: large rounded container with generous padding

## 8. Information Architecture

Primary destinations:
- Home
- Edit Image
- Result Detail
- Settings

V1 keeps navigation intentionally shallow:
- Home -> Edit Composer -> Thinking/Review -> Result
- Settings accessible globally or from Home

## 9. Technical Architecture

### 9.1 Android Stack

- Kotlin
- Jetpack Compose
- Navigation Compose
- ViewModel + unidirectional state flow
- Retrofit or Ktor client for REST
- SSE or WebSocket client for live job events
- Coil for image loading
- Room optional only if local history is added in V1.1+, not required for V1

### 9.2 Suggested Module Layout

- `app`
- `core:designsystem`
- `core:network`
- `core:storage`
- `feature:settings`
- `feature:edit`
- `feature:result`

### 9.3 Layering

- UI layer: screens, composables, state renderers
- Domain layer: use cases for prompt enhancement, edit job creation, event observation, and asset download
- Data layer: DTOs, repositories, API adapters, secure key storage

## 10. API Integration Model

### 10.1 Authentication

The app uses a user-supplied Agnes.AI API key entered in Settings.

Requirements:
- Key stored securely on device using Android Keystore-backed encrypted storage
- App must not hardcode API keys
- App must support updating and deleting the key
- App should provide `Test connection`

### 10.2 Settings Surface

Required Settings items:
- Agnes.AI API key
- Optional API base URL override
- Optional toggle for more verbose Thinking log
- Test connection action

### 10.3 API Abstraction

Even if the app directly calls Agnes-compatible APIs in V1, the client architecture should use an internal provider abstraction so that a proxy or alternate backend can be inserted later without rewriting the feature module.

Suggested interfaces:
- `AuthConfigRepository`
- `EditRepository`
- `JobEventsRepository`
- `AssetRepository`

## 11. Network Contract Shape

The exact Agnes.AI API schema may differ, so the app should target the following conceptual contract.

### 11.1 Asset Upload

Possible patterns:
- Presigned upload URL flow
- Multipart upload

Client concept:
- choose image
- upload image
- receive `assetId` or direct input URL reference

### 11.2 Prompt Enhancement

Conceptual request:
- input image reference
- original prompt
- mode = `smart_edit`

Conceptual response:
- enhanced prompt
- optional step metadata

### 11.3 Edit Job Creation

Conceptual request:
- input asset ID
- original prompt
- enhanced prompt
- mode code: `smart_edit`
- optional edit constraints

Conceptual response:
- edit job ID
- initial status

### 11.4 Job Event Stream

Recommended transport:
- Server-Sent Events first choice
- WebSocket second choice
- Polling only as fallback

Conceptual event types:
- `job.created`
- `step.started`
- `step.completed`
- `prompt.enhanced`
- `generation.started`
- `generation.progress`
- `generation.completed`
- `job.failed`

### 11.5 Result Retrieval

Conceptual result:
- result asset ID
- download URL or CDN URL
- optional metadata for width, height, and generation timings

## 12. Thinking / Load Skill Log Design

This feature is a first-class product requirement.

### 12.1 Visible Events

The app displays backend job events as user-facing "Thinking" steps.

Example visible labels:
- `Load Skill aigc-model-guide`
- `Load Skill image-prompt-craft`
- `Load Skill image-generation / reference-image`

### 12.2 Mapping Layer

The client must not expose raw backend implementation codes directly.

Instead:
- backend may emit internal machine events
- app maps them to stable user-facing labels

Example:
- internal event: `provider.route.smart_edit`
- visible label: `Load Skill aigc-model-guide`

### 12.3 UX Rules

- Steps appear incrementally in order
- Running step is visually highlighted
- Completed steps remain visible
- Failed steps show readable failure text
- The card is collapsed or half-expanded by default, but visibly present
- Verbose detail is optional behind a setting or info affordance

## 13. Smart Edit Domain Model

### 13.1 Core Entities

- `EditInputImage`
- `EditPrompt`
- `EnhancedPrompt`
- `ThinkingStep`
- `EditJob`
- `EditResultAsset`

### 13.2 Example Client Data Model

- `ThinkingStep`
  - `id`
  - `displayName`
  - `status`
  - `durationMs`
  - `detail`

- `EditJobUiState`
  - `inputImageUri`
  - `originalPrompt`
  - `enhancedPrompt`
  - `steps`
  - `jobStatus`
  - `progressMessage`
  - `resultImageUrl`
  - `errorMessage`

## 14. Error Handling

### 14.1 User-Facing Error Categories

- Missing API key
- Invalid API key
- Upload failed
- Unsupported or invalid reference image
- Prompt rejected by content policy
- Job timeout
- Network disconnected
- Result retrieval failed

### 14.2 UX Behavior

- Show clear action-oriented error messages
- Preserve the user prompt where possible
- Preserve the selected image if retry is possible
- Highlight the failed Thinking step if a step-level failure is known

## 15. Security and Privacy

- API key stored securely using encrypted local storage and Keystore support
- Avoid logging API keys
- Avoid storing original images beyond what is needed for upload and result history
- Use HTTPS only
- Redact sensitive request headers from logs
- Provide a way to delete local cached outputs

## 16. Analytics

V1 analytics should be intentionally small and focused.

Suggested events:
- `settings_api_key_saved`
- `settings_api_connection_tested`
- `edit_image_opened`
- `edit_image_selected`
- `smart_edit_prompt_submitted`
- `prompt_enhancement_shown`
- `generation_started`
- `generation_succeeded`
- `generation_failed`
- `result_downloaded`
- `result_shared`
- `result_regenerated`

The event names can later be aligned with Agnes-like naming if desired.

## 17. Testing Strategy

### 17.1 Unit Tests

- Prompt enhancement use case state transitions
- Thinking event to UI mapping
- API key validation and secure storage behaviors
- Edit job reducer / state machine transitions

### 17.2 Integration Tests

- Asset upload -> prompt enhancement -> edit job -> result happy path using mocked APIs
- Failure scenarios for invalid key, upload failure, and job failure
- SSE/WebSocket event stream handling

### 17.3 UI Tests

- User can save API key in Settings
- User can select image and enter prompt
- Thinking card becomes visible with streaming steps
- Prompt enhancement review is shown
- Result actions are available after success

## 18. Rollout Plan

### Phase 1

- Settings with API key management
- Home and Edit Composer
- Upload + Prompt Enhancement
- Visible Thinking log
- Job progress and Result screen

### Phase 2

- Local history
- Before/after comparison
- More verbose logs

### Phase 3

- Additional edit tools:
  - Remove BG
  - Split Layers
  - Text Edit

## 19. Open Implementation Assumptions

These assumptions are explicit so the implementation plan can proceed without ambiguity:

- V1 targets direct Agnes.AI-compatible API usage from Android
- Smart Edit is the only editing mode in V1
- One reference image is supported in V1
- Thinking / Load Skill logs are visible in the product UI
- The app uses a dark theme strongly inspired by the Agnes screenshots
- The API supports either structured prompt enhancement and job events directly, or enough raw data to emulate them client-side
- If live event streaming is unavailable, the client falls back to polling and reconstructs the visible Thinking state from returned job metadata

## 20. Recommended Next Step

Write the implementation plan for:

- package/module structure
- screen-by-screen tasks
- API contracts and repository interfaces
- event streaming implementation
- secure settings storage
- testing milestones
