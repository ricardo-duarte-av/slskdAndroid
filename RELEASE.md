# Release & Publishing

This document covers signing, optimized release builds, and the CI pipeline that
builds every push and auto-publishes to the Play Store.

## What's automated

- **Release builds are minified + resource-shrunk** (`isMinifyEnabled` / `isShrinkResources`)
  with R8 using `proguard-android-optimize.txt` + `app/proguard-rules.pro`.
- **Logcat is quieted in release**: OkHttp's HTTP logging is gated to `Level.NONE`, and
  `android.util.Log.v/d/i` calls are stripped by an `-assumenosideeffects` rule. Warnings
  and errors still log.
- **Signing** is read from `keystore.properties` locally, or from environment variables in CI.
- **CI** (`.github/workflows/ci.yml`) builds debug + release (APK **and** AAB) on every push,
  uploads them as artifacts. On `v*` tags it also publishes the AAB to Play (internal, live)
  and cuts a **GitHub Release** with the debug + release APKs attached.

### Cutting a release

Publishing is tied to version tags (so each Play upload gets a deliberate, unique
`versionCode` — Play rejects duplicates). `main` pushes only build + upload artifacts.

> ⚠️ **Never tag a commit whose message contains `[skip ci]`.** GitHub applies the skip
> directive to any push event naming that commit as its head — a tag push included — so the
> publish and release jobs are silently skipped and you get a tag with no release. This bit
> v0.3.0 once, when the tag landed on a screenshots-regeneration commit. If you need to tag
> such a commit, put an empty commit on top first and tag that.

1. Bump `versionCode` (and usually `versionName`) in `app/build.gradle.kts`, commit, push.
2. Tag it: `git tag vX.Y.Z && git push origin vX.Y.Z`. The tag triggers:
   - **Play** → AAB uploaded to the internal track and released **live** to internal
     testers (status `completed`), no manual promotion needed.
   - **GitHub Release** → created with `slskdAndroid-vX.Y.Z-debug.apk` and
     `slskdAndroid-vX.Y.Z-release.apk` as downloads.

## The upload keystore

A 4096-bit RSA upload keystore was generated at `app/release/upload-keystore.jks`
(validity 10000 days, alias `upload`). **It is gitignored** (`*.jks`) and must never be
committed. Credentials live in `keystore.properties` (also gitignored):

```properties
storeFile=release/upload-keystore.jks
storePassword=<store password>
keyAlias=upload
keyPassword=<key password>
```

> ⚠️ **Back up `app/release/upload-keystore.jks` and its passwords somewhere safe**
> (password manager + offline copy). If you lose them you cannot ship updates to the
> same app listing without going through Google's key-reset process. The passwords were
> printed once in the chat that generated the key — store them now.

The build leaves the release variant **unsigned** if neither `keystore.properties` nor the
signing env vars are present (so contributors/forks can still build).

### Recreating the keystore (if ever needed)

```bash
keytool -genkeypair -v \
  -keystore app/release/upload-keystore.jks \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=slskdAndroid, OU=Mobile, O=Aguiar Vieira, L=Lisbon, ST=Lisbon, C=PT"
```

## Building releases locally

```bash
./gradlew :app:assembleRelease   # signed APK  -> app/build/outputs/apk/release/
./gradlew :app:bundleRelease      # signed AAB  -> app/build/outputs/bundle/release/
```

## GitHub Actions secrets

Add these under **Repo → Settings → Secrets and variables → Actions**:

| Secret | What it is | How to produce it |
| --- | --- | --- |
| `KEYSTORE_BASE64` | The keystore file, base64-encoded | `base64 -w0 app/release/upload-keystore.jks` |
| `KEYSTORE_PASSWORD` | Store password | from `keystore.properties` |
| `KEY_ALIAS` | `upload` | from `keystore.properties` |
| `KEY_PASSWORD` | Key password | from `keystore.properties` |
| `PLAY_SERVICE_ACCOUNT_JSON_BASE64` | Play service-account JSON, base64-encoded | see below: `base64 -w0 play-service-account.json` |

The CI decodes the keystore to a temp file and exposes it via `KEYSTORE_FILE`; the app's
`signingConfigs.release` reads `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
`KEY_PASSWORD` from the environment.

Builds run on **every push and PR**. The **publish** job runs only on `v*` tags in the
`ricardo-duarte-av/slskdAndroid` repo (adjust the `github.repository` check in `ci.yml` if you
fork/rename) — pushes to `main` build and upload artifacts but never publish.

## One-time Play Console setup (manual — Google requires it)

Google does not let you create a brand-new app purely from CI; the first upload and the
store listing must be done by hand. After that, CI takes over.

1. **Create the app** in the [Play Console](https://play.google.com/console):
   *All apps → Create app*. Set name, default language, app/game, free/paid.
   The package name must be **`pt.aguiarvieira.androidslskd`** (it cannot be changed later).
2. **Complete the required declarations** the console nags you about: privacy policy URL,
   data safety, content rating, target audience, ads, app category, contact details.
3. **Upload the first AAB manually** to a track (Internal testing is easiest) so the app
   "exists" with a version. Build it with `./gradlew :app:bundleRelease` and upload
   `app/build/outputs/bundle/release/app-release.aab`. *(Gradle Play Publisher cannot create
   the very first release on a track — it can only update an app that already has one.)*
4. **Opt in to Play App Signing** (default for new apps). You upload with the
   *upload key* above; Google re-signs with the app signing key it holds. Keep your upload
   key safe regardless.

## Creating the Play service account (for CI publishing)

1. In **Play Console → Setup → API access** (or *Users & permissions*), link a Google Cloud
   project, then create a **service account** (this jumps you to Google Cloud Console).
2. In **Google Cloud Console → IAM & Admin → Service Accounts**, create the account, then
   under its **Keys** tab → *Add key → JSON*. Download the JSON — this is the file you
   base64-encode into `PLAY_SERVICE_ACCOUNT_JSON_BASE64`.
3. Back in **Play Console → Users & permissions**, **grant** the service-account email
   access to *this app* with at least: *Release to testing tracks* / *Release to production,
   exclude devices, and use Play App Signing* and *Edit and delete draft apps* as needed.
4. Save the JSON locally only as `play-service-account.json` (gitignored). Do **not** commit it.

### How CI publishes

The build job produces the signed AAB; the publish job downloads that artifact and uploads
it straight to the **Play Developer API** via the `r0adkll/upload-google-play` action.
Defaults (in `ci.yml`): **track = `internal`**, **status = `completed`** — the release goes
live to internal testers as soon as Play finishes processing, with no "review and roll out"
step in the Console. (It was `draft` until 0.3.x, which meant every tagged upload sat waiting
for someone to promote it by hand.) The R8 `mapping.txt` is uploaded alongside it (when
present) for crash deobfuscation.

To go further, change `tracks` to `alpha`/`beta`/`production` in `ci.yml`. Two caveats before
pointing this at **production**: every `v*` tag would then ship to all users with no human
gate, and Google requires an app's *first* production release to be created manually in the
Console — the API rejects it until one exists.

> **Version codes:** each Play upload needs a unique, increasing `versionCode`. It lives in
> `app/build.gradle.kts` (`11` as of v0.3.0) — bump it for every release, or Play rejects the
> upload as a duplicate.

## Why not Gradle Play Publisher?

The Gradle Play Publisher plugin (`com.github.triplet.play` 3.12.1) targets AGP's old
internal `BaseAppModuleExtension`, which **AGP 9 removed** — applying it fails with
`Extension of type 'BaseAppModuleExtension' does not exist`. Until GPP ships AGP-9 support,
publishing goes through the Play Developer API directly (decoupled from the Gradle build),
which is what the `publish` job does.
