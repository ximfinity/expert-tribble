# Releasing PhantomFloor

Pushing a version tag such as `v1.1.0` runs `.github/workflows/release.yml`, which:

1. builds a **signed** release APK (version name from the tag, version code from the run number),
2. publishes it as a **GitHub Release** (`PhantomFloor-1.1.0.apk`), and
3. pushes the same file to **itch.io** with [butler](https://itch.io/docs/butler/), if the itch.io settings below are present.

## One-time setup

### 1. Create the signing key (on your own computer)

Every update must be signed with the same key, or Android refuses to install it over the previous
version. Create it **outside** the project folder, so it can never be committed by accident.
`keytool` comes with any Java JDK; if you don't have one, install
[Eclipse Temurin](https://adoptium.net/) (JDK 17 or newer).

```bash
keytool -genkeypair -v -keystore phantomfloor-release.jks -alias phantomfloor \
  -keyalg RSA -keysize 4096 -validity 10000
```

Use a long random password from a password manager. **Back up the `.jks` file and the password**
(a password-manager attachment plus an offline copy). If you lose either, you can't ship updates
that install over existing copies.

### 2. Add the GitHub secrets

Copy the key to the clipboard as base64 without writing an extra file:

```bash
# macOS
base64 -i phantomfloor-release.jks | pbcopy
# Linux
base64 -w0 phantomfloor-release.jks | xclip -selection clipboard
# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("phantomfloor-release.jks")) | Set-Clipboard
```

In the repo, open **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | paste the clipboard |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | `phantomfloor` (or the alias you chose) |
| `SIGNING_KEY_PASSWORD` | key password (the same as the store password unless you set a different one) |

Afterwards, copy something harmless to clear the clipboard. GitHub encrypts secrets, never shows
them again, masks them in logs, and doesn't give them to pull requests from forks.

### 3. Connect itch.io

1. Create the project on itch.io: **Dashboard → Create new project**, with kind **Downloadable**. Under **Pricing**, choose **"No payments" + "suggested donation"**, or **"$0 or donate"**.
2. Create an API key: **itch.io Settings → API keys → Generate new API key**. Copy it straight
   into GitHub; don't save it in a file or paste it anywhere else.
3. In GitHub, add the **secret** `BUTLER_API_KEY` set to that key. If it ever leaks, revoke it on
   the same itch.io page and add a new one.
4. In GitHub, under **Settings → Secrets and variables → Actions → Variables**, add the variable
   `ITCH_TARGET` set to `yourname/your-project-slug:android`. For example, if your page is
   `https://yourname.itch.io/phantomfloor`, use `yourname/phantomfloor:android`.

The first upload creates the `android` channel on the page. Mark that file as **Android** in the
itch.io upload settings once.

### 4. App links

In `gradle.properties`:

- `phantomSupportUrl`: your itch.io page (or Ko-fi). The in-app **♥ Support** button opens it.
- `phantomUpdateRepo`: `owner/repo` the app checks for newer GitHub Releases. The repository
  must be **public** for this to work; otherwise set it empty and let itch.io notify players.

### Keep it locked down

- Turn on two-factor authentication for both GitHub and itch.io.
- Only people with write access to the repo can push the tags that trigger a release.
- Test builds from the **Build APK** workflow are signed with the public `app/debug.keystore`.
  That key is meant to be public; it only lets test builds install over each other. Never use it for releases.

## Cutting a release

Either open **Actions → Release → Run workflow**, enter a version such as `1.1.0`, and run it on
`main` (the workflow creates the `v1.1.0` tag), or push a tag yourself:

```bash
git checkout main && git pull
git tag v1.1.0
git push origin v1.1.0
```

Follow the run in the **Actions** tab. When it's green, the APK is attached to the GitHub Release
and live on itch.io.
