package com.phantomfloor.scenes

import com.phantomfloor.gl.GLES30
import com.phantomfloor.audio.Ambience
import com.phantomfloor.audio.Sfx
import com.phantomfloor.engine.FrameState
import com.phantomfloor.engine.PersonPoint
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.SpriteBatch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * SHADOW PUPPETS: the floor glows with flickering candlelight and shadows of witches, ghosts,
 * zombies and long clawed hands wander across it, each with its own gait. They notice people:
 * zombies shamble over with arms out, ghosts drift close and raise their sleeves, witches circle
 * and cackle, and hands creep in from the edges to claw at feet. Puppets fade away now and then
 * and reappear somewhere else, so the cast keeps changing.
 */
class ShadowPuppetsScene : Scene("puppets", "Shadow Puppets") {

    private val count = SceneParam("count", "How many puppets", 2f, MAX_PUPPETS.toFloat(), 7f, step = 1f)
    private val cast = SceneParam("cast", "Cast: All · Witches · Ghosts · Zombies · Hands", 0f, 4f, 0f, step = 1f)
    private val size = SceneParam("size", "Puppet size", 0.1f, 0.35f, 0.19f)
    private val stretch = SceneParam("stretch", "Shadow length", 1f, 2f, 1.3f)
    private val follow = SceneParam("follow", "Drawn to people", 0f, 1f, 0.7f)
    private val speed = SceneParam("speed", "Wander speed", 0.3f, 2f, 1f)
    private val light = SceneParam("light", "Candle light", 0.2f, 1.5f, 1f)
    private val tint = SceneParam("tint", "Candle ↔ Moonlight", 0f, 1f, 0f)
    override val params = listOf(count, cast, size, stretch, follow, speed, light, tint)

    override val ambience = Ambience(wind = 0.25f, drone = 0.22f, droneHz = 46f, crickets = 0.15f)

    private enum class Kind(val shader: Float) { WITCH(0f), GHOST(1f), ZOMBIE(2f), HAND(3f), BROOM(4f) }

    private class Puppet(var kind: Kind, var x: Float, var y: Float, val seed: Float) {
        var heading = 0f
        var phase = 0f
        var reach = 0f
        var facingRight = true
        var alpha = 0f
        var life = 25f
        var fadingOut = false
        var lean = 0f       // hands: direction they reach (radians)
        var extend = 0f     // hands: how far out of the edge they've crept, 0..1
        var targetId = -1
    }

    private lateinit var floorProgram: GlProgram
    private lateinit var puppetProgram: GlProgram
    private val batch = SpriteBatch(MAX_PUPPETS)
    private val rng = Random(Random.nextLong())
    private val puppets = ArrayList<Puppet>()
    private var flicker = 0f
    private var flickerTarget = 0f
    private var aspect = 16f / 9f

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        floorProgram = gl.shaders.program("fullscreen.vert", "puppet_floor.frag")
        puppetProgram = gl.shaders.program("sprite.vert", "puppet.frag")
        batch.onSurfaceCreated()
    }

    private fun pickKind(): Kind = when (cast.int) {
        1 -> if (rng.nextFloat() < 0.3f) Kind.BROOM else Kind.WITCH
        2 -> Kind.GHOST
        3 -> Kind.ZOMBIE
        4 -> Kind.HAND
        else -> when (rng.nextInt(9)) {
            0, 1 -> Kind.WITCH
            2 -> Kind.BROOM
            3, 4 -> Kind.GHOST
            5, 6 -> Kind.ZOMBIE
            else -> Kind.HAND
        }
    }

    private fun spawn(p: Puppet? = null): Puppet {
        val kind = pickKind()
        val puppet = p ?: Puppet(kind, 0f, 0f, rng.nextFloat() * 100f)
        puppet.kind = kind
        puppet.alpha = 0f
        puppet.fadingOut = false
        puppet.life = 18f + rng.nextFloat() * 25f
        puppet.reach = 0f
        puppet.targetId = -1
        if (kind == Kind.HAND) {
            // Hands live on the floor's edges and creep inward.
            when (rng.nextInt(3)) {
                0 -> { puppet.x = 0.15f + rng.nextFloat() * (aspect - 0.3f); puppet.y = -0.02f; puppet.lean = HALF_PI }
                1 -> { puppet.x = -0.02f; puppet.y = 0.1f + rng.nextFloat() * 0.5f; puppet.lean = 0f }
                else -> { puppet.x = aspect + 0.02f; puppet.y = 0.1f + rng.nextFloat() * 0.5f; puppet.lean = PI }
            }
            puppet.extend = 0f
        } else {
            puppet.x = 0.15f + rng.nextFloat() * (aspect - 0.3f)
            puppet.y = 0.08f + rng.nextFloat() * 0.45f
            puppet.heading = rng.nextFloat() * 6.283f
        }
        return puppet
    }

    override fun update(frame: FrameState) {
        val dt = frame.dt
        aspect = frame.aspect
        // Candle flicker: a jittery target the flame chases.
        if (rng.nextFloat() < dt * 8f) flickerTarget = rng.nextFloat()
        flicker += (flickerTarget - flicker) * (dt * 12f).coerceAtMost(1f)

        while (puppets.size < count.int) puppets += spawn()
        while (puppets.size > count.int) puppets.removeAt(puppets.lastIndex)

        val s = size.value
        for (p in puppets) {
            p.life -= dt
            if (p.life <= 0f) p.fadingOut = true
            p.alpha = if (p.fadingOut) p.alpha - dt / 1.5f else (p.alpha + dt / 1.5f).coerceAtMost(1f)
            if (p.alpha <= 0f && p.fadingOut) { spawn(p); continue }

            val target = nearestPerson(frame, p)
            if (p.kind == Kind.HAND) updateHand(frame, p, target, dt) else updateWalker(frame, p, target, dt, s)
        }
    }

    private fun nearestPerson(frame: FrameState, p: Puppet): PersonPoint? {
        val notice = 0.25f + follow.value * 0.9f
        if (follow.value <= 0f) return null
        return frame.people.minByOrNull { hypot(it.x * aspect - p.x, it.y - p.y) }
            ?.takeIf { hypot(it.x * aspect - p.x, it.y - p.y) < notice }
    }

    private fun updateWalker(frame: FrameState, p: Puppet, target: PersonPoint?, dt: Float, s: Float) {
        val base = when (p.kind) {
            Kind.ZOMBIE -> 0.035f
            Kind.GHOST -> 0.055f
            Kind.BROOM -> 0.16f
            else -> 0.06f
        } * speed.value
        var vx: Float
        var vy: Float
        if (target != null && p.kind != Kind.BROOM) {
            // Approach the person, stopping a little to one side of them.
            val tx = target.x * aspect - (if (p.x < target.x * aspect) 1f else -1f) * (0.08f + s * 0.45f)
            val ty = target.y - 0.02f
            val dx = tx - p.x
            val dy = ty - p.y
            val d = hypot(dx, dy)
            val k = if (d > 0.02f) (base * (1f + follow.value * 0.6f)) / d else 0f
            vx = dx * k
            vy = dy * k
            p.reach += ((if (d < 0.25f) 1f else 0.3f) - p.reach) * (dt * 2f).coerceAtMost(1f)
            if (p.targetId != target.id) {
                p.targetId = target.id
                callOut(frame, p)
            } else if (d < 0.3f && ready(p.seed.toLong(), frame.time, 7f + rng.nextFloat() * 4f)) {
                callOut(frame, p)
            }
        } else {
            // Wander: a slowly turning heading; walls turn them back.
            p.heading += (rng.nextFloat() - 0.5f) * 2.5f * dt
            vx = cos(p.heading) * base
            vy = sin(p.heading) * base * 0.6f
            p.reach += (0f - p.reach) * (dt * 1.5f).coerceAtMost(1f)
            p.targetId = -1
            if (p.kind == Kind.BROOM && target != null && ready(p.seed.toLong(), frame.time, 9f)) callOut(frame, p)
        }
        // Zombies lurch: speed pulses with each dragging step.
        if (p.kind == Kind.ZOMBIE) { val k = 0.45f + 0.55f * kotlin.math.abs(sin(p.phase)); vx *= k; vy *= k }
        val maxY = 1f - 2f * s * stretch.value * 0.85f
        val nx = p.x + vx * dt
        val ny = p.y + vy * dt
        if (nx < 0.08f || nx > aspect - 0.08f) p.heading = PI - p.heading
        if (ny < 0.03f || ny > maxY) p.heading = -p.heading
        p.x = nx.coerceIn(0.08f, aspect - 0.08f)
        p.y = ny.coerceIn(0.03f, maxY.coerceAtLeast(0.04f))
        if (kotlin.math.abs(vx) > 0.004f) p.facingRight = vx > 0f
        else if (target != null) p.facingRight = target.x * aspect > p.x
        // Walk cycle advances with distance (ghosts and brooms bob with time instead).
        p.phase += when (p.kind) {
            Kind.GHOST -> dt * 2.2f
            Kind.BROOM -> dt * 3f
            else -> hypot(vx, vy) / (s * 0.5f) * dt * 2.2f + dt * 0.3f
        }
    }

    private fun updateHand(frame: FrameState, p: Puppet, target: PersonPoint?, dt: Float) {
        // Creeps out of its edge; when someone is near it stretches toward their feet and claws.
        val want = if (p.fadingOut) 0f else if (target != null) 1f else 0.45f + 0.15f * sin(p.phase * 0.3f)
        p.extend += (want - p.extend) * (dt * 1.2f).coerceAtMost(1f)
        val aim = if (target != null) atan2(target.y - p.y, target.x * aspect - p.x) else p.lean
        p.lean += wrap(aim - p.lean) * (dt * 1.5f).coerceAtMost(1f)
        p.reach += ((if (target != null) 1f else 0f) - p.reach) * (dt * 2f).coerceAtMost(1f)
        p.phase += dt * (1.2f + p.reach * 2.5f)
        if (target != null && p.targetId != target.id) {
            p.targetId = target.id
            frame.sound.play(Sfx.WHISPER, 0.6f, pan(p.x / aspect))
        }
        if (target == null) p.targetId = -1
    }

    private fun callOut(frame: FrameState, p: Puppet) {
        val pan = pan(p.x / aspect)
        when (p.kind) {
            Kind.WITCH, Kind.BROOM -> frame.sound.play(Sfx.CACKLE, 0.7f, pan)
            Kind.GHOST -> frame.sound.play(Sfx.MOAN, 0.7f, pan)
            Kind.ZOMBIE -> frame.sound.play(Sfx.GROAN, 0.6f, pan)
            Kind.HAND -> frame.sound.play(Sfx.WHISPER, 0.6f, pan)
        }
    }

    override fun draw(frame: FrameState) {
        val t = tint.value
        floorProgram.use()
        floorProgram.setCommon(frame)
        floorProgram.set2f("uCandle", frame.aspect / 2f + 0.03f * sin(frame.time * 0.7f), 0.22f)
        floorProgram.set1f("uBrightness", light.value)
        floorProgram.set1f("uFlicker", flicker)
        floorProgram.set3f("uTint", 1f - 0.45f * t, 0.62f + 0.12f * t, 0.3f + 0.65f * t)
        gl.drawFullscreen()

        val s = size.value
        val sway = (flicker - 0.5f) * 0.05f // shadows tremble with the flame
        batch.begin()
        for (p in puppets) {
            if (p.alpha <= 0f) continue
            if (p.kind == Kind.HAND) {
                // Anchored at the edge: the quad's bottom sits on the anchor, pointing inward.
                val len = s * stretch.value * (0.6f + 1.1f * p.extend)
                val ux = cos(p.lean)
                val uy = sin(p.lean)
                batch.add(
                    p.x + ux * len, p.y + uy * len, p.lean - HALF_PI + sway, len,
                    p.phase, p.reach, 1f, p.alpha, Kind.HAND.shader, p.seed, len / (s * 1.1f), 0.03f,
                )
            } else {
                // Feet on the floor; the figure (and its shadow) extends away from the candle.
                val half = s * stretch.value
                batch.add(
                    p.x, p.y + half, sway, half,
                    p.phase, p.reach, if (p.facingRight) 1f else 0f, p.alpha, p.kind.shader, p.seed, stretch.value, 0.035f,
                )
            }
        }
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        puppetProgram.use()
        puppetProgram.set1f("uAspect", frame.aspect)
        puppetProgram.set2f("uOffset", 0f, 0f)
        batch.draw()
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun wrap(x: Float): Float {
        var v = x
        while (v > PI) v -= 2f * PI
        while (v < -PI) v += 2f * PI
        return v
    }

    private companion object {
        const val MAX_PUPPETS = 14
        const val PI = 3.1415927f
        const val HALF_PI = 1.5707964f
    }
}
