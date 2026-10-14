# PhantomFloor 👻

Real-time, procedurally generated interactive floor projections for Android, for Halloween and
every season after it. There is no video playback: every frame is computed on the GPU and reacts
to the people walking on it (camera + ML Kit) and to the sound in the room (microphone + FFT).

| Ectoplasmic Fog | Ground Smoke | The Swarm | Cursed Fluid (lava) |
|---|---|---|---|
| ![fog](docs/previews/fog.png) | ![smoke](docs/previews/smoke.png) | ![swarm](docs/previews/swarm.png) | ![lava](docs/previews/lava.png) |
| **Living Water** | **Christmas** | **Hanukkah** | **Fall Leaves** |
| ![water](docs/previews/water.png) | ![christmas](docs/previews/christmas.png) | ![hanukkah](docs/previews/hanukkah.png) | ![leaves](docs/previews/leaves.png) |
| **Spring Garden** | **Fireworks** | **Fireflies** | **Pool Splash** |
| ![garden](docs/previews/garden.png) | ![fireworks](docs/previews/fireworks.png) | ![fireflies](docs/previews/fireflies.png) | ![pool](docs/previews/pool.png) |

*Previews rendered from the real shaders in headless WebGL2 (`tools/shader-preview`), with the
simulations (smoke, snow cover, garden growth, ripples) driven by a scripted walker.*

## Requirements

- Android 8.0+ (minSdk 26) with OpenGL ES 3.0. Half-float render targets are used when available.
- A projector or TV-projector pointed at the floor, connected by wireless display (screen
  mirroring / Miracast / Smart View) or HDMI / USB-C. See **Casting** below.
- Optional: the device's camera pointed at the projected area (for tracking), and a microphone.

## Installation

### 1. Download

Get the latest `PhantomFloor-x.y.z.apk` from either:

- **itch.io:** https://ximfinity.itch.io/phantom-floor-interactive-projector-floor-app (free, donations welcome), or
- **GitHub Releases:** https://github.com/ximfinity/expert-tribble/releases/latest

Download it **on the Android phone or tablet** you'll run it on (Android 8.0 or newer).

### 2. Install on the phone

1. Open the downloaded APK, from the browser's download bar or the **Files** app.
2. If Android says installs from this source aren't allowed, tap **Settings**, turn on **Allow from
   this source** for your browser or file manager, then go back.
3. Tap **Install**.
4. If **Play Protect** warns about an unknown app, tap **More details → Install anyway**. It's
   flagged only because the app doesn't come from the Play Store.
5. Open **PhantomFloor**, and allow **camera** (for tracking feet) and **microphone** (for
   reacting to sound) when asked. Both are optional, and nothing leaves the phone.

**Installing from a computer instead:** with USB debugging on, install Google's standalone
[Platform Tools](https://developer.android.com/tools/releases/platform-tools) and run
`adb install -r PhantomFloor-x.y.z.apk`.

### 3. First run

1. Point the projector at the floor and connect it (see **Casting** below), or plug in HDMI.
2. **Tap** anywhere to open the controls, then press **Cast to projector / TV**.
3. Press **Edit keystone**, drag the 4 corners to fit the floor, then press **Done**.
4. Optional: **Calibrate camera to floor** so tracking lines up with the projection.
5. Choose a season and a scene. Fingers on the phone act as feet if you don't use the camera.

### Updating

Download the new APK and install it over the old one. Your settings and keystone are kept. The app
also shows **Update available** in the control panel when a newer GitHub Release exists (this
works only while the GitHub repository is public).

### Troubleshooting

| Problem | Fix |
|---|---|
| "App not installed" / "conflicts with an existing package" | The copy on the phone was signed with a different key, for example a test build from GitHub Actions. Uninstall PhantomFloor, then install again. |
| "There was a problem parsing the package" | The download is incomplete or the phone is older than Android 8.0. Download it again. |
| You tapped a `.zip` and nothing happened | Test builds from Actions come zipped. Unzip first, then open the `.apk` inside. |
| Black screen on the projector | Make sure you're casting or mirroring, then tap to open the controls and pick a scene. |

### Test builds (for development)

Every pull request and push to `main` builds a test APK. Open **Actions → Build APK**, choose a
run, and download **PhantomFloor-debug-apk**. Test builds are signed with a different key than
releases, so uninstall one before installing the other.

## Build

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # homography, FFT and version-compare unit tests
```

`ndk.abiFilters` keeps only ARM ABIs, because ML Kit's native libraries are large. Add `x86_64` to run on an emulator.

## Setting up a floor

1. Mount the projector, then open the panel and press **Cast to projector / TV** (or plug in HDMI).
2. **Tap** anywhere on the projection to open the control panel; tap again to close it.
3. **Edit keystone**: drag the 4 corner handles until the glowing grid fits the floor. You can drag several corners at once. Press **Done** when finished. The corners are saved.
4. **Calibrate camera to floor**: keep the floor clear and hold the camera still. The projector shows a black frame, then four white dots one at a time. The camera finds each dot and fits a camera→floor homography. This also absorbs the keystone and any camera tilt. The result is saved.
5. Choose a scene and tune its sliders. Turn on **Show tracked points** to see green rings under detected feet.

With **Touch acts as a person** turned on, fingers on the screen act as feet. This lets you test every scene without the camera.

## Casting

**Cast to projector / TV** opens Android's screen-cast picker. When the phone connects to a wireless
display or an HDMI adapter, PhantomFloor renders full-screen on that display (Android's
`Presentation` API), and the phone becomes the control panel plus a touchpad: touches on it act as
feet, and while editing keystone they drag the projector's corners. Press the button again to stop.

Chromecast / Google Cast receivers can't run a custom GPU renderer, so they aren't supported.
Use a projector or TV with Miracast / screen mirroring, an Android TV device that accepts screen
casting, or an HDMI / USB-C adapter.

## Seasons and scenes

| Season | Scenes |
|---|---|
| Halloween | Ectoplasmic Fog, Ground Smoke, The Swarm, Cursed Fluid, Living Water |
| Winter | Snowfall, Christmas, Hanukkah |
| Fall | Fall Leaves |
| Spring | Spring Garden |
| 4th of July | Fireworks |
| Summer | Fireflies, Pool Splash |

## Architecture

```
MainActivity ── immersive, FLAG_KEEP_SCREEN_ON, permissions, tap to toggle, ControlPanel (ViewBinding)
 │
 ├─ PhantomSurfaceView (GLES 3.0)  ── keystone drag / touch-as-person input
 │   └─ PhantomRenderer (GL thread, vsync)
 │        1. gather inputs  → FrameState (people[8], audio bands, beat)
 │        2. scene.update() → CPU sims (boids, monster director) + GPU sims (ripple ping-pong)
 │        3. scene.draw()   → per-scene offscreen canvas (scaled resolution)
 │        4. warp pass      → canvas through keystone homography; dissolve transition,
 │                            edge feather, brightness; everything else pure #000000
 │
 ├─ CameraController (CameraX ImageAnalysis, 640×480, KEEP_ONLY_LATEST)
 │   └─ FloorVisionPipeline (Channel → coroutine on Dispatchers.Default → ML Kit Task.await())
 │        ├─ FEET mode:   Pose detection, heel+toe midpoint per foot (1 person, 2 points)
 │        ├─ BODIES mode: Object detection, bottom-centre of each box (up to 5 people)
 │        ├─ FloorMapping homography: camera → canvas UV
 │        └─ PersonTracker (ids, smoothing, velocity) → AtomicReference snapshot for GL
 │
 └─ AudioAnalyzer (AudioRecord on Dispatchers.IO, 1024-pt FFT, 50% overlap)
      bass / mid / treble relative to room ambience + spectral-flux onset ("beat") detection
```

No thread ever blocks another. The GL thread reads `@Volatile` and atomic snapshots, the UI
writes `@Volatile` parameters, and vision and audio run in coroutines.

### Keystone correction

`Homography.squareToQuad` (Heckbert's closed form) maps the unit canvas square to the 4 dragged
corners. `warp.vert` writes the projective `w` into `gl_Position.w`:

```glsl
vec3 p = uHomography * vec3(uv, 1.0);
gl_Position = vec4(p.xy, 0.0, p.z);
```

The GPU then interpolates the canvas UV perspective-correctly, so a single quad warps exactly,
with no tessellation and no affine seams along the diagonal. Non-convex corner positions are
rejected while dragging. Touches are mapped back through the inverse homography.

### Scenes (`app/src/main/assets/shaders`, `scenes/`)

| Scene | Technique | Reacts to people | Reacts to sound |
|---|---|---|---|
| **Ectoplasmic Fog** | 2-level domain-warped simplex fBm, light-offset sample for fake volumetric self-shadowing, ridged-noise wisps, purple/green region mixing | Fog is pushed out, swirls around moving legs, follows their wake, and leaves a clear pocket with a glowing rim | Bass thickens the fog, loud sounds churn it faster, beats flash lightning |
| **The Swarm** | CPU Boids (separation, alignment, cohesion, wander, stop-and-go, soft walls) on a uniform spatial grid; one instanced draw of SDF spiders with an animated tetrapod gait; a hand-held flashlight beam that sweeps the floor on a damped spring with hand tremor | Spiders flee from feet (fast feet scare more) and scurry out of the flashlight beam. Panic spreads to neighbours as a scatter wave. Eyes glow hotter when panicked | Loud onsets startle the whole swarm. Beats flash the lantern light |
| **Cursed Fluid** | Flow-displaced animated Voronoi, bubbles that swell and pop, GPU wave-equation ripples; finite-difference normals with wet specular (blood) or glowing cracks (lava) | Pool tendrils creep toward each person; footsteps splash ripples | Bass boosts bubbling and lava heat; beats drop ripples |
| **Living Water** | Ripple simulation + a half-res monster height-field pass (tentacles, skeletal hand, screaming face, built from tube/dome primitives) + refracted Voronoi caustics | Monsters surface near people, turn toward them, and sink with a splash when stepped on; eyes in the water close as people approach | The face's mouth opens with loudness; loud beats summon a monster |
| **Ground Smoke** | GPU stable fluids: splats, vorticity confinement, Jacobi pressure projection, semi-Lagrangian advection; flow-streaked noise detail | Feet drag the smoke along, shove it aside and leave curling wakes | Bass makes the fog machines puff harder |
| **Snowfall / Christmas / Hanukkah** | Snow-depth field (footprints press it down, fresh snow refills them), instanced six-fold snowflakes with shadows, string lights, menorah | Footprints, flakes blown aside by passers-by | Beats brighten the scene |
| **Fall Leaves** | CPU leaf physics with height, flutter, spin and gusts; instanced SDF maple/oak leaves with veins and lifting shadows | Leaves kicked up and swept along by feet | — |
| **Spring Garden** | Planted/grown field (growth chases planting at a limited rate); procedural flowers on a jittered grid go sprout → leaves → bloom | Grass and flowers grow wherever people walk, then slowly wilt | Beats and loudness brighten the bloom |
| **Fireworks** | Spark particles with drag and cooling colour, crossettes, glitter; fading trail buffer; flashes and smoke | A burst under every stride | Loud onsets set off bursts |
| **Fireflies** | Wander/orbit steering; Kuramoto phase coupling for synchronised blinking; additive glow | Fireflies gather around and orbit people | — |
| **Pool Splash** | Ripple simulation, refracted tile floor and lane, caustics, sun glints, foam, ballistic droplets that ripple where they land | Wading ripples; arriving or big steps splash | Loud onsets splash |

Scene switches use an ectoplasmic noise dissolve. Both scenes keep animating during the transition.

### Performance knobs

- Each scene renders at its own `resolutionScale` (fog 0.5, fluid/water 0.75, swarm 1.0), multiplied by the **Render quality** slider. The warp pass upscales bilinearly.
- Scenes compile their shaders and allocate their canvas on first use, and only the live scene(s) keep a full-size canvas, so adding scenes doesn't slow startup or eat memory.
- Ripple simulations run at 256 rows in RGBA16F. On GPUs without half-float render targets they fall back to biased RGBA8.
- The Boids grid lookup is O(N·k). 2000 spiders cost about 1–2 ms of CPU.

## Adding a scene

1. Write `myscene.frag`. `#include "noise.glsl"` and `#include "common.glsl"` give you noise, hashes, Voronoi, SDF helpers, `uPeople[]` and the audio uniforms.
2. Subclass `Scene`, declare `SceneParam`s (sliders appear automatically), and draw with `gl.drawFullscreen()`.
3. Add it to `SceneCatalog` (pass a `Season` to group it in the panel).

`tools/shader-preview/check.js` and `check-seasons.js` compile every program in WebGL2 and render
previews. This catches GLSL errors without a device.

## Known limitations

- ML Kit pose detection tracks one person (two feet). Use **Bodies** mode for groups.
- Tracking needs some light on people. The projection itself lights them, but a pitch-black room limits it. An IR camera with an IR illuminator is the pro upgrade.
- Calibration assumes the camera stays still afterwards. Re-run it if the camera moves.
- Chromecast isn't supported (see **Casting**).
- Ground Smoke needs half-float render targets (nearly all GPUs since ~2016); otherwise it falls back to procedural smoke.
- The app uses the internet only to check GitHub for a newer release. Camera and microphone data never leave the device.
