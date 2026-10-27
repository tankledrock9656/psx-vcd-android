# PSX VCD Converter for Android

This Android app integrates the Rust `psx-vcd` engine as an ARM64 JNI library. Conversion runs locally on the phone after installation; the runtime app does not need network access.

## Current support

- Input: CUE sheets referencing local BIN files in the selected folder tree.
- Output: POPSTARTER/OPL-compatible VCD according to the bundled engine implementation.
- Multitrack handling: delegated to the upstream `psx-vcd` code. Test the result with a known image before relying on it.
- MDF/MDS: not implemented.
- Android ABI: ARM64 (`arm64-v8a`), appropriate for the target Samsung Galaxy A16 variant.

The app asks the user to select the directory containing a game's CUE sheet and its referenced files. It stages only the CUE and files referenced by it into private app storage, runs the converter, and then lets the user choose where to save the VCD.

## Build

GitHub Actions installs the Android NDK and Rust toolchain, builds the native shared library, packages it in the APK, and uploads the debug APK as an artifact.

## License and attribution

The bundled upstream engine source is from https://github.com/leji-a/psx-vcd and is licensed under MIT OR Apache-2.0. The respective license files are kept under `native/psx-vcd-engine/`.
