# Audio generation tools (Dumpling Rings)

Two re-runnable scripts produce every file under `assets/audio/`.

## `generate_music.py` - music + stingers via Suno API (sunoapi.org)

```
export SUNO_API_KEY=...            # never commit the key; tools/**/.env is git-ignored
python3 tools/audio_generate/generate_music.py            # all tracks, skips existing .ogg
python3 tools/audio_generate/generate_music.py --only menu_theme,level_complete_sting
```

* Endpoint `POST /api/v1/generate` (customMode + instrumental, model `V6`, `duration` set per track,
  `negativeTags` to steer away from vocals), then polls `GET /api/v1/generate/record-info?taskId=`
  every 20 s. Each task yields 2 clips: both mp3s are kept in `assets/audio/source/music/`
  (`<name>.mp3` = chosen primary, `<name>_altN.mp3` = alternate).
* Task IDs are cached in `assets/audio/source/music/suno_tasks.json`, so an interrupted run resumes
  polling without re-spending credits. Delete an entry (and its .ogg) to regenerate a track.
* A `User-Agent` header is required - the default Python UA is blocked by Cloudflare (403 / error 1010).
* Post-processing (ffmpeg + numpy): `loudnorm` to -16 LUFS / -1.5 dBTP, 44.1 kHz stereo; loop tracks
  get the last 2 s equal-power crossfaded into the first 2 s (loop point = sample 0, so the player can
  simply restart at 0); stingers get trailing silence removed plus a 1.5 s fade-out.
  Output: OGG Vorbis q5 in `assets/audio/music/`.
* Manifest entries (prompt, model, taskId, clip ids, duration) are merged into `assets/audio/manifest.json`.

## `synth_sfx.py` - procedural SFX and ambience (numpy only)

```
python3 tools/audio_generate/synth_sfx.py [--force]
```

Additive partials with per-partial decay, filtered noise (biquads for short sounds, FFT filters for long
beds), simple envelopes and a small Schroeder comb/allpass reverb. Deterministic seed. Peaks at -3 dBFS,
44.1 kHz mono. WAV sources go to `assets/audio/source/sfx/`, OGG q5 to `assets/audio/sfx/` and
`assets/audio/ambience/` (ambience loops are 46-52 s with a 2 s seamless crossfade).

## Licensing
See `docs/AUDIO_REPORT.md`. Music is AI-generated through a third-party Suno API reseller; commercial
rights depend on the underlying Suno account plan and must be confirmed by the project owner.
SFX/ambience are original synthesis owned by the project.
