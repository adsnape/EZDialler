# QuickDial 📞

A clean, deploy-ready Android app that shows **6 large contact buttons** on
a single screen. One tap calls the contact immediately. Long-press (or the ✏️
button) swaps a contact for another.

---

## Features

| Feature | Detail |
|---|---|
| 6 large call buttons | 2 × 3 grid, full-screen |
| Contact photos | Loaded from device contacts via Glide |
| Initials avatar | Coloured fallback when no photo |
| Searchable picker | Tap/long-press to choose a contact per slot |
| Direct call | `ACTION_CALL` — no dialler step |
| Permissions | Runtime requests for READ_CONTACTS + CALL_PHONE |
| Dark theme | Deep navy/indigo palette, Material 3 |
| Min SDK | API 26 (Android 8.0) — covers ~95 % of active devices |

---

## Build & Install

### Prerequisites
- **Android Studio Ladybug** (2024.2.1) or newer  
- **JDK 17** (bundled with Android Studio)  
- Android SDK with **API 34** platform installed  

### Steps

```bash
# 1. Clone / unzip into a folder
cd QuickDial

# 2. Open in Android Studio → File → Open → select this folder
#    Android Studio will sync Gradle automatically.

# 3. Connect a device (USB debugging on) OR start an emulator

# 4. Run
./gradlew installDebug          # debug build, installs on connected device
# or
./gradlew assembleRelease       # unsigned release APK → app/build/outputs/apk/release/
```

## Project Structure

```
QuickDial/
├── app/
│   ├── src/main/
│   │   ├── java/com/quickdial/app/
│   │   │   ├── Contact.kt              # Data model
│   │   │   ├── ContactRepository.kt    # Contacts ContentProvider + SharedPrefs
│   │   │   ├── MainActivity.kt         # Home screen (6 buttons)
│   │   │   ├── SetupActivity.kt        # Contact picker
│   │   │   └── ContactPickerAdapter.kt # RecyclerView adapter
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml       # 2×3 GridLayout
│   │   │   │   ├── item_contact_card.xml   # Single large button
│   │   │   │   ├── activity_setup.xml      # Search + list
│   │   │   │   └── item_contact_picker.xml # List row
│   │   │   ├── values/
│   │   │   │   ├── colors.xml   # Deep dark palette
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml   # Material 3 Dark
│   │   │   ├── drawable/        # Vector icons + avatar shape
│   │   │   └── font/            # Roboto Medium reference
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## Customisation

| Want to change… | Where |
|---|---|
| Number of slots (default 6) | `ContactRepository.MAX_CONTACTS` |
| Accent colour | `colors.xml` → `accent` |
| Card corner radius | `item_contact_card.xml` → `cardCornerRadius` |
| App name | `strings.xml` → `app_name` |

---

## Permissions

| Permission | Why |
|---|---|
| `READ_CONTACTS` | Display contact names, numbers, photos |
| `CALL_PHONE` | Place calls directly without opening the dialler |

Both are requested at runtime with clear rationale dialogs.
