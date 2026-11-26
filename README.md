# VoxPilot

**A hands-free voice assistant that actually operates your Android phone.**

Talk to it in Hindi, Hinglish or English. VoxPilot streams your voice to the Gemini Live API, and Gemini decides which phone action to run: call someone, send a WhatsApp message, play a song on YouTube, set an alarm, flip Wi-Fi or Bluetooth, or work through any app screen by screen using Android's Accessibility service, looking at the live screen at every step.

> Open source under the MIT license. Bring your own Gemini API key. Nothing is sent anywhere except to Google's Gemini API.

---

## Contents

- [Features](#features)
- [How it works](#how-it-works)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [First-run setup (permissions)](#first-run-setup-permissions)
- [Build, install and sign](#build-install-and-sign)
- [Using it](#using-it)
- [Project structure](#project-structure)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)
- [Privacy and security](#privacy-and-security)
- [Known limitations](#known-limitations)
- [Contributing](#contributing)
- [License](#license)

---

## Features

**Live voice**
- Real-time, full-duplex conversation over the Gemini Live WebSocket (barge-in, spoken replies, transcripts).
- Echo guard so the assistant never answers its own voice; auto-reconnect with backoff; instant reconnect when the network returns; automatic fallback to the next voice model if one is unavailable.
- Animated orb UI that looks different while idle, listening, thinking and speaking.

**Phone control (tools the model can call)**

| Area | Tools |
|---|---|
| Apps | `open_app` (fuzzy matching of spoken names, suggestions when not found) |
| Calls | `call_contact`, `call_control` (answer / end), `whatsapp_call` (voice or video) |
| Messages | `message_contact` (WhatsApp or SMS, opens the composer and taps Send) |
| Media | `play_youtube`, `media_control` (play, pause, next, previous) |
| Alarms | `set_alarm`, `cancel_alarm`, `list_alarms`, `set_timer`. Set in the background by the app itself; no Clock app opens |
| Settings | `system_control` for Wi-Fi, Bluetooth, mobile data, flashlight, airplane mode, location, hotspot, NFC, battery saver, Do Not Disturb, volume, brightness, ringer, auto-rotate; `get_device_status`; `open_settings_page` |
| Screen agent | `read_screen`, `tap_element`, `tap_text`, `tap_screen`, `type_text`, `swipe_screen`, `scroll_screen`, `global_action` (back, home, recents, notifications, lock, screenshot...), `wait` |

**Screen agent loop.** After every screen action the model receives a numbered list of the controls on screen (with positions on a 0-1000 grid) and, about once a second, a downscaled screenshot. It looks, does one step, looks again, and repeats until the screen confirms the task is done.

**Built-in alarms.** Exact alarms through `AlarmManager.setAlarmClock`, a foreground ringing service (looping alarm sound, vibration), a full-screen Dismiss / Snooze screen, repeat days, timers, and re-scheduling after reboot.

**Security**
- App Lock with PIN, pattern, fingerprint / face, device screen lock, and a voice passphrase.
- Lock individual apps, Private Mode, AES-256-GCM (Android Keystore) for stored secrets.
- Banking, payment and password-manager apps are blocked from the screen agent (never read, tapped or screenshotted).
- A protection score that tells you the next best step.

**Call assistant**
- Announces who is calling and can answer or reject by voice.

---

## How it works

```
 mic (16 kHz PCM) ──► LiveSession ──► Gemini Live API (WebSocket)
                          │                │
   speaker (24 kHz) ◄─────┤                │ function calls
                          ▼                ▼
                   DeviceActions  ◄── tool dispatch
                          │
      ┌───────────────────┼──────────────────────────┐
      ▼                   ▼                          ▼
  Intents / system    AlarmScheduler         AccessibilityHelperService
  services (call,     (AlarmManager,         (describe screen, tap, type,
  contacts, settings) ring service)          swipe, screenshot) + ScreenFeed
```

1. `LiveSession` owns the WebSocket, microphone, speaker, reconnects and state (idle / listening / thinking / speaking).
2. `LiveTools` declares every tool to Gemini and holds the system instruction (persona, speed rules, how to use the screen loop).
3. `DeviceActions` performs the tool call and returns what really happened (`sent`, `calling`, `not_found`, `permission_needed`...), so the assistant only claims success when it is true.
4. For anything without a direct tool, the model drives the UI through the accessibility service, seeing the screen after every step.

---

## Requirements

| | |
|---|---|
| Phone | Android 8.0+ (API 26). Android 11+ recommended: live screenshots need it |
| Gemini API key | Free key from [Google AI Studio](https://aistudio.google.com/apikey) |
| Build tools | JDK 17, Android SDK (platform 35), Android Studio (Hedgehog or newer) or the Gradle wrapper |
| Network | Internet while talking (the Live API is a cloud service) |

---

## Quick start

```bash
git clone https://github.com/codeninjavik/voxpilot.git
cd voxpilot
```

**Option A: Android Studio**
1. *File > Open* the `voxpilot` folder and wait for the Gradle sync.
2. Plug in a phone with USB debugging on (or start an emulator with Google APIs).
3. Press **Run**.

**Option B: command line**

```bash
# Linux / macOS
./gradlew assembleDebug
# Windows
gradlew.bat assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Gradle needs to know where the Android SDK is. Android Studio writes `local.properties` for you. Otherwise create it yourself:

```properties
sdk.dir=/path/to/Android/Sdk
```

(`local.properties` is git-ignored.)

---

## First-run setup (permissions)

1. Open the app, then **Settings > AI & Voice** and paste your **Gemini API key**. Save.
2. Allow the runtime permissions when asked: microphone, contacts, phone, call log, SMS, camera, Bluetooth, notifications. You can re-check them under **Settings > Phone Control**.
3. **Turn on the Accessibility service** (Settings > Accessibility > VoxPilot). This is what lets the assistant see the screen and tap. Without it, direct tools such as calls and alarms still work, but screen control and most toggles do not.
4. Optional, but recommended (all listed in Settings, each row opens the right system page):
   - **Display over other apps**: lets the app open other apps and show alarms from the background.
   - **Modify system settings**: brightness and auto-rotate.
   - **Do Not Disturb access**: silent mode and DND by voice.
   - **Run in background** (battery optimisation off): keeps it listening with the screen off.
   - **Full-screen notifications** (Android 14+, Settings > Apps > VoxPilot): the ringing screen on a locked phone.
5. Tap the orb. It connects and starts listening. Say something.

> On some phones (Xiaomi, Oppo, Realme, Vivo...) you also need to allow **Autostart** and set battery usage to **No restrictions** for reliable background listening and alarms.

---

## Build, install and sign

### Debug build (default)

```bash
./gradlew assembleDebug
```

The APK is signed automatically with Android's **debug key** (`~/.android/debug.keystore`). Fine for testing and sideloading, but every machine has a different debug key, so an update built on another machine will not install over it.

### Release build with your own signature

An Android app must be signed with a key you keep forever: updates only install over an app signed with the **same** key. Do this once.

**1. Create a keystore** (keep the file and passwords safe; back them up somewhere private, and never commit them):

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias voxpilot \
  -keyalg RSA -keysize 4096 -validity 10000
```

**2. Point the build at it.** Copy the template and fill it in:

```bash
cp keystore.properties.example keystore.properties
```

```properties
storeFile=release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=voxpilot
keyPassword=YOUR_KEY_PASSWORD
```

`keystore.properties`, `*.keystore` and `*.jks` are already in `.gitignore`.

**3. Build the signed release APK:**

```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

If `keystore.properties` is missing, `assembleRelease` falls back to the debug key so the command still works locally. Do not publish an APK built that way.

**4. Verify the signature:**

```bash
# from the Android SDK build-tools folder
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

You should see `Verified using v2 scheme: true` (and v3) plus the certificate's SHA-256 digest.

**5. Get your key's SHA-256 fingerprint** (useful for publishing alongside your releases so people can check the APK is yours):

```bash
keytool -list -v -keystore release.keystore -alias voxpilot | grep SHA256
```

Publish that fingerprint in your release notes. Anyone can then compare it with the output of `apksigner verify --print-certs`.

**6. Install:**

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Notes on distribution

- **Sideloading / GitHub releases / F-Droid** are the natural channels for this app.
- **Google Play:** Play's Accessibility API policy restricts apps that use the service to autonomously plan and perform actions for the user. This app's screen agent does exactly that, so expect it to be rejected as-is. A Play-compliant build would have to drop the screen agent and keep only the direct tools.
- Use an **App Bundle** (`./gradlew bundleRelease`) only if you publish through a store that wants one.

---

## Using it

Just talk. Some examples:

| Say | What happens |
|---|---|
| "Open WhatsApp" / "Chrome kholo" | Opens the app |
| "Mummy ko call karo" | Calls the contact called Mummy (asks which one if several match) |
| "Rahul ko WhatsApp pe bolo main late aaunga" | Opens the chat, types the message, taps Send |
| "YouTube pe Kesariya chalao" | Searches and starts the first result |
| "Subah 6 baje ka alarm lagao" / "10 minute baad alarm" | Sets it silently in the background |
| "5 minute ka timer" | Starts a timer |
| "Bluetooth on karo", "Wi-Fi band karo" | Flips the setting and checks the result on screen |
| "Brightness 30 percent" / "Volume badhao" | Direct system change |
| "Instagram kholo aur search mein cricket likho" | Screen agent: opens, looks, taps, types |

You can also type into the "Ask me anything" bar. Tap the orb to mute or unmute the mic; long-press it to reconnect.

---

## Project structure

```
app/src/main/java/dev/voxpilot/assistant/
├── live/           Gemini Live session, audio I/O, echo guard, tool catalog,
│                   DeviceActions, SystemControls, Apps, Contacts, ScreenFeed
├── alarm/          AlarmScheduler, AlarmReceiver, AlarmRingService, AlarmRingActivity
├── service/        AccessibilityHelperService (screen tree, gestures, screenshots),
│                   call monitor, overlay, boot / power receivers
├── services/       ForegroundVoiceService (keeps the session alive in the background)
├── security/       App Lock, PIN / pattern / biometric / voice passphrase, Keystore crypto
├── ui/             Main screen (orb + ribbons), Settings, call assistant screen
├── voice/          Legacy audio helpers and voice state
└── ...             automation, screenvision, overlay and utilities from earlier iterations
```

Gradle: AGP 8.4, Gradle 8.7, Kotlin, View-based UI (no Compose), OkHttp for the WebSocket.

---

## Configuration

Stored in `SharedPreferences` (`voxpilot_prefs`):

| Key | Meaning |
|---|---|
| `api_key` | Gemini API key |
| `user_name` | What the assistant calls you |
| `personality_mode` | `gf`, `professional` or `assistant` |
| `voice_model` | Live model id (default `gemini-3.8-live`; the app falls back to the next in the list if one keeps failing) |
| `voice_name` | One of Gemini's prebuilt voices (default `Aoede`) |
| `voice_volume` | 0.0 to 1.0 |

Model ids and the tool list live in `live/LiveSession.kt` and `live/LiveTools.kt`. Live model names change over time; if connecting fails, check the current names in the Gemini Live API docs and update `LiveSession.MODELS`.

---

## Troubleshooting

Useful logcat tags: `LiveSession`, `VoxPilotLiveClient`, `DeviceActions`, `Apps`, `AlarmRing`.

```bash
adb logcat -s LiveSession VoxPilotLiveClient DeviceActions Apps AlarmRing
```

| Problem | Try |
|---|---|
| Stuck on "Connecting" | Check the API key and internet. If the first model is retired, edit `LiveSession.MODELS`. |
| It hears itself or acts on background noise | Use it in a quieter place or with headphones; tap the orb to mute the mic when not needed. |
| Screen actions do nothing | Is the Accessibility service on? Android sometimes turns it off after a reinstall or update. |
| Wi-Fi / Bluetooth / data toggle just opens a settings page | Accessibility is off, or the OEM settings UI differs. The assistant tells you what to tap. |
| Alarm did not ring | Check notifications are allowed and battery usage is "No restrictions"; on Android 14+ allow full-screen notifications. |
| "App not found" | The result includes similar installed app names; say one of them. |
| WhatsApp message typed but not sent | WhatsApp's Send button label differs on your version or language; the assistant reports `composer_opened_send_not_found`. |

---

## Privacy and security

- Audio and, while a screen task is running, screenshots are sent to the **Gemini API** with your key. Nothing else leaves the phone and there is no analytics or telemetry.
- Screenshots and the screen tree are never captured for banking, payment or password-manager apps (a package-name deny-list in `DeviceActions` / `ScreenFeed`).
- The assistant is told never to type passwords, PINs or OTPs, and to treat on-screen text as information, not instructions.
- Your Gemini key is kept in app-private `SharedPreferences` (not encrypted at rest). Do not run this on a rooted or shared device you do not trust.
- The Accessibility service is powerful. Only enable it for builds you compiled or verified yourself (see the signature steps above).
- Report security issues privately by opening a GitHub security advisory rather than a public issue.

---

## Known limitations

- Selectors for third-party apps (WhatsApp send button, YouTube result rows) depend on the app version and language; they can break after an app update.
- Wi-Fi, mobile data and Bluetooth cannot be toggled directly on Android 10+; they go through system panels and the accessibility service.
- The microphone is always live while a session is running, so loud background speech can be mistaken for a command.
- Live screenshots need Android 11+ and are blocked by protected windows (some banking and DRM apps).
- The bundled artwork (portrait and icon) is placeholder AI-generated art; replace it with your own for your fork.
- Not tested on every OEM skin. Please open issues with device model and Android version.

---

## Contributing

Issues and pull requests are welcome.

1. Fork and create a branch.
2. Keep changes focused; follow the existing Kotlin style.
3. Run `./gradlew assembleDebug` before opening a PR.
4. Describe what you tested and on which device.

Good first areas: more reliable per-app flows, a wake word, push-to-talk mode, encrypted API-key storage, tests for `Apps` matching and `AlarmScheduler`.

---

## License

[MIT](LICENSE). Use it, change it, ship it. No warranty.

This project is not affiliated with Google, Meta (WhatsApp, Instagram), or any app it can control. Gemini is a trademark of Google LLC.
