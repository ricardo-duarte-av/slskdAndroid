# Material 3 Expressive — compliance plan

Audit of the current UI against Material 3 / M3 Expressive, plus a staged remediation plan.

Audited 2026-07-28 against `androidx.compose.material3` **1.5.0-alpha21** (pinned in
`gradle/libs.versions.toml`, overriding Compose BOM `2026.05.01`).

**Scope note:** every finding below cites a file and line that was read during the audit.

**API surface:** `alpha21` is not present anywhere on this machine, but the **`alpha23` AAR is**
(`~/.gradle/caches/.../material3-android/1.5.0-alpha23/material3.aar`), and it was decompiled to
enumerate what actually exists. Findings below name real APIs from that artifact. Caveat: alpha23
is **two alphas newer** than the pin, so presence there does not prove presence in alpha21 — each
adoption still needs a compile check (see Stage 0).

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

### Stage 0 — API verification spike (blocker for Stage 3)

Mostly **done** — the alpha23 AAR was decompiled during this audit and the component inventory is
folded into B5–B8 above. What remains is small:

1. **Confirm against alpha21, not alpha23.** The pin is two alphas older than the artifact that was
   inspected. Cheapest check: a scratch file importing each component to be adopted, then
   `:app:compileDebugKotlin`. Anything that doesn't resolve drops out of Stage 3.
2. **Find the required `@OptIn` marker per component** (`ExperimentalMaterial3ExpressiveApi` vs
   `ExperimentalMaterial3Api`) — these move between alphas.
3. **Decide the pin.** The catalog has said `1.5.0-alpha21` since the *initial commit* (`d96c132`,
   2026-06-17) and has never been touched, so it is deliberate, not drift. But alpha23 is out and
   the expressive surface is still moving; bumping before investing in Stage 3 avoids adopting an
   API that changed shape one release later.

*Risk:* alpha-to-alpha API churn. Anything adopted in Stage 3 can break on the next bump —
which is an argument for doing Stage 3 immediately after a deliberate pin decision, not before.

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

### Stage 2b — Remaining localization gaps (not extraction)

Extraction is complete, but the app is still not fully localizable. These are **format** problems,
deliberately left rather than half-fixed:

- **`formatBytes` hardcodes `Locale.US`** (`String.format(Locale.US, "%.1f %s", …)`) so the decimal
  separator is always a point, and the unit symbols `B/KB/MB/GB/TB` are literals. Same for
  `"$it kbps"` and `"$value kHz"` in the file-metadata lines.
- **Time formats are hardcoded patterns** — `DateTimeFormatter.ofPattern("HH:mm")` in chat and
  rooms, `"MMM d, HH:mm"` in the search list. These are top-level `private val`s, so wiring them to
  resources means making them composable or context-aware; the better fix is
  `ofLocalizedTime(FormatStyle.SHORT)`, which respects the user's 12/24-hour preference. Two unused
  `*_time_format` resources were removed rather than left dangling.
- **slskd's own error text is untranslatable** — it arrives as `UiText.Raw` from `core:network`
  (`SlskdConnectionTester`) and repositories' `Throwable.message`. Fixing this means typed failures
  in `core:network`, which is a separate refactor.

**Still to verify on device:** pseudolocale (`en-XA`) run for truncation and concatenation, and an
RTL (`ar-XB`) pass — `supportsRtl="true"` is declared but has never been exercised.

### Stage 3 — Component conformance (medium, design-visible)

Depends on Stage 0 and Stage 2.

- B6: `ListItem` for settings rows, toggle rows, conversation rows, room rows.
- B8: `scrollBehavior` on all 11 top app bars; consider Medium/Large for Search and Browse.
- B7: consistent progress indicators — wavy everywhere or plain everywhere.
- B9: `SnackbarHost` in each `Scaffold`; route errors through it; add undo to destructive bulk
  actions.
- B5: shape tokens via `MaterialExpressiveTheme(shapes = …)` using `largeIncreased` /
  `extraLargeIncreased`; `DepthCard` reads the ladder from the theme instead of literals.
- Expressive components where they earn their place: selection bar → `FloatingToolbar`, sort
  dropdown → `ButtonGroup`, per-peer overflow → `AppBarRow`. All confirmed present in alpha23,
  pending the alpha21 compile check.

**Verification:** screenshot diff per screen before/after; this is the stage most likely to need
your eye rather than a test.

### Stage 4 — Adaptive layout (medium)

- B11: `currentWindowAdaptiveInfo()` / window size classes for content, not just the nav suite.
- Max content width 840–1040dp on Large/XL; consider list-detail for Search list → detail and
  Rooms list → room, which are already shaped like list-detail.
- B12: spacing tokens in `core:designsystem`, 8dp grid.
- B10: `PredictiveBackHandler` + `NavHost` shared-axis transitions.

**Verification:** foldable + tablet emulator; predictive back gesture on each nested screen.

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
3. **Alpha pin** — hold at alpha21, or move to alpha23+ before investing in expressive components?
   The pin is original and deliberate (unchanged since `d96c132`), so this is a real decision, not
   a cleanup. Blocks Stage 0 → Stage 3.
4. **Accent card style** — B4 can be fixed by pairing an explicit content color, or by dropping the
   `lerp` for real scheme roles (`primaryContainer` ladder). The second is more correct but changes
   the look of a feature you shipped deliberately in v0.1.8.
