package com.shiostudios.dumplingrings.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Versioned level schema (see docs/LEVEL_SCHEMA.md).
 *
 * Coordinates are normalised to the unit board [0,1]x[0,1]; angles are degrees, 0° = +x (east),
 * increasing counter-clockwise in mathematical convention (the renderer flips y).
 */
@Serializable
data class GapDef(
    /** start of the gap in the ring's LOCAL frame (before the ring's current rotation is applied) */
    val startDeg: Double,
    val widthDeg: Double,
)

@Serializable
data class ArcDef(
    /** lowest allowed absolute angle (deg) of the ring */
    val minDeg: Int,
    /** highest allowed absolute angle (deg) of the ring; min<=angle<=max, inclusive. */
    val maxDeg: Int,
)

@Serializable
data class RingDef(
    val id: String,
    val center: List<Double>,
    val radius: Double,
    val thickness: Double,
    val gaps: List<GapDef>,
    val initialAngleDeg: Int,
    val stepDeg: Int = 30,
    /** Direction (deg, world frame) in which the released ring flies off towards the basket (visual only). */
    val exitAngleDeg: Int = 90,
    /** Optional rotation restriction (restricted arc mechanic). */
    val arc: ArcDef? = null,
    /** Ring ids that must be removed before this ring can rotate or release (locked order mechanic). */
    val lockedBy: List<String> = emptyList(),
    /** Rings sharing a linkGroup rotate together: delta * linkRatio (chain = +1, hinge = -1). */
    val linkGroup: String? = null,
    val linkRatio: Int = 1,
    val materialId: String = "dough_sesame",
    /** Colour id for colour gates; a ring passes freely through gate obstacles of the same colour. */
    val colorId: String? = null,
)

@Serializable
enum class ObstacleShape {
    @SerialName("arc") ARC,
    @SerialName("segment") SEGMENT,
}

@Serializable
data class ObstacleDef(
    val id: String,
    val shape: ObstacleShape,
    /** ARC: center of the arc; SEGMENT: pivot point (segment from `from` to `to`, rotated around center). */
    val center: List<Double>,
    val radius: Double = 0.0,
    val startDeg: Double = 0.0,
    val widthDeg: Double = 0.0,
    /** SEGMENT end points (absolute board coords) when shape == SEGMENT. */
    val from: List<Double> = emptyList(),
    val to: List<Double> = emptyList(),
    val thickness: Double = 0.03,
    /** Colour gate: rings with the same colorId pass through; others are blocked. null = blocks all. */
    val colorId: String? = null,
    /** Rotating obstacle: rotates around `center` together with the given ring (same angle). */
    val linkedRingId: String? = null,
    val baseAngleDeg: Int = 0,
    val visualId: String = "chopstick",
)

/** Over/under pattern of a crossing ring pair, from ring `a`'s perspective; crossings sorted by angle around a's center. */
@Serializable
enum class WeavePattern {
    /** a is over b at crossing 0, b over a at crossing 1 (interlocked) */
    @SerialName("alt1") ALT1,
    /** a is over b at crossing 1, b over a at crossing 0 (interlocked) */
    @SerialName("alt2") ALT2,
    /** a lies entirely on top of b */
    @SerialName("a_over") A_OVER,
    /** b lies entirely on top of a (b must go first unless a has gaps at both crossings) */
    @SerialName("b_over") B_OVER,
}

@Serializable
data class WeaveDef(val a: String, val b: String, val pattern: WeavePattern)

@Serializable
data class RewardDef(val coins: Int = 20, val boosters: Map<String, Int> = emptyMap())

@Serializable
data class LevelDefinition(
    val schemaVersion: Int = 2,
    val id: String,
    val index: Int,
    val worldId: String,
    val chapter: Int,
    val chapterLevel: Int,
    val seed: Long,
    val name: String = "",
    val mechanicTags: List<String> = emptyList(),
    val rings: List<RingDef>,
    val obstacles: List<ObstacleDef> = emptyList(),
    /** Weave pattern per crossing pair; pairs not listed default to ALT1. */
    val weaves: List<WeaveDef> = emptyList(),
    val winCondition: String = "remove_all_rings",
    val parMoves: Int,
    /** 2-star threshold (moves). */
    val goodMoves: Int,
    val difficulty: Int,
    val reward: RewardDef = RewardDef(),
    val tutorialCueId: String? = null,
    val isChefLevel: Boolean = false,
    /** Canonical solution as "ringId:angleDeg" steps; shipped for the hint fallback and regression tests. */
    val canonicalSolution: List<String> = emptyList(),
    val topologyHash: String = "",
)

/** Solver / QA metadata written by tools, kept out of the client bundle. */
@Serializable
data class SolveMetadata(
    val levelId: String,
    val solvable: Boolean,
    val optimalMoves: Int,
    val optimalProven: Boolean,
    val statesExplored: Int,
    val avgBranching: Double,
    val deadEndRatio: Double,
    val solveMillis: Long,
    val solution: List<String>,
    val topologyHash: String,
    val notes: List<String> = emptyList(),
)
