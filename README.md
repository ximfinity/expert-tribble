# PhantomFloor 👻

A real-time, procedurally generated Halloween floor projection for Android. There is no video
playback: every frame of fog, spiders, blood, lava and living water is computed on the GPU and
reacts to the people walking on it (camera + ML Kit) and to the sound in the room (microphone + FFT).

| Ectoplasmic Fog | The Swarm | Cursed Fluid (lava) | Living Water |
|---|---|---|---|
| ![fog](docs/previews/fog.png) | ![swarm](docs/previews/swarm.png) | ![lava](docs/previews/lava.png) | ![water](docs/previews/water.png) |

*Previews rendered from the real shaders in headless WebGL2 (`tools/shader-preview`). The two dark
pockets in the fog are where two "people" are standing.*

## Requirements

- Android 8.0+ (minSdk 26) with OpenGL ES 3.0. Half-float render targets are used when available.
- A projector pointed at the floor, fed by the device over HDMI / USB-C (screen mirroring).
- Optional: the device's camera pointed at the projected area (for tracking), and a microphone.

## Build

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # homography + FFT unit tests
```

`ndk.abiFilters` keeps only ARM ABIs, because ML Kit's native libraries are large. Add `x86_64` to run on an emulator.

## Setting up a floor

1. Mount the projector and the phone, and mirror the phone's screen to the projector.
2. **3-finger tap** anywhere to open the control panel.
3. **Edit keystone**: drag the 4 corner handles until the glowing grid fits the floor. You can drag several corners at once. 3-finger tap when done. The corners are saved.
4. **Calibrate camera to floor**: keep the floor clear and hold the camera still. The projector shows a black frame, then four white dots one at a time. The camera finds each dot and fits a camera→floor homography. This also absorbs the keystone and any camera tilt. The result is saved.
5. Choose a scene and tune its sliders. Turn on **Show tracked points** to see green rings under detected feet.

With **Touch acts as a person** turned on, fingers on the screen act as feet. This lets you test every scene without the camera.

## Architecture

```
MainActivity ── immersive, FLAG_KEEP_SCREEN_ON, permissions, 3-finger tap, ControlPanel (ViewBinding)
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
| **The Swarm** | CPU Boids (separation, alignment, cohesion, wander, stop-and-go, soft walls) on a uniform spatial grid; one instanced draw of SDF spiders with an animated tetrapod gait | Spiders flee from feet (fast feet scare more). Panic spreads to neighbours as a scatter wave. Eyes glow hotter when panicked | Loud onsets startle the whole swarm. Beats flash the lantern light |
| **Cursed Fluid** | Flow-displaced animated Voronoi, bubbles that swell and pop, GPU wave-equation ripples; finite-difference normals with wet specular (blood) or glowing cracks (lava) | Pool tendrils creep toward each person; footsteps splash ripples | Bass boosts bubbling and lava heat; beats drop ripples |
| **Living Water** | Ripple simulation + a half-res monster height-field pass (tentacles, skeletal hand, screaming face, built from tube/dome primitives) + refracted Voronoi caustics | Monsters surface near people, turn toward them, and sink with a splash when stepped on; eyes in the water close as people approach | The face's mouth opens with loudness; loud beats summon a monster |

Scene switches use an ectoplasmic noise dissolve. Both scenes keep animating during the transition.

### Performance knobs

- Each scene renders at its own `resolutionScale` (fog 0.5, fluid/water 0.75, swarm 1.0), multiplied by the **Render quality** slider. The warp pass upscales bilinearly.
- Ripple simulations run at 256 rows in RGBA16F. On GPUs without half-float render targets they fall back to biased RGBA8.
- The Boids grid lookup is O(N·k). 2000 spiders cost about 1–2 ms of CPU.

## Adding a scene

1. Write `myscene.frag`. `#include "noise.glsl"` and `#include "common.glsl"` give you noise, hashes, Voronoi, SDF helpers, `uPeople[]` and the audio uniforms.
2. Subclass `Scene`, declare `SceneParam`s (sliders appear automatically), and draw with `gl.drawFullscreen()`.
3. Add it to `PhantomRenderer.scenes`.

`tools/shader-preview/check.js` compiles every program in WebGL2 and renders previews. This
catches GLSL errors without a device.

## Known limitations

- ML Kit pose detection tracks one person (two feet). Use **Bodies** mode for groups.
- Tracking needs some light on people. The projection itself lights them, but a pitch-black room limits it. An IR camera with an IR illuminator is the pro upgrade.
- Calibration assumes the camera stays still afterwards. Re-run it if the camera moves.
- The app mirrors to the projector. A `Presentation` on a secondary display, with controls kept on the phone, is a natural next step.
