# libmobi upstream record

- Repository: https://github.com/bfabiszewski/libmobi
- Tag: `v0.12`
- Commit: `85dcfe803fc2a21020ddcf15c3eb66b93d388add`
- Reproducible `git archive --format=tar v0.12` SHA-256: `73bfb526dc11f5ecc1e57835aa84e0c8719a2a43427036d177fb529db0ac951b`
- License: LGPL-3.0-or-later; see `COPYING`.
- Vendored content: `src/`, `AUTHORS`, `ChangeLog`, `README.md`, `COPYING`.
- Local integration: Android `ndk-build` compiles the C sources with bundled miniz and without encryption/decryption support. The JNI layer only detects encrypted books and rejects them.
- Source availability: this directory contains the exact corresponding source plus the application JNI integration.
- Local changes: upstream files under this directory are unmodified; Android build flags and JNI glue live in `core/mobi/src/main/cpp`.
