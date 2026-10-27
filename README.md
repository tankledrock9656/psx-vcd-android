# PSX VCD Converter for Android

Android project for converting PlayStation 1 BIN/CUE images to VCD for POPS on PS2. Builds are produced by GitHub Actions.

## Current status

This is the first project scaffold, not a release-ready converter yet.

- Android UI and file picker: included.
- GitHub Actions Android APK build: included.
- BIN/CUE conversion engine: planned integration with [`leji-a/psx-vcd`](https://github.com/leji-a/psx-vcd), which documents support for multi-file CUE sheets and combining tracks.
- MDF/MDS input: not implemented yet. The app must reject it clearly rather than silently produce a broken VCD.
- Multi-track audio preservation: must be validated against test images before calling this release ready.

## Create the GitHub repository

1. Download and extract this archive.
2. In GitHub, create a new empty repository named `psx-vcd-android`.
3. Upload the extracted files to the repository root, or use GitHub's web upload interface.
4. Open **Actions** and enable workflows if GitHub asks.
5. Run **Build Android APK** from the Actions tab.
6. Download the `psx-vcd-android-debug-apk` artifact from a successful run.

The workflow currently builds a debug APK of the Android shell. It does not yet include the converter engine, so do not use this first APK to convert game images.

## Planned milestones

1. Integrate and package the conversion engine for Android ARM64.
2. Add MDF/MDS parsing or a verified conversion path to a supported intermediate format.
3. Test single-track and multi-track images, including audio tracks and pregaps.
4. Produce a signed release APK and publish it from GitHub Actions.

## Target

- Android, ARM64 phone
- Offline operation
- Output format: POPS-compatible VCD
- No game images are uploaded anywhere
