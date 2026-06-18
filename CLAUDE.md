# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**slskdAndroid** is a native Android client for [slskd](https://github.com/slskd/slskd) — a headless Soulseek (peer-to-peer file sharing) daemon that exposes a REST API and a SignalR (WebSocket) interface. This app is the mobile front-end that talks to a user's running slskd instance over the network.

The UI target is **Material 3 Expressive** (the dynamic, motion-rich evolution of Material 3 — expressive shapes, spring-based motion, dynamic color), via `androidx.compose.material3` 1.5.0-alpha and `MaterialExpressiveTheme`.

## Toolchain (bleeding edge — June 2026)

Versions are centralized in `gradle/libs.versions.toml`.

- **Gradle 9.5.1**, **AGP 9.2.1**, **Kotlin 2.4.0**, **KSP 2.3.9** (KSP2, decoupled from the Kotlin version), **Hilt 2.59.2**.
- **compileSdk / targetSdk 37, minSdk 26.** SDK is at `/home/daedric/android-sdk`; build-tools `37.0.0` and platform `android-37` are required and installed.
- **Compose:** BOM `2026.05.01` for the common artifacts, with `androidx.compose.material3` **pinned to `1.5.0-alpha21`** (overrides the BOM) for the Expressive APIs.
- **JDK 17** for compilation (system Java is 21; toolchain targets 17).

### Bleeding-edge gotchas already hit (don't re-discover these)

- **AGP 9 has built-in Kotlin** — do **not** apply `org.jetbrains.kotlin.android` (it errors). `com.android.application`/`com.android.library` compile Kotlin directly. The Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` *is* still applied explicitly.
- **AGP 9 `CommonExtension` is non-generic** (`CommonExtension`, not `CommonExtension<*, *, *, *, *, *>`), and exposes only getters — use property access (`defaultConfig.minSdk = 26`, `buildFeatures.compose = true`), not the `defaultConfig { }` / `buildFeatures { }` block DSL, inside convention plugins. `targetSdk` was removed from the *library* DSL (set it only on the application).
- **Hilt 2.59.2 bundles `kotlin-metadata-jvm:2.2.20`** which can't read Kotlin 2.4.0 class metadata. The Hilt convention plugin force-adds `kotlin-metadata-jvm` (pinned to the Kotlin version) to the KSP processor classpath to fix it. Hilt 2.58+ also requires AGP 9.
- **`build-logic`'s `Project.libs` catalog accessor must be `internal`** — if public, it leaks onto module build scripts' classpath and shadows the generated `libs` accessor in the script body (symptom: `Unresolved reference: androidx` in `dependencies {}` while the `plugins {}` block still resolves).
- `hiltViewModel()` now lives in `androidx.hilt.lifecycle.viewmodel.compose` (the `androidx.hilt.navigation.compose` one is deprecated).
- **KSP2 masks plain Kotlin errors.** With Hilt, a missing import or unresolved symbol in a `@Module`/injected class surfaces as `[ksp] … 'SomeType' could not be resolved` / `PROCESSING_ERROR`, *not* a normal `unresolved reference`. To find the real error, temporarily remove the offending `@Module` (or the `slskd.hilt` plugin) and run `:module:compileDebugKotlin` — the Kotlin compiler then reports the true diagnostic. Also: a type that appears in an injected class's **public constructor or members** must be resolvable by the *consuming* module's KSP (so producing modules expose such deps with `api`, or keep third-party types out of the public surface — e.g. build the verifier's `OkHttpClient` inside the function, not as a field).

## Connection & auth (API key only)

There is **no username/password login** — the app authenticates with an slskd **API key** sent in the `X-API-Key` header. The user is **forced through a first-run setup**: `MainViewModel` reads persisted settings and gates the start destination — `CONNECTION_SETUP_ROUTE` when unconfigured, `SEARCH_ROUTE` once configured. Setup submits a URL + API key, which `DefaultConnectionSettingsRepository.verifyAndSave` **validates against the server** (`GET /api/v0/application`, which requires auth) before persisting; only on success does it save and navigate onward.

- Persistence: `core:datastore` (Preferences DataStore, file `slskd_connection`).
- Dynamic targeting: Retrofit is built with a **placeholder base URL**; `SlskdAuthInterceptor` rewrites scheme/host/port per request from `SlskdConnectionState.current` and adds the API key. `SlskdConnectionState` is a `@Singleton` mutable holder kept in sync with persisted settings by the repository (via an `@ApplicationScope` collector). Networking fails fast if unconfigured.
- `applicationId` is `pt.aguiarvieira.androidslskd` (debug builds get a `.debug` suffix). The source `namespace`/packages remain `com.slskdandroid`.

## Live search

`SearchRepository.search(query)` returns a `Flow<SearchProgress>` (not a one-shot list): it POSTs the search over REST to get an id, then streams the search hub's `RESPONSE` events via `SlskdSearchHub` (filtered by id), accumulating responses **keyed by username** and emitting a growing list. On the hub's `UPDATE`/`isComplete` event it reconciles with a final REST `getSearchResponses` fetch — which backfills any responses that arrived before the hub connection was established — then completes. The UI (`SearchUiState.Searching`/`Complete`) shows incremental results with a live progress bar.

## Understanding slskd (you will need to browse this)

The app is meaningless without matching slskd's contract. When implementing networking, **fetch and read the upstream docs rather than guessing**:

- **Repo:** https://github.com/slskd/slskd
- **REST API:** slskd serves an OpenAPI/Swagger spec from a running instance (typically `http://<host>:5030/swagger`). The API source lives under `src/slskd/**/API/Controllers` in the repo. Endpoints cover searches, transfers (downloads/uploads), browsing peer shares, rooms/chat, and server/session state.
- **Real-time:** slskd pushes live updates over **SignalR** hubs (ASP.NET Core's WebSocket layer), not plain WebSockets, mounted under `/hub/<name>` (e.g. `/hub/search`). The Android client uses `com.microsoft.signalr` — a raw OkHttp WebSocket is not sufficient. Hubs authorize under `AuthPolicy.Any`, so the **`X-API-Key` header** authenticates the connection (set via `HubConnectionBuilder…withHeader`). **The Java SignalR client deserializes payloads with Gson, not kotlinx.serialization** — keep separate Gson-friendly wire types (nullable fields, camelCase names) for hub events; see `SearchHubEvent.kt`.
  - **Search hub** (`src/slskd/Search/API/Hubs/SearchHub.cs`): server→client methods `LIST` (all searches on connect), `CREATE`, `UPDATE` (a `Search`; `isComplete` is a serialized bool), `RESPONSE` (`{ searchId, response }`), `DELETE`. All broadcast to every client, so filter by `searchId`. Browse other `Hubs` (transfers, logs, application) the same way for those features.
- **Auth:** slskd uses API keys and/or JWT bearer tokens obtained from a login endpoint. Confirm the current scheme against the repo before implementing auth.

Use WebFetch on the above (or the live Swagger JSON of a configured instance) to confirm exact paths, request/response shapes, and hub method names before writing networking code — these change between slskd releases.

### Live dev server (temporary)

A running slskd instance is available for checking real HTTP responses during development:

- **Base URL:** `https://slsk.aguiarvieira.pt/`
- **API key:** `abcdefghijklmnopqrstuvwxyz` (sent in the `X-API-Key` header)

Example: `curl -H "X-API-Key: abcdefghijklmnopqrstuvwxyz" https://slsk.aguiarvieira.pt/api/v0/application`. This key is rotated when not in use — treat it as throwaway, not a secret to protect.

## Architecture

Follow Google's official architecture guidance, as demonstrated by [NowInAndroid](https://github.com/android/nowinandroid). Kotlin + Jetpack Compose, MVVM, Hilt DI, multi-module by feature.

- **Offline-first**: a local source of truth (Room/DataStore) where it makes sense (e.g. saved searches, transfer history, server config). Live transfer/search state may be ephemeral and SignalR-driven rather than persisted — decide per feature.
- **Unidirectional data flow**: events down, immutable state up via Kotlin `Flow`/`StateFlow`.
- **Module layout**: `app/` (navigation + scaffolding), `feature:<name>:{api,impl}`, and `core:*`. Currently present: `app`, `core:{model,common,designsystem,datastore,network,data}`, and `feature:{search,connection,downloads,uploads,rooms,chat,users,browse}:{api,impl}`. Add `core:database`/`core:ui` as features need them.
- **Top-level navigation**: the 7 sections (Search, Downloads, Uploads, Rooms, Chat, Users, Browse) are driven by `app/navigation/SlskdApp.kt` using Material 3 **`NavigationSuiteScaffold`** (adaptive: bottom bar on compact, rail on medium, drawer on expanded). Each `feature:*:api` exposes a `<X>_ROUTE` constant; each `impl` exposes a `NavGraphBuilder.<x>Screen()` extension wired into the app's `NavHost`. `TopLevelDestination` (enum) maps route → label → icon. Most tabs are `PlaceholderScreen` (from `core:designsystem`) pending build-out; **Search** is the only fully-implemented one. The shell only renders once a connection is configured (the connection gate in `MainActivity`). The slskd REST + SignalR clients belong in `core:network`; mapping to domain models and exposing `Flow`s belongs in `core:data` repositories. Base package is `com.slskdandroid`.
- **Convention plugins** in `build-logic/convention/` (`slskd.android.application[.compose]`, `slskd.android.library[.compose]`, `slskd.android.feature`, `slskd.hilt`) own all shared build config. New modules apply these aliases (e.g. `alias(libs.plugins.slskd.android.feature)`) rather than configuring AGP/Kotlin directly.
- **Material 3 Expressive theming** lives in `core:designsystem` — keep theme, dynamic color, motion specs, and shared components there; feature modules consume them and never define their own colors/shapes.

### Standard patterns
ViewModel exposes a single `StateFlow<UiState>` and a `onAction(action)` entry point; UiState is a `sealed interface` (Loading/Success/Error); Screens split into a stateful `Route` (collects state, holds `hiltViewModel()`) and a stateless `Screen` (pure, previewable). Repositories are interfaces with `OfflineFirst*`/network-backed implementations. See `~/.claude/skills/claude-android-skill/references/` for full templates.

## Build & test

**Do not run Gradle builds (`assembleDebug`, etc.) — the user compiles and runs the app themselves.** Write the code and stop; don't invoke the wrapper to "verify" unless explicitly asked. The commands below are for reference / for the user.

Use the Gradle wrapper (`./gradlew`). All of these are verified working:

- **Build APK:** `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- **Unit tests:** `./gradlew testDebugUnitTest` (single test: `./gradlew :feature:search:impl:testDebugUnitTest --tests "com.slskdandroid.feature.search.impl.SearchViewModelTest"`)
- **Lint:** `./gradlew lintDebug` (per module: `./gradlew :app:lintDebug`)

`local.properties` (gitignored) must point at the SDK: `sdk.dir=/home/daedric/android-sdk`.

## Release, signing & CI

Full details live in `RELEASE.md`; the essentials:

- **Signing:** release builds are signed via `signingConfigs.release` in `app/build.gradle.kts`, which reads `keystore.properties` (gitignored, local) or the `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars (CI). With neither present the release is left **unsigned** (so forks/PRs still build). The upload keystore is `app/release/upload-keystore.jks` (gitignored — never commit it). Bump `versionCode` for every Play upload (it must strictly increase).
- **Optimized release:** `isMinifyEnabled` + `isShrinkResources` with R8 (`proguard-android-optimize.txt` + `app/proguard-rules.pro`). Keep rules cover kotlinx.serialization, the SignalR/Gson `Hub*` wire types (field names must survive R8), and Retrofit/OkHttp. `-assumenosideeffects` strips `Log.v/d/i`; OkHttp HTTP logging is already `Level.NONE` in release — so release logcat stays quiet (warnings/errors still log).
- **CI:** `.github/workflows/ci.yml`. Trigger model:

  | Trigger | What runs |
  | --- | --- |
  | any push / PR | `build` → debug+release APK + AAB + `mapping.txt` as artifacts |
  | `v*` tag | build **+** Play upload (internal/draft) **+** GitHub Release with APKs |

  - `build` job (every push/PR): builds debug + release **APK and AAB** plus the R8 `mapping.txt`, uploaded as artifacts.
  - `publish` job (`v*` tags only): uploads the signed AAB to the Play **internal** track as a **draft** via the Play Developer API (`r0adkll/upload-google-play`). **Do not use Gradle Play Publisher** — GPP 3.12.1 targets AGP's removed `BaseAppModuleExtension` and is incompatible with AGP 9.
  - `release` job (`v*` tags only): creates a **GitHub Release** with the debug + release APKs attached as downloads.
- **Runner gotcha:** GitHub runners' bundled `sdkmanager` can't see API 37. CI downloads the exact `commandlinetools-linux-14742923` build and installs `platforms;android-37.0` (note the `.0`) + `build-tools;37.0.0`.
- **Secrets** (repo Actions secrets): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON_BASE64`.
- **Cutting a release:** bump `versionCode`/`versionName`, then push a `vX.Y.Z` tag — the tag triggers both the Play draft upload and the GitHub Release with APKs (`main` pushes only build).
- **Benign CI noise:** the marketplace actions still target Node 20, so runs show "Node.js 20 is deprecated" annotations (the runner force-runs them on Node 24). Cosmetic — ignore until those actions publish updates.
