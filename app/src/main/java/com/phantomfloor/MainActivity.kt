package com.phantomfloor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.phantomfloor.audio.AudioAnalyzer
import com.phantomfloor.audio.SoundEngine
import com.phantomfloor.databinding.ActivityMainBinding
import com.phantomfloor.engine.EngineSettings
import com.phantomfloor.engine.Keystone
import com.phantomfloor.engine.PhantomRenderer
import com.phantomfloor.engine.TouchPeople
import com.phantomfloor.scenes.Scene
import com.phantomfloor.scenes.SceneParam
import com.phantomfloor.ui.CastController
import com.phantomfloor.ui.ControlPanel
import com.phantomfloor.ui.ProjectorPresentation
import com.phantomfloor.ui.TapDetector
import com.phantomfloor.util.Prefs
import com.phantomfloor.util.UpdateChecker
import com.phantomfloor.vision.CameraController
import com.phantomfloor.vision.FloorCalibrator
import com.phantomfloor.vision.FloorMapping
import com.phantomfloor.vision.FloorVisionPipeline
import com.phantomfloor.vision.PersonTracker
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wires the engine together: GL renderer, camera/ML Kit pipeline, microphone analyzer, and the
 * hidden control overlay (tap the floor to toggle). Runs full-screen, immersive, screen always on.
 */
class MainActivity : AppCompatActivity(), ControlPanel.Callbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var renderer: PhantomRenderer
    private lateinit var panel: ControlPanel
    private lateinit var tapDetector: TapDetector
    private lateinit var camera: CameraController
    private lateinit var vision: FloorVisionPipeline
    private lateinit var audio: AudioAnalyzer
    private val sound = SoundEngine()

    private val settings = EngineSettings()
    private val keystone = Keystone()
    private val tracker = PersonTracker()
    private val touches = TouchPeople()
    private val mapping = FloorMapping()
    private lateinit var calibrator: FloorCalibrator

    private var trackingEnabled = false
    private var trackingRunning = false

    private lateinit var cast: CastController
    private var presentation: ProjectorPresentation? = null
    /** False after the user stops projecting, so the same display isn't grabbed again. */
    private var autoProject = true

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startTracking() else {
            setTrackingEnabled(false)
            toast(getString(R.string.permission_camera_denied))
        }
    }

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) audio.start(lifecycleScope) else {
            settings.audioReactive = false
            binding.audioSwitch.isChecked = false
            prefs.saveSettings(settings)
            toast(getString(R.string.permission_audio_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureWindow()

        prefs = Prefs(this)
        prefs.loadSettings(settings)
        prefs.loadKeystone()?.let(keystone::setAll)
        mapping.set(prefs.loadCalibration())

        calibrator = FloorCalibrator(mapping) { ok, message ->
            runOnUiThread { onCalibrationFinished(ok, message) }
        }
        audio = AudioAnalyzer(applicationContext)
        camera = CameraController(this, this)
        vision = FloorVisionPipeline(tracker, mapping, calibrator)
        vision.mode = if (prefs.trackingBodies) FloorVisionPipeline.Mode.BODIES else FloorVisionPipeline.Mode.FEET

        sound.onLoudSound = { audio.suppressBeats() }
        applySoundSettings()
        renderer = PhantomRenderer(applicationContext, keystone, settings, tracker, touches, audio, calibrator, sound)
        prefs.loadParams(renderer.scenes)
        val sceneIndex = renderer.scenes.indexOfFirst { it.id == prefs.sceneId }.coerceAtLeast(0)
        renderer.requestScene(sceneIndex)

        binding.glView.start(renderer, keystone, settings, touches)
        binding.glView.onKeystoneEdited = { prefs.saveKeystone(keystone.corners()) }

        cast = CastController(this) { display -> onProjectorDisplayChanged(display) }
        binding.castPad.setOnTouchListener { v, ev ->
            presentation?.forwardTouch(ev, v.width, v.height)
            true
        }

        panel = ControlPanel(binding, renderer.scenes, settings, this)
        panel.build(sceneIndex)
        bindControls()

        tapDetector = TapDetector(this) { onFloorTap() }

        trackingEnabled = prefs.trackingEnabled
        if (trackingEnabled) ensureCameraThenStart()

        if (prefs.firstRun) {
            prefs.firstRun = false
            showHint(getString(R.string.first_run_hint), autoHideMs = 6000)
        }

        lifecycleScope.launch {
            val update = UpdateChecker(BuildConfig.UPDATE_REPO, BuildConfig.VERSION_NAME).check() ?: return@launch
            binding.updateButton.text = getString(R.string.update_available, update.version)
            binding.updateButton.visibility = View.VISIBLE
            binding.updateButton.setOnClickListener { openUrl(update.url) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (panel.isVisible) updateStatus()
                    delay(500)
                }
            }
        }
    }

    // --- Window: immersive, black, always on ----------------------------------------------------

    private fun configureWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        cast.start()
    }

    override fun onStop() {
        cast.stop()
        stopProjecting()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        presentation?.onResume() ?: binding.glView.onResume()
        if (settings.audioReactive) ensureAudioThenStart()
        sound.start()
    }

    override fun onPause() {
        presentation?.onPause() ?: binding.glView.onPause()
        audio.stop()
        sound.stop()
        if (calibrator.isActive) calibrator.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        vision.close()
        camera.shutdown()
        super.onDestroy()
    }

    // --- Gestures -------------------------------------------------------------------------------

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Only taps on the projection count; taps on the panel or Done button are theirs.
        if (ev.actionMasked != MotionEvent.ACTION_DOWN || !isOverControls(ev)) tapDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun isOverControls(ev: MotionEvent): Boolean {
        val rect = Rect()
        return listOf(binding.controlPanel, binding.doneButton).any {
            it.visibility == View.VISIBLE && it.getGlobalVisibleRect(rect) && rect.contains(ev.rawX.toInt(), ev.rawY.toInt())
        }
    }

    private fun onFloorTap() {
        when {
            settings.keystoneEdit -> Unit // corners are dragged here; the Done button finishes
            calibrator.isActive -> {
                calibrator.cancel()
                hideHint()
            }
            panel.isVisible -> panel.hide()
            else -> panel.show()
        }
    }

    // --- Controls -------------------------------------------------------------------------------

    private fun bindControls() = with(binding) {
        keystoneButton.setOnClickListener { enterKeystoneEdit() }
        resetKeystoneButton.setOnClickListener {
            keystone.reset()
            prefs.saveKeystone(keystone.corners())
        }

        trackingSwitch.isChecked = trackingEnabled
        trackingSwitch.setOnCheckedChangeListener { _, checked -> setTrackingEnabled(checked) }
        (if (prefs.trackingBodies) modeBodiesChip else modeFeetChip).isChecked = true
        trackingModeGroup.setOnCheckedStateChangeListener { _, ids ->
            val bodies = ids.firstOrNull() == modeBodiesChip.id
            prefs.trackingBodies = bodies
            vision.mode = if (bodies) FloorVisionPipeline.Mode.BODIES else FloorVisionPipeline.Mode.FEET
            tracker.clear()
        }
        calibrateButton.setOnClickListener { startCalibration() }

        debugSwitch.isChecked = settings.showTracking
        debugSwitch.setOnCheckedChangeListener { _, checked ->
            settings.showTracking = checked; prefs.saveSettings(settings)
        }
        touchSwitch.isChecked = settings.touchAsPeople
        touchSwitch.setOnCheckedChangeListener { _, checked ->
            settings.touchAsPeople = checked; prefs.saveSettings(settings)
            if (!checked) touches.clear()
        }

        soundSwitch.isChecked = settings.soundEffects
        soundSwitch.setOnCheckedChangeListener { _, checked ->
            settings.soundEffects = checked
            applySoundSettings()
            prefs.saveSettings(settings)
        }

        audioSwitch.isChecked = settings.audioReactive
        audioSwitch.setOnCheckedChangeListener { _, checked ->
            settings.audioReactive = checked
            prefs.saveSettings(settings)
            if (checked) ensureAudioThenStart() else audio.stop()
        }

        closeButton.setOnClickListener { panel.hide() }
        castButton.setOnClickListener { onCastClicked() }
        supportButton.setOnClickListener { openUrl(BuildConfig.SUPPORT_URL) }
        versionText.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        doneButton.setOnClickListener { exitKeystoneEdit() }
    }

    override fun onSceneSelected(index: Int) {
        renderer.requestScene(index)
        prefs.sceneId = renderer.scenes[index].id
    }

    override fun onParamChanged(scene: Scene, param: SceneParam) {
        prefs.saveParam(scene, param.key, param.value)
    }

    override fun onSettingsChanged() {
        applySoundSettings()
        prefs.saveSettings(settings)
    }

    private fun applySoundSettings() {
        sound.enabled = settings.soundEffects
        sound.volume = settings.soundVolume
    }

    private fun enterKeystoneEdit() {
        panel.hide()
        touches.clear()
        settings.keystoneEdit = true
        binding.doneButton.visibility = View.VISIBLE
        showHint(getString(R.string.keystone_hint))
    }

    private fun exitKeystoneEdit() {
        settings.keystoneEdit = false
        binding.doneButton.visibility = View.GONE
        prefs.saveKeystone(keystone.corners())
        hideHint()
        panel.show()
    }

    // --- Camera tracking ------------------------------------------------------------------------

    private fun setTrackingEnabled(enabled: Boolean) {
        trackingEnabled = enabled
        prefs.trackingEnabled = enabled
        if (binding.trackingSwitch.isChecked != enabled) binding.trackingSwitch.isChecked = enabled
        if (enabled) ensureCameraThenStart() else stopTracking()
    }

    private fun ensureCameraThenStart() {
        if (granted(Manifest.permission.CAMERA)) startTracking() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startTracking() {
        if (trackingRunning) return
        trackingRunning = true
        vision.start(lifecycleScope)
        lifecycleScope.launch {
            try {
                camera.start(vision)
            } catch (t: Throwable) {
                trackingRunning = false
                vision.stop()
                toast("Camera unavailable: ${t.message}")
            }
        }
    }

    private fun stopTracking() {
        trackingRunning = false
        camera.stop()
        vision.stop()
        calibrator.cancel()
    }

    private fun startCalibration() {
        if (!trackingRunning) {
            setTrackingEnabled(true)
            if (!trackingRunning) return
        }
        panel.hide()
        showHint(getString(R.string.calibration_hint))
        calibrator.start()
    }

    private fun onCalibrationFinished(ok: Boolean, message: String) {
        hideHint()
        if (ok) {
            prefs.saveCalibration(mapping.get())
            toast(getString(R.string.calibration_done))
        } else {
            toast(getString(R.string.calibration_failed, message))
        }
        panel.show()
    }

    // --- Audio ----------------------------------------------------------------------------------

    private fun ensureAudioThenStart() {
        if (granted(Manifest.permission.RECORD_AUDIO)) audio.start(lifecycleScope)
        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // --- Helpers --------------------------------------------------------------------------------

    // --- Casting to a projector / TV ------------------------------------------------------------

    private fun onCastClicked() {
        val display = cast.display
        when {
            presentation != null -> {
                autoProject = false
                stopProjecting()
            }
            display != null -> {
                autoProject = true
                startProjecting(display)
            }
            else -> {
                autoProject = true
                toast(getString(R.string.cast_help))
                cast.openCastSettings()
            }
        }
    }

    private fun onProjectorDisplayChanged(display: Display?) {
        val current = presentation
        if (current != null && (display == null || current.display.displayId != display.displayId)) stopProjecting()
        if (display != null && presentation == null && autoProject) startProjecting(display)
        updateCastButton()
    }

    /** Moves rendering to the external display; the phone becomes controls + touchpad. */
    private fun startProjecting(display: Display) {
        if (presentation != null) return
        binding.glView.onPause()
        binding.glView.visibility = View.GONE
        val p = ProjectorPresentation(this, display, renderer, keystone, settings, touches)
        try {
            p.show()
        } catch (e: WindowManager.InvalidDisplayException) {
            binding.glView.visibility = View.VISIBLE
            binding.glView.onResume()
            return
        }
        presentation = p
        binding.castPad.text = getString(R.string.cast_pad_hint, display.name)
        binding.castPad.visibility = View.VISIBLE
        updateCastButton()
    }

    private fun stopProjecting() {
        val p = presentation ?: return
        presentation = null
        p.dismiss()
        binding.castPad.visibility = View.GONE
        binding.glView.visibility = View.VISIBLE
        binding.glView.onResume()
        updateCastButton()
    }

    private fun updateCastButton() {
        val display = cast.display
        binding.castButton.text = when {
            presentation != null -> getString(R.string.cast_stop, presentation?.display?.name ?: "")
            display != null -> getString(R.string.cast_start, display.name)
            else -> getString(R.string.cast)
        }
    }

    private fun updateStatus() {
        val a = audio.levels
        val cam = when {
            !trackingRunning -> "off"
            mapping.isCalibrated -> "calibrated"
            else -> "uncalibrated"
        }
        val err = renderer.lastError?.let { "\n⚠ $it" } ?: ""
        val out = presentation?.display?.name?.let { " · on $it" } ?: ""
        binding.statusText.text = String.format(
            Locale.US,
            "%.0f fps%s · %d tracked · cam %s %dms\nmic %s lvl %.2f bass %.2f%s",
            renderer.fps, out, renderer.trackedCount, cam, vision.lastInferenceMs,
            if (settings.audioReactive) "on" else "off", a.level, a.bass, err,
        )
    }

    private val hideHintRunnable = Runnable { hideHint() }

    private fun showHint(text: String, autoHideMs: Long = 0) {
        binding.hintText.removeCallbacks(hideHintRunnable)
        binding.hintText.text = text
        binding.hintText.visibility = View.VISIBLE
        if (autoHideMs > 0) binding.hintText.postDelayed(hideHintRunnable, autoHideMs)
    }

    private fun hideHint() {
        binding.hintText.removeCallbacks(hideHintRunnable)
        binding.hintText.visibility = View.GONE
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            toast(url)
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
