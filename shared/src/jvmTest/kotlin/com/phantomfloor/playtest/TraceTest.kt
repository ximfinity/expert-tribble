package com.phantomfloor.playtest

import com.phantomfloor.audio.Sfx
import com.phantomfloor.audio.SoundSink
import com.phantomfloor.engine.FrameState
import com.phantomfloor.games.BalloonPopGame
import com.phantomfloor.games.DefuseBombGame
import com.phantomfloor.games.FloorIsLavaGame
import com.phantomfloor.games.TicTacToeGame
import com.phantomfloor.games.WhackAMoleGame
import com.phantomfloor.gl.SpriteBatch
import com.phantomfloor.games.GameScene
import com.phantomfloor.games.SimonGame
import com.phantomfloor.games.StompSpiderGame
import java.io.File
import java.util.Locale
import org.junit.Test

/**
 * Focused play-test probes that back findings in docs/playtest/REPORT.md. Each writes a file to
 * shared/build/playtest/ (copied to build/playtest/ by tools/playtest/run.sh):
 *  - simon_trace.txt: one adult playing Simon, frame changes only (lit pads, texts, sounds, dropped frames)
 *  - lava_camper.txt: Floor is Lava score for a player who stands still off the grid / in the middle
 *  - wanderer_starts.txt: how quickly passers-by who ignore the game start a round by accident
 *  - frames/NAME.json: named game moments for tools/playtest/game-frames.js to render with the real shaders
 * Run just this: `./gradlew :shared:jvmTest --tests '*TraceTest*'`
 */
class TraceTest {
    private fun run(game: GameScene, bots: List<FloorBot>, seconds: Float, log: (Float, GameScene.Snapshot, List<FloorBot>, List<String>) -> Unit) {
        val aspect = 16f / 9f
        val sounds = ArrayList<String>()
        val frame = FrameState().apply {
            this.aspect = aspect
            sound = object : SoundSink { override fun play(sfx: Sfx, volume: Float, pan: Float) { sounds += sfx.name } }
        }
        game.onResize(1920, 1080)
        frame.dt = 1f / 60f
        game.update(frame)
        var view = game.snapshot(frame)
        var t = 0f
        while (t < seconds) {
            t += 1f / 60f
            frame.time = t; frame.dt = 1f / 60f; frame.nanoTime = (t * 1e9).toLong()
            sounds.clear()
            val pts = bots.mapNotNull { b -> b.think(1f / 60f, view, aspect, game.id); b.step(1f / 60f, aspect, frame.nanoTime) }
            if (pts.size < bots.size) sounds += "(tracking dropped a frame)"
            frame.setPeople(pts)
            game.update(frame)
            view = game.snapshot(frame)
            log(t, view, bots, sounds.toList())
        }
    }

    @Test
    fun simonTrace() {
        val out = StringBuilder()
        var last = ""
        run(SimonGame(), listOf(FloorBot(1, PERSONAS[1], 7 * 31)), 150f) { t, v, bots, snd ->
            val pads = v.shapes.filter { it.type == GameScene.SHAPE_PAD && it.size > 0.1f && it.size < 0.2f }.take(4)
            val lit = pads.mapIndexed { i, p -> if (p.p2 > 0.6f) "$i" else "." }.joinToString("")
            val txt = v.texts.joinToString(" | ") { it.text }
            val line = "lit=$lit  $txt  ${snd.joinToString(",")}"
            if (line != last) out.append(String.format(Locale.US, "%6.2f  bot=(%.2f,%.2f)  %s\n", t, bots[0].x, bots[0].y, line))
            last = line
        }
        File("build/playtest").mkdirs(); File("build/playtest/simon_trace.txt").writeText(out.toString())
    }

    @Test
    fun lavaCamper() {
        // One player stands still at the right edge of the floor; one stands in the middle.
        val out = StringBuilder()
        for ((name, x) in listOf("edge camper (x=1.70)" to 1.70f, "centre stander (x=0.89)" to 0.89f)) {
            val bot = FloorBot(1, PERSONAS[1], 1)
            var started = false
            var score = 0
            val game = FloorIsLavaGame()
            var ending = ""
            run(game, listOf(bot), 100f) { _, v, _, _ ->
                val start = v.texts.firstOrNull { it.text == "START" }
                if (!started) { if (start != null) { bot.x = start.x; bot.y = start.y } ; if (start == null) { started = true } }
                else { bot.x = x; bot.y = 0.47f }
                if (started && game.playPhase == GameScene.Phase.PLAYING) score = maxOf(score, game.playScore)
                if (game.playPhase == GameScene.Phase.OVER && ending.isEmpty()) ending = game.playOverMessage
            }
            out.append("$name: survived ${score}s of a 90 s round; ending: ${ending.ifEmpty { "(none)" }}\n")
        }
        File("build/playtest").mkdirs(); File("build/playtest/lava_camper.txt").writeText(out.toString())
    }

    @Test
    fun wandererStarts() {
        // Passers-by who never look at the game, starting away from START (top half of the floor).
        val out = StringBuilder()
        for (n in listOf(1, 3, 6)) {
            val waits = ArrayList<Float>()
            for (seed in 1..5) {
                val bots = List(n) { FloorBot(it + 1, PERSONAS[3], seed * 100 + it).apply { y = 0.6f + (it % 3) * 0.12f } }
                var attractSince = 0f
                var last = GameScene.Phase.ATTRACT
                val game = StompSpiderGame()
                run(game, bots, 600f) { t, _, _, _ ->
                    val ph = game.playPhase
                    if (ph != last) {
                        if (ph == GameScene.Phase.ATTRACT) attractSince = t
                        if (ph == GameScene.Phase.COUNTDOWN) waits += t - attractSince
                        last = ph
                    }
                }
            }
            waits.sort()
            val median = if (waits.isEmpty()) Float.NaN else waits[waits.size / 2]
            out.append(String.format(Locale.US, "%d wanderer(s): %d accidental starts in 5 x 600 s; median wait on the START screen %.1f s, fastest %.1f s\n", n, waits.size, median, waits.firstOrNull() ?: Float.NaN))
        }
        File("build/playtest").mkdirs(); File("build/playtest/wanderer_starts.txt").writeText(out.toString())
    }

    /** Full sprite floats and coloured labels of the current frame, as JSON (read via reflection; harness only). */
    private fun frameJson(game: GameScene, people: List<FloorBot>, aspect: Float): String {
        val batch = GameScene::class.java.getDeclaredField("shapes").apply { isAccessible = true }.get(game) as SpriteBatch
        val f = FloatArray(SpriteBatch.FLOATS)
        val sprites = (0 until batch.count).joinToString(",") { i -> batch.read(i, f); f.joinToString(",", "[", "]") }
        @Suppress("UNCHECKED_CAST")
        val labels = GameScene::class.java.getDeclaredField("labels").apply { isAccessible = true }.get(game) as List<Any>
        val texts = labels.joinToString(",") { l ->
            fun g(n: String) = l.javaClass.getDeclaredField(n).apply { isAccessible = true }.get(l)
            val str = (g("s") as String).replace("\\", "\\\\").replace("\"", "\\\"")
            "{\"s\":\"$str\",\"v\":[${listOf("x", "y", "h", "r", "g", "b", "a").joinToString(",") { g(it).toString() }}]}"
        }
        val accent = (GameScene::class.java.getDeclaredMethod("getAccent").apply { isAccessible = true }.invoke(game) as FloatArray).joinToString(",")
        val ppl = people.joinToString(",") { "[${it.x},${it.y}]" }
        return "{\"aspect\":$aspect,\"accent\":[$accent],\"sprites\":[$sprites],\"texts\":[$texts],\"people\":[$ppl]}"
    }

    /** Captures named moments of real games played by bots, for tools/playtest/game-frames.js to render. */
    @Test
    fun dumpFrames() {
        class Shot(val name: String, val phase: GameScene.Phase, val nth: Int, val at: Float)
        val plan: List<Triple<() -> GameScene, Int, List<Shot>>> = listOf(
            Triple(::SimonGame, 1, listOf(Shot("simon_attract", GameScene.Phase.ATTRACT, 1, 0.2f), Shot("simon_input", GameScene.Phase.PLAYING, 1, 5f),
                Shot("simon_over", GameScene.Phase.OVER, 1, 1f), Shot("simon_countdown_stale", GameScene.Phase.COUNTDOWN, 2, 1.5f), Shot("simon_round2_start", GameScene.Phase.PLAYING, 2, 0.05f))),
            Triple(::FloorIsLavaGame, 3, listOf(Shot("lava_attract", GameScene.Phase.ATTRACT, 1, 0.2f), Shot("lava_early", GameScene.Phase.PLAYING, 1, 12f), Shot("lava_late", GameScene.Phase.PLAYING, 1, 55f), Shot("lava_countdown_stale", GameScene.Phase.COUNTDOWN, 2, 1.5f))),
            Triple(::DefuseBombGame, 1, listOf(Shot("bomb_show", GameScene.Phase.PLAYING, 1, 1.4f), Shot("bomb_input", GameScene.Phase.PLAYING, 1, 4.5f), Shot("bomb_over", GameScene.Phase.OVER, 1, 1f), Shot("bomb_countdown_stale", GameScene.Phase.COUNTDOWN, 2, 1.5f))),
            Triple(::StompSpiderGame, 3, listOf(Shot("stomp_playing", GameScene.Phase.PLAYING, 1, 40f), Shot("stomp_over", GameScene.Phase.OVER, 1, 1.5f))),
            Triple(::WhackAMoleGame, 3, listOf(Shot("moles_playing", GameScene.Phase.PLAYING, 1, 20f))),
            Triple(::BalloonPopGame, 3, listOf(Shot("balloons_playing", GameScene.Phase.PLAYING, 1, 30f), Shot("balloons_attract", GameScene.Phase.ATTRACT, 1, 0.2f))),
            Triple(::TicTacToeGame, 1, listOf(Shot("ttt_playing", GameScene.Phase.PLAYING, 1, 5f), Shot("ttt_over", GameScene.Phase.OVER, 1, 1f))),
        )
        val dir = File("build/playtest/frames").apply { mkdirs() }
        for ((make, n, shots) in plan) {
            val game = make()
            val bots = List(n) { FloorBot(it + 1, PERSONAS[1], 7 * 31 + it) }
            val todo = shots.toMutableList()
            var last = GameScene.Phase.ATTRACT
            var since = 0f
            val count = HashMap<GameScene.Phase, Int>().apply { put(GameScene.Phase.ATTRACT, 1) }
            run(game, bots, 200f) { t, _, _, _ ->
                val ph = game.playPhase
                if (ph != last) { since = t; count.merge(ph, 1, Int::plus); last = ph }
                val hit = todo.firstOrNull { it.phase == ph && it.nth == count[ph] && t - since >= it.at }
                if (hit != null) { todo.remove(hit); File(dir, "${hit.name}.json").writeText(frameJson(game, bots, 16f / 9f)) }
            }
        }
    }
}
