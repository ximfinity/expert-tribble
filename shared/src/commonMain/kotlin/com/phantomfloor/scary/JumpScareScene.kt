package com.phantomfloor.scary

import com.phantomfloor.audio.Ambience
import com.phantomfloor.audio.Sfx
import com.phantomfloor.engine.FrameState
import com.phantomfloor.engine.PersonPoint
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.scenes.Scene
import com.phantomfloor.scenes.SceneParam
import com.phantomfloor.scenes.Season
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * JUMP SCARE. The floor stays pitch black and silent. When someone new walks on, it waits a beat
 * (a heartbeat thuds), then a screaming ghoul or skull lunges up out of the floor right in front
 * of them with a flash, holds for a second, and burns away. Each person is scared once until the
 * floor has been empty for a few seconds, so a crowd gets one scare each, not a strobe.
 */
class JumpScareScene : Scene("scare_jump", "Jump Scare", Season.SCARY) {

    private val suspense = SceneParam("suspense", "Wait before the scare (s)", 0.3f, 6f, 1.5f)
    private val rest = SceneParam("rest", "Rest between scares (s)", 3f, 120f, 15f)
    private val monster = SceneParam("monster", "Monster: Ghoul · Skull · Random", 0f, 2f, 2f, step = 1f)
    private val size = SceneParam("size", "Monster size", 0.15f, 0.5f, 0.3f)
    private val flash = SceneParam("flash", "Flash brightness", 0f, 1f, 0.6f)
    private val loudness = SceneParam("loud", "Scream volume", 0f, 1f, 1f)
    private val heartbeat = SceneParam("heartbeat", "Heartbeat while waiting", 0f, 1f, 1f, step = 1f)
    override val params = listOf(suspense, rest, monster, size, flash, loudness, heartbeat)

    override val ambience = Ambience(drone = 0.22f, droneHz = 41f, wind = 0.1f)

    private enum class State { IDLE, WAITING, SCARING, RESTING }

    private lateinit var program: GlProgram
    private val rng = Random(Random.nextLong())
    private var state = State.IDLE
    private var stateTime = 0f
    private var waitFor = 0f
    private var targetId = -1
    private var nextBeat = 0f
    private val scared = HashSet<Int>()
    private var emptyFor = 0f
    private var redPulse = 0f

    // The scare itself, in world units.
    private var fromX = 0f; private var fromY = 0f
    private var toX = 0f; private var toY = 0f
    private var angle = 0f
    private var kind = 0f

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        program = gl.shaders.program("fullscreen.vert", "scare_face.frag")
    }

    private fun setState(s: State) { state = s; stateTime = 0f }

    override fun update(frame: FrameState) {
        val dt = frame.dt
        stateTime += dt
        redPulse = (redPulse - dt / 3f).coerceAtLeast(0f)
        emptyFor = if (frame.people.isEmpty()) emptyFor + dt else 0f
        if (emptyFor > 3f) scared.clear()

        when (state) {
            State.IDLE -> {
                val victim = frame.people.firstOrNull { it.id !in scared } ?: return
                targetId = victim.id
                waitFor = suspense.value * (0.7f + rng.nextFloat() * 0.6f)
                nextBeat = 0.2f
                setState(State.WAITING)
            }
            State.WAITING -> {
                val victim = frame.people.firstOrNull { it.id == targetId }
                if (victim == null) { setState(State.IDLE); return }
                if (heartbeat.value > 0.5f && stateTime >= nextBeat) {
                    // The heartbeat speeds up and gets louder as the scare approaches.
                    val k = (stateTime / waitFor).coerceIn(0f, 1f)
                    frame.sound.play(Sfx.HEARTBEAT, 0.5f + 0.5f * k)
                    nextBeat += 0.9f - 0.45f * k
                }
                if (stateTime >= waitFor) startScare(frame, victim)
            }
            State.SCARING -> {
                // Keep the face in front of the victim if they back away.
                frame.people.firstOrNull { it.id == targetId }?.let { p ->
                    val px = p.x * frame.aspect
                    val dx = toX - px; val dy = toY - p.y
                    val d = hypot(dx, dy)
                    if (d > size.value * 1.4f) { toX = px + dx / d * size.value * 1.4f; toY = p.y + dy / d * size.value * 1.4f }
                }
                if (stateTime > SCARE_TIME) setState(State.RESTING)
            }
            State.RESTING -> if (stateTime > rest.value) setState(State.IDLE)
        }
    }

    private fun startScare(frame: FrameState, victim: PersonPoint) {
        val a = frame.aspect
        val px = victim.x * a
        // Face appears ahead of where they're walking; if they're standing still, toward the middle.
        var dx = victim.vx * a
        var dy = victim.vy
        if (hypot(dx, dy) < 0.05f) { dx = a / 2f - px; dy = 0.5f - victim.y }
        val len = hypot(dx, dy).coerceAtLeast(1e-4f)
        dx /= len; dy /= len
        val s = size.value
        toX = (px + dx * s * 1.1f).coerceIn(s * 0.6f, a - s * 0.6f)
        toY = (victim.y + dy * s * 1.1f).coerceIn(s * 0.6f, 1f - s * 0.6f)
        fromX = toX + dx * s * 1.5f
        fromY = toY + dy * s * 1.5f
        angle = atan2(dy, dx) - HALF_PI
        kind = when (monster.int) { 0 -> 0f; 1 -> 1f; else -> if (rng.nextBoolean()) 0f else 1f }
        scared += victim.id
        redPulse = 1f
        val pan = pan(toX / a)
        frame.sound.play(Sfx.STINGER, loudness.value, pan)
        frame.sound.play(Sfx.SCREAM, loudness.value, pan)
        setState(State.SCARING)
    }

    override fun draw(frame: FrameState) {
        program.use()
        program.setCommon(frame)
        val t = stateTime
        val active = state == State.SCARING
        program.set1f("uActive", if (active) 1f else 0f)
        program.set1f("uRedPulse", redPulse * 0.6f)
        if (!active) {
            program.set1f("uFlash", 0f)
            gl.drawFullscreen()
            return
        }
        // Lunge: rushes in from further away and swells with an overshoot.
        val lunge = easeOutBack((t / LUNGE).coerceIn(0f, 1f))
        val shake = (if (t < 1.4f) 1f else 0f) * 0.012f
        val x = fromX + (toX - fromX) * lunge.coerceAtMost(1f) + sin(t * 63f) * shake
        val y = fromY + (toY - fromY) * lunge.coerceAtMost(1f) + cos(t * 71f) * shake
        val scale = size.value * (0.25f + 0.75f * lunge)
        val scream = when {
            t < 0.08f -> t / 0.08f
            t < 1.3f -> 1f - 0.15f * sin(t * 9f).coerceAtLeast(0f)
            else -> (1f - (t - 1.3f) / 0.8f).coerceAtLeast(0.2f)
        }
        val fade = ((t - (SCARE_TIME - 1.1f)) / 1.1f).coerceIn(0f, 1f)
        val strobe = flash.value * (if (t < 0.12f) 1f - t / 0.12f else if (t in 0.22f..0.3f) 0.4f else 0f)
        program.set4f("uFace", x, y, scale, angle + sin(t * 37f) * shake * 3f)
        program.set4f("uFaceAnim", t, kind, scream, fade)
        program.set1f("uFlash", strobe)
        gl.drawFullscreen()
    }

    private fun easeOutBack(x: Float): Float {
        val c1 = 2.2f
        val c3 = c1 + 1f
        val k = x - 1f
        return 1f + c3 * k * k * k + c1 * k * k
    }

    private companion object {
        const val SCARE_TIME = 2.8f
        const val LUNGE = 0.2f
        const val HALF_PI = 1.5707964f
    }
}
