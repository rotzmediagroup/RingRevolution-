# Level JSON schema (schemaVersion 2)

Files: `content/levels/world_0X/level_NNN.json` (L001–L050 world 1, L051–L100 world 2, L101–L150 world 3), plus
`content/worlds.json`, `content/chapters.json`, `content/cosmetics.json`, `content/economy.json`, `content/roadmap.json`.
Solver metadata lives in `content/qa/solve/level_NNN.json` (not shipped). Coordinates are normalised to the unit board,
angles in degrees, 0° = east, counter-clockwise positive (the renderer flips y).

```jsonc
{
  "schemaVersion": 2,
  "id": "L001", "index": 1, "worldId": "blossom_teahouse", "chapter": 1, "chapterLevel": 1,
  "seed": 100000,                     // generator seed: regenerates this exact level
  "name": "Eerste stoom 1",
  "mechanicTags": ["basic_rotation", "release"],
  "rings": [{
    "id": "r1", "center": [0.5, 0.5], "radius": 0.19, "thickness": 0.055,
    "gaps": [{"startDeg": 0, "widthDeg": 80}],   // local frame; several gaps = double-gap mechanic
    "initialAngleDeg": 120, "stepDeg": 30,
    "exitAngleDeg": 0,                           // visual fly-out direction only
    "arc": {"minDeg": 60, "maxDeg": 240},        // optional restricted rotation range (absolute angle)
    "lockedBy": ["r2"],                          // optional: these rings must be removed first
    "linkGroup": "g1", "linkRatio": 1,           // optional: chain (+1) / hinge (-1)
    "materialId": "dough_sesame", "colorId": "red" // colorId only with colour gates
  }],
  "obstacles": [{
    "id": "o1", "shape": "segment", "center": [0.75, 0.5], "from": [0.6, 0.5], "to": [0.9, 0.5], "thickness": 0.028,
    "colorId": null,                 // colour gate when set: same-colour rings pass
    "linkedRingId": null, "baseAngleDeg": 0,   // rotating obstacle: rotates around `center` with the ring
    "visualId": "chopstick"          // chopstick | lantern_gate | lantern_arm
  }],
  "weaves": [{"a": "r1", "b": "r2", "pattern": "alt1"}],   // alt1 | alt2 | a_over | b_over
  "winCondition": "remove_all_rings",
  "parMoves": 1, "goodMoves": 3, "difficulty": 1,
  "reward": {"coins": 22, "boosters": {}},
  "tutorialCueId": "rotate_first_ring",
  "isChefLevel": false,
  "canonicalSolution": ["r1:330"],  // ringId:absoluteAngleDeg per move (hint fallback + regression tests)
  "topologyHash": "..."
}
```

Validation rules enforced by `levels validate` (hard CI failure): exactly 150 files, id/world/chapter consistent, ring
count equals the roadmap, chef flag on every 10th level, rings inside the safe area with radius ≥ 0.08 and thickness ≥
0.03 (≥ 44 pt touch targets on a 390 pt board), no ring releasable at start, no free ring, solvable without boosters,
`parMoves` equals the solver result, canonical solution replays to a win, 2-star threshold above par, unique topology
hash, known mechanic tags, at most one new mechanic per level (tutorial-before-combination), tutorial cue on levels 1–10,
reward defined. See `docs/SOLVER.md` for the semantics.
