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
  uploads them as artifacts, and publishes the AAB to Play on pushes to `main` / `v*` tags.

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

Builds run on **every push and PR**. The **publish** job runs only on pushes to `main` or
`v*` tags in the `ricardo-duarte-av/slskdAndroid` repo (adjust the `github.repository`
check in `ci.yml` if you fork/rename).

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

The publish job decodes the JSON, sets `PLAY_SERVICE_ACCOUNT_JSON` to its path, and runs:

```bash
./gradlew :app:publishReleaseBundle -Pslskd.enablePlayPublishing
```

Defaults (overridable via env in `ci.yml`): **track = `internal`**, **status = `draft`**.
Change `PLAY_TRACK` to `alpha`/`beta`/`production` and `PLAY_RELEASE_STATUS` to `completed`
once you're confident. Note the Gradle Play Publisher plugin is only applied when
`-Pslskd.enablePlayPublishing` is passed, so normal builds never depend on it.

> **Version codes:** each Play upload needs a unique, increasing `versionCode`. It's `1` in
> `app/build.gradle.kts` today — bump it for every release (or wire it to the CI run number
> if you want that automated later).

## Compatibility note

Gradle Play Publisher `3.12.1` predates AGP 9. It's kept off the classpath for everyday
builds (applied only behind the opt-in flag), so if a GPP/AGP-9 incompatibility surfaces it
only affects the publish job, not local or CI builds. If `publishReleaseBundle` fails on a
GPP-vs-AGP9 issue, fall back to uploading the CI-built `app-release-aab` artifact manually
until a compatible GPP release lands.
