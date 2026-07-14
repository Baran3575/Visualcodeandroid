# VS Code Mobile (Android)

A WebView wrapper that runs **[vscode.dev](https://vscode.dev/)** as a mobile Android app —
built and signed as an **APK via GitHub Actions** (no local Gradle/Android Studio required).

- Native **Kotlin WebView** app (no Cordova/Capacitor bloat).
- **Configurable URL** — point it at `vscode.dev` or your own **code-server** to edit device/remote files.
- **Mobile UI patch** (`app/src/main/assets/mobile.css`) enlarges touch targets and turns the
  sidebar/aux panels into full-screen overlays so the editor feels like a phone code editor (Acode-style).
- Signed release APK produced automatically on every push; published to a GitHub Release on tag.

## Build & install

1. Fork/clone this repo and push to `main` (or run **Actions → Build APK → Run workflow**).
2. Download the `app-release-signed` (or `app-release-unsigned`) artifact from the run.
3. On the phone: **Settings → Security → Install unknown apps** → enable for your file manager, then open the APK.

### Signing (optional but recommended)

Generate a keystore once:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias vscodeandroid
```

Base64-encode it and add **repository Secrets** (`Settings → Secrets → Actions`):

| Secret | Value |
|--------|-------|
| `KEYSTORE` | `base64 -w0 release.jks` |
| `KEY_ALIAS` | `vscodeandroid` |
| `KEY_STORE_PASSWORD` | the keystore password |
| `KEY_PASSWORD` | the key password |

Without these secrets the workflow still builds an **unsigned** APK (installable for personal use).

### Publish a release

Create a GitHub Release/tag → the signed APK is attached automatically.

## Using code-server (edit local/remote files)

WebView can't open device folders directly (no File System Access API), so run
[code-server](https://github.com/coder/code-server) on a machine/LAN and open it from the app:

1. In the app, tap **⋮ → Open URL…**
2. Enter e.g. `http://192.168.1.50:8080` (or a public HTTPS code-server).
3. It's saved; the app loads it next launch. Self-signed certs are accepted on trusted LANs.

## Tuning the mobile UI

The layout patch lives in `app/src/main/assets/mobile.css` (with a small helper in
`app/src/main/assets/inject.js`). Edit the selectors there and push — the new APK is rebuilt by CI.
VS Code web uses stable `.monaco-workbench` parts; you may need a pass or two on a real device.
