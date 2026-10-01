# Dumpling Rings — Formal puzzle semantics and solver

## 1. Mechanical state
A level is a set of **rings** (flat hoops lying on the table), **obstacles** (chopsticks / lantern gates / lantern arms
resting on top of the rings) and the pairwise **weave patterns** of crossing rings. The mechanical state is only:

| Field | Meaning |
|---|---|
| `angles[i]` | angle index of ring *i* (`angle = index × stepDeg`, step 30° in the campaign; 15° supported) |
| `removed[i]` | whether ring *i* has been lifted off the table |

Nothing decorative (particles, squash, flying dumplings) is part of the state.

## 2. Geometry ("what does *free* mean")
* A ring's wire is its circle minus its gaps (`gaps[] = {startDeg, widthDeg}` in the ring's local frame).
* Two rings whose circles cross have exactly two crossing points. The pair's **weave pattern** (from ring `a`'s
  perspective, crossings sorted by angle around `a`'s centre) is one of `alt1` (a over at crossing 0, b over at crossing 1),
  `alt2` (the reverse), `a_over`, `b_over` (one ring lies entirely on top). Unlisted pairs default to `alt1`.
* **Lift rule.** Ring *A* can be lifted iff every piece of wire that rests **on top of** *A* passes through a gap of *A*:
  * for every active crossing ring *B* and every crossing where *B* is over *A*: *B*'s wire at that crossing must lie in a gap
    of *A* — OR *B*'s own gap must be at that crossing (then there is no wire there) — OR *B* has been removed;
  * every obstacle wire sample inside *A*'s band must lie in a gap of *A* (colour gates are ignored by rings of the same
    `colorId`; a rotating obstacle's position depends on its driver ring's angle);
  * *A* must not be locked (`lockedBy` rings still present).
* The test is sampled (1° along wires, band = half thicknesses) and is **pairwise**: whether *B* blocks *A* depends only on
  (`angle[A]`, `angle[B]`). The rule engine precomputes a boolean `PassTable[A][B]` per pair and per (ring, obstacle); the
  renderer draws the same crossings with the same over/under, so visual and logic cannot diverge.

## 3. Actions
* `RotateTo(ring, targetIndex)` — one drag, whatever the distance, costs **1 move**. Rings sharing a `linkGroup` rotate
  together (`linkRatio` +1 chain, −1 hinge); removed members drop out of the chain. A rotation is illegal if the ring (or
  any linked member) is locked, or if any member would leave its `arc` on the way (both drag directions are tried).
* After every move the engine **cascades**: all releasable rings are removed simultaneously, repeatedly, until none is
  releasable. Several rings in one cascade = a combo.
* `ForceRelease(ring)` — Golden Steamer booster only; never produced by the solver.
* Undo and restart are free and are not moves. **Win** = all rings removed.

A level is rejected by the generator/validator if any ring is releasable in the initial state or if a ring interacts with
nothing (a "free ring").

## 4. Solver
`Solver` (core/solver/Solver.kt) is deterministic and shared by the generator, the validator, the on-device hint engine
and all boosters.

1. **Breadth-first search** over `(angles, removed)` with a packed `StateKey` transposition table. BFS over unit-cost moves
   gives a *provably minimal* solution when it completes. Budget: 2 000 000 states for validation.
2. Moves are pruned safely: (a) target angles with an identical full pass signature are merged (`meaningfulTargets`);
   (b) a rotation that flips no pass bit relative to the current partner angles is dropped — it can be deferred without
   changing the move count because moves commute and locks only ever open.
3. **Fallback for large levels** (7+ rings exhaust BFS): greedy best-first search (heuristic = active rings + 0.35 × blocked
   constraints) finds a solution quickly, then depth-limited DFS with a transposition table tries shorter lengths. If a
   depth is exhausted with no solution the result is proven optimal; otherwise the stored `parMoves` is an **upper bound**
   (`optimalProven=false` in `content/qa/solve/*.json`). An over-estimated par only makes 3 stars easier, never unfair.

Metrics recorded per level: optimal/bounded moves, proven flag, states explored, average branching, dead-end ratio,
solve time, topology hash (geometry + mechanics, ignoring start angles) for duplicate detection.

## 5. Generator (content/qa reproducibility)
`LevelGenerator` turns a roadmap row (`content/roadmap.json`: rings, focus, variation, difficulty) into a layout:
random crossing layout (clean crossings ≥ 30°, every ring crosses another, connected graph, inside the safe area) →
chapter recipe (pegs, locks, stacked weaves, colour gates, arcs, chains, hinges, double gaps, rotors) → random start
angles → solver. Candidates are accepted only when solvable without boosters, not releasable at start, recipe satisfied,
par inside the difficulty band and topology hash unique. The accepted `seed` is stored in the level so it can be
regenerated bit-for-bit (`Rng` = xorshift64*, platform independent).

## 6. Tools
```
./gradlew :tools:levels:installDist
tools/levels/build/install/levels/bin/levels generate [from] [to]   # writes content/levels + content/qa/solve
tools/levels/build/install/levels/bin/levels validate               # all acceptance checks, exit 1 on failure
tools/levels/build/install/levels/bin/levels report                 # docs/LEVEL_REPORT.md
python3 tools/level_preview/preview.py content/levels/world_01/level_001.json   # PNG preview for QA
```
