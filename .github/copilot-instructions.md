# Moonfin for Android TV - Developer Guide

## Project Overview

**Moonfin** is an enhanced fork of the official Jellyfin Android TV client, optimized for Android TV, Nvidia Shield, and Fire TV devices. The key differentiator is **Jellyseerr integration** - the first native Jellyfin client with TMDB-based content discovery and requesting.

## Development Environment

**Prerequisites:**
- **Java 21** (required - not Java 25 or other versions)
- **Android SDK** (Platform API 35, Build Tools 35.0.0)
- **local.properties** file with `sdk.dir` pointing to Android SDK location

**Quick setup:**
```bash
# Set Java 21
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

# Create local.properties
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# Build debug APK
./gradlew assembleDebug
```

For detailed setup instructions, see [DEVELOPMENT_SETUP.md](../DEVELOPMENT_SETUP.md).

**Important files (gitignored):**
- `local.properties` - Android SDK location (required for builds)
- `keystore.properties` - Release signing credentials (see [KEYSTORE_SETUP.md](../KEYSTORE_SETUP.md))
- `moonfin-release.keystore` - Release keystore file
- `~/.android/debug.keystore` - Debug keystore (shared across projects)

## Architecture

### Multi-Module Gradle Structure

```
app/                           # Main Android TV application
playback/
  ├── core/                    # Core playback abstractions
  ├── jellyfin/                # Jellyfin-specific playback integration
  └── media3/
      ├── exoplayer/           # ExoPlayer implementation
      └── session/             # Media3 session management
preference/                    # Shared preferences module
buildSrc/                      # Custom Gradle build logic (VersionUtils)
```

**Key architectural decisions:**
- Custom `playback` module hierarchy abstracts media playback from Jellyfin SDK specifics
- Gradle version catalog (`gradle/libs.versions.toml`) centralizes all dependency versions
- Uses typesafe project accessors (`projects.playback.core`)

### Dependency Injection (Koin)

All DI is managed via **Koin** with modules organized by domain:

```kotlin
// app/src/main/java/org/jellyfin/androidtv/di/
androidModule     // System services (AudioManager, UiModeManager, WorkManager)
appModule         // SDK, repositories, ViewModels, ImageLoader (Coil)
authModule        // Authentication & session management
playbackModule    // PlaybackManager, MediaManager, ExoPlayer setup
preferenceModule  // UserPreferences, JellyseerrPreferences
utilsModule       // Helpers, utilities
```

**Injection patterns:**
- Constructor injection in ViewModels: `class MyViewModel(private val api: ApiClient, ...)`
- Composables: `koinInject<Type>()` or `koinViewModel<T>()`
- Java interop: `KoinJavaComponent.inject<Type>(Type::class.java)`

### UI Architecture: Hybrid Leanback + Compose

The app uses **Android TV Leanback** for list navigation with targeted **Jetpack Compose** integration:

- **Leanback Fragments** handle core browsing (`HomeFragment`, `ItemListFragment`)
- **Compose embedded** via `ComposeView` for:
  - Main toolbar (`MainToolbar`)
  - Featured media bar slideshow (`MediaBarSlideshowView`)
  - Jellyseerr discovery UI
  - Settings screens

**Pattern:** Embed Compose in Leanback presenters/ViewHolders for modern UI elements while preserving TV navigation.

### Jellyfin SDK Integration

- SDK client: `org.jellyfin.sdk:jellyfin-core` v1.8.4
- API client injected via Koin as `ApiClient`
- Custom client name: `"Moonfin Android TV"` (see `appModule.kt`)
- Supports snapshot SDK versions via `gradle.properties`: `sdk.version=snapshot|unstable-snapshot|local|default`

**SDK Usage:**
```kotlin
val api = koinInject<ApiClient>()
val items = api.itemsApi.getResumeItems(userId, ...)
```

### Jellyseerr Integration

Custom Ktor-based HTTP client for TMDB/Jellyseerr API:
- Repository: `JellyseerrRepository` (`app/src/main/java/org/jellyfin/androidtv/data/repository/`)
- API models: `app/src/main/java/org/jellyfin/androidtv/data/model/jellyseerr/`
- UI: `app/src/main/java/org/jellyfin/androidtv/ui/jellyseerr/`
- Configuration: `JellyseerrPreferences` (stored per-server)

**Features:** Content discovery, quality profile selection, season picker, request tracking, NSFW filtering.

## Build System

### Version Management

Versions are defined in `gradle.properties`:
```properties
moonfin.version=v1.3.1
```

**Version code calculation** (`buildSrc/src/main/kotlin/VersionUtils.kt`):
- Format: `MA.MI.PA-PR` → `MAMIPAPR`
- Example: `1.3.1` → `1030199`, `1.3.1-rc.2` → `1030102`
- Pre-release defaults to `99` if omitted

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install debug to connected device
./gradlew installDebug

# Build release (requires keystore.properties)
./gradlew assembleRelease

# Run tests
./gradlew test

# Run Detekt linting
./gradlew detekt
```

**Output locations:**
- Debug APK: `app/build/outputs/apk/debug/moonfin-androidtv-v*.*.?-debug.apk`
- Release APK: `app/build/outputs/apk/release/moonfin-androidtv-v*.*.?-release.apk`

**Keystore setup:** Copy `keystore.properties.template` → `keystore.properties` and configure signing credentials. See [KEYSTORE_SETUP.md](../KEYSTORE_SETUP.md) for details.

### Build Types

- **Debug:** Application ID `org.moonfin.androidtv.debug`, name "Moonfin Debug"
  - Uses Android debug keystore (`~/.android/debug.keystore`)
  - Explicitly configured to use debug signing (prevents release key conflicts)
- **Release:** Application ID `org.moonfin.androidtv`, name "Moonfin"
  - Uses custom release keystore when `keystore.properties` exists

## Code Conventions

### Language Mix: Kotlin + Java

- **New code:** Write in Kotlin
- **Legacy code:** Java files exist (e.g., `FullDetailsFragment.java`, `PlaybackController.java`)
- **Interop:** Use `@JvmOverloads`, `@JvmStatic`, `KoinJavaComponent` for Java→Kotlin

### Compose Best Practices

- Use `koinInject()` for dependencies in `@Composable` functions
- Prefer `collectAsState()` for Flow observation
- TV focus: Use `Modifier.focusable()` and `onFocusChanged` for D-pad navigation
- **Performance:** Minimize Compose usage in high-frequency list items (use Leanback Presenters)

### State Management

- ViewModels use Kotlin `StateFlow` and `Flow`
- User preferences: `UserPreferences[UserPreferences.key]` pattern
- Repositories expose `Flow` for reactive data

### Image Loading (Coil)

- `ImageLoader` configured with OkHttp, SVG, GIF support
- Helpers: `itemImages()`, `itemBackdropImages()` extensions for Jellyfin image URLs
- Example:
  ```kotlin
  val imageLoader = koinInject<ImageLoader>()
  imageLoader.enqueue(ImageRequest.Builder(context).data(url).target(...).build())
  ```

### Playback Architecture

The app uses a custom `PlaybackManager` (from `playback:core`) with plugins:
- `exoPlayerPlugin` - ExoPlayer integration
- `jellyfinPlugin` - Jellyfin SDK playback reporting
- `media3SessionPlugin` - Media session for notification/lock screen

**Key classes:**
- `PlaybackManager` - Core playback state machine
- `RewriteMediaManager` - Bridges new playback system with legacy `MediaManager`
- `PlaybackController` - Legacy controller (Java), gradually being phased out

## Key Files & Directories

### Core Infrastructure
- `app/src/main/java/org/jellyfin/androidtv/di/` - Koin DI modules
- `app/src/main/java/org/jellyfin/androidtv/preference/` - User preferences
- `app/src/main/res/values/strings.xml` - Localized strings (70+ languages)
- `playback/jellyfin/src/main/kotlin/playsession/` - Playback session reporting to Jellyfin

### UI Components by Section

#### Home Screen
- `app/src/main/java/org/jellyfin/androidtv/ui/home/HomeFragment.kt` - Main home fragment container with backdrop/logo management
- `app/src/main/res/layout/fragment_home.xml` - Home screen layout (ConstraintLayout)
- `app/src/main/java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt` - Leanback BrowseSupportFragment for rows
- `app/src/main/java/org/jellyfin/androidtv/ui/home/SimpleInfoRowView.kt` - Lightweight metadata row (year, rating, runtime)

#### Toolbar
- `app/src/main/java/org/jellyfin/androidtv/ui/shared/toolbar/MainToolbar.kt` - Compose toolbar with library buttons
- `app/src/main/java/org/jellyfin/androidtv/ui/shared/toolbar/MainToolbarViewModel.kt` - Toolbar state & library data

#### Media Bar (Featured Carousel)
- `app/src/main/java/org/jellyfin/androidtv/ui/home/mediabar/MediaBarSlideshowView.kt` - Compose carousel UI with AnimatedContent transitions
- `app/src/main/java/org/jellyfin/androidtv/ui/home/mediabar/MediaBarSlideshowViewModel.kt` - Carousel state, auto-advance, navigation
- `app/src/main/java/org/jellyfin/androidtv/ui/home/mediabar/MediaBarSlideshow.kt` - Data models (MediaBarSlideItem, MediaBarState)
- `app/src/main/java/org/jellyfin/androidtv/ui/home/HomeFragmentMediaBarRow.kt` - Leanback row integration
- `app/src/main/java/org/jellyfin/androidtv/ui/home/MediaBarPresenter.kt` - Leanback presenter wrapper

#### Home Rows
- `app/src/main/java/org/jellyfin/androidtv/ui/home/HomeFragmentRow.kt` - Interface for home rows
- `app/src/main/java/org/jellyfin/androidtv/ui/home/HomeFragmentBrowseRowDef.kt` - Standard browse rows (Continue Watching, Next Up, etc.)
- `app/src/main/java/org/jellyfin/androidtv/ui/home/HomeFragmentLatestRow.kt` - Latest media rows

#### Item Details
- `app/src/main/java/org/jellyfin/androidtv/ui/itemdetail/FullDetailsFragment.java` - Full details screen (legacy Java)
- `app/src/main/java/org/jellyfin/androidtv/ui/itemdetail/ItemListFragment.kt` - Lists (seasons, episodes, etc.)

#### Jellyseerr Discovery
- `app/src/main/java/org/jellyfin/androidtv/ui/jellyseerr/JellyseerrDiscoverFragment.kt` - Main discovery screen
- `app/src/main/java/org/jellyfin/androidtv/ui/jellyseerr/MediaDetailsFragment.kt` - TMDB media details
- `app/src/main/java/org/jellyfin/androidtv/ui/jellyseerr/PersonDetailsFragment.kt` - Cast/crew details
- `app/src/main/java/org/jellyfin/androidtv/ui/jellyseerr/JellyseerrViewModel.kt` - Discovery state management
- `app/src/main/java/org/jellyfin/androidtv/data/repository/JellyseerrRepository.kt` - Jellyseerr API client
- `app/src/main/java/org/jellyfin/androidtv/data/model/jellyseerr/` - Jellyseerr data models

#### Settings
- `app/src/main/java/org/jellyfin/androidtv/ui/preference/screen/MoonfinPreferencesScreen.kt` - Moonfin-specific settings (Media Bar, backdrop blur, etc.)
- `app/src/main/java/org/jellyfin/androidtv/ui/preference/screen/UserPreferencesScreen.kt` - User preferences screen
- `preference/src/main/kotlin/org/jellyfin/preference/store/` - Preference storage implementations

#### Playback
- `app/src/main/java/org/jellyfin/androidtv/ui/playback/VideoQueueManager.kt` - Video queue management
- `app/src/main/java/org/jellyfin/androidtv/ui/playback/PlaybackController.java` - Legacy playback controller (Java)
- `playback/core/src/main/kotlin/org/jellyfin/playback/core/PlaybackManager.kt` - Modern playback manager
- `app/src/main/java/org/jellyfin/androidtv/ui/playback/overlay/LeanbackOverlayFragment.java` - Video player overlay

#### Presenters (Leanback Card Views)
- `app/src/main/java/org/jellyfin/androidtv/ui/presentation/CardPresenter.kt` - Standard media card presenter
- `app/src/main/java/org/jellyfin/androidtv/ui/presentation/PositionableListRowPresenter.kt` - Row presenter base
- `app/src/main/java/org/jellyfin/androidtv/ui/card/DefaultCardView.kt` - Default card view implementation

#### Background Management
- `app/src/main/java/org/jellyfin/androidtv/ui/background/BackgroundService.kt` - Backdrop image management
- `app/src/main/java/org/jellyfin/androidtv/ui/background/BackgroundAdapter.kt` - Background blur/fade logic

#### Navigation
- `app/src/main/java/org/jellyfin/androidtv/ui/navigation/NavigationRepository.kt` - App navigation coordinator
- `app/src/main/java/org/jellyfin/androidtv/ui/navigation/Destinations.kt` - Navigation destination definitions

#### Drawables & Icons
- `app/src/main/res/drawable/moonfin_ic_channel_background.xml` - App icon background (grid pattern)
- `app/src/main/res/mipmap-*/` - App icon resources (various densities)
- `app/src/main/res/drawable/` - UI icons and shapes

## Testing

- Unit tests: Use Kotest + MockK
- Test location: `app/src/test/` and module-specific `src/test/`
- Run with: `./gradlew test`
- Tests use JUnit Platform (JUnit 5 via Kotest)

## Common Tasks

### Adding a New Preference

1. Define in `UserPreferences` or `JellyseerrPreferences` (`preference/` module)
2. Add UI in settings fragments or Compose settings screens
3. Access via `userPreferences[UserPreferences.newKey]`

### Adding a Jellyseerr Feature

1. Update API models in `app/src/main/java/org/jellyfin/androidtv/data/model/jellyseerr/`
2. Add repository method in `JellyseerrRepository`
3. Create/update UI in `app/src/main/java/org/jellyfin/androidtv/ui/jellyseerr/`
4. Use Ktor client for HTTP requests

### Updating Jellyfin SDK

Change in `gradle/libs.versions.toml`:
```toml
jellyfin-sdk = "1.x.x"
```
Or use snapshots via `gradle.properties`: `sdk.version=snapshot`

## Debugging

- Use `timber.log.Timber` for logging (already configured)
- LogInitializer sets up Timber automatically via AndroidX Startup
- Debug builds include LeakCanary (disabled by default in `gradle.properties`)

## Brand Identity

- Application name: **Moonfin** (not Jellyfin)
- Package: `org.moonfin.androidtv`
- Client info: "Moonfin Android TV" (sent to Jellyfin server)
- Keep references consistent with Moonfin branding in user-facing strings
