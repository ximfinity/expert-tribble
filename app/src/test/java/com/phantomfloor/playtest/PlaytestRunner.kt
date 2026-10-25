package com.phantomfloor.playtest

import com.phantomfloor.audio.Sfx
import com.phantomfloor.audio.SoundSink
import com.phantomfloor.engine.FrameState
import com.phantomfloor.games.GameScene
import java.util.Locale
import kotlin.math.abs

/** One finished round. */
internal data class Round(val startedAt: Float, val seconds: Float, val score: Int, val ending: String)

/** Everything observed while bots played one game. */
internal data class SessionResult(
    val gameId: String,
    val title: String,
    val persona: String,
    val players: Int,
    val simulatedSeconds: Float,
    val firstStartAt: Float?,
    val rounds: List<Round>,
    val sounds: Map<Sfx, Int>,
    val maxShapes: Int,
    /** Texts drawn partly off the floor (world x outside 0..aspect or y outside 0..1). */
    val offscreenTexts: Set<String>,
    /** Pairs of texts drawn on top of each other in the same frame. */
    val overlappingTexts: Set<String>,
    val error: String?,
)

/**
 * Plays [game] with [players] bots of [persona] for [seconds] of simulated time at 60 fps,
 * recording rounds, scores, sounds and on-floor text problems. Bot moves are seeded, but each game
 * seeds its own rng from System.nanoTime(), so numbers vary a little between runs.
 */
internal fun playSession(game: GameScene, persona: Persona, players: Int, seconds: Float, seed: Int = 7, aspect: Float = 16f / 9f): SessionResult {
    val sounds = HashMap<Sfx, Int>()
    val frame = FrameState().apply {
        this.aspect = aspect
        sound = object : SoundSink {
            override fun play(sfx: Sfx, volume: Float, pan: Float) { sounds.merge(sfx, 1, Int::plus) }
        }
    }
    val bots = List(players) { FloorBot(it + 1, persona, seed * 31 + it) }
    val rounds = ArrayList<Round>()
    val offscreen = HashSet<String>()
    val overlaps = HashSet<String>()
    var maxShapes = 0
    var firstStart: Float? = null
    var roundStart = 0f
    var lastPhase = game.playPhase
    var lastScore = 0
    var lastOverText = ""
    val dt = 1f / 60f
    var t = 0f
    var error: String? = null
    try {
        game.onResize(1920, 1080)
        frame.dt = dt
        game.update(frame) // the app always updates before drawing
        var view = game.snapshot(frame)
        while (t < seconds) {
            t += dt
            frame.time = t
            frame.dt = dt
            frame.nanoTime = (t * 1e9).toLong()
            val points = bots.mapNotNull { b ->
                b.think(dt, view, aspect, game.id)
                b.step(dt, aspect, frame.nanoTime)
            }
            frame.setPeople(points)
            game.update(frame)
            view = game.snapshot(frame)
            maxShapes = maxOf(maxShapes, view.shapes.size)
            checkTexts(view, aspect, offscreen, overlaps)

            val phase = game.playPhase
            if (phase == GameScene.Phase.PLAYING) lastScore = game.playScore
            if (phase != lastPhase) {
                if (phase == GameScene.Phase.PLAYING) {
                    roundStart = t
                    if (firstStart == null) firstStart = t
                }
                if (phase == GameScene.Phase.OVER) {
                    // The large accent text on the results screen is the ending message.
                    lastOverText = game.playOverMessage
                    rounds += Round(roundStart, t - roundStart, game.playScore, lastOverText)
                }
                lastPhase = phase
            }
        }
    } catch (e: Throwable) {
        error = "${e::class.simpleName}: ${e.message} at ${e.stackTrace.firstOrNull { it.className.startsWith("com.phantomfloor") }}"
    }
    if (game.playPhase == GameScene.Phase.PLAYING && firstStart != null) {
        rounds += Round(roundStart, t - roundStart, lastScore, "(still playing when the session ended)")
    }
    return SessionResult(game.id, game.title, persona.name, players, t, firstStart, rounds, sounds, maxShapes, offscreen, overlaps, error)
}

/** Rough text extents: labels are centred, and bold glyphs are about 0.45 x the label height wide. */
private fun checkTexts(view: GameScene.Snapshot, aspect: Float, offscreen: MutableSet<String>, overlaps: MutableSet<String>) {
    val boxes = view.texts.map { tx ->
        val h = tx.height
        val w = tx.text.length * h * 0.45f
        floatArrayOf(tx.x - w / 2, tx.y - h / 2, tx.x + w / 2, tx.y + h / 2)
    }
    view.texts.forEachIndexed { i, tx ->
        val b = boxes[i]
        if (b[0] < 0f || b[2] > aspect || b[1] < 0f || b[3] > 1f) offscreen += tx.text
        for (j in 0 until i) {
            val o = boxes[j]
            if (b[0] < o[2] && o[0] < b[2] && b[1] < o[3] && o[1] < b[3] && tx.text != view.texts[j].text) {
                overlaps += "\"${view.texts[j].text}\" / \"${tx.text}\""
            }
        }
    }
}

internal fun formatReport(results: List<SessionResult>): String = buildString {
    appendLine("# PhantomFloor headless play-test")
    appendLine()
    appendLine("Simulated players (`FloorBot`) only use what is drawn on the floor. Personas:")
    for (p in PERSONAS) appendLine("- **${p.name}**: speed ${p.speed}/s, reaction ${p.reaction}s, memory error ${(p.memoryError * 100).toInt()}%${if (p.wanders) ", ignores the game" else ""}")
    appendLine()
    for ((title, group) in results.groupBy { it.title }) {
        appendLine("## $title")
        appendLine()
        appendLine("| Persona | Players | First start (s) | Rounds | Scores | Round length (s) | Endings | Sounds |")
        appendLine("|---|---|---|---|---|---|---|---|")
        for (r in group) {
            val scores = r.rounds.joinToString(", ") { it.score.toString() }.ifEmpty { "-" }
            val lens = r.rounds.joinToString(", ") { String.format(Locale.US, "%.0f", it.seconds) }.ifEmpty { "-" }
            val endings = r.rounds.map { it.ending }.distinct().joinToString(", ").ifEmpty { "-" }
            val snd = r.sounds.entries.sortedByDescending { it.value }.take(5).joinToString(" ") { "${it.key.name.lowercase()}×${it.value}" }
            val start = r.firstStartAt?.let { String.format(Locale.US, "%.1f", it) } ?: "never"
            appendLine("| ${r.persona} | ${r.players} | $start | ${r.rounds.size} | $scores | $lens | $endings | $snd |")
        }
        group.mapNotNull { it.error }.distinct().forEach { appendLine("\n**Error:** `$it`") }
        val off = group.flatMap { it.offscreenTexts }.toSet()
        if (off.isNotEmpty()) appendLine("\n**Text that may run off the floor:** ${off.joinToString(", ") { "\"$it\"" }}")
        val ov = group.flatMap { it.overlappingTexts }.toSet()
        if (ov.isNotEmpty()) appendLine("\n**Text that may overlap:** ${ov.joinToString(", ")}")
        appendLine("\nMax shapes on screen: ${group.maxOf { it.maxShapes }}")
        appendLine()
    }
}
