# Google Drive sync setup

4ENote can sign you in with your Google account and upload notes to a regular, visible
**"4ENote" folder in your own Google Drive** (not the hidden "app data" space — you can see and
open the uploaded files in the normal Drive app/website).

This requires a Google Cloud project with OAuth credentials that only the project owner can
create. The app ships with an obviously-fake placeholder client ID so it still builds and runs for
everyone else; follow the steps below to plug in real credentials.

## 1. Create a Google Cloud project

1. Go to https://console.cloud.google.com/projectcreate and create a new project (or reuse an
   existing one).
2. Note the project name/ID — you'll use it in the next steps.

## 2. Enable the Google Drive API

1. In the Cloud Console, go to **APIs & Services > Library**.
2. Search for **Google Drive API** and click **Enable**.

## 3. Configure the OAuth consent screen

1. Go to **APIs & Services > OAuth consent screen**.
2. Choose **External** (unless you have a Google Workspace org and want **Internal**).
3. Fill in the required app information (app name, support email, developer contact email).
4. Under **Scopes**, you do not need to add `drive.file` here — it's requested at runtime via the
   Authorization API and Google will show a normal incremental-consent screen for it. Scopes added
   here are mainly relevant for apps requesting *sensitive/restricted* scopes that need
   verification; `drive.file` is a non-sensitive scope.
5. Under **Test users** (while the app is in "Testing" publishing status), add the Google
   account(s) you'll sign in with during development. Google will refuse to authorize accounts
   that aren't listed here until the app is verified/published.

## 4. Get your debug signing SHA-1 fingerprint

Both the Android OAuth client (step 5) and the Authorization API need to know your app's signing
certificate fingerprint.

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

Copy the `SHA1:` value from the output (looks like `AA:BB:CC:...`).

If you don't have a debug keystore yet, running the app once from Android Studio or
`./gradlew assembleDebug` creates `~/.android/debug.keystore` automatically.

## 5. Create an Android OAuth client

1. Go to **APIs & Services > Credentials > Create Credentials > OAuth client ID**.
2. Application type: **Android**.
3. Package name: `com.gerko.fourenote`
4. SHA-1 certificate fingerprint: paste the value from step 4.
5. Click **Create**.

This client is what the Android Authorization API (`AuthorizationClient`, used for Drive access)
resolves automatically based on your app's package name + signing certificate — you don't need to
put its client ID anywhere in code. But it must exist in the same Cloud project as the Web client
below, or Drive authorization will fail.

For a release build, repeat this step with the **release** keystore's SHA-1 once you have one.

## 6. Create a Web application OAuth client (for Credential Manager sign-in)

Credential Manager's "Sign in with Google" needs a **Web application** type client ID (used as
the token audience), even though the app is Android-only — this is Google's documented pattern,
not a mistake.

1. Go to **APIs & Services > Credentials > Create Credentials > OAuth client ID**.
2. Application type: **Web application**.
3. Name it something like `4ENote Web Client (for Android sign-in)`.
4. You can leave "Authorized redirect URIs" empty — it's not used for this flow.
5. Click **Create** and copy the resulting **Client ID** (looks like
   `1234567890-abc...xyz.apps.googleusercontent.com`).

## 7. Plug the client ID into the project

Create a file named `drive-credentials.properties` **at the repository root** (next to
`settings.gradle.kts`) — this filename matches the `/drive-credentials*` entry already in
`.gitignore`, so it will never be committed:

```properties
GOOGLE_OAUTH_WEB_CLIENT_ID=1234567890-abc...xyz.apps.googleusercontent.com
```

`app/build.gradle.kts` reads this file at build time and injects the value as the
`R.string.google_oauth_client_id` resource. If the file is absent, the app falls back to a clearly
fake placeholder (`000000000000-fakeplaceholderclientid00000000.apps.googleusercontent.com`) so it
still compiles — sign-in will just fail with an error from Google.

No other code changes are needed. Rebuild the app (`./gradlew assembleDebug`) after creating the
file.

## 8. Try it

1. Install and run the app.
2. Tap the refresh/sync icon in the note list's top bar.
3. Pick (or add) your Google account in the Credential Manager bottom sheet. Make sure it's one of
   the test users from step 3 while the app is unverified.
4. Approve the Drive access request when prompted (this is the separate Authorization API
   consent screen for the `drive.file` scope).
5. Check your Google Drive — a **"4ENote"** folder should appear at the root containing one JSON
   file per note (`<title>.json`).

## Known limitations / follow-ups

- Sync is "sync everything, every time you tap the button" — no diffing/incremental sync, no
  conflict resolution, no background/automatic sync.
- Deleting a note locally does not delete (or trash) its Drive copy.
- The upload format is a simple JSON blob (`id`, `title`, `inkData`, `createdAt`, `updatedAt`),
  not a Drive-native Doc — it's meant to be readable by 4ENote itself (e.g. a future
  "restore from Drive" feature), not edited directly in Google Docs/Sheets.
- This whole flow is **untested against a real Google Cloud project** since the agent implementing
  it has no way to create OAuth credentials. Smoke-test end-to-end once you've completed steps 1–7.
