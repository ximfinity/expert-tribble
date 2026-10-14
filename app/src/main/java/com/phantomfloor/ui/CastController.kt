package com.phantomfloor.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display

/**
 * Watches for a second display the app can present on: a wireless display the user cast to
 * (Miracast / Smart View / a TV's screen mirroring), or an HDMI / USB-C adapter.
 *
 * Note: Google Cast (Chromecast) receivers can't run this renderer, so "Cast" opens the
 * system's wireless display / screen-cast settings instead.
 */
class CastController(private val context: Context, private val onChanged: (Display?) -> Unit) :
    DisplayManager.DisplayListener {

    private val displays = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    val display: Display?
        get() = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()

    fun start() {
        displays.registerDisplayListener(this, Handler(Looper.getMainLooper()))
        onChanged(display)
    }

    fun stop() = displays.unregisterDisplayListener(this)

    /** Opens the system screen-cast picker; falls back to display settings on devices without one. */
    fun openCastSettings() {
        val intents = listOf(
            Intent("android.settings.CAST_SETTINGS"),
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
        )
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    override fun onDisplayAdded(displayId: Int) = onChanged(display)
    override fun onDisplayRemoved(displayId: Int) = onChanged(display)
    override fun onDisplayChanged(displayId: Int) = Unit
}
