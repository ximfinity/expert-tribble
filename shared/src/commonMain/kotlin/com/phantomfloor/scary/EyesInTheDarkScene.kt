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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * EYES IN THE DARK. Black floor. When someone walks on, glowing eyes blink open in the darkness
 * around them and watch, whispering. Moving fast makes the nearest ones close and hide; standing
 * still lets more gather and creep closer, until a ring of teeth opens under the person's feet and
 * snaps shut.
 */
class EyesInTheDarkScene : Scene("scare_eyes", "Eyes in the Dark", Season.SCARY) {

    private val maxEyes = SceneParam("eyes", "How many eyes", 4f, MAX.toFloat(), 14f, step = 1f)
    private val creep = SceneParam("creep", "Creep speed", 0f, 1f, 0.5f)
    private val snapAfter = SceneParam("snap", "Snap when still for (s)", 2f, 12f, 4f)
    private val teeth = SceneParam("teeth", "Teeth from below", 0f, 1f, 1f, step = 1f)
    private val loudness = SceneParam("loud", "Scare volume", 0f, 1f, 1f)
    override val params = listOf(maxEyes, creep, snapAfter, teeth, loudness)

    override val ambience = Ambience(drone = 0.3f, droneHz = 36f, wind = 0.08f)

    private class Eye(
        var owner: Int, var angle: Float, var dist: Float, val size: Float, val red: Boolean, val seed: Float,
        var open: Float = 0f, var closing: Boolean = false, var blinkIn: Float = 2f, var blink: Float = 0f,
        var x: Float = 0f, var y: Float = 0f,
    )

    private lateinit var program: GlProgram
    private val rng = Random(Random.nextLong())
    private val eyes = ArrayList<Eye>()
    private val still = HashMap<Int, Float>()
    private var spawnIn = 0f
    private var whisperIn = 2f
    private var occupiedFor = 0f

    // The maw: opens under a still person, then snaps shut.
    private var mawOwner = -1
    private var mawTime = -1f
    private var mawX = 0f
    private var mawY = 0f
    private var cooldown = 0f
    private var flash = 0f

    private val eyeData = FloatArray(MAX * 4)
    private val eyeData2 = FloatArray(MAX * 4)

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        program = gl.shaders.program("fullscreen.vert", "scare_eyes.frag")
    }

    override fun update(frame: FrameState) {
        val dt = frame.dt
        val a = frame.aspect
        val people = frame.people.associateBy { it.id }
        occupiedFor = if (people.isEmpty()) 0f else occupiedFor + dt
        cooldown = (cooldown - dt).coerceAtLeast(0f)
        flash = (flash - dt * 3f).coerceAtLeast(0f)

        // Spawn: more eyes open the longer people stay, up to the limit.
        val want = if (people.isEmpty()) 0 else minOf(maxEyes.int, 2 + (occupiedFor * 1.3f).toInt())
        spawnIn -= dt
        if (eyes.count { !it.closing } < want && spawnIn <= 0f && mawTime < 0f) {
            val owner = people.values.random(rng)
            eyes += Eye(owner.id, rng.nextFloat() * 6.283f, 0.3f + rng.nextFloat() * 0.25f, 0.016f + rng.nextFloat() * 0.012f, rng.nextFloat() < 0.35f, rng.nextFloat() * 100f)
            spawnIn = 0.35f + rng.nextFloat() * 0.6f
        }

        for (p in people.values) {
            val speed = hypot(p.vx * a, p.vy)
            still[p.id] = if (speed < 0.06f) (still[p.id] ?: 0f) + dt else 0f
        }
        still.keys.retainAll(people.keys)

        for (e in eyes) {
            val p = people[e.owner]
            if (p == null) { e.closing = true }
            else {
                val px = p.x * a
                val speed = hypot(p.vx * a, p.vy)
                // Creep closer while the person stands still; drift back when they move.
                val minDist = 0.13f
                e.dist += if (speed < 0.1f) -creep.value * 0.03f * dt else 0.02f * dt
                if (mawOwner == e.owner && mawTime >= 0f) e.dist += (minDist * 0.8f - e.dist) * (dt * 4f).coerceAtMost(1f)
                e.dist = e.dist.coerceIn(minDist * 0.8f, 0.6f)
                e.angle += sin(frame.time * 0.3f + e.seed) * 0.05f * dt
                e.x = px + cos(e.angle) * e.dist * 1.2f
                e.y = p.y + sin(e.angle) * e.dist
                // Fast movement nearby makes eyes snap shut and hide.
                if (speed > 0.45f && e.dist < 0.3f && rng.nextFloat() < dt * 3f) e.closing = true
                if (e.x !in 0.02f..a - 0.02f || e.y !in 0.02f..0.98f) e.closing = true
            }
            // Blinking.
            e.blinkIn -= dt
            if (e.blinkIn <= 0f) { e.blink = 0.18f; e.blinkIn = 2f + rng.nextFloat() * 5f }
            e.blink = (e.blink - dt).coerceAtLeast(0f)
            val target = if (e.closing) 0f else if (e.blink > 0f) 0.05f else 1f
            e.open += (target - e.open) * (dt * if (e.closing) 10f else 5f).coerceAtMost(1f)
        }
        eyes.removeAll { it.closing && it.open < 0.03f }

        // Whispers come from the dark, more often the more eyes are watching.
        whisperIn -= dt * (0.3f + eyes.size * 0.08f)
        if (whisperIn <= 0f && eyes.isNotEmpty()) {
            val e = eyes.random(rng)
            frame.sound.play(Sfx.WHISPER, 0.5f + 0.4f * loudness.value, pan(e.x / a))
            whisperIn = 2.5f + rng.nextFloat() * 3f
        }

        updateMaw(frame, people)
    }

    private fun updateMaw(frame: FrameState, people: Map<Int, PersonPoint>) {
        val dt = frame.dt
        if (mawTime < 0f) {
            if (teeth.value < 0.5f || cooldown > 0f) return
            val victim = still.entries.firstOrNull { it.value > snapAfter.value }?.key ?: return
            val p = people[victim] ?: return
            mawOwner = victim
            mawTime = 0f
            mawX = p.x * frame.aspect
            mawY = p.y
            frame.sound.play(Sfx.RUMBLE, 0.8f * loudness.value, pan(p.x))
            frame.sound.play(Sfx.GROAN, 0.7f * loudness.value, pan(p.x))
            return
        }
        mawTime += dt
        if (mawTime >= MAW_OPEN && mawTime - dt < MAW_OPEN) {
            frame.sound.play(Sfx.SNAP, loudness.value, pan(mawX / frame.aspect))
            frame.sound.play(Sfx.STINGER, loudness.value, pan(mawX / frame.aspect))
            flash = 0.5f
            // Everything shuts its eyes after the bite.
            for (e in eyes) e.closing = true
        }
        if (mawTime > MAW_OPEN + 0.6f) {
            mawTime = -1f
            mawOwner = -1
            cooldown = 8f
            still.clear()
        }
    }

    private fun mawRadius(): Float {
        val t = mawTime
        return when {
            t < 0f -> 0f
            t < MAW_OPEN - 0.1f -> 0.2f * easeOut(t / 0.9f)          // opens wide
            t < MAW_OPEN -> 0.2f * (1f - (t - (MAW_OPEN - 0.1f)) / 0.1f) // and SNAPS
            else -> 0f
        }
    }

    override fun draw(frame: FrameState) {
        program.use()
        program.setCommon(frame)
        // Closing eyes can briefly push the list past the shader's limit; newest ones win.
        val shown = eyes.takeLast(MAX)
        val n = shown.size
        shown.forEachIndexed { i, e ->
            eyeData[i * 4] = e.x; eyeData[i * 4 + 1] = e.y; eyeData[i * 4 + 2] = e.open; eyeData[i * 4 + 3] = e.size
            val p = frame.people.firstOrNull { it.id == e.owner }
            eyeData2[i * 4] = p?.let { it.x * frame.aspect } ?: e.x
            eyeData2[i * 4 + 1] = p?.y ?: (e.y - 0.1f)
            eyeData2[i * 4 + 2] = if (e.red) 1f else 0f
            eyeData2[i * 4 + 3] = e.seed
        }
        program.set4fv("uEyes", eyeData, MAX)
        program.set4fv("uEyes2", eyeData2, MAX)
        program.set1i("uEyeCount", n)
        val r = mawRadius()
        program.set4f("uMaw", mawX, mawY, r, if (r > 0f) 1f else 0f)
        program.set1f("uFlash", flash)
        gl.drawFullscreen()
    }

    private fun easeOut(x: Float): Float { val t = x.coerceIn(0f, 1f); return 1f - (1f - t) * (1f - t) * (1f - t) }

    private companion object {
        const val MAX = 24
        const val MAW_OPEN = 1.6f
    }
}
