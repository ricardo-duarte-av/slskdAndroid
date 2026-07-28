# Material 3 Expressive — compliance plan

Audit of the current UI against Material 3 / M3 Expressive, plus a staged remediation plan.

Audited 2026-07-28 against `androidx.compose.material3` **1.5.0-alpha23** (pinned in
`gradle/libs.versions.toml`, overriding Compose BOM `2026.05.01`). The audit was originally taken
against alpha21; the pin was bumped to alpha23 on 2026-07-28 — see Stage 0.

**Scope note:** every finding below cites a file and line that was read during the audit.

**API surface:** the component inventory below was taken by decompiling the alpha23 AAR
(`~/.gradle/caches/.../material3-android/1.5.0-alpha23/material3.aar`), so the named APIs are real
rather than remembered. Since the project now pins alpha23, that inventory matches the build.

---

## Scorecard

| Category | Score | Status |
|---|---|---|
| Color tokens | 7/10 | pass |
| Typography | 6/10 | warn |
| Shape | 5/10 | warn |
| Elevation | 8/10 | pass |
| Components | 5/10 | warn |
| Layout | 4/10 | warn |
| Navigation | 7/10 | pass |
| Motion | 6/10 | warn |
| Accessibility | 4/10 | warn |
| Theming | 6/10 | warn |
| **Overall** | **58/100** | |

### What's already right

Worth stating plainly, because it shapes the plan — the foundations are sound and none of the
stages below require re-architecting anything.

- **Dynamic color is correctly implemented.** `SlskdTheme` (`core/designsystem/.../theme/Theme.kt:33`)
  gates `dynamicLightColorScheme`/`dynamicDarkColorScheme` on API 31+ with static baseline fallback
  — exactly the spec pattern.
- **`MaterialExpressiveTheme` + `MotionScheme.expressive()`** are wired at the root (`Theme.kt:41`).
- **Tonal elevation, not shadows.** The `surfaceContainerLow/High/Highest` ladder in
  `nestedCardColor` (`DepthCard.kt:80`) is the MD3 depth model done properly.
- **Adaptive navigation.** `NavigationSuiteScaffold` (`SlskdApp.kt:159`) gives bottom bar / rail /
  drawer across size classes for free.
- **Type scale roles are used consistently** (`labelSmall`, `bodyMedium`, `titleMedium`, …) rather
  than hardcoded `sp` values.

---

## Findings

### Critical (score 0–3 territory)

**A1. Launch theme is MD2-era and light-locked** — `app/src/main/res/values/themes.xml:5`
```xml
<style name="Theme.SlskdAndroid" parent="android:Theme.Material.Light.NoActionBar">
```
Two problems. It's the **framework Material (MD2)** theme, which the MD3 guidance explicitly calls
out as a thing not to mix with MD3. And it's hard-locked to `.Light`, so the pre-Compose window
background is white — a white flash on every cold start in dark mode, before `SlskdTheme` takes
over. Fix: a DayNight-capable parent and a `windowBackground` that follows the system, or adopt the
androidx SplashScreen API.

**A2. Long-press-only actions are unlabelled for TalkBack** — 5 sites, 0 semantics ✅ *fixed in Stage 1*

> **Correction.** The first draft of this audit claimed these actions were *unreachable* by screen
> readers. That was wrong: `combinedClickable` does publish `onLongClick` to the semantics tree
> (that is what its `onLongClickLabel` parameter is for), so the gesture always worked. The real
> defect was narrower — all 5 sites passed **no label**, so TalkBack announced a bare
> "double-tap and hold" with no indication of what it does, and the codebase had **zero** uses of
> `Modifier.semantics`, `customActions`, or `onClickLabel` to name them.

Long-press is the *only* route to replying to a message (`ChatScreen.kt`, `RoomsScreen.kt`),
selecting a whole directory (`SearchDetailScreen.kt`), and entering multi-select on a transfer
(`DownloadsScreen.kt`, `UploadsScreen.kt`). Fixed by adding `onLongClickLabel` plus a named
`CustomAccessibilityAction` at each site, so the action also appears in TalkBack's actions menu
rather than depending on a discoverable gesture. Severity: warning, not critical.

**A3. Zero localization** — 0 `stringResource` calls, ~250–350 literals
`app/src/main/res/values/strings.xml` contains one string (`app_name`). Every label, error, and
`contentDescription` in the app is a Kotlin literal; no feature module even has a `res/` directory.
`android:supportsRtl="true"` is declared but meaningless without externalized strings. Heuristic
count of user-facing literals by module: search 83, downloads 52, uploads 46, browse 36, rooms 36,
chat 31, users 27, designsystem 18, app 14, settings 14, connection 10.

### Warnings

**B1. Notification icons are platform stock** — `MessageNotifier.kt:180`, `NotificationService.kt:317`
`android.R.drawable.stat_notify_chat` / `stat_notify_sync`. Off-brand, inconsistent across OEMs,
and not the monochrome app-specific vector the spec wants. `app/src/main/res/drawable/` currently
holds only `ic_launcher_foreground.xml`.

**B2. `background` where `surface` belongs** — `MainActivity.kt:59`
`Surface(color = MaterialTheme.colorScheme.background)`. MD3 de-emphasizes `background` in favour
of `surface` / `surfaceContainer*` roles.

**B3. Alpha-modified content colors break contrast guarantees** — `ChatScreen.kt:179`, `RoomsScreen.kt`
`contentColor.copy(alpha = 0.7f)` on `labelSmall` timestamps. MD3 wants a distinct role
(`onSurfaceVariant`) — alpha math on top of a container color has no contrast guarantee, and at
`labelSmall` this is very likely under 4.5:1 in at least one of light/dark.

**B4. Accent cards mix color without repairing the pairing** — `DepthCard.kt:88`
`lerp(surface, primary, 0.06f/0.12f/0.20f)` produces a container color that is not a scheme role,
so `Surface`'s `contentColorFor()` can't resolve a matching `on*` color and content falls through
to whatever `LocalContentColor` is. At depth 2 (20% primary) that's `onSurface` text on a
primary-tinted background — the tonal-pairing anti-pattern, and the most likely real contrast
failure in the app. Affects Search/Downloads/Uploads whenever Card style = Accent.

**B5. Shape magic numbers** — `DepthCard.kt:98`
`RoundedCornerShape(16.dp / 12.dp / 8.dp)` hardcoded rather than driven from
`MaterialTheme.shapes`. `MaterialExpressiveTheme` is called without `shapes` or `typography`
(`Theme.kt:41`), so both fall back to defaults. Confirmed available on `Shapes` in alpha23:
`largeIncreased`, `extraLargeIncreased`, `extraExtraLarge` — the expressive corner tokens the
depth ladder should be reading from.

**B6. Hand-rolled components where M3 has one** — 0 `ListItem` uses in the codebase
`ListItem` exists (alpha23 also ships an `InteractiveListItem` variant and
`defaultListItemShapes`). Candidates:
- Settings/filter toggle rows are `Row + Text + Switch` (`SearchDetailScreen.kt:358`) → `ListItem`
  with `trailingContent`.
- The selection bar is a bare `Surface + Row` (`SearchDetailScreen.kt:148`) → `FloatingToolbar` or
  `FlexibleBottomAppBar`, both confirmed present.
- Conversation and room rows → `ListItem` with `leadingContent`/`trailingContent`.

**B7. Only one of the expressive component set is in use.**
`LinearWavyProgressIndicator` (`TransferStatusLine.kt:311`) is adopted, but the search progress bar
next to it is a plain `LinearProgressIndicator` (`SearchDetailScreen.kt:132`) — inconsistent within
the same app. Confirmed present and unused: `ButtonGroup`, `SplitButton`, `FloatingToolbar`,
`LoadingIndicator`, `Carousel`/`MultiAspectCarousel`, `AppBarRow`/`AppBarColumn` (overflow-aware
action containers). No shape-morph API found in `material3` itself — that lives in
`androidx.graphics:graphics-shapes`, which is not currently a dependency.

**B8. Top app bars have no scroll behavior** — 11 `TopAppBar` call sites, all small, none with
`scrollBehavior`. MD3 expects `TopAppBarDefaults.enterAlwaysScrollBehavior()` (or
`exitUntilCollapsed` with a taller bar) on scrolling content. The expressive bar variants are all
present: `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar`, `TwoRowsTopAppBar`.

**B9. No transient-feedback surface.** Zero `Snackbar`/`SnackbarHost` in the app. Errors render as
static centered text, and destructive bulk actions (`BulkRemove`, `RemoveSelected` —
`DownloadsViewModel.kt:104`) commit with no confirmation and no undo. Snackbar-with-undo is the MD3
pattern for exactly this.

**B10. Back is not predictive.** 8 `BackHandler` call sites, no `PredictiveBackHandler`, and no
`android:enableOnBackInvokedCallback` in the manifest. With `targetSdk 37` predictive back is
on by default *(verify current default for 37)*, so these in-app back steps animate the whole
activity rather than the in-app transition. Related: `NavHost` (`SlskdApp.kt:176`) specifies no
enter/exit transitions, so destination changes use library defaults rather than shared-axis.

**B11. Phone-first layout.** No window size class usage anywhere for *content* (the nav suite
adapts, the screens inside it don't). On tablet/desktop widths every list stretches to full width
with no max-width constraint — the guidance is 840–1040dp. No foldable/hinge handling.

**B12. Off-grid spacing.** Padding values include 2/4/6/10/12/14dp against the I/O 2026 8dp
spacing system (e.g. `DirectoryHeader` `top = 6.dp, bottom = 2.dp`, `SearchDetailScreen.kt:466`).
No spacing tokens exist in `core:designsystem`.

**B13. Icon-only navigation** — `SlskdApp.kt:171`
Seven unlabeled destinations. `contentDescription` keeps it screen-reader accessible, but the spec
warns on discoverability. Deliberate choice — listed for completeness, not necessarily to change.

---

## Staged plan

Sequencing rationale: Stage 1 is cheap and touches few files. Stage 2 is mechanical but touches
*every* screen, so it should land as one atomic change with nothing else in flight. Stages 3–5 all
edit call sites, so they must come after Stage 2 to avoid rebasing string extraction onto moved
code.

### Stage 0 — API verification spike ✅ **DONE**

**DONE** (2026-07-28).

1. **Pin bumped alpha21 → alpha23.** The catalog had said `1.5.0-alpha21` since the *initial
   commit* (`d96c132`, 2026-06-17) — deliberate, not drift — but alpha23 was already out and the
   expressive surface is still moving, so bumping *before* Stage 3 avoids adopting an API that
   changes shape a release later. `assembleDebug`, 74 unit tests and `lintDebug` all pass on
   alpha23 with no source changes; dependency resolution confirms `material3-android:1.5.0-alpha23`
   and `material3-adaptive-navigation-suite:1.5.0-alpha23` (both overriding the BOM's 1.4.0). The
   only warning is a pre-existing `rememberModalBottomSheetState` deprecation in `RoomsScreen`,
   unrelated to the bump.
2. **The component inventory in B5–B8 now matches the build**, so Stage 3 no longer needs a
   confirmation spike.
3. **Still open:** the required `@OptIn` marker per component
   (`ExperimentalMaterial3ExpressiveApi` vs `ExperimentalMaterial3Api`) is only discoverable at
   compile time and moves between alphas — resolve it per component as Stage 3 adopts each.

*Risk:* alpha-to-alpha API churn. Anything adopted in Stage 3 can break on the next bump, so Stage 3
is best done soon after this pin rather than long after it.

### Stage 1 — Foundations ✅ **DONE**

Landed 2026-07-28. `assembleDebug` + 74 unit tests + `lintDebug` all green.

| # | Item | Outcome |
|---|---|---|
| A1 | DayNight launch theme | Added `values-night/themes.xml` with the dark framework parent; both variants keep `?android:colorBackground` |
| A2 | Label the 5 long-press sites | `onLongClickLabel` + named `CustomAccessibilityAction` at each; `onClickLabel` where the tap is real |
| B1 | Monochrome notification icons | New `ic_stat_message` (core:data) and `ic_stat_watching` (app) vectors replace `android.R.drawable.stat_notify_*` |
| B2 | `background` → `surface` | `MainActivity.kt` |
| B3 | Alpha content colours | Timestamps now use `onSurfaceVariant` on surface cards; on `primaryContainer` cards the type scale carries the de-emphasis instead |
| B4 | Accent card pairing | Blends toward `primaryContainer`/`onPrimaryContainer` in lockstep; `DepthCard` gained a `contentColor` param resolving via `contentColorFor` |
| — | No-op `onClick` on message cards | Kept (it hosts the long-press ripple) but hidden from a11y via `semantics { onClick(action = null) }` |
| — | *Bonus:* pre-existing `MissingPermission` lint error | Explicit `POST_NOTIFICATIONS` check in `MessageNotifier.post()` + a `core:data` manifest declaring it |

**Still needs a device pass** (cannot be verified from a build): TalkBack over a message thread,
cold start in dark mode, and contrast on accent cards at depth 2 in both light and dark.

### Stage 2 — Localization (large, mechanical) — **IN PROGRESS**

~365 literals across 11 modules. **Decisions settled 2026-07-28:**

1. **Per-module `res/values/strings.xml`** in each `feature:*:impl` (NowInAndroid convention).
   Because a feature impl's package equals its namespace, `R` needs no import there — only
   `core:designsystem` does, since its files sit in a `.component` subpackage.
2. **ViewModel-originated text uses `@StringRes` ids in `UiState`**, resolved by the Composable.
   Keeps ViewModels free of `Context`, keeps the existing plain-JVM tests working (assert on the
   `Int`), and is mechanical. Applies to every `.catch { emit(Error("Couldn't load …")) }` — which
   is all 8 remaining modules.

### Stage 2 — Localization — **DONE** (extraction pass)

All 11 modules converted. **247 strings + 7 plurals** across 12 resource files. `assembleDebug`,
74 unit tests and `lintDebug` all green.

| Module | strings | plurals |
|---|---|---|
| search | 48 | 3 |
| downloads / uploads | 34 each | 1 each |
| users | 26 | — |
| rooms | 22 | 2 |
| browse | 20 | — |
| chat | 18 | — |
| settings | 13 | — |
| app | 12 | — |
| core:designsystem | 11 | — |
| connection | 9 | — |

What the rollout established:
- **Non-composable string builders became `@Composable`**: `TransferStatus.mixedSummary`,
  `formatInterval`, `Download/Upload.statusLine`, `statusLabel`, `metaLine`.
- **`ResultSort.label: String` → `@StringRes labelRes: Int`**, same shape as
  `TopLevelDestination.label` → `labelRes`.
- **`UiText` for ViewModel-originated text** — 8 UiStates changed from `String` to `UiText`; 5 test
  assertions now compare `UiText.Raw("…")`.
- **Punctuation and protocol tokens stay in code**: `" · "`, and the slskd `TransferStates` flag
  strings (`"Errored"`, `"Cancelled"`, …) which are matched against the wire format, not shown.

### Stage 2b — Remaining localization gaps ✅ **DONE**

- **`formatBytes` existed in four copies**, each pinned to `Locale.US`. Consolidated into
  `core:designsystem`'s `Formatters.kt`, with the decimal separator now taken from the locale and
  the unit symbols (`B/KB/MB/GB/TB`) moved to resources. **Sizes stay binary (1024-based)** —
  `Formatter.formatFileSize` would localize units for free but is SI on modern Android, which would
  visibly shift every size in the app (1 GiB → "1.07 GB"); Soulseek reports binary.
- **`qualityLabel`, `formatDuration`, `fileMeta` were duplicated** between search and browse,
  as were their test files. Now shared. `kHz`/`kbps` stay untranslated — SI symbols, identical
  across locales; the defect there was the decimal separator, not the symbol.
- **Locale is read observably.** `Locale.getDefault()` inside a composable is invisible to
  composition, so a runtime locale change would leave already-composed text stale. The composable
  formatters read `LocalConfiguration.current.locales[0]`; pure variants take an explicit `Locale`
  so they stay plain-JVM testable and deterministic. (Caught by lint's `NonObservableLocale`.)
- **Time formats are localized**: `ofPattern("HH:mm")` → `ofLocalizedTime(SHORT)` in chat and
  rooms, `ofPattern("MMM d, HH:mm")` → `ofLocalizedDateTime(MEDIUM, SHORT)` in the search list.
  These forced a 24-hour clock and US field order on every locale.
- **slskd's error text is now translatable.** `ConnectionFailure` (in `core:model`, so features
  don't need `core:network`) replaces `IOException("Authentication failed — check the API key")`
  with typed cases — `InvalidUrl`, `AuthRejected`, `HttpError(code)`, `Unreachable` — mapped to
  resources in the ViewModel.
- **One Stage 2 miss found and fixed**: the browse selection bar still had a hand-rolled
  `"$count file${if (count == 1) "" else "s"}"`.

Still open: repositories other than the connection tester surface raw `Throwable.message` (Retrofit
/ OkHttp text) as `UiText.Raw`. Making those typed too is a larger refactor across every repository
and is not covered here.

### Stage 3 — Component conformance ✅ **DONE**

- **B5 shape tokens.** `nestedCardShape` reads `MaterialTheme.shapes` (large/medium/small) instead
  of literal 16/12/8dp. Same values, but a token now.
- **B6 `ListItem`.** Settings rows, search options toggles and chat conversation rows. Toggle rows
  also gained `Modifier.toggleable(role = Role.Switch)` so a screen reader announces one control.
  *alpha23 deprecated the `headlineContent`-first overload — the trailing-lambda form is used.*
- **B7 progress consistency.** Search uses `LinearWavyProgressIndicator`, matching the transfer bars.
- **B8 app bar scroll behaviour.** 17 of 19 `TopAppBar`s; the two omissions
  (`ConnectionSetupScreen`, `PlaceholderScreen`) have no scrolling content.
- **B9 transient feedback.** `SnackbarHost` on Downloads and Uploads, fed by a one-shot `Channel`
  of events alongside the state flow. This also fixed a real defect: `runBulk` swallowed every
  failure, so a bulk action that failed was indistinguishable from one that worked until the next
  poll contradicted it. Failures are now counted and reported.
  - **Undo is offered only where it is real.** Downloads can be restored by re-enqueuing
    (username, filename, size) — exactly what Retry does. **Uploads deliberately have no Undo**:
    they are peer-driven and slskd exposes no re-initiation endpoint, so the button would do
    nothing.
- **Expressive components — none adopted.** See below; all three candidates were rejected on
  inspection or reverted after testing.

Three candidates were considered. **None survived**, which is a result rather than a gap — the
components exist, but none of them fits the content we actually have:

- **`HorizontalFloatingToolbar` for the selection bars** — *tried, then reverted.* It is designed
  as a compact, content-wrapping pill of icon actions and enforces a fixed
  `FloatingToolbarDefaults.containerSize`. Our selection bar carries a text summary
  ("3 files · 41.2 MB") plus two labelled buttons, and forcing `fillMaxWidth()` on it — with a
  `Spacer(weight(1f))` that needs bounded constraints the toolbar doesn't provide — produced a
  container occupying roughly half the screen on device. Using it properly would mean dropping the
  summary text and reducing the actions to icons: a redesign of the selection UX, not a component
  swap. The `Surface`+`Row` bottom bar is retained.

- **`ButtonGroup` for the sort selector** — the labels are "Upload Speed (Fastest to Slowest)" and
  "Queue Depth (Least to Most)". A segmented row of two long labels overflows badly; the dropdown
  is the right control until the labels are shortened.
- **`AppBarRow` for overflow** — it exists to collapse a *row of app bar actions* into a menu. Our
  app bars have at most two actions, and the per-peer overflow menus aren't in an app bar at all.
  Adopting it would add indirection without solving a problem we have.

### Stage 4 — Adaptive layout — **PARTLY DONE**

Done (2026-07-28):

- **B11 readable content width.** `ReadableWidth` in `core:designsystem` caps a scrolling column at
  840dp and centres it; applied to the 9 lists across Search (list + detail), Downloads, Uploads,
  Rooms, Chat and Browse (tree + files). Applied to the *lists*, not whole screens, so app bars and
  bottom bars stay full-bleed. **Below 840dp this is a no-op**, so it changes nothing on a phone —
  it only takes effect on tablets, foldables and desktop windows.
- **B12 spacing tokens.** `Spacing` (4/8/16/24/32dp) defined in `core:designsystem`. Existing
  off-grid literals (2/6/10/14dp) are *not* rewritten — see below.
- **B10 predictive back + destination motion.** `android:enableOnBackInvokedCallback="true"` stated
  explicitly rather than relying on the targetSdk default, and the `NavHost` now uses shared-axis
  (X) transitions instead of the library's default fade, which read as a cut on a 7-tab app.

Deliberately **not** done, with reasons:

- **Mass spacing migration.** Rewriting every 2/6/10/14dp literal to the 8dp grid would shift the
  density of every dense list row at once, and there is no way to judge the result except by
  looking at each screen. The tokens exist so new code has a vocabulary; migration should happen
  screen by screen alongside other visual work.
- **List-detail on large screens.** Search list to detail, and Rooms list to open room, are shaped
  like list-detail panes and would benefit on tablets. That is an architectural change to
  navigation (two panes, a selected-item concept in state, back behaviour that differs by size
  class), not a layout tweak, and it wants a design decision first.
- **`PredictiveBackHandler`.** The 8 `BackHandler` sites change state (close thread, clear
  selection) rather than navigating. Wiring the progress flow means designing a per-screen
  animation for each, which is design work rather than conformance.

**Needs a large screen to verify.** The width cap is provably inert on a phone, so a phone build
confirms nothing about it either way — it wants a tablet or a resizable emulator.

### Stage 5 — Accessibility sweep (small–medium)

- Systematic contrast audit of every `on*` pairing, light and dark, standard and high contrast.
- Touch target audit (48dp minimum) on the small icon buttons in dense rows.
- Semantics pass: heading roles, merged descendants on cards, meaningful `onClickLabel`s.
- Decide B13 (nav labels) with real data — it's a judgment call, not a defect.

**Verification:** Accessibility Scanner; TalkBack end-to-end on each of the 7 sections.

---

## Suggested order

```
Stage 1 ──────────────► ship
Stage 0 ──┐
          ├─► Stage 2 ──────────► ship (atomic)
          │        │
          └────────┴─► Stage 3 ──► ship
                          │
                          └─► Stage 4 ──► Stage 5 ──► ship
```

Stage 1 and Stage 0 can run in parallel; everything after Stage 2 is sequential because it all
edits the same call sites.

## Open questions

1. **Nav labels (B13)** — keep icon-only, or add labels? Affects Stage 5.
2. **Strings location** — per-module or shared module? Blocks Stage 2.
3. ~~**Alpha pin**~~ — resolved 2026-07-28: bumped to `1.5.0-alpha23` before Stage 3. See Stage 0.
4. **Accent card style** — B4 can be fixed by pairing an explicit content color, or by dropping the
   `lerp` for real scheme roles (`primaryContainer` ladder). The second is more correct but changes
   the look of a feature you shipped deliberately in v0.1.8.
