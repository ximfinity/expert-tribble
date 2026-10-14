# Releasing PhantomFloor

Pushing a version tag such as `v1.1.0` runs `.github/workflows/release.yml`, which:

1. builds a **signed** release APK (version name from the tag, version code from the run number),
2. publishes it as a **GitHub Release** (`PhantomFloor-1.1.0.apk`), and
3. pushes the same file to **itch.io** with [butler](https://itch.io/docs/butler/), if the itch.io settings below are present.

## One-time setup

### 1. Create the signing key (on your own computer)

Every update must be signed with the same key, or Android refuses to install it over the previous
version. **Back up the `.jks` file and both passwords somewhere safe, such as a password manager.**

```bash
keytool -genkeypair -v -keystore phantomfloor-release.jks -alias phantomfloor \
  -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` comes with any Java JDK. Then base64-encode the file so it can be stored as a secret:

```bash
base64 -w0 phantomfloor-release.jks > keystore.b64      # macOS: base64 -i phantomfloor-release.jks -o keystore.b64
```

### 2. Add the GitHub secrets

In the repo, open **Settings → Secrets and variables → Actions → Secrets** and add:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | contents of `keystore.b64` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | `phantomfloor` (or the alias you chose) |
| `SIGNING_KEY_PASSWORD` | key password (the same as the store password unless you set a different one) |

### 3. Connect itch.io

1. Create the project on itch.io: **Dashboard → Create new project**, with kind **Downloadable**. Under **Pricing**, choose **"No payments" + "suggested donation"**, or **"$0 or donate"**.
2. Create an API key: **itch.io Settings → API keys → Generate new API key**.
3. In GitHub, add the **secret** `BUTLER_API_KEY` set to that key.
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

## Cutting a release

```bash
git checkout main && git pull
git tag v1.1.0
git push origin v1.1.0
```

Follow the run in the **Actions** tab. When it's green, the APK is attached to the GitHub Release
and live on itch.io.
