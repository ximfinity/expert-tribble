# PhantomFloor 👻

Real-time, procedurally generated interactive floor projections for Android, for Halloween and
every season after it. There is no video playback: every frame is computed on the GPU and reacts
to the people walking on it (camera + ML Kit) and to the sound in the room (microphone + FFT).
Every scene also has its own procedurally synthesised sound effects, and a **Games** section turns
the floor into a stomp-to-play arcade.

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

**About & help** (near the bottom of the panel) has these steps in short form, whether you're up to
date, links to the itch.io page and GitHub, a **Report a problem** button that opens a GitHub issue
pre-filled with diagnostics (long-press the header to copy them), the privacy note and credits.

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
| Scary | Jump Scare, Eyes in the Dark, Grasping Hands |
| Winter | Snowfall, Christmas, Hanukkah |
| Fall | Fall Leaves |
| Spring | Spring Garden |
| 4th of July | Fireworks |
| Summer | Fireflies, Pool Splash |
| Games | Stomp the Spider, Whack-a-Mole, Defuse the Bomb, Tic-Tac-Toe, Simon Says, Balloon Pop, Floor is Lava, Coloring |

## Floor games

Every game starts the same way: **stand on the glowing START circle** until its ring fills (about
0.7 s, so people just walking across don't start a game), a 3-2-1 countdown plays over the fresh
board, and **GO!** The score and best score show on the floor, hits pop up as "+1", and the round
ends with a results screen before the START circle comes back. A camera that briefly loses
someone standing on a pad doesn't count it as a new step, and in Simon Says and Defuse the Bomb a
friend following you onto the pad you just stepped on isn't a mistake. Touch-as-a-person works here too, so you can try every game on the
phone screen.

| Game | How to play |
|---|---|
| **Stomp the Spider** | Spiders scuttle in from the edges. Stomp as many as you can in 60 seconds. |
| **Whack-a-Mole** | Moles pop out of a 3×3 grid of holes. Step on one before it ducks back down. |
| **Defuse the Bomb** | The bomb flashes a code on four coloured wires. Step on them in the same order before the timer runs out. Each level adds a step and takes away time. |
| **Tic-Tac-Toe** | Stand on a square for a moment to claim it. Two players take turns, or play against the computer (difficulty slider). |
| **Simon Says** | Four coloured pads light up in a growing sequence. Repeat it with your feet. |
| **Balloon Pop** | Balloons float across the floor. Pop them by stepping on them; gold ones are worth 5. |
| **Floor is Lava** | Tiles cover the whole floor; they flash, then turn to lava, faster as the round goes on. Everyone has 3 lives; standing on lava costs one. Survive as long as you can (90 s round). |
| **Coloring** | Free play: step on a colour swatch, then walk to paint. Pick an outline (pumpkin, star, heart, house, fish) and hold **CLEAR** to start over. |

## Scary

Scenes for haunted houses and Halloween parties. **The floor stays completely black and silent
until someone walks on**, so guests don't know anything is there.

| ![jump scare](docs/previews/jumpscare.gif) | ![eyes](docs/previews/scare_eyes_maw.png) | ![hands](docs/previews/scare_hands.png) |
|---|---|---|

| Scene | What happens |
|---|---|
| **Jump Scare** | A heartbeat thuds, then a screaming ghoul or skull lunges up out of the floor right in front of the person, with a flash and a stinger. It holds for a second and burns away. Each person gets one scare until the floor has been empty for a few seconds, so a crowd doesn't get a strobe of screams. Settings: the wait before the scare, rest between scares, monster (ghoul, skull, random), size, flash brightness, scream volume, heartbeat. |
| **Eyes in the Dark** | Glowing eyes blink open in the darkness around anyone on the floor and watch them, whispering. Moving fast makes them snap shut and hide. Stand still and more gather and creep closer, until a ring of teeth opens under your feet and snaps shut. |
| **Grasping Hands** | Where someone stops, the floor rumbles and splits open with glowing cracks, and rotting hands claw up out of them toward their feet. Walk away and they sink back into the dark. |

The scares are loud and bright on purpose. Turn down **Scream volume** / **Scare volume** and
**Flash brightness** for younger guests, and warn anyone sensitive to flashing light.

## Sound effects

Every scene has its own sound, synthesised live from noise, oscillators and filters, with no audio
files. Fog whooshes around feet, spiders skitter, snow crunches with each step, monsters growl as
they rise out of the water and splash as they sink, fireworks boom and crackle, and fireflies come
with crickets. Each game has its own beeps, pops, buzzers and fanfares. Ambience crossfades when the
scene changes.

Turn **Sound effects** and **Effects volume** up or down in the panel. While audio reactivity is on,
the microphone uses echo cancellation and ignores its own loud effects, so a boom doesn't trigger
another boom.

## Network / IR camera (Wyze, RTSP)

Instead of the phone's camera, PhantomFloor can track people through any network camera that serves
an **RTSP** stream. A camera mounted on the ceiling sees the whole floor from above and doesn't
move, and an **infrared night-vision** camera still sees people in the dark room a projection needs.

1. Get an RTSP URL from the camera:
   - **Wyze Cam v2 / v3 / Pan**: install Wyze's RTSP firmware from Wyze's support site, turn on
     RTSP in the Wyze app (**Settings → Advanced Settings → RTSP**), and copy the URL it shows,
     e.g. `rtsp://user:password@192.168.1.50/live`.
   - **Newer Wyze cameras** (no RTSP firmware): run
     [docker-wyze-bridge](https://github.com/mrlt8/docker-wyze-bridge) on a computer or NAS on
     your network. Each camera then appears as `rtsp://<bridge-ip>:8554/<camera-name>`.
   - **Other IP cameras** (Reolink, Tapo, Amcrest, Hikvision and so on): use the RTSP URL from the
     camera's manual. Pick the lower-resolution "sub stream" if there is one; tracking doesn't need
     more than 640×360 and it lowers latency.
2. In the panel, under **Camera tracking**, choose **Network camera (RTSP)**, paste the URL and
   press **Connect**. The status line shows `rtsp 640x360 15fps` once frames arrive.
3. Run **Calibrate camera to floor** again. Calibration waits out the stream's delay, and each
   camera keeps its own calibration, so you can switch back to the phone camera without redoing it.

Tips: mount the camera upright (use the Wyze app's **Flip** option if it hangs upside down), set
the camera's night vision to **On** instead of Auto so it doesn't switch modes mid-party, and use
H.264 rather than H.265 if the stream doesn't play. The phone and camera must be on the same
network. The URL, including any password, is stored only in the app's private storage.

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
 │  or RtspCameraSource (Media3 ExoPlayer RTSP → hardware decoder → YUV ImageReader)
 │   └─ FloorVisionPipeline (VisionFrame → 1-slot Channel → Dispatchers.Default → ML Kit Task.await())
 │        ├─ FEET mode:   Pose detection, heel+toe midpoint per foot (1 person, 2 points)
 │        ├─ BODIES mode: Object detection, bottom-centre of each box (up to 5 people)
 │        ├─ FloorMapping homography: camera → canvas UV
 │        └─ PersonTracker (ids, smoothing, velocity) → AtomicReference snapshot for GL
 │
 ├─ AudioAnalyzer (AudioRecord on Dispatchers.IO, 1024-pt FFT, 50% overlap)
 │    bass / mid / treble relative to room ambience + spectral-flux onset ("beat") detection
 │
 └─ SoundEngine (AudioTrack thread, 512-sample blocks, up to 48 synthesised voices + ambience)
      scenes call frame.sound.play(Sfx.X, gain, pan) from the GL thread; a lock-free queue hands them over
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
| **Ground Smoke** | GPU stable fluids: splats, vorticity confinement, Jacobi pressure projection, semi-Lagrangian advection; a colour channel stirred in by motion; a fading footprint-glow field | Feet drag the smoke along, shove it aside and leave curling wakes. Moving through it tints it (rainbow by speed, or a fixed colour) and each step leaves a glowing shoe print | Bass makes the fog machines puff harder |
| **Snowfall / Christmas / Hanukkah** | Snow-depth field (shoe-shaped prints with tread, left and right feet alternating along the walking direction; fresh snow refills them), instanced six-fold snowflakes with shadows, string lights, menorah | Footprints with crunching steps, flakes blown aside by passers-by | Beats brighten the scene |
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
- The scary scenes react to the first frame someone is detected. With the phone camera, tracking needs some light, so in a pitch-black haunted-house room use an IR network camera.
- Tracking needs some light on people. The projection itself lights them, but a pitch-black room limits it. Use a [network IR camera](#network--ir-camera-wyze-rtsp) for dark rooms.
- A network camera adds its stream delay (typically 0.3–1 s) to tracking. Use the camera's sub stream and a wired or 5 GHz connection to keep it low.
- Calibration assumes the camera stays still afterwards. Re-run it if the camera moves.
- Chromecast isn't supported (see **Casting**).
- Ground Smoke needs half-float render targets (nearly all GPUs since ~2016); otherwise it falls back to procedural smoke.
- The app uses the internet only to check GitHub for a newer release. Camera and microphone data never leave the device.
