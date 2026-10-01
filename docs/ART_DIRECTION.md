# Ring Revolution — Art Direction Brief

Shared visual universe with Dumpling Diner (see `assets/imported/sheets/`). All generated art must match these sheets.

## Style keywords (use in every image prompt)
"hand-painted cozy mobile game illustration, warm soft cinematic lighting, gentle bloom, subtle depth of field,
chibi kawaii proportions, smooth painterly shading, rich saturated but soft colors, high detail, no text, no watermark,
no letters, no logo"

## Negative (avoid)
text, letters, watermark, signature, photo-realistic, 3D render look, harsh black outlines, grid, checkerboard, clutter, dark gritty, horror, humans

## Worlds
| World | Palette | Motifs |
|---|---|---|
| 1 Blossom Teahouse (L1–50) | honey wood #C98A4B, bamboo green #7FA865, cream #FFF4E3, sakura pink #F6A7C1, morning gold #FFD88A | tea house interior/veranda, cherry blossoms, bamboo steamers with steam, teacups, paper lanterns (unlit), morning sun |
| 2 Lantern Night Market (L51–100) | indigo dusk #26305E, amber lantern #FFB347, red #D9453B, gold #E8C170, mist #9FA8DA | night market street, glowing paper lanterns, food stalls, warm reflections, bokeh, mist |
| 3 Moonlit Mountain Kitchen (L101–150) | moon blue #3F5C8C, jade #5FB89A, lavender #B9A5D9, snow white #F4F6FF, gold #E6C76A | mountain temple kitchen, moonlight, soft clouds, jade ornaments, winter blossoms, magic steam |

## Rings
Ring geometry is drawn procedurally in-engine (exact hitboxes). Generated art supplies only seamless *material tiles*
(sesame dough, matcha, beet pink, ube purple, gold, bamboo) used as texture fills.

## Output rules
- Backgrounds: 1024x1536 portrait (phone), 1536x1024 (tablet landscape). No transparency needed.
- Any sprite requiring transparency is generated on a flat pure #00FF00 or pure white background and keyed/alpha-matted in the pipeline; results are QA'd visually.
- Record prompt, model, seed, size for every output in `assets/generated/manifest.json`.
