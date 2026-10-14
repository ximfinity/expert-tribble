package com.phantomfloor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
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
import com.phantomfloor.databinding.ActivityMainBinding
import com.phantomfloor.engine.EngineSettings
import com.phantomfloor.engine.Keystone
import com.phantomfloor.engine.PhantomRenderer
import com.phantomfloor.engine.TouchPeople
import com.phantomfloor.scenes.Scene
import com.phantomfloor.scenes.SceneParam
import com.phantomfloor.ui.ControlPanel
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

    private val settings = EngineSettings()
    private val keystone = Keystone()
    private val tracker = PersonTracker()
    private val touches = TouchPeople()
    private val mapping = FloorMapping()
    private lateinit var calibrator: FloorCalibrator

    private var trackingEnabled = false
    private var trackingRunning = false

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

        renderer = PhantomRenderer(applicationContext, keystone, settings, tracker, touches, audio, calibrator)
        prefs.loadParams(renderer.scenes)
        val sceneIndex = renderer.scenes.indexOfFirst { it.id == prefs.sceneId }.coerceAtLeast(0)
        renderer.requestScene(sceneIndex)

        binding.glView.start(renderer, keystone, settings, touches)
        binding.glView.onKeystoneEdited = { prefs.saveKeystone(keystone.corners()) }

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

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        binding.glView.onResume()
        if (settings.audioReactive) ensureAudioThenStart()
    }

    override fun onPause() {
        binding.glView.onPause()
        audio.stop()
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

        audioSwitch.isChecked = settings.audioReactive
        audioSwitch.setOnCheckedChangeListener { _, checked ->
            settings.audioReactive = checked
            prefs.saveSettings(settings)
            if (checked) ensureAudioThenStart() else audio.stop()
        }

        closeButton.setOnClickListener { panel.hide() }
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
        prefs.saveSettings(settings)
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

    private fun updateStatus() {
        val a = audio.levels
        val cam = when {
            !trackingRunning -> "off"
            mapping.isCalibrated -> "calibrated"
            else -> "uncalibrated"
        }
        val err = renderer.lastError?.let { "\n⚠ $it" } ?: ""
        binding.statusText.text = String.format(
            Locale.US,
            "%.0f fps · %d tracked · cam %s %dms\nmic %s lvl %.2f bass %.2f%s",
            renderer.fps, renderer.trackedCount, cam, vision.lastInferenceMs,
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
