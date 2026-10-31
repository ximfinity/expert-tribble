package com.phantomfloor.scary

import com.phantomfloor.audio.Ambience
import com.phantomfloor.audio.Sfx
import com.phantomfloor.engine.FrameState
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
 * GRASPING HANDS. Black floor. Where someone stops, the ground rumbles and splits with glowing
 * cracks, then rotting hands burst up around them and claw toward their feet. Walk away and the
 * hands sink back and the cracks go dark; stop somewhere else and it starts again.
 */
class GraspingHandsScene : Scene("scare_hands", "Grasping Hands", Season.SCARY) {

    private val handsEach = SceneParam("hands", "Hands per person", 2f, 6f, 4f, step = 1f)
    private val handSize = SceneParam("size", "Hand size", 0.08f, 0.22f, 0.14f)
    private val delay = SceneParam("delay", "Stand still before it opens (s)", 0f, 4f, 0.8f)
    private val loudness = SceneParam("loud", "Scare volume", 0f, 1f, 1f)
    override val params = listOf(handsEach, handSize, delay, loudness)

    override val ambience = Ambience(drone = 0.28f, droneHz = 33f, wind = 0.06f)

    private class Hand(val offsetAngle: Float, val ring: Float, val seed: Float, var rise: Float = 0f, var angle: Float = 0f, var grip: Float = 0f)
    private class Site(val owner: Int, val x: Float, val y: Float, var age: Float = 0f, var away: Float = 0f, var glow: Float = 0f, val hands: MutableList<Hand> = ArrayList())

    private lateinit var program: GlProgram
    private val rng = Random(Random.nextLong())
    private val sites = ArrayList<Site>()
    private val stillFor = HashMap<Int, Float>()
    private var flash = 0f

    private val handData = FloatArray(MAX_HANDS * 4)
    private val handData2 = FloatArray(MAX_HANDS * 4)
    private val crackData = FloatArray(MAX_SITES * 4)

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        program = gl.shaders.program("fullscreen.vert", "scare_hands.frag")
    }

    override fun update(frame: FrameState) {
        val dt = frame.dt
        val a = frame.aspect
        flash = (flash - dt * 4f).coerceAtLeast(0f)
        val people = frame.people.associateBy { it.id }

        // A new site opens where someone has paused, away from any open site of theirs.
        for (p in people.values) {
            val px = p.x * a
            val slow = hypot(p.vx * a, p.vy) < 0.15f
            stillFor[p.id] = if (slow) (stillFor[p.id] ?: 0f) + dt else 0f
            val near = sites.any { it.owner == p.id && hypot(it.x - px, it.y - p.y) < 0.3f && it.away < 1f }
            if (!near && (stillFor[p.id] ?: 0f) >= delay.value) {
                if (sites.size >= MAX_SITES) sites.removeAt(0)
                val n = handsEach.int
                val site = Site(p.id, px, p.y)
                repeat(n) { i ->
                    site.hands += Hand(i * 6.283f / n + rng.nextFloat() * 0.6f, 0.75f + rng.nextFloat() * 0.35f, rng.nextFloat() * 50f)
                }
                sites += site
                frame.sound.play(Sfx.RUMBLE, loudness.value, pan(p.x))
            }
        }
        stillFor.keys.retainAll(people.keys)

        val r = handSize.value
        for (s in sites) {
            s.age += dt
            val p = people[s.owner]
            val px = p?.let { it.x * a }
            val dist = if (p != null && px != null) hypot(px - s.x, p.y - s.y) else 9f
            s.away = if (dist > 0.35f) s.away + dt else 0f
            val closing = s.away > 1.2f
            s.glow += ((if (closing) 0f else 1f) - s.glow) * (dt * if (closing) 1.2f else 3f).coerceAtMost(1f)
            // Hands burst up shortly after the cracks open.
            if (s.age > HANDS_AFTER && s.age - dt <= HANDS_AFTER) {
                frame.sound.play(Sfx.SNAP, loudness.value * 0.8f, pan(s.x / a))
                frame.sound.play(Sfx.GROAN, loudness.value, pan(s.x / a))
                flash = 0.25f
            }
            for (h in s.hands) {
                val hx = s.x + cos(h.offsetAngle) * r * 1.5f * h.ring
                val hy = s.y + sin(h.offsetAngle) * r * 1.5f * h.ring
                val want = if (closing || s.age < HANDS_AFTER) 0f else 1f
                h.rise += (want - h.rise) * (dt * if (want > h.rise) 7f else 2f).coerceAtMost(1f)
                // Reach toward the person's feet (or the site's centre once they've gone).
                val tx = px ?: s.x
                val ty = p?.y ?: s.y
                val target = atan2(ty - hy, tx - hx) - HALF_PI
                h.angle += wrap(target - h.angle) * (dt * 5f).coerceAtMost(1f)
                // Clutching: faster and tighter when the feet are close.
                val near = (1f - hypot(tx - hx, ty - hy) / (r * 3f)).coerceIn(0f, 1f)
                h.grip = 0.35f + 0.35f * sin(s.age * (4f + 6f * near) + h.seed) * (0.5f + 0.5f * near) + 0.3f * near
            }
        }
        sites.removeAll { it.away > 1.2f && it.glow < 0.02f }
    }

    override fun draw(frame: FrameState) {
        program.use()
        program.setCommon(frame)
        var n = 0
        val r = handSize.value
        sites.forEachIndexed { i, s ->
            crackData[i * 4] = s.x; crackData[i * 4 + 1] = s.y
            crackData[i * 4 + 2] = r * 3f * easeOut(s.age / 1.2f)
            crackData[i * 4 + 3] = s.glow
            for (h in s.hands) {
                if (n >= MAX_HANDS) break
                handData[n * 4] = s.x + cos(h.offsetAngle) * r * 1.5f * h.ring
                handData[n * 4 + 1] = s.y + sin(h.offsetAngle) * r * 1.5f * h.ring
                handData[n * 4 + 2] = h.rise
                handData[n * 4 + 3] = h.angle
                handData2[n * 4] = h.grip.coerceIn(0f, 1f)
                handData2[n * 4 + 1] = h.seed
                handData2[n * 4 + 2] = r
                handData2[n * 4 + 3] = 0f
                n++
            }
        }
        program.set4fv("uCracks", crackData, MAX_SITES)
        program.set1i("uCrackCount", sites.size)
        program.set4fv("uHands", handData, MAX_HANDS)
        program.set4fv("uHands2", handData2, MAX_HANDS)
        program.set1i("uHandCount", n)
        program.set1f("uFlash", flash)
        gl.drawFullscreen()
    }

    private fun wrap(x: Float): Float {
        var v = x
        while (v > PI) v -= 2f * PI
        while (v < -PI) v += 2f * PI
        return v
    }

    private fun easeOut(x: Float): Float { val t = x.coerceIn(0f, 1f); return 1f - (1f - t) * (1f - t) * (1f - t) }

    private companion object {
        const val MAX_HANDS = 12
        const val MAX_SITES = 8
        const val HANDS_AFTER = 0.7f
        const val PI = 3.1415927f
        const val HALF_PI = 1.5707964f
    }
}
