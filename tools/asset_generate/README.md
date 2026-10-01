# Asset generation pipeline

`generate_art.py` holds the full job list for all generated art (backgrounds, maps,
menu key art, chapter scenes, material tiles, app icon, UI ornaments). It is re-runnable:
any job whose output already exists under `assets/generated/` is skipped, and every run
is recorded in `assets/generated/manifest.json`.

## API key

The image API key is **never** stored in the repo. Export it in your shell before running:

```bash
export IMAGE_API_KEY="lr-agent-..."      # ask the project owner for the key
python3 tools/asset_generate/generate_art.py
```

Optional: `IMAGE_API_URL` overrides the endpoint (default `https://llm.rotz.ai/v1/images/generations`,
OpenAI-compatible `images/generations`, models `klein` (default), `flux2`, `sdxl`).

## Common commands

```bash
python3 tools/asset_generate/generate_art.py --list                # show jobs
python3 tools/asset_generate/generate_art.py --group A             # only backgrounds
python3 tools/asset_generate/generate_art.py --only chapter_03 --force --seed 1234
python3 tools/asset_generate/generate_art.py --only mat_gold --force --prompt-suffix ", brighter"
```

Jobs run sequentially (single-GPU server). Dependencies: `requests`, `Pillow`.
HTTPS goes through the preconfigured proxy; do not disable TLS verification.

## Post-processing

- Material tiles (`materials/`) are made seamless with a mirror-offset cross-fade.
- UI ornaments (`ui/`) are generated on pure white and keyed to `*_alpha.png` by distance-to-white
  matting (soft ramp, colour un-premultiplied at edges). QA each result; fall back to procedural UI
  panels if the key looks bad.

QA notes per asset live in `docs/GENERATED_ART_REPORT.md` and in `qaNote` of the manifest.
