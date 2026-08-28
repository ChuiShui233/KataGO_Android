<p align="center">
  <img src="KataGO_Android/APP.png" width="180" alt="KataGO Android Logo" />
</p>

<h1 align="center">KataGO Android — Super-Repo</h1>

<p align="center">
  <strong>Jetpack Compose + Fluent UI Go client with embedded KataGo engine and multi-provider AI commentary</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%20arm64--v8a-green" alt="platform" />
  <img src="https://img.shields.io/badge/KataGo-main-blue" alt="KataGo" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.20-purple" alt="Kotlin" />
  <img src="https://img.shields.io/badge/AGP-8.7.3-orange" alt="AGP" />
  <img src="https://img.shields.io/badge/license-MIT-lightgrey" alt="license" />
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#super-repo-structure">Super-Repo</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#build">Build</a> •
  <a href="#ai-commentary">AI Commentary</a>
</p>

---

## Overview

KataGO Android is an Android Go (Weiqi/Baduk) client with a built-in KataGo engine. It supports local CPU/GPU play and AI-powered game commentary via multiple LLM providers. This repository is a **super-repo** that aggregates all required components via `git subtree` (arm64-v8a only).

> **App version:** 1.0.3 (versionCode 20260820) · **KataGo:** `main` branch via subtree · **Platform:** `arm64-v8a` only

---

## Features

- **Human vs. Engine:** 9/13/19 boards, color selection, handicap, pass/undo/scoring
- **Dual engine:**
  - CPU `libkatago.so` — standalone process via `exec()`, GTP pipe
  - GPU `libkatago_opencl.so` — shared library loaded via JNI in app process, GTP pipe
- **GPU via clvk:** OpenCL on Vulkan (`libOpenCL.so` from clvk); auto fallback to CPU if not installed
- **AI commentary:** Groq / Gemini / OpenAI / Claude / DeepSeek / Kimi / Qwen / OpenRouter — auto analysis of mistakes, winrate/score, chat Q&A
- **Mistake detection:** threshold on KataGo winrate delta, only calls LLM when needed
- **Request queue & routing:** priority `user > blunder > turning point > mistake > fight > good move`, auto discard stale, auto failover (`manual / auto / round-robin / fastest / cheapest`)
- **Security:** Android Keystore + AES-GCM, Token usage tracking
- **Board UX:** tap-preview + confirm, AI pondering marks, coordinates, game save/load, model import, multi-language (zh-CN/zh-TW/en/ja/ko/de)

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose + Fluent UI |
| Async | Kotlin Coroutines + Flow |
| Network | HttpURLConnection |
| Storage | SharedPreferences + Android Keystore |
| Engine | KataGo (`KataGo/cpp`, `USE_BACKEND=OPENCL`) |
| GPU | clvk (OpenCL on Vulkan) |

---

## Super-Repo Structure

This repo uses `git subtree` so that `KataGo` source can be edited directly in the super-repo and synced with upstream.

```
KataGO_Android-source/          # super-repo (this repo)
├── KataGo/                     # subtree: https://github.com/lightvector/KataGo.git#main (editable)
├── vulkan-build/clvk/          # subtree: https://github.com/kpet/clvk.git#main
├── build-tools/
│   ├── opencl-headers/         # subtree: https://github.com/KhronosGroup/OpenCL-Headers.git#main
│   ├── android-toolchain.cmake # arm64-v8a, NDK autodetect (r25b and newer)
│   ├── build-android-opencl.sh # build libkatago_opencl.so
│   ├── android-clang / android-clang++ # NDK wrappers
│   └── sync-subtrees.sh        # pull upstream updates
├── KataGO_Android/             # native Android app (not subtree)
│   ├── app/src/main/kotlin/com/chuishui/katago/
│   ├── app/src/main/jniLibs/arm64-v8a/
│   └── gradle/
└── README.md
```

> `KataGo-1.17.2` has been renamed to `KataGo` to track `main` without a version suffix.

---

## Quick Start

### Prerequisites

| Tool | Version |
|------|---------|
| Android SDK | compileSdk 35, minSdk 29, targetSdk 35 |
| Android NDK | r25b or newer (autodetected via `ANDROID_NDK` / `ANDROID_NDK_HOME` / `../ndk/android-ndk-r*`) |
| JDK | 11+ |
| Gradle | 8.11.1 via wrapper (`./gradlew`) |

### Clone

```bash
git clone <super-url> KataGO_Android-source
cd KataGO_Android-source

# only needed after clvk upstream update
git -C vulkan-build/clvk submodule update --init --recursive
```

### Build Engine (GPU, optional)

```bash
# NDK autodetected; or export ANDROID_NDK=/path/to/ndk
./build-tools/build-android-opencl.sh
# output: KataGO_Android/app/src/main/jniLibs/arm64-v8a/libkatago_opencl.so
```

### Build APK

```bash
cd KataGO_Android
./gradlew assembleDebug
# or from super-repo: ./KataGO_Android/build.sh assembleDebug
# output: KataGO_Android/app/build/outputs/apk/debug/app-debug.apk
```

Release signing (optional, via Gradle properties):

```properties
KATA_ANDROID_KEYSTORE=/path/to/keystore.jks
KATA_ANDROID_KEYSTORE_PASSWORD=...
KATA_ANDROID_KEY_ALIAS=...
KATA_ANDROID_KEY_PASSWORD=...
```

### Import Model

Place KataGo weight files (`.bin.gz`) in:

```
/storage/emulated/0/Download/KataGO-AOS/model/
```

or import via in-app file picker.

### GPU / clvk

Import a clvk-built `libOpenCL.so` via **Settings → GPU** file picker (`.zip` or `.so` from `build-tools/install-clvk-lib.sh`). Without it, GPU mode is disabled and engine falls back to CPU.

```bash
# after building clvk via GitHub Actions artifact
./build-tools/install-clvk-lib.sh /path/to/libOpenCL-clvk-arm64-v8a.zip
```

---

## Build Details

- **Toolchain:** `build-tools/android-toolchain.cmake` — `arm64-v8a` only, `CMAKE_SYSTEM_NAME=Android`, API 29
- **Engine cmake:** `build-tools/build-android-opencl.sh` runs `cmake -DUSE_BACKEND=OPENCL -DNDK=...` then `ninja`, patches ELF `DT_FLAGS_1` (clear PIE) and `.init_array` garbage, installs to `jniLibs/arm64-v8a`
- **App:** `KataGO_Android/app/build.gradle.kts:18` has `abiFilters += "arm64-v8a"` and `packaging.jniLibs.useLegacyPackaging = true`

---

## AI Commentary Architecture

```
KataGo Engine
     ↓
GoMistakeDetector (winrate delta)
     ↓
GoAiEvent → GoAiCoach → GoAiPromptBuilder
     ↓
AiRouter (BEST / AUTO / CHEAPEST / MANUAL per event type)
     ↓
AiRequestQueue (priority, discard stale)
     ↓
AiProvider → UI
```

**Detection thresholds:**

| Mode | Threshold | Behavior |
|------|-----------|----------|
| OFF | — | never |
| MAJOR_MISTAKES | ≥10% | blunders only |
| IMPORTANT_MISTAKES | ≥5% | blunders + mistakes |
| AGGRESSIVE | ≥3% | almost every move |

**Routing per event:**

| Event | Policy |
|-------|--------|
| BLUNDER | BEST |
| mistake / turning point / fight | AUTO |
| GOOD_MOVE | CHEAPEST |
| user question / summary | MANUAL / AUTO |

**Modes:**
- **Commentary** (bottom panel): explains KataGo data (`answer()`, compact context)
- **Tutor** (full-screen): model reasons alone (`answerGlobal()`, `maxTokens=6000`, `reasoningEffort=high`, board JSON, `[tip]D4[/tip]` highlights)

---

## Subtree Workflow

```bash
# pull upstream (requires network)
./build-tools/sync-subtrees.sh
# or manually:
git subtree pull --prefix=KataGo https://github.com/lightvector/KataGo.git main --squash -m "subtree: pull KataGo main"
git subtree pull --prefix=vulkan-build/clvk https://github.com/kpet/clvk.git main --squash
git subtree pull --prefix=build-tools/opencl-headers https://github.com/KhronosGroup/OpenCL-Headers.git main --squash

# edit KataGo directly in super-repo, then push to fork:
git subtree split --prefix=KataGo --branch katago-split
git push <your-fork> katago-split:main
```

---

## Configuration

Configure LLM providers in **Settings → AI Assistant**: provider, Base URL, API Key (Keystore-encrypted), model, test connection. Failover across configured providers is automatic.

---

## License

- App code: see repository license
- KataGo engine (`KataGo/`): KataGo license (`KataGo/LICENSE`)
- clvk (`vulkan-build/clvk/`): its original license

## Acknowledgments

- [KataGo](https://github.com/lightvector/KataGo) by lightvector
- [clvk](https://github.com/kpet/clvk) — OpenCL on Vulkan
- Khronos OpenCL Headers
