# Releasing Hotel ERP mobile

The app ships as a signed APK attached to a **GitHub Release**. Installed apps
check GitHub on launch and offer any newer release as an in-app update
(`data/GithubUpdater.kt` + `ui/UpdateGate.kt`).

Pushing a `v*` tag triggers `.github/workflows/release.yml`, which builds a
**signed** release APK and publishes it to the matching GitHub Release.

---

## One-time setup

1. **Point the app at your repo.** In `gradle.properties`:
   ```properties
   githubRepo=your-user/your-repo
   ```
   (CI passes this automatically from `github.repository`.)

2. **Create a keystore**, once, and never lose it:
   ```bash
   keytool -genkey -v -keystore release.keystore -alias herp-mobile \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

3. **Add the signing secrets.** Repo → **Settings → Secrets and variables →
   Actions → New repository secret**:

   | Secret | Value |
   |---|---|
   | `KEY_ALIAS` | `herp-mobile` |
   | `KEYSTORE_PASSWORD` | your keystore password |
   | `KEY_PASSWORD` | same password |
   | `KEYSTORE_BASE64` | `base64 -w0 release.keystore` output |

   > Every release must be signed with the **same** keystore, or Android rejects
   > the update with a signature mismatch and the only way out is for every user
   > to uninstall and reinstall. Back up `release.keystore` and its password.

4. **Firebase.** `app/google-services.json` is committed (it holds no secrets and
   ships inside the APK regardless). If you'd rather keep it out of the repo, add
   its contents as a `GOOGLE_SERVICES_JSON` secret — the workflow writes the file
   when it isn't present.

---

## Cut a release

1. Commit your changes.
2. Tag it (semantic version, `v`-prefixed):
   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```
3. Actions builds the signed APK and creates the release. The asset is named
   `herp-mobile-1.1.0.apk`; `versionCode` is the CI run number and `versionName`
   is the tag without its `v`.

Watch it under **Actions**; the result appears under **Releases**.

### Release notes
The workflow auto-generates notes from commits and PRs. To write your own, edit
the release on GitHub after it publishes — the release **body** is exactly what
users read in the "Update available" dialog, so keep it short and user-facing:

```
- Voice notes play reliably again
- Requests can be assigned to a colleague
- Faster property switching
```

---

## How the update reaches users

1. On launch the app calls `releases/latest` for `githubRepo`.
2. If that tag is a higher version than the installed `versionName`, it shows
   **Update available** with the release notes.
3. **Update** opens the APK download in the browser, which installs it. Android
   may ask the user to allow installs from the browser the first time.

The app never installs APKs itself: that needs `REQUEST_INSTALL_PACKAGES`, which
makes Play Protect flag or block sideloaded installs. Drafts and pre-releases are
ignored. A failed check is silent — an update offer must never stand between
someone and their shift.

---

## Test a signed build locally

```bash
KEYSTORE_FILE="release.keystore" \
KEYSTORE_PASSWORD="<password>" \
KEY_ALIAS="herp-mobile" \
KEY_PASSWORD="<password>" \
./gradlew :app:assembleRelease -PappVersionName=1.1.0 -PappVersionCode=2
```

Output: `app/build/outputs/apk/release/app-release.apk`.

Without the `KEYSTORE_*` env vars, release builds fall back to the debug key so
local testing still works — but a debug-signed APK cannot update a
release-signed install.

---

## Version numbering

- **Tag / versionName** — semantic and human-facing: `v1.1.0`. The update check
  compares this.
- **versionCode** — the CI run number, always increasing. Android requires a
  higher `versionCode` to accept an update.
