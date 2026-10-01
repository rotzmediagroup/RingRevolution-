# Audio Report - Dumpling Rings

Generated 2026-10-01 by the audio tooling in `tools/audio_generate/` (see its README for how to re-run).
Nothing here is committed automatically; review before adding to git (`assets/audio/` is ~86 MB incl. source mp3/wav).

## Summary

* **Music (7 loops + 3 stingers)** - Suno API via sunoapi.org, model `V6` (the docs list V5 / V4_5 as deprecated).
  One request per track, 2 clips each; the clip closest to the requested length is the primary, the other is kept
  as `*_altN.mp3` in `assets/audio/source/music/`. Post-processed with ffmpeg `loudnorm` (target -16 LUFS,
  -1.5 dBTP); measured integrated loudness lands between -14.7 and -16.5 LUFS (single-pass loudnorm, within
  normal tolerance), peaks -1.4 .. -4.0 dBFS. Loop tracks have the final 2 s equal-power crossfaded into the
  first 2 s, so playing from sample 0 to end and restarting at 0 is seamless. Stingers are trimmed of trailing
  silence with a 1.5 s fade-out. Exported OGG Vorbis q5.
* **SFX (27)** - 100% procedural numpy synthesis (`synth_sfx.py`): additive partials, filtered noise, envelopes,
  small Schroeder reverb. 44.1 kHz mono, peak -3 dBFS. Most are 0.2-1.2 s; bell/chime sounds (combo, stars,
  level/chapter bells, unlock, golden booster) run 1.4-2.2 s because of their decaying tails - trim `tail=` in the
  recipe if shorter is wanted.
* **Ambience (3 loops)** - procedural, 46-52 s, 2 s seamless crossfade, peak -3 dBFS.
* **API failures:** none in the final run. First attempt returned HTTP 403 "error code: 1010" (Cloudflare blocking
  Python's default User-Agent) for every request; fixed by sending a custom `User-Agent`. All 10 tasks reached
  `SUCCESS` in roughly 1-3 minutes each. Credits before run: 8440.

## Files

| id | type | file | duration (s) | loop | source |
|---|---|---|---|---|---|
| amb_market | ambience | `assets/audio/ambience/amb_market.ogg` | 46.00 | yes | procedural synth (numpy) |
| amb_mountain | ambience | `assets/audio/ambience/amb_mountain.ogg` | 52.00 | yes | procedural synth (numpy) |
| amb_teahouse | ambience | `assets/audio/ambience/amb_teahouse.ogg` | 46.00 | yes | procedural synth (numpy) |
| chef_finale_variation_w1 | music | `assets/audio/music/chef_finale_variation_w1.ogg` | 147.95 | yes | Suno API (sunoapi.org) |
| chef_finale_variation_w2 | music | `assets/audio/music/chef_finale_variation_w2.ogg` | 147.64 | yes | Suno API (sunoapi.org) |
| chef_finale_variation_w3 | music | `assets/audio/music/chef_finale_variation_w3.ogg` | 148.02 | yes | Suno API (sunoapi.org) |
| menu_theme | music | `assets/audio/music/menu_theme.ogg` | 138.04 | yes | Suno API (sunoapi.org) |
| world1_blossom_teahouse | music | `assets/audio/music/world1_blossom_teahouse.ogg` | 147.59 | yes | Suno API (sunoapi.org) |
| world2_lantern_night_market | music | `assets/audio/music/world2_lantern_night_market.ogg` | 148.00 | yes | Suno API (sunoapi.org) |
| world3_moonlit_mountain_kitchen | music | `assets/audio/music/world3_moonlit_mountain_kitchen.ogg` | 147.64 | yes | Suno API (sunoapi.org) |
| sfx_booster_golden | sfx | `assets/audio/sfx/sfx_booster_golden.ogg` | 1.62 | no | procedural synth (numpy) |
| sfx_booster_hint | sfx | `assets/audio/sfx/sfx_booster_hint.ogg` | 1.50 | no | procedural synth (numpy) |
| sfx_booster_twist | sfx | `assets/audio/sfx/sfx_booster_twist.ogg` | 1.10 | no | procedural synth (numpy) |
| sfx_button_back | sfx | `assets/audio/sfx/sfx_button_back.ogg` | 0.20 | no | procedural synth (numpy) |
| sfx_button_tap | sfx | `assets/audio/sfx/sfx_button_tap.ogg` | 0.18 | no | procedural synth (numpy) |
| sfx_chapter_bell | sfx | `assets/audio/sfx/sfx_chapter_bell.ogg` | 2.15 | no | procedural synth (numpy) |
| sfx_coin_chime | sfx | `assets/audio/sfx/sfx_coin_chime.ogg` | 1.16 | no | procedural synth (numpy) |
| sfx_combo_1 | sfx | `assets/audio/sfx/sfx_combo_1.ogg` | 1.37 | no | procedural synth (numpy) |
| sfx_combo_2 | sfx | `assets/audio/sfx/sfx_combo_2.ogg` | 1.44 | no | procedural synth (numpy) |
| sfx_combo_3 | sfx | `assets/audio/sfx/sfx_combo_3.ogg` | 1.51 | no | procedural synth (numpy) |
| sfx_dough_tap | sfx | `assets/audio/sfx/sfx_dough_tap.ogg` | 0.75 | no | procedural synth (numpy) |
| sfx_dumpling_jump | sfx | `assets/audio/sfx/sfx_dumpling_jump.ogg` | 1.13 | no | procedural synth (numpy) |
| sfx_gate_open | sfx | `assets/audio/sfx/sfx_gate_open.ogg` | 1.60 | no | procedural synth (numpy) |
| sfx_invalid | sfx | `assets/audio/sfx/sfx_invalid.ogg` | 0.91 | no | procedural synth (numpy) |
| sfx_level_complete_bell | sfx | `assets/audio/sfx/sfx_level_complete_bell.ogg` | 1.64 | no | procedural synth (numpy) |
| sfx_lock_click | sfx | `assets/audio/sfx/sfx_lock_click.ogg` | 0.66 | no | procedural synth (numpy) |
| sfx_ring_release_pop | sfx | `assets/audio/sfx/sfx_ring_release_pop.ogg` | 1.30 | no | procedural synth (numpy) |
| sfx_ring_select | sfx | `assets/audio/sfx/sfx_ring_select.ogg` | 0.68 | no | procedural synth (numpy) |
| sfx_rotate_tick_1 | sfx | `assets/audio/sfx/sfx_rotate_tick_1.ogg` | 0.22 | no | procedural synth (numpy) |
| sfx_rotate_tick_2 | sfx | `assets/audio/sfx/sfx_rotate_tick_2.ogg` | 0.22 | no | procedural synth (numpy) |
| sfx_rotate_tick_3 | sfx | `assets/audio/sfx/sfx_rotate_tick_3.ogg` | 0.22 | no | procedural synth (numpy) |
| sfx_snap | sfx | `assets/audio/sfx/sfx_snap.ogg` | 0.72 | no | procedural synth (numpy) |
| sfx_star_1 | sfx | `assets/audio/sfx/sfx_star_1.ogg` | 1.40 | no | procedural synth (numpy) |
| sfx_star_2 | sfx | `assets/audio/sfx/sfx_star_2.ogg` | 1.40 | no | procedural synth (numpy) |
| sfx_star_3 | sfx | `assets/audio/sfx/sfx_star_3.ogg` | 1.40 | no | procedural synth (numpy) |
| sfx_undo | sfx | `assets/audio/sfx/sfx_undo.ogg` | 0.90 | no | procedural synth (numpy) |
| sfx_unlock | sfx | `assets/audio/sfx/sfx_unlock.ogg` | 1.77 | no | procedural synth (numpy) |
| chapter_complete_fanfare | stinger | `assets/audio/music/chapter_complete_fanfare.ogg` | 16.03 | no | Suno API (sunoapi.org) |
| level_complete_sting | stinger | `assets/audio/music/level_complete_sting.ogg` | 8.42 | no | Suno API (sunoapi.org) |
| world_unlock_fanfare | stinger | `assets/audio/music/world_unlock_fanfare.ogg` | 17.64 | no | Suno API (sunoapi.org) |

## Suno task IDs

| track | taskId | duration |
|---|---|---|
| world1_blossom_teahouse | `aa1c0648a96bbda094b346afaac22736` | 150 s requested |
| world2_lantern_night_market | `ffe783d747a9ba8e4db498211d558e86` | 150 s requested |
| world3_moonlit_mountain_kitchen | `7f578fd8900ab70645f836ab03c6c0f1` | 150 s requested |
| menu_theme | `9ee2a7517f6d24c2b59e90b880b1e895` | 140 s requested |
| chef_finale_variation_w1 | `3e17eb49de7e8dcb609208316e162eb9` | 150 s requested |
| chef_finale_variation_w2 | `8f906670f9bccb38aa4ad797112ec321` | 150 s requested |
| chef_finale_variation_w3 | `62d035923129689bd9500aa7857edbf5` | 150 s requested |
| level_complete_sting | `c8fb51f3f64c81bcd02ab3da750ad72e` | 12 s requested |
| chapter_complete_fanfare | `d902dfff2df2a709604ee1b9b88353c1` | 16 s requested |
| world_unlock_fanfare | `a671a573b900320ccfe5c2de3bfd8d96` | 18 s requested |

Prompts and clip ids are recorded per asset in `assets/audio/manifest.json`.

## Licensing / commercial use

sunoapi.org is a third-party reseller of Suno generation. Its documentation home page
(<https://docs.sunoapi.org/>, retrieved 2026-10-01) states:

> "Watermark-Free Commercial Use - All music generated through our API is watermark-free, making it immediately
> suitable for commercial projects. This removes the need for additional fees, enabling creators and developers to
> effortlessly integrate high-quality tracks into their work."

and, under use cases for content creators:

> "Create royalty-free music for videos, podcasts, and social media content with our watermark-free output,
> enjoying unlimited commercial use rights."

The same wording appears on <https://sunoapi.org/>. No dedicated Terms of Service page was found at
`sunoapi.org/terms`, `/terms-of-service` or `/privacy` (all 404), so the above marketing statement is the only
commercial-use statement available from the vendor.

**Important caveat:** the reseller's claim does not override Suno's own terms. Under Suno's terms, ownership and
commercial-use rights for generated audio depend on the Suno account plan under which the audio was generated
(free-tier output is non-commercial; Pro/Premier plans grant commercial rights). Which plan backs the reseller's
API is not visible to us. **The project owner must confirm commercial rights (and ideally obtain written
confirmation from sunoapi.org / check Suno's current Terms of Service at <https://suno.com/terms>) before
shipping these tracks in a paid product.** If that cannot be confirmed, the tracks should be treated as
placeholders and replaced (e.g. regenerate under a verified Suno Pro/Premier account, or commission a composer).

SFX and ambience are original procedural synthesis produced in-repo with no third-party samples and are owned by
the project outright.

## Listen-check notes (ffprobe / volumedetect / ebur128)

* All music files decode cleanly; durations 138-148 s for loops, 8.4 / 16.0 / 17.6 s for the three stingers.
* `level_complete_sting` came back shorter than the requested 12 s (8.4 s after silence trim) - this is fine for its
  purpose; regenerate (`--only level_complete_sting` after deleting its .ogg and task entry) if a longer tail is wanted.
* Loop seams were created by crossfade rather than beat alignment; if a seam is audible on a particular track, the
  crossfade length can be raised in `make_loop(xfade=...)` or the alternate clip tried.
* Recommended in-game mix: music bus -6 dB relative to SFX, ambience -12 dB under music.
