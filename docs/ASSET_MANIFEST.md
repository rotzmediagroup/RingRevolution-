# Asset manifest

Three machine-readable manifests are the source of truth; this page explains them.

| Manifest | Contents |
|---|---|
| `assets/imported/manifest.json` | 1251 sprites sliced from the 18 owner-supplied Dumpling Diner sheets: id, file, sheet, bbox, size, category, tags, animation name/frame, source, licence, sha1 |
| `assets/generated/manifest.json` | 38 image-generation jobs (56 PNGs): prompt, negative list, model (`klein` = FLUX.2 Klein 9B via llm.rotz.ai), size, seed, timestamp, world, usage, QA note |
| `assets/audio/manifest.json` | 10 Suno tracks (task ids, prompts, model V6) + 27 procedural SFX + 3 ambience loops: type, source, duration, loop flag, licence note |
| `assets/runtime_manifest.json` | every file shipped in the APK (`app/src/main/assets`): id, source file, usage, world, bytes, sha1 — written by `tools/asset_import/pack_runtime_assets.py` |

## Licensing / ownership
* Dumpling Diner sheets: owner-supplied, reuse permitted by the project bible; unmodified sources kept in `assets/imported/sheets/`.
* Generated art: produced with the owner's own image API (FLUX.2 models hosted by the owner); outputs are owner-owned.
* Music: generated through sunoapi.org (see `docs/AUDIO_REPORT.md` for the quoted commercial-use statement and the caveat that
  rights depend on the account plan — owner to confirm before shipping).
* SFX and ambience: original procedural synthesis in `tools/audio_generate/synth_sfx.py` — fully owned.
* Fonts: Nunito, Baloo 2 — SIL Open Font License 1.1.

## Status flags
All runtime assets are final (no development placeholders). Items flagged for the owner's eye in `docs/GENERATED_ART_REPORT.md`:
world1 teahouse background keeps small steamers at the table ends (centre stays clear); world1 map has one tiny chibi figure;
chapter 13 scene contains a small blank plaque.

## Reproducing
```
python3 tools/asset_import/extract_sheets.py           # sheets -> sprites + manifest + contact sheets
IMAGE_API_KEY=... python3 tools/asset_generate/generate_art.py
SUNO_API_KEY=...  python3 tools/audio_generate/generate_music.py; python3 tools/audio_generate/synth_sfx.py
python3 tools/asset_import/pack_runtime_assets.py       # -> app/src/main/assets + runtime manifest + launcher icons
python3 tools/asset_import/check_runtime_assets.py      # reference check (CI)
```
