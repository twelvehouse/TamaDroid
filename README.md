<p align="center">
  <img src="docs/tamadroid_icon.png" width="140" alt="TamaDroid icon">
</p>

<h1 align="center">TamaDroid</h1>

An Android **Tamagotchi (P1/P2) emulator** — app **and** a home-screen widget —
powered by [TamaLib](https://github.com/jcrona/tamalib) (jcrona) via the Android NDK.
The pet is rendered in an authentic LCD style and lives in real time.

## Features
- **Emulation** — TamaLib (C, GPLv2) reused *unmodified* through an NDK/JNI bridge,
  so the verified emulator core is never re-ported. Runs P1 and P2 ROMs.
- **Authentic LCD** — the 32×16 dot matrix + 8 status icons rendered in Compose,
  with selectable background frame and LCD font.
- **A / B / C buttons** — event-driven input so even quick taps register reliably.
- **Sound** — buzzer driven by the emulator via a HAL upcall, played as a square wave.
- **Lives in real time** — a foreground service keeps the pet running in the
  background, with an ongoing notification you can stop at any time.
- **Call notifications** — when the pet needs attention, a high-priority
  notification (with its own sound) is raised.
- **Save / restore** — automatic state saving and restore on launch.
- **Home-screen widget** — shows the live LCD (tap to open the app); themeable
  background/dot colour, opacity, and refresh interval.
- **Settings** — theme presets, custom colour presets, opacity, and widget refresh rate.
- **Bring your own ROM** — import via the system file picker or paste; ROMs are
  never bundled (see below).

## Project layout
- `app/` — the Android app.
  - `src/main/cpp/` — native side: `tamalib/` (the TamaLib core, copied unmodified),
    `tama_jni.c` (JNI bridge + HAL), `CMakeLists.txt`.
  - `src/main/java/com/tamadroid/` — Kotlin/Compose UI, engine, audio, service, widget.
- `roms/` — where you drop your local ROM (git-ignored, never committed — Bandai copyright).

## Credits
This project leans heavily on two existing works:

- **[TamaLib](https://github.com/jcrona/tamalib)** by jcrona — the hardware-independent
  Tamagotchi emulator core (C, GPLv2). It is reused **unmodified** as the native engine,
  via the NDK/JNI bridge.
- **[Tamagotchi-Emulator-Pebble](https://github.com/StefanBauwens/Tamagotchi-Emulator-Pebble)**
  by StefanBauwens — a TamaLib-based Pebble emulator. TamaDroid follows its integration
  approach (HAL wiring, icon handling, save/catch-up) and reuses its LCD image/font assets
  (the icons, background frame, and `lcd16x2` font under `app/src/main/res`).

Huge thanks to both authors.

## Building
Open the project in **Android Studio** (it supplies the JDK/Gradle), or build from the
CLI with `gradlew`. Requires the Android **NDK** and **CMake** for the native core.

- compileSdk 35, minSdk 26, Kotlin/Compose.
- NDK 29 + CMake 3.22 (see `app/build.gradle.kts`).
- `local.properties` (your `sdk.dir`) is git-ignored — Android Studio creates it for you.

## ROMs
ROMs aren't bundled — they're Bandai's copyright and can't be dumped from the hardware.
The community uses ROMs transcribed from chip-die photos; the
[Pebble emulator](https://github.com/StefanBauwens/Tamagotchi-Emulator-Pebble) links to P1/P2
ones in the `u12_t` text format this app expects. Import yours in the app (file picker or paste).

## License
**GPLv2** — TamaLib is GPLv2 and is linked via the NDK, so TamaDroid as a whole is
distributed under GPLv2. TamaLib's license is at
[`app/src/main/cpp/tamalib/LICENSE`](app/src/main/cpp/tamalib/LICENSE).
The reused Pebble LCD image/font assets (see Credits) are by their original author and
carry no stated license — confirm with the author before redistributing.
