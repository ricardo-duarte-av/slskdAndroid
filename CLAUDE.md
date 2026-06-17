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
- `applicationId` is `pt.aguiarvieira.androidslksd` (debug builds get a `.debug` suffix). The source `namespace`/packages remain `com.slskdandroid`.

## Understanding slskd (you will need to browse this)

The app is meaningless without matching slskd's contract. When implementing networking, **fetch and read the upstream docs rather than guessing**:

- **Repo:** https://github.com/slskd/slskd
- **REST API:** slskd serves an OpenAPI/Swagger spec from a running instance (typically `http://<host>:5030/swagger`). The API source lives under `src/slskd/**/API/Controllers` in the repo. Endpoints cover searches, transfers (downloads/uploads), browsing peer shares, rooms/chat, and server/session state.
- **Real-time:** slskd pushes live updates (search responses, transfer progress, chat) over **SignalR** hubs (ASP.NET Core's WebSocket layer), not plain WebSockets. Browse the `Hubs` in the slskd source to learn hub names, methods, and event payloads. The Android client needs a SignalR client (e.g. `com.microsoft.signalr`) — a raw OkHttp WebSocket is not sufficient.
- **Auth:** slskd uses API keys and/or JWT bearer tokens obtained from a login endpoint. Confirm the current scheme against the repo before implementing auth.

Use WebFetch on the above (or the live Swagger JSON of a configured instance) to confirm exact paths, request/response shapes, and hub method names before writing networking code — these change between slskd releases.

## Architecture

Follow Google's official architecture guidance, as demonstrated by [NowInAndroid](https://github.com/android/nowinandroid). Kotlin + Jetpack Compose, MVVM, Hilt DI, multi-module by feature.

- **Offline-first**: a local source of truth (Room/DataStore) where it makes sense (e.g. saved searches, transfer history, server config). Live transfer/search state may be ephemeral and SignalR-driven rather than persisted — decide per feature.
- **Unidirectional data flow**: events down, immutable state up via Kotlin `Flow`/`StateFlow`.
- **Module layout**: `app/` (navigation + scaffolding), `feature:<name>:{api,impl}`, and `core:*`. Currently present: `app`, `core:{model,common,designsystem,datastore,network,data}`, `feature:{search,connection}:{api,impl}`. Add `core:database`/`core:ui` as features need them. The slskd REST + SignalR clients belong in `core:network`; mapping to domain models and exposing `Flow`s belongs in `core:data` repositories. Base package is `com.slskdandroid`.
- **Convention plugins** in `build-logic/convention/` (`slskd.android.application[.compose]`, `slskd.android.library[.compose]`, `slskd.android.feature`, `slskd.hilt`) own all shared build config. New modules apply these aliases (e.g. `alias(libs.plugins.slskd.android.feature)`) rather than configuring AGP/Kotlin directly.
- **Material 3 Expressive theming** lives in `core:designsystem` — keep theme, dynamic color, motion specs, and shared components there; feature modules consume them and never define their own colors/shapes.

### Standard patterns
ViewModel exposes a single `StateFlow<UiState>` and a `onAction(action)` entry point; UiState is a `sealed interface` (Loading/Success/Error); Screens split into a stateful `Route` (collects state, holds `hiltViewModel()`) and a stateless `Screen` (pure, previewable). Repositories are interfaces with `OfflineFirst*`/network-backed implementations. See `~/.claude/skills/claude-android-skill/references/` for full templates.

## Build & test

Use the Gradle wrapper (`./gradlew`). All of these are verified working:

- **Build APK:** `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- **Unit tests:** `./gradlew testDebugUnitTest` (single test: `./gradlew :feature:search:impl:testDebugUnitTest --tests "com.slskdandroid.feature.search.impl.SearchViewModelTest"`)
- **Lint:** `./gradlew lintDebug` (per module: `./gradlew :app:lintDebug`)

`local.properties` (gitignored) must point at the SDK: `sdk.dir=/home/daedric/android-sdk`.
