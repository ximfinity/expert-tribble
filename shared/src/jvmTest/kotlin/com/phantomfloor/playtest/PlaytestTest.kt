package com.phantomfloor.playtest

import com.phantomfloor.games.BalloonPopGame
import com.phantomfloor.games.DefuseBombGame
import com.phantomfloor.games.FloorIsLavaGame
import com.phantomfloor.games.GameScene
import com.phantomfloor.games.SimonGame
import com.phantomfloor.games.StompSpiderGame
import com.phantomfloor.games.TicTacToeGame
import com.phantomfloor.games.WhackAMoleGame
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bots play every scored game and the results go to `shared/build/playtest/report.md`.
 * Coloring is GPU-only (paint lives in a texture), so it's reviewed with the shader previews.
 *
 * Run just this: `./gradlew :shared:jvmTest --tests '*PlaytestTest*'`
 */
class PlaytestTest {

    private val games: List<() -> GameScene> = listOf(
        ::StompSpiderGame, ::WhackAMoleGame, ::DefuseBombGame, ::TicTacToeGame,
        ::SimonGame, ::BalloonPopGame, ::FloorIsLavaGame,
    )

    @Test
    fun botsPlayEveryGame() {
        val seconds = System.getProperty("playtest.seconds")?.toFloatOrNull() ?: 150f
        val results = ArrayList<SessionResult>()
        for (make in games) {
            for (persona in PERSONAS) {
                val counts = if (make().id == "game_ttt") listOf(1) else listOf(1, 3)
                for (n in counts) results += playSession(make(), persona, n, seconds)
            }
        }
        val out = File("build/playtest").apply { mkdirs() }
        File(out, "report.md").writeText(formatReport(results))

        for (r in results) assertNull("${r.title} (${r.persona} x${r.players}) crashed: ${r.error}", r.error)
        // Anyone who tries should be able to start every game from the START pad.
        for (r in results.filter { it.persona != "wanderer" }) {
            assertTrue("${r.title}: ${r.persona} never got a round started", r.firstStartAt != null)
        }
    }
}
