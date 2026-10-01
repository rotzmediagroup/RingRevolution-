# Generated Art Report

Pipeline: `tools/asset_generate/generate_art.py` (see its README). Model: FLUX.2 Klein 9B (`klein`) via
the OpenAI-compatible image endpoint; ~7-45 s per image. All 38 jobs generated and approved.
Full records (prompt, seed, timings) in `assets/generated/manifest.json`.

## Summary
- A Backgrounds: 6 (3 portrait 1024x1536, 3 landscape 1536x1024)
- B World maps: 3 (1024x1536)
- C Menu key art: 1 (1024x1536)
- D Chapter scenes: 15 (1024x768)
- E Material tiles: 6 (512x512, mirror-blended seamless in post)
- F World intros: 3 (1024x768)
- G App icon: 1 (1024x1024)
- H UI ornaments: 3 raw + 3 `_alpha.png` keyed
- Regenerations: 22 (max 2 retries per job; none exhausted without an approved result)

## Learnings
- The endpoint has no negative-prompt field; the ART_DIRECTION negative list is recorded but not sent.
  Klein happily draws humans and lantern/sign glyphs for "night market" prompts, so the style string now
  also carries "no humans, no people, no signboards with writing, plain blank lanterns" and crowds are
  described as "only dumpling characters and chibi animals".
- "world map" as a phrase makes the model paint a globe; use "level-select map painting".
- Material prompts must say "one flat continuous surface, no individual buns/dumplings" or you get a grid of
  dumplings.
- White-background keying works well for these ornaments with a border-connected flood-fill matte
  (global distance-to-white ate interior specular highlights on the button plate). Procedural UI panels are
  not needed, but the keyed PNGs are 1024-1536 px and should be 9-sliced or scaled in-engine.
- `flux2` was not used; klein quality was sufficient and the whole set runs in ~15 minutes.

## Outputs
| id | file | size | model | seed | world | QA |
|---|---|---|---|---|---|---|
| world1_teahouse_bg | `backgrounds/world1_teahouse_bg.png` | 1024x1536 | klein | 45587 | W1 | OK (retry 1). Steamers/cups kept to table ends; centre clear. |
| world1_teahouse_bg_land | `backgrounds/world1_teahouse_bg_land.png` | 1536x1024 | klein | 45231 | W1 | OK first pass. |
| world2_nightmarket_bg | `backgrounds/world2_nightmarket_bg.png` | 1024x1536 | klein | 46170 | W2 | OK (retry 2). Earlier passes had lantern glyphs and human silhouettes. |
| world2_nightmarket_bg_land | `backgrounds/world2_nightmarket_bg_land.png` | 1536x1024 | klein | 46197 | W2 | OK (retry 2). Earlier pass had human silhouettes. |
| world3_mountain_bg | `backgrounds/world3_mountain_bg.png` | 1024x1536 | klein | 45313 | W3 | OK first pass. |
| world3_mountain_bg_land | `backgrounds/world3_mountain_bg_land.png` | 1536x1024 | klein | 45340 | W3 | OK first pass. |
| app_icon_1024 | `icon/app_icon_1024.png` | 1024x1024 | klein | 45524 | W- | OK first pass. Rounded-square bevel baked into art; crop/mask as needed. |
| world1_map | `map/world1_map.png` | 1024x1536 | klein | 45669 | W1 | OK (retry 1). First pass had numbered circles on path. |
| world2_map | `map/world2_map.png` | 1024x1536 | klein | 46224 | W2 | OK (retry 2). 'world map' wording produced a globe in the sky; reworded to 'level-select map'. |
| world3_map | `map/world3_map.png` | 1024x1536 | klein | 45422 | W3 | OK first pass. |
| mat_bamboo | `materials/mat_bamboo.png` | 512x512 | klein | 45516 | W- | OK first pass. |
| mat_beet_pink | `materials/mat_beet_pink.png` | 512x512 | klein | 45734 | W- | OK (retry 1). First pass rendered dumplings. |
| mat_gold | `materials/mat_gold.png` | 512x512 | klein | 46279 | W- | OK (retry 2). Earlier passes had a tile grid. |
| mat_matcha | `materials/mat_matcha.png` | 512x512 | klein | 45486 | W- | OK first pass. |
| mat_sesame_dough | `materials/mat_sesame_dough.png` | 512x512 | klein | 46267 | W- | OK (retry 2). Earlier passes rendered individual buns. |
| mat_ube_purple | `materials/mat_ube_purple.png` | 512x512 | klein | 45501 | W- | OK first pass. |
| menu_keyart | `menu/menu_keyart.png` | 1024x1536 | klein | 45451 | W1 | OK first pass. Empty space top for logo. |
| chapter_01 | `scenes/chapter_01.png` | 1024x768 | klein | 45749 | W1 | OK first pass. |
| chapter_02 | `scenes/chapter_02.png` | 1024x768 | klein | 45764 | W1 | OK first pass. |
| chapter_03 | `scenes/chapter_03.png` | 1024x768 | klein | 45779 | W1 | OK first pass. |
| chapter_04 | `scenes/chapter_04.png` | 1024x768 | klein | 45794 | W1 | OK first pass. |
| chapter_05 | `scenes/chapter_05.png` | 1024x768 | klein | 46286 | W1 | OK (retry 1). First pass drew a winged human girl instead of a crane. |
| chapter_06 | `scenes/chapter_06.png` | 1024x768 | klein | 46302 | W2 | OK (retry 1). First pass had humans in background. |
| chapter_07 | `scenes/chapter_07.png` | 1024x768 | klein | 46317 | W2 | OK (retry 1). First pass had signage text. |
| chapter_08 | `scenes/chapter_08.png` | 1024x768 | klein | 46332 | W2 | OK (retry 1). First pass had humans and signage text. |
| chapter_09 | `scenes/chapter_09.png` | 1024x768 | klein | 46347 | W2 | OK (retry 1). First pass had humans. |
| chapter_10 | `scenes/chapter_10.png` | 1024x768 | klein | 45885 | W2 | OK first pass. |
| chapter_11 | `scenes/chapter_11.png` | 1024x768 | klein | 45900 | W3 | OK first pass. |
| chapter_12 | `scenes/chapter_12.png` | 1024x768 | klein | 46362 | W3 | OK (retry 1). First pass drew a human girl. |
| chapter_13 | `scenes/chapter_13.png` | 1024x768 | klein | 45930 | W3 | OK first pass. |
| chapter_14 | `scenes/chapter_14.png` | 1024x768 | klein | 46377 | W3 | OK (retry 1). First pass had a sign with glyphs. |
| chapter_15 | `scenes/chapter_15.png` | 1024x768 | klein | 45960 | W3 | OK first pass. |
| world_intro_1 | `scenes/world_intro_1.png` | 1024x768 | klein | 46392 | W1 | OK (retry 1). First pass had plaque text. |
| world_intro_2 | `scenes/world_intro_2.png` | 1024x768 | klein | 46407 | W2 | OK (retry 1). First pass had humans and signage text. |
| world_intro_3 | `scenes/world_intro_3.png` | 1024x768 | klein | 46006 | W3 | OK first pass. |
| ui_bamboo_frame | `ui/ui_bamboo_frame.png` | 1024x1024 | klein | 46071 | W- | OK. Hollow centre keyed via centre seed. |
| ui_button_plate | `ui/ui_button_plate.png` | 1024x1024 | klein | 46050 | W- | OK. Global white-distance key ate interior highlights; fixed with border-connected flood-fill key. |
| ui_sign_board | `ui/ui_sign_board.png` | 1536x1024 | klein | 46021 | W- | OK. Keyed cleanly (flood-fill matting). |
