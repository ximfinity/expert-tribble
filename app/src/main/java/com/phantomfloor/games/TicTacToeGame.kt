package com.phantomfloor.games

import com.phantomfloor.audio.Sfx
import com.phantomfloor.scenes.SceneParam

/**
 * TIC-TAC-TOE on the floor: stand in a square for a moment to claim it. Play against a friend,
 * or against the computer (X is always the player who starts).
 */
class TicTacToeGame : GameScene("game_ttt", "Tic-Tac-Toe") {

    private val vsComputer = SceneParam("cpu", "Play vs computer", 0f, 1f, 1f, step = 1f)
    private val difficulty = SceneParam("difficulty", "Computer skill", 0f, 1f, 0.7f)
    override val params = listOf(vsComputer, difficulty)
    override val howToPlay = "Stand in a square to claim it.\nThree in a row wins!"
    override val roundSeconds: Float? = null
    override val accent = floatArrayOf(0.4f, 0.75f, 1f)

    private val board = IntArray(9)     // 0 empty, 1 X, 2 O
    private val dwell = FloatArray(9)
    private var turn = 1
    private var cpuDelay = 0f
    private var winLine: IntArray? = null
    private var finishAt = -1f
    private var xWins = 0
    private var oWins = 0

    private fun cx(i: Int) = aspect / 2f + (i % 3 - 1) * 0.3f
    private fun cy(i: Int) = 0.52f + (1 - i / 3) * 0.25f

    override fun scoreText() = "X $xWins  ·  O $oWins"

    override fun startRound() {
        board.fill(0); dwell.fill(0f)
        turn = 1
        winLine = null
        finishAt = -1f
        cpuDelay = 0f
    }

    private val cpuTurn get() = vsComputer.value > 0.5f && turn == 2

    override fun updatePlaying(dt: Float) {
        if (finishAt >= 0f) {
            if (phaseTime >= finishAt) {
                val w = winner()
                score = xWins
                val cpu = vsComputer.value > 0.5f
                endRound(
                    when (w) {
                        1 -> if (cpu) "YOU WIN!" else "X WINS!"
                        2 -> if (cpu) "COMPUTER WINS" else "O WINS!"
                        else -> "DRAW"
                    },
                )
            }
            return
        }
        if (cpuTurn) {
            cpuDelay += dt
            if (cpuDelay > 0.9f) { place(chooseMove()); cpuDelay = 0f }
            return
        }
        for (i in 0 until 9) {
            if (board[i] != 0) { dwell[i] = 0f; continue }
            dwell[i] = if (occupants(cx(i), cy(i), 0.12f).isNotEmpty()) dwell[i] + dt else 0f
            if (dwell[i] >= DWELL) { place(i); break }
        }
    }

    private fun place(i: Int) {
        if (i < 0 || board[i] != 0) return
        board[i] = turn
        dwell.fill(0f)
        frame.sound.play(if (turn == 1) Sfx.CLICK else Sfx.BONK, 0.8f, pan(cx(i) / aspect))
        val w = winner()
        if (w != 0 || board.none { it == 0 }) {
            if (w == 1) xWins++ else if (w == 2) oWins++
            if (w != 0) { frame.sound.play(Sfx.FANFARE, 0.8f); confetti(aspect / 2f, 0.52f, 60) }
            finishAt = phaseTime + 1.5f
        } else {
            turn = 3 - turn
        }
    }

    private fun winner(): Int {
        for (l in LINES) {
            val a = board[l[0]]
            if (a != 0 && a == board[l[1]] && a == board[l[2]]) { winLine = l; return a }
        }
        return 0
    }

    /** Minimax for a perfect move; with lower skill, sometimes plays a random square instead. */
    private fun chooseMove(): Int {
        val empty = (0 until 9).filter { board[it] == 0 }
        if (empty.isEmpty()) return -1
        if (rng.nextFloat() > difficulty.value) return empty[rng.nextInt(empty.size)]
        var best = -2
        var move = empty[0]
        for (i in empty) {
            board[i] = 2
            val v = -negamax(1)
            board[i] = 0
            if (v > best) { best = v; move = i }
        }
        return move
    }

    private fun negamax(player: Int): Int {
        for (l in LINES) {
            val a = board[l[0]]
            if (a != 0 && a == board[l[1]] && a == board[l[2]]) return if (a == player) 1 else -1
        }
        if (board.none { it == 0 }) return 0
        var best = -2
        for (i in 0 until 9) if (board[i] == 0) {
            board[i] = player
            best = maxOf(best, -negamax(3 - player))
            board[i] = 0
        }
        return best
    }

    override fun drawPlaying() {
        val line = winLine
        for (i in 0 until 9) {
            val win = line != null && i in line
            shape(SHAPE_PAD, cx(i), cy(i), 0.12f, 0.12f, 0.14f, 0.22f, 1f, p1 = 0.2f, p2 = if (win) 0.5f else 0f)
            if (win) shape(SHAPE_GLOW, cx(i), cy(i), 0.2f, 1f, 1f, 0.5f, 0.8f)
            when (board[i]) {
                1 -> shape(SHAPE_X, cx(i), cy(i), 0.09f, 1f, 0.35f, 0.35f)
                2 -> shape(SHAPE_RING, cx(i), cy(i), 0.085f, 0.35f, 0.6f, 1f, 1f, p1 = 0.3f)
            }
            if (dwell[i] > 0f) {
                val c = if (turn == 1) floatArrayOf(1f, 0.35f, 0.35f) else floatArrayOf(0.35f, 0.6f, 1f)
                shape(SHAPE_PROGRESS, cx(i), cy(i), 0.115f, c[0], c[1], c[2], 1f, p1 = dwell[i] / DWELL)
            }
        }
        if (finishAt < 0f) {
            val who = if (turn == 1) "X" else "O"
            label(if (cpuTurn) "Computer is thinking..." else "$who's turn", aspect / 2f, 0.1f, 0.05f, if (turn == 1) 1f else 0.5f, if (turn == 1) 0.5f else 0.7f, if (turn == 1) 0.5f else 1f)
        }
    }

    companion object {
        private const val DWELL = 0.8f
        private val LINES = arrayOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
        )
    }
}
