# libmobi Android test fixtures

These files are unchanged copies from the official libmobi v0.12 test suite:

- Source: <https://github.com/bfabiszewski/libmobi/tree/v0.12/tests/samples>
- Version: `v0.12`
- Commit: `85dcfe803fc2a21020ddcf15c3eb66b93d388add`
- License: GNU Lesser General Public License, version 3 or later (LGPL-3.0-or-later),
  as declared by the upstream project and reproduced in
  `third_party/libmobi/COPYING`.

Only the five files needed for parser compatibility tests are included. The
local names make each role explicit:

| Upstream name | Local name | Test role |
|---|---|---|
| `sample-textread.mobi` | `kf7-textread.mobi` | KF7 success |
| `sample-ncx.mobi` | `kf8-ncx.azw3` | KF8/AZW3 success |
| `sample-multimedia.mobi` | `multimedia.mobi` | Embedded image export |
| `sample-drm-v1.mobi` | `drm-v1.mobi` | DRM rejection |
| `sample-invalid-indx.fail` | `invalid-indx.fail` | Corrupt input handling |

Renaming does not alter file contents. `SHA256SUMS` records the upstream file
hashes under their local names.
