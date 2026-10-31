package com.phantomfloor.playtest

import com.phantomfloor.engine.PersonPoint
import com.phantomfloor.games.GameScene
import com.phantomfloor.games.GameScene.SeenShape
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** How a simulated player moves and thinks. Speeds are in world units per second (floor height = 1). */
internal data class Persona(
    val name: String,
    /** Top walking/running speed. About 0.5 is a brisk adult walk on a 3 m floor. */
    val speed: Float,
    /** Seconds between seeing something and reacting to it. */
    val reaction: Float,
    /** Chance of misremembering each step of a Simon / bomb sequence. */
    val memoryError: Float,
    /** Ignores the game and wanders (a guest who doesn't know it's a game). */
    val wanders: Boolean = false,
)

internal val PERSONAS = listOf(
    Persona("kid", speed = 0.9f, reaction = 0.35f, memoryError = 0.08f),
    Persona("adult", speed = 0.55f, reaction = 0.25f, memoryError = 0.03f),
    Persona("casual", speed = 0.35f, reaction = 0.5f, memoryError = 0.12f),
    Persona("wanderer", speed = 0.3f, reaction = 0.5f, memoryError = 0f, wanders = true),
)

/**
 * A simulated player who only uses what is drawn on the floor (shapes and text), the way a real
 * guest would: walks to START, chases spiders/moles/balloons, memorises flashing pads, claims
 * tic-tac-toe squares, and dodges lava. Movement has a speed limit, reaction delay, and tracking
 * jitter, so results reflect what real people could manage.
 */
internal class FloorBot(val id: Int, val persona: Persona, seed: Int) {
    private val rng = Random(seed)
    var x = 0.4f + rng.nextFloat()
    var y = 0.1f + rng.nextFloat() * 0.3f
    private var vx = 0f
    private var vy = 0f
    private var targetX = x
    private var targetY = y
    private var thinkIn = 0f
    private var wanderAngle = rng.nextFloat() * 6.28f

    // Sequence games
    private val seen = ArrayList<Int>()
    private val litBefore = BooleanArray(4)
    private var recalled: List<Int> = emptyList()
    private var stepIndex = 0
    private var needExit = false
    private var wasShowing = false

    /** Moves toward the current target; returns this frame's tracked point (null if tracking dropped it). */
    fun step(dt: Float, aspect: Float, nanos: Long): PersonPoint? {
        val dx = targetX - x
        val dy = targetY - y
        val d = hypot(dx, dy)
        val maxStep = persona.speed * dt
        val ox = x; val oy = y
        if (d > 1e-4f) {
            val k = min(1f, maxStep / d)
            x += dx * k; y += dy * k
        }
        x = x.coerceIn(0.02f, aspect - 0.02f)
        y = y.coerceIn(0.02f, 0.98f)
        vx = (x - ox) / max(dt, 1e-4f)
        vy = (y - oy) / max(dt, 1e-4f)
        if (rng.nextFloat() < 0.01f) return null // camera misses a frame now and then
        val jx = (rng.nextFloat() - 0.5f) * 0.012f
        val jy = (rng.nextFloat() - 0.5f) * 0.012f
        return PersonPoint(id, (x + jx) / aspect, y + jy, vx / aspect, vy, nanos)
    }

    /** Looks at the floor and picks where to go next (after the persona's reaction delay). */
    fun think(dt: Float, view: GameScene.Snapshot, aspect: Float, gameId: String) {
        thinkIn -= dt
        // Watching a sequence must happen every frame, or flashes are missed.
        if (gameId == "game_simon" || gameId == "game_bomb") watchSequence(view)
        if (thinkIn > 0f) return
        thinkIn = persona.reaction * (0.7f + rng.nextFloat() * 0.6f)

        val texts = view.texts.map { it.text }
        val start = view.texts.firstOrNull { it.text == "START" }
        if (persona.wanders) return wander(aspect)
        if (start != null) return go(start.x, start.y)

        when (gameId) {
            "game_stomp" -> chase(view.shapes.filter { it.type == GameScene.SHAPE_SPIDER })
            "game_moles" -> chase(view.shapes.filter { it.type == GameScene.SHAPE_MOLE && it.p1 > 0.5f && it.p2 == 0f })
            "game_balloons" -> chase(view.shapes.filter { it.type == GameScene.SHAPE_BALLOON })
            "game_simon", "game_bomb" -> playSequence(view, texts, aspect)
            "game_ttt" -> playTicTacToe(view, texts)
            "game_lava" -> dodgeLava(view)
            else -> wander(aspect)
        }
    }

    private fun go(tx: Float, ty: Float) { targetX = tx; targetY = ty }

    private fun wander(aspect: Float) {
        wanderAngle += (rng.nextFloat() - 0.5f) * 1.5f
        val tx = x + kotlin.math.cos(wanderAngle) * 0.3f
        val ty = y + kotlin.math.sin(wanderAngle) * 0.3f
        if (tx !in 0.05f..aspect - 0.05f || ty !in 0.05f..0.95f) wanderAngle += 3.14f
        go(tx.coerceIn(0.05f, aspect - 0.05f), ty.coerceIn(0.05f, 0.95f))
    }

    private fun chase(targets: List<SeenShape>) {
        val t = targets.minByOrNull { hypot(it.x - x, it.y - y) } ?: return
        go(t.x, t.y)
    }

    private fun pads(view: GameScene.Snapshot) = view.shapes.filter { it.type == GameScene.SHAPE_PAD && it.size > 0.1f && it.size < 0.2f }

    private fun watchSequence(view: GameScene.Snapshot) {
        val texts = view.texts.map { it.text }
        val showing = texts.any { it.startsWith("Watch") }
        val pads = pads(view).take(4)
        if (showing && !wasShowing) {
            // A new sequence is starting: forget the last one.
            seen.clear(); recalled = emptyList(); stepIndex = 0; needExit = false
        }
        wasShowing = showing
        if (showing) {
            pads.forEachIndexed { i, p ->
                val lit = p.p2 > 0.6f
                if (lit && !litBefore[i]) seen += i
                litBefore[i] = lit
            }
        } else {
            for (i in 0 until 4) litBefore[i] = false
        }
    }

    private fun playSequence(view: GameScene.Snapshot, texts: List<String>, aspect: Float) {
        val pads = pads(view).take(4)
        val inputting = texts.any { it.startsWith("Your turn") || it.startsWith("Step ") }
        if (!inputting || pads.size < 4) {
            // Wait in the middle, off every pad.
            go(aspect / 2f, 0.52f); needExit = false
            return
        }
        if (recalled.isEmpty() && seen.isNotEmpty()) {
            recalled = seen.map { if (rng.nextFloat() < persona.memoryError) rng.nextInt(4) else it }
            stepIndex = 0
        }
        if (stepIndex >= recalled.size) return go(aspect / 2f, 0.52f)
        val p = pads[recalled[stepIndex]]
        if (needExit) {
            // Step off toward the centre before stepping on the next pad (re-entry is what counts).
            go(aspect / 2f, 0.52f)
            if (hypot(x - aspect / 2f, y - 0.52f) < 0.05f) needExit = false
            return
        }
        go(p.x, p.y)
        if (hypot(x - p.x, y - p.y) < p.size * 0.5f) { stepIndex++; needExit = true }
    }

    private fun playTicTacToe(view: GameScene.Snapshot, texts: List<String>) {
        if (texts.any { it.startsWith("Computer") }) return
        val squares = view.shapes.filter { it.type == GameScene.SHAPE_PAD && it.size in 0.11f..0.13f }
        val taken = view.shapes.filter { it.type == GameScene.SHAPE_X || it.type == GameScene.SHAPE_RING }
        val free = squares.filter { s -> taken.none { hypot(it.x - s.x, it.y - s.y) < 0.02f } }
        // Stay on the square we picked until it's claimed.
        val current = free.firstOrNull { hypot(it.x - targetX, it.y - targetY) < 0.01f }
        if (current != null) return
        val centre = squares.getOrNull(4)
        val pick = free.firstOrNull { centre != null && hypot(it.x - centre.x, it.y - centre.y) < 0.01f }
            ?: free.randomOrNull(rng) ?: return
        go(pick.x, pick.y)
    }

    private fun dodgeLava(view: GameScene.Snapshot) {
        val tiles = view.shapes.filter { it.type == GameScene.SHAPE_LAVA }
        val here = tiles.minByOrNull { hypot(it.x - x, it.y - y) } ?: return
        val unsafe = { t: SeenShape -> t.p1 > 0.3f || t.p2 > 0f }
        if (!unsafe(here) && rng.nextFloat() < 0.8f) return go(here.x, here.y)
        val safe = tiles.filter { !unsafe(it) }.minByOrNull { hypot(it.x - x, it.y - y) } ?: return
        go(safe.x, safe.y)
    }
}
