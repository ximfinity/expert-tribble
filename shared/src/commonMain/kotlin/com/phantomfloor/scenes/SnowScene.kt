package com.phantomfloor.scenes

import com.phantomfloor.gl.GLES30
import com.phantomfloor.audio.Ambience
import com.phantomfloor.audio.Sfx
import com.phantomfloor.engine.FrameState
import com.phantomfloor.gl.GlProgram
import com.phantomfloor.gl.GlResources
import com.phantomfloor.gl.SpriteBatch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * WINTER: snow keeps falling and settling; walking leaves footprints that slowly fill back in.
 * The same scene, themed, gives Snowfall, Christmas (string lights) and Hanukkah (blue and
 * white lights plus a menorah lit for the chosen night).
 */
class SnowScene(private val theme: Theme) : Scene(theme.id, theme.title, Season.WINTER) {

    enum class Theme(val id: String, val title: String) {
        SNOW("snow", "Snowfall"),
        CHRISTMAS("christmas", "Christmas"),
        HANUKKAH("hanukkah", "Hanukkah"),
    }

    private val snowfall = SceneParam("snowfall", "Snowfall", 0f, 2f, 1f)
    private val flakeSize = SceneParam("size", "Flake size", 0.5f, 2.5f, 1f)
    private val wind = SceneParam("wind", "Wind", 0f, 1f, 0.3f)
    private val refill = SceneParam("refill", "Footprints fill in", 0f, 1f, 0.35f)
    private val lights = SceneParam("lights", "Light brightness", 0f, 1.5f, 1f)
    private val night = SceneParam("night", "Night of Hanukkah", 1f, 8f, 1f, step = 1f)

    override val params = when (theme) {
        Theme.SNOW -> listOf(snowfall, flakeSize, wind, refill)
        Theme.CHRISTMAS -> listOf(snowfall, flakeSize, wind, refill, lights)
        Theme.HANUKKAH -> listOf(night, snowfall, flakeSize, wind, refill, lights)
    }

    override val ambience = when (theme) {
        Theme.SNOW -> Ambience(wind = 0.55f)
        Theme.CHRISTMAS -> Ambience(wind = 0.4f, chimes = 0.25f)
        Theme.HANUKKAH -> Ambience(wind = 0.4f, chimes = 0.15f, drone = 0.15f, droneHz = 110f)
    }

    private lateinit var ground: GlProgram
    private lateinit var flakeProgram: GlProgram
    private val cover = StampField("snow_cover.frag")
    private val sprites = SpriteBatch(MAX_FLAKES)
    private var pendingFill = 0f
    private val footsteps = FootstepTracker()
    private val prints = ArrayList<FootstepTracker.Footprint>()

    private val rng = Random(theme.ordinal * 31 + 5)
    private val fx = FloatArray(MAX_FLAKES)
    private val fy = FloatArray(MAX_FLAKES)
    private val fz = FloatArray(MAX_FLAKES)
    private val fvx = FloatArray(MAX_FLAKES)
    private val fvy = FloatArray(MAX_FLAKES)
    private val fsize = FloatArray(MAX_FLAKES)
    private val fseed = FloatArray(MAX_FLAKES)
    private var initialized = false
    private var windAngle = 0.4f

    override fun onSurfaceCreated(gl: GlResources) {
        super.onSurfaceCreated(gl)
        ground = gl.shaders.program("fullscreen.vert", "snow_ground.frag")
        flakeProgram = gl.shaders.program("sprite.vert", "snowflake.frag")
        cover.onSurfaceCreated(gl)
        sprites.onSurfaceCreated()
    }

    override fun onResize(width: Int, height: Int) {
        super.onResize(width, height)
        cover.onResize(width, height, clear = floatArrayOf(0.85f, 0f, 0f, 1f))
    }

    private fun respawn(i: Int, aspect: Float, anyHeight: Boolean) {
        fx[i] = rng.nextFloat() * aspect
        fy[i] = rng.nextFloat()
        fz[i] = if (anyHeight) rng.nextFloat() else 1f
        fvx[i] = 0f
        fvy[i] = 0f
        fsize[i] = 0.6f + rng.nextFloat() * 0.8f
        fseed[i] = rng.nextFloat()
    }

    override fun update(frame: FrameState) {
        val dt = frame.dt
        val aspect = frame.aspect
        if (!initialized) {
            for (i in 0 until MAX_FLAKES) respawn(i, aspect, anyHeight = true)
            initialized = true
        }

        // Wind slowly veers; gusts come and go.
        windAngle += (sin(frame.time * 0.13f) * 0.3f) * dt
        val gust = 0.6f + 0.4f * sin(frame.time * 0.37f) * sin(frame.time * 0.11f + 1f)
        val w = wind.value * 0.12f * gust
        val wx = cos(windAngle) * w
        val wy = sin(windAngle) * w

        val n = (MAX_FLAKES * (snowfall.value / 2f)).toInt()
        for (i in 0 until n) {
            // Flakes flutter as they fall, drift with the wind, and get blown aside by passers-by.
            val sway = sin(frame.time * (1.5f + fseed[i]) + fseed[i] * 40f) * 0.02f
            var ax = 0f
            var ay = 0f
            for (p in frame.people) {
                val dx = fx[i] - p.x * aspect
                val dy = fy[i] - p.y
                val d = hypot(dx, dy)
                if (d < 0.15f && d > 1e-4f) {
                    val k = (1f - d / 0.15f) * (0.3f + hypot(p.vx * aspect, p.vy) * 2f)
                    ax += dx / d * k
                    ay += dy / d * k
                }
            }
            fvx[i] += (ax - fvx[i] * 2f) * dt
            fvy[i] += (ay - fvy[i] * 2f) * dt
            fx[i] += (wx * (0.5f + fz[i]) + sway + fvx[i]) * dt
            fy[i] += (wy * (0.5f + fz[i]) + fvx[i] * 0.3f + fvy[i]) * dt
            fz[i] -= dt * (0.08f + 0.06f * fseed[i])
            if (fz[i] <= 0f || fx[i] < -0.1f || fx[i] > aspect + 0.1f || fy[i] < -0.1f || fy[i] > 1.1f) {
                respawn(i, aspect, anyHeight = false)
            }
        }

        // Snow cover: batch tiny refills so 8-bit fields still accumulate.
        pendingFill += dt * refill.value * 0.05f * (0.3f + snowfall.value)
        val fill = if (pendingFill >= 0.01f || !cover.lowPrecision) pendingFill.also { pendingFill = 0f } else 0f
        // Each stride presses one shoe-shaped print, alternating feet along the walking direction.
        prints.clear()
        footsteps.update(frame.people, aspect, frame.time, prints)
        for (f in prints) {
            cover.stamp(f.x, f.y, PRINT_LENGTH, 0.85f, f.dirX, f.dirY, f.side)
            frame.sound.play(Sfx.CRUNCH, 0.5f, f.x * 2f - 1f)
        }
        cover.step {
            it.set1f("uFill", fill)
            it.set1f("uSoften", if (fill > 0f) 0.15f else 0f)
        }
    }

    override fun draw(frame: FrameState) {
        ground.use()
        ground.setCommon(frame)
        ground.texture("uCover", 0, cover.texture)
        ground.set2f("uCoverTexel", 1f / (256f * frame.aspect), 1f / 256f)
        ground.set1f("uTheme", theme.ordinal.toFloat())
        ground.set1f("uLights", lights.value)
        ground.set1f("uNight", night.value)
        gl.drawFullscreen()

        val n = (MAX_FLAKES * (snowfall.value / 2f)).toInt()
        sprites.begin()
        for (i in 0 until n) {
            val z = fz[i]
            val fade = (z / 0.06f).coerceIn(0f, 1f) * ((1f - z) / 0.08f).coerceIn(0f, 1f)
            val size = 0.006f * flakeSize.value * fsize[i] * (0.6f + 1.6f * z)
            val blue = theme == Theme.HANUKKAH && fseed[i] > 0.5f
            sprites.add(
                fx[i], fy[i], fseed[i] * 6.28f + frame.time * 0.3f * (fseed[i] - 0.5f), size,
                if (blue) 0.7f else 1f, if (blue) 0.85f else 1f, 1f, fade * 0.95f,
                p0 = ((fsize[i] - 0.9f) * 2f).coerceIn(0f, 1f) * (1f - z * 0.5f),
                p1 = z, p2 = 0f, p3 = z,
            )
        }

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        flakeProgram.use()
        flakeProgram.set1f("uAspect", frame.aspect)
        // Shadow pass, offset in units of flake size (higher flakes are drawn bigger, so their
        // shadows also land further away).
        flakeProgram.set2f("uOffset", 1.2f, -1.6f)
        drawFlakes(shadow = true)
        flakeProgram.set2f("uOffset", 0f, 0f)
        drawFlakes(shadow = false)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawFlakes(shadow: Boolean) {
        flakeProgram.set1f("uShadow", if (shadow) 1f else 0f)
        sprites.draw()
    }

    companion object {
        private const val MAX_FLAKES = 900
        private const val PRINT_LENGTH = 0.075f
    }
}
