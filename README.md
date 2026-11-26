# 🛡️ VoxPilot / MYRA — Next-Gen AI Android Assistant & Comprehensive Cyber Security Suite

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin" />
  <img src="https://img.shields.io/badge/Architecture-MVVM%20%2F%20Coroutines-FF2D55?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Security-AES--256%20%7C%20Biometric-00E5FF?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Build-Gradle%20Passing-success?style=for-the-badge" />
</p>

---

## 🌟 Overview

**VoxPilot / MYRA** is an advanced, high-performance Android AI Assistant and Military-Grade Cyber Security Suite designed with a stunning Cyberpunk / Sci-Fi HUD Neon UI (`#080508` dark background, `#0D0D1A` glassmorphic cards, and `#FF2D55` Cyber Red accents). It combines intelligent voice assistant control with real-time hardware telemetry, privacy auditing, advanced QR code intelligence, and intrusion protection.

---

## 🚀 Key Features & Modules

### 1. 🌌 Quantum Core Sci-Fi HUD Dashboard
- **Real-Time System Telemetry**: Live RAM usage, storage breakdown, battery level, and remaining time calculation (dynamic discharging estimation & charging status).
- **Network & Carrier Intelligence**: Real-time Wi-Fi SSID name, signal strength (% and 4-bar level indicator), local IPv4 address, and Mobile SIM Operator Name (Jio/Airtel/Vi/BSNL) with 4G/5G generation badge.

### 2. 🔍 Smart App Permission Analyzer
- Audits all installed third-party apps on the device.
- Classifies apps by risk level (`HIGH`, `MEDIUM`, `LOW`) based on sensitive permissions (`SMS`, `Contacts`, `Camera`, `Microphone`, `Location`).

### 3. 🌐 Network Security & Connection Insights
- Real-time monitoring of active internet validation and connection type (Wi-Fi / Cellular).
- **Dual VPN Tunnel Protection Detector**: Automatically detects active VPN encrypted tunnels (WireGuard, OpenVPN, Cloudflare WARP, 3rd-party VPN apps) via network capabilities and active network interfaces (`tun0`, `ppp0`).

### 4. 🎣 Phishing & Scam Detection Center
- Instant URL structure and domain reputation analysis.
- Detects IP-based hostnames, typosquatting, brand spoofing keywords, and SSL/HTTPS encryption status.

### 5. 📷 Payment & Wi-Fi QR Code Safety Scanner
- Powered by **CameraX** and **Google ML Kit Barcode Scanning**.
- **Zero-Auto-Execution Policy**: Never automatically executes payments or connects to Wi-Fi without explicit user confirmation.
- **Payment Intelligence**: Parses UPI URIs (`upi://pay`), extracting Receiver Name, Merchant Name, VPA/UPI ID, Amount, and Currency, with payment app chooser (Paytm, GPay, PhonePe, BHIM).
- **Wi-Fi Intelligence**: Parses MeCARD/Wi-Fi formats (`WIFI:S:...;P:...`), showing SSID, encryption (WPA2/WPA3), password masking, and signal strength.

### 6. 📦 Local APK & Malware Scanner
- Scans installed 3rd-party apps and downloaded APK files in device storage for dangerous permissions and risk signatures.

### 7. 🔌 Active Connection Endpoint Monitor
- Enumerates active network interfaces (`wlan0`, `rmnet`, `tun0`) and local IP endpoints in real-time.

### 8. 🔒 Secure Encrypted Vault (Military-Grade Privacy)
- **AES-256 Encryption**: Backed by Android Keystore for secure private notes, passwords, and sensitive text.
- **Protected Media Vault**: Import private photos and videos into secure internal app storage, viewable only inside the vault.
- **FLAG_SECURE Protection**: Completely blocks screenshots, screen recording, and app thumbnail preview in Android Recents.
- **Instant Auto-Lock**: Automatically locks the vault instantly when minimized (`onPause`).
- **Custom Vault PIN**: Dedicated in-app PIN independent of system lock.
- **Intruder Selfie Capture**: Automatically captures a photo using the front camera (`CameraSnapshotHelper`) after 3 consecutive incorrect PIN attempts and logs it in the Intruder Selfie Gallery.

### 9. 💬 SMS & Notification Phishing Guard
- Monitors incoming messages and notifications for scam keywords and fraudulent banking links.

### 10. 🎙️ Full Voice & AI Assistant Control
- Control every feature hands-free using voice commands (e.g., *"Quantum dashboard kholo"*, *"Network security check karo"*, *"QR scanner kholo"*, *"Secure vault kholo"*).

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 100%
- **UI Architecture**: Activities, XML Layouts, Material Design 3, Cyberpunk Neon styling
- **Core Libraries**:
  - CameraX (`1.3.4`) & Google ML Kit Barcode Scanning (`17.3.0`)
  - Kotlin Coroutines & Flow (`Dispatchers.IO` / `Dispatchers.Main`)
  - Android Keystore / Security-Crypto & BiometricPrompt
  - TelephonyManager, WifiManager, ConnectivityManager

---

## ⚙️ Getting Started & Build Instructions

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/your-username/voxpilot-main.git
   ```
2. **Open in Android Studio**:
   - Open Android Studio and select **Open**, pointing to the cloned project root directory.
3. **Sync Gradle & Build**:
   - Let Gradle sync dependencies.
   - Run the build task:
     ```bash
     ./gradlew app:assembleDebug
     ```
4. **Deploy**:
   - Connect an Android device or start an Emulator (API 26+) and click **Run**.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
