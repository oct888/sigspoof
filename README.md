# SigSpoof

SigSpoof is an Xposed module for Android that allows you to spoof the code signature of individual applications. By hooking into the system's package manager, it allows you to replace the signing information reported by the system for specific apps.

## ⚠️ Security Model & Release Builds

**CRITICAL:** The security of this module relies entirely on the integrity of the **Release Build**.

*   **Self-Verification:** The SigSpoof core (running in `system_server`) allows configuration changes *only* from the genuine SigSpoof app. It enforces this by verifying the cryptographic signature of the calling application against its own build-time certificate.
*   **Why Release Matters:**
    *   **Release Builds:** Signed with a private key. The system service verifies this signature before accepting commands. This prevents malicious apps from hijacking the service to spoof signatures arbitrarily.
    *   **Debug Builds:** Signed with the public Android debug key (or disable verification entirely). **DO NOT USE DEBUG BUILDS ON A DAILY DRIVER.** A debug build is insecure because any malicious app signed with the debug key could potentially control the spoofing service.

## Building

To ensure security, you must build a signed release APK.

1.  **Prerequisites:**
    *   JDK 17+
    *   Android SDK

2.  **Configure Signing:**
    Create a `local.properties` file in the project root if it doesn't exist, and define your keystore details. This is required so the build system can embed the public key into the service for verification.

    ```properties
    sdk.dir=/path/to/android/sdk
    signing.storeFile=/path/to/your/keystore.jks
    signing.storePassword=your_store_password
    signing.keyAlias=your_key_alias
    signing.keyPassword=your_key_password
    ```

3.  **Build Release APK:**
    ```bash
    ./gradlew :app:assembleRelease
    ```
    The output APK will be in `app/build/outputs/apk/release/`.

## Installation & Usage

1.  **Install:** Install the `app-release.apk` on your rooted device.
2.  **Enable Module:**
    *   Open your LSPosed manager.
    *   Enable **SigSpoof**.
    *   **Scope:** Ensure **System Framework** (`android`) is selected. This is required for the system-side hooks.
3.  **Reboot:** Restart your device to load the module.
4.  **Spoofing:**
    *   Open the SigSpoof app.
    *   Grant the "Query All Packages" permission if prompted.
    *   Select an app from the list.
    *   Enter the fake signature data
    *   Apply changes and **Force Stop** the target app for the spoof to take effect.

## Technical Architecture

SigSpoof operates using a split architecture:

1.  **System Server Hook (`XposedEntry` & `SignatureSpooferHooks`):**
    *   Hooks into `com.android.server.pm.PackageManagerService` (or `ComputerEngine` on Android 11+).
    *   Intercepts `generatePackageInfo` calls to inject fake `signatures` and `signingInfo` into the returned `PackageInfo` object for targeted apps.
    *   This component runs with high privileges inside the system process.

2.  **IPC Service (`SpooferService`):**
    *   A custom Binder service is instantiated within `system_server`.
    *   It manages the configuration files (stored in a protected directory) that dictate which apps are spoofed.
    *   It exposes an `ISpooferService` interface.

3.  **Secure Communication (`UserService`):**
    *   When the SigSpoof UI app launches, it requests a binder handle.
    *   The system-side `UserService` intercepts this request.
    *   It **verifies the signature** of the requesting app against the embedded certificate (generated during the build process).
    *   Only if the signature matches is the Binder handle returned, allowing the UI to read/write the spoofing configuration.

## Credits

*   [4h9fbZ](https://github.com/4h9fbZ) for the initial signature spoofing xposed hook code.
*   [Hide-My-Applist](https://github.com/Dr-TSNG/Hide-My-Applist) IPC used in this app inspired by HMA.
