# PhantomFloor play-test report

Date: 2026-10-25. Produced by the `playtester` agent (`.claude/agents/playtester.md`).

Evidence comes from `tools/playtest/run.sh`, which writes `build/playtest/`:
- **Bots:** four personas play every scored game for 150 s (`report.md`).
- **Probes:** `TraceTest.kt` writes `simon_trace.txt`, `lava_camper.txt` and `wanderer_starts.txt`.
- **Panel:** Paparazzi screenshots of the control panel.
- **Scenes:** WebGL2 renders of every scene.
- **Games:** renders of key game moments, drawn with the real `game_bg.frag` and `shape.frag`. White dots are the bots' feet.

Games seed their RNG from `System.nanoTime()` (`GameScene.kt:51`), so numbers vary a little between runs. The patterns below held across three runs.

> **Editor's note:** the scene previews stamp snow footprints as round blobs, a simplification in the preview harness (`tools/shader-preview/check-seasons.js`). The app itself has drawn shoe-shaped prints with tread since 1.2.0 (`footprint.glsl`, `FootstepTracker`). The snow notes below are corrected for that.

## Summary: the 5 changes that would help most

1. **Make step detection survive a dropped camera frame.** Today one lost frame while standing on a pad counts as a new step: "WRONG!" in Simon, −3 s in Defuse. (`GameScene.kt:109-119`)
2. **Reset the field when the countdown starts.** Today the countdown and the next round's first frame show the previous round. In Simon, a pad still lit from the last round looks like the first flash, so careful players lose in about 2 s.
3. **Make START harder to trigger by accident.** Require about 0.7 s of standing on it, with a ring that fills, and ignore people who were already standing there. Six passers-by start a round every 3.4 s (median). Someone standing on START restarts the game instantly.
4. **Rework Floor is Lava scoring.** Every engaged bot scores the maximum (59–60 of 60), and standing still off the grid also scores 59.
5. **Make the floor work for groups and the panel work for the host.**
   - Sequence games: count a group's repeat step as one step.
   - Panel: a "Now playing" card on the phone, and real switches and chip groups for settings that are on/off or pick-one.
   - Add an About screen.

## Bugs found

### B1. A dropped tracking frame counts as a new step (High)

- **Observed:** in `build/playtest/simon_trace.txt`, an adult bot stands on a pad it has already stepped on. The bots drop 1% of tracking frames, which is realistic for ML Kit and RTSP. After a dropped frame the game registers a fresh step: WRONG! at 16.35 s and 46.48 s, each right after `(tracking dropped a frame)`.
- **Reproduce:** run `./gradlew :app:testDebugUnitTest --tests '*TraceTest.simonTrace*'`, then `grep -B2 WRONG app/build/playtest/simon_trace.txt`.
- **Cause:** `GameScene.stepped()` (`GameScene.kt:109-119`) treats "not inside last frame" as a new entry, and `inside` is rebuilt from scratch every frame (`:156`, `:198`). So a single missing frame re-arms the target. This affects Simon, Defuse, Whack-a-Mole, the Coloring swatches and START.
- **Fix:** keep a `lastSeen[key] = phaseTime` map. Count a step only if the key has been absent for more than about 0.25 s, and prune entries older than 1 s.

### B2. Countdown, results and the first playing frame show the previous round (High)

- **Observed:**
  - Simon's countdown reads "Your turn: 3 of 3" with the blue pad lit, under the "2" ([img](img/game_simon_countdown_stale.png)).
  - Lava's countdown shows the last round's field at 70% lava, which snaps clear at GO ([img](img/game_lava_countdown_stale.png)).
  - Defuse's countdown shows the explosion scorch instead of the bomb.
- **Effect in Simon:**
  - After a round ends on WRONG!, the wrong pad stays fully lit through the results screen, the title screen and the countdown. It is still lit when "Watch..." appears ([img](img/game_simon_round2_start.png)).
  - In the trace, the stale pad 0 is lit at 39.30 s, the real first flash is pad 3 at 40.00 s, and the bot steps on pad 0 at 41.10 s: WRONG.
  - That is why every Simon round after the first lasts about 2 s and scores 0.
  - A person watching for "the pad that lights up" is misled the same way, so this is a game bug, not a bot bug.
- **Cause:**
  - `compose()` calls `drawPlaying()` during the countdown and results screens (`GameScene.kt:237-238`, `:248-249`).
  - `startRound()` runs only after the countdown (`:172-175`).
  - `SimonGame.startRound` never clears `lit` (`SimonGame.kt:33-37`), and `lit` decays only inside `updatePlaying`.
- **Fix:**
  - Call `startRound()` and set `score = 0` when the countdown starts.
  - Add `lit.fill(0f)` to `SimonGame.startRound`.
  - Dim the playing field to about 40% during the countdown.

### B3. Passers-by start rounds by accident, and anyone standing on START restarts the game instantly (High at parties)

Measured in `wanderer_starts.txt`, with bots that ignore the game and start away from START, 5 runs × 600 s:

| Passers-by | Accidental starts | Median wait on the title screen | Fastest |
|---|---|---|---|
| 1 | 33 | 15.2 s | 0.0 s |
| 3 | 39 | 7.5 s | 0.0 s |
| 6 | 42 | 3.4 s | 0.0 s |

- **Cause:**
  - One frame inside START's radius of 0.085 starts the game (`GameScene.kt:164-166`).
  - START is only tracked on the title screen, so anyone standing on it when the results screen ends counts as a new step.
  - START sits near the edge where people walk in, at y = 0.18.
- **Fix:**
  - Track START in every phase and require 0.7 s of standing on it.
  - Show a `SHAPE_PROGRESS` ring that fills while someone stands there, as Tic-Tac-Toe and CLEAR already do.
  - Optionally move START towards the centre.

### B4. Floor is Lava can't be lost, and the area off the grid is permanently safe (High for that game)

- **Observed:**
  - Every non-wanderer session scores 59–60 per 60 s round.
  - Standing still just right of the grid scores 59 (`lava_camper.txt`); standing still in the middle scores 35–55.
  - The dark safe margins show in [img](img/game_lava_late.png).
- **Cause:**
  - The 9×5 grid covers x 0.15–1.63 (of 1.78) and y 0.06–0.88, and off-grid feet are never hot (`FloorIsLavaGame.kt:126-128`, `:166-168`).
  - `toInt()` truncation makes the edges uneven.
  - Tiles warn for 1.5 s before melting, lava peaks at 70% of the floor, and some lava cools every 4 s.
  - Burns never cost points; the score clock just pauses.
- **Fix:**
  - Fill the floor with an 11×6 grid sized from both axes, use `floor()`, and treat off-grid feet as out.
  - Over the round, shrink the warning to 0.7 s and ramp lava up to 85%.
  - Give each player 3 lives, shown with a coloured ring; the score is time survived, and the round ends when everyone is out.

### B5. Groups punish themselves in Defuse and Simon (Medium)

- **Observed:** with 3 players, Defuse scores are almost all 0 (rounds of 5–7 s, about 26 buzzes per session), and Simon behaves the same way.
- **Cause:** when a friend re-steps the pad that was just entered correctly, it counts as a wrong step (`DefuseBombGame.kt:321-324`, `SimonGame.kt:66-69`). Real groups do this all the time, because everyone runs to the same colour.
- **Fix:**
  - Ignore steps on the last correct pad for about 1.5 s; just flash it.
  - Make "Step 2 of 3" more prominent.
  - Add a hint dot on Easy.

### B6. Mixed messages on the results screen (Low)

- **Simon:**
  - WRONG! plays the buzz and the fanfare together, and shows "NEW BEST!" with confetti ([img](img/game_simon_over.png)), because `GameScene.kt:99-103` always celebrates a new best.
  - Fix: play the buzz first and the fanfare 0.6 s later.
- **Tic-Tac-Toe vs computer:**
  - It says "O WINS!" ([img](img/game_ttt_over.png)).
  - Fix: say "COMPUTER WINS" or "YOU WIN!" instead.

### B7. Harness issues (fixed in this pass)

- **Fixed:**
  - The network-camera screenshot didn't show the RTSP field.
  - The panel screenshots skipped the Projection section, including the Cast button.
  - A `PlaytestRunner` comment wrongly claimed runs are deterministic.
- **Still open:** the text-overlap check doesn't cover the countdown number drawn over pads and feet.

## Per-game notes

- **Stomp the Spider** ([playing](img/game_stomp_playing.png), [over](img/game_stomp_over.png))
  - Scores spread well by skill: kid 92, adult 66, casual 16–18 per 60 s.
  - On the results screen the spiders freeze mid-step; let them scuttle off the floor.
  - Splats need a squash-in and a "+1".
- **Whack-a-Mole** ([img](img/game_moles_playing.png))
  - Fair and skill-based: kid 47–57, casual 21–25, wanderer 2 per 45 s.
  - Add a squash and a dust puff on each hit.
- **Defuse the Bomb** ([img](img/game_bomb_input.png))
  - The orange timer on the dark-red bomb has low contrast.
  - A wrong wire needs a visible "−3 s" and a shake.
  - See also B2 and B5.
- **Tic-Tac-Toe** ([img](img/game_ttt_playing.png))
  - A board lasts 8–15 s, then 7 s of results, and then someone has to press START again.
  - Make it best-of-5, with the next board starting automatically.
- **Simon Says** ([img](img/game_simon_attract.png))
  - Broken after round 1 (B1, B2).
  - The HUD shows "Length 0" at the start; show "Round 1" instead.
- **Balloon Pop** ([img](img/game_balloons_playing.png))
  - Balloons pop when they drift into a foot, not only when stepped on (`BalloonPopGame.kt:193`). Three passers-by scored 118, more than one engaged casual player (65).
  - Require a step or foot speed to pop, and add a combo counter.
- **Floor is Lava** ([early](img/game_lava_early.png), [late](img/game_lava_late.png)): see B4. "Burns" never matters to the score.
- **Coloring** ([img](img/panel_game_coloring.png))
  - Not bot-tested, because the paint lives on the GPU.
  - The "eraser" label sits at the floor edge, where edge feathering will clip it.
  - Mirror the palette on the opposite edge.
- **Floor UI in general:**
  - The HUD reads from one side only; mirror the score, rotated 180°, at the opposite edge.
  - "step here" is small; raise it from 0.03 to 0.04.
  - There's no "GO!" frame, only a tone.

## Per-scene notes

- **Fog** ([img](img/scene_fog.png)): rich; the clear rings around feet read well.
- **Swarm** ([normal](img/scene_swarm.png), [spectral](img/scene_swarm_spectral.png)): in normal mode the dark spiders on dim fog become holes in a faint glow on a projector. Make spectral the default, or rim-light the spiders.
- **Smoke** ([img](img/scene_smoke.png)): density piles up into white blotches at the edges; fade it out near the edges.
- **Blood** ([img](img/scene_blood.png)): ringed by black, so it doesn't fill the floor.
- **Snow** ([img](img/scene_snow.png)): the bright grey floor puts a lot of light into a dark party; offer a dimmer "night snow" option. (Footprints in the app are already shoe-shaped; see the editor's note.)
- **Garden** ([img](img/scene_garden.png)): lovely; let flowers bloom in with a little overshoot.
- **Fireflies** ([img](img/scene_fireflies.png)): too sparse and dim to read on a real projector.
- **Fireworks** ([img](img/scene_fireworks.png)): dead time between bursts; add launch streaks and smoke afterglow.
- **Transition** ([img](img/scene_transition.png)): looks great.

## Q1. GUI

Screenshots: [part 1](img/panel_part1.png), [part 2](img/panel_part2.png), [network camera](img/panel_network_camera.png), [part 5](img/panel_part5.png).

**Control panel:**

1. **Order the panel the way a host sets up.**
   - Add a Setup card with numbered, ticked steps (Cast, Keystone, Camera, Calibrate) that collapses once all are done.
   - Below it, add a "Now playing" row with the scene name, ◀ ▶ and Pause.
2. **Use the right control for each setting.**
   - Add `SceneParam` kinds RANGE, TOGGLE and CHOICE, shown as a slider, a switch and a chip group.
   - Today "Play vs computer: 1" and "Picture: None·Pumpkin·…: 1" are sliders ([TTT](img/panel_game_ttt.png), [Coloring](img/panel_game_coloring.png)).
   - Show values in words, e.g. "Normal (1×)".
3. **Make all 21 scenes discoverable.** Season tabs with counts, plus thumbnails from `docs/previews`.
4. **Make the panel readable at arm's length.**
   - Raise labels from 13 to 15 sp and the status line from 12 to 13 sp.
   - The RTSP help text is too faint.
   - The RTSP field clips its hint; put Connect on a row below the field.
5. **Make spacing consistent.** The gap between the season and scene chip groups differs from the gap between rows.
6. **Rename "Touch acts as a person"** to "Walk with your finger (no camera)".
7. **Make the panel easy to close.** Put an ✕ in the panel header; today Hide is the last row and needs a full scroll.
8. **Keep the touch targets as they are.** Chips are about 36 dp and sliders are full width.

**On the floor:**

- Fix B2 and B3.
- Mirror the HUD at the opposite edge.
- Add a "GO!" frame.
- After about 20 s on the title screen, show a ghost foot stepping on START so guests learn without reading.

## Q2. Animations

- **All games:**
  - Score pop-ups: "+1" floats up and fades over 0.6 s.
  - The HUD bounces when the score changes (scale 1 → 1.25 → 1).
  - Countdown numbers ease out from 1.6× with a ring burst, then "GO!".
  - The results card slides in and the score counts up with ticks.
  - A new `Floater` list in `GameScene` covers most of this.
- **Stomp:** splats squash in, and the spiders flee the floor on the results screen.
- **Moles:** they rise with an overshoot; a hit squashes the mole for 80 ms and puffs dust (`SHAPE_DISC`).
- **Simon and Bomb:**
  - Pads press down 8% when stepped on and bloom when lit.
  - The bomb wobbles more as time runs out.
  - A wrong wire flashes red and shows "−3 s".
- **Lava:**
  - Warning tiles pulse at 4 Hz with a spreading crack.
  - Tiles bubble when they melt and puff steam when they cool.
  - A burning player gets a ring of embers.
- **Balloons:** squash and stretch as they drift, and pop into a ring plus shreds.
- **Fireworks:** add launch streaks, crackle and smoke afterglow (`FluidSolver`).
- **Fireflies:** more of them, a slow pulse, and they gather at feet that stand still (`BoidSwarm`).
- **Snow:** flakes kick up behind fast feet.
- **Idle scenes:** after 30 s with nobody tracked, an "invisible walker" keeps the floor from looking frozen.

## Q3. New games and visualizations

- **Musical Floor (S).** A 4×3 grid of tiles, each playing a pentatonic note when stepped on. A group can play a tune, and a mode lights up the next note of a known song.
  - Why it fits: no rules, so it suits every age.
  - Reuses: `GameScene` with `freePlay`, `SHAPE_PAD`, `stepped()`, the `Sfx` tones, and `RippleField` for ripples under each step.
- **Red Light, Green Light (S).** While the floor is red, anyone whose feet move is out; the first player to reach the far edge wins.
  - Why it fits: a camera judges stillness better than a human referee.
  - Reuses: `PersonPoint` velocity, `SHAPE_GLOW`, `Sfx.BUZZ`/`FANFARE`.
- **Trail Painter (S).** Everyone leaves fading coloured footprints that blend like paint.
  - Why it fits: a calm attract mode between games.
  - Reuses: `StampField`, `FootstepTracker`, `footprint.glsl`.
- **Hot Potato (M).** A bomb sits under one person's feet; step next to someone to pass it, and a hidden fuse decides who loses.
  - Why it fits: it uses multi-person tracking well.
  - Reuses: `SHAPE_BOMB`, `Sfx.TICK`/`EXPLOSION`, confetti.
- **Bug Herding (M).** Players work together to herd a fleeing swarm into a pen.
  - Reuses: `BoidSwarm` (which already flees feet), `SHAPE_RING`, the `GameScene` timer.
- **Dance Pads (M).** Arrows reach four floor zones on the music's beats; step on each zone as its arrow arrives.
  - Reuses: `AudioAnalyzer` beats, `SHAPE_PAD`, `stepped()`.
- **Koi Pond (S, visualization).** Koi glide under water and scatter from feet; standing still lets them gather.
  - Reuses: the `PoolScene` water shaders, `RippleField`, `BoidSwarm`.
- **Constellations (S, visualization).** Feet leave stars that link with faint lines and drift into a turning sky.
  - Why it fits: it's mostly black, so it works on dim projectors.
  - Reuses: `StampField` and the firefly glow sprites.
- **Ice Cracks (M, visualization).** Cracks spread from heavy or fast steps, and water wells up through them.
  - Reuses: `RippleField`, `FluidSolver`, the snow ground.

## Q4. About page and itch.io links

**What exists today:**
- `supportButton` opens `SUPPORT_URL` (the itch.io page).
- `versionText` is 11 sp at 40% white.
- `updateButton` appears only when `UpdateChecker` finds a newer release.
- There are no credits, privacy statement or help.

**Proposal:** an "About & help" sheet, opened from a row above Hide, with:
1. **Header:** icon, app name, version and build. Long-press copies diagnostics (device, GL renderer, camera mode, calibration state).
2. **Updates:** "Up to date ✓" or "Download v1.x", plus a badge dot on the panel header when an update exists.
3. **Quick help:** the setup graphic (`docs/itch/how-it-works.png`), tips, and "Replay first-run tips".
4. **Links:**
   - itch.io (`SUPPORT_URL`)
   - GitHub (`https://github.com/${UPDATE_REPO}`)
   - "Report a problem", which opens a new GitHub issue pre-filled with the diagnostics.
5. **Privacy:** "Camera and mic frames are processed on this phone and never leave it. The only network call is a GitHub version check." It links to a `PRIVACY.md`, which the Play Store would also require.
6. **Credits:** licences for ML Kit, Material Components and Media3.
7. **The ♥ Support button.**

## Q5. Donations without nagging

- **Channels:** keep itch.io's "$0 or donate" as the main one, with Ko-fi as an optional second button.
- **Ask once, at a happy moment.** After a real party (more than 45 minutes of running, and at least 3 rounds or about 10 people tracked), show one card the next time the host opens the panel:
  - Text: "Hope the party was a hit! PhantomFloor is free and made by one person. ♥ Support · Maybe later · Don't ask again".
  - Show it at most every 60 days, and never again after "Don't ask again" or a tap on Support.
  - Never show it on the floor or while casting.
- **Placement:** move the permanent button into About, and keep a small ♥ in the panel header.
- **Play Store:**
  - Google Play generally requires its own billing for tips paid inside an app. Either hide the external donate link in a Play build (a `BuildConfig.STORE` flag) or sell tips as consumable Play Billing products.
  - Keep the external link in the itch.io and GitHub builds.
  - A Play listing also needs a privacy-policy URL and Data safety answers (on-device processing, no data collected).

## Prioritised backlog

| Item | Impact | Effort | Files |
|---|---|---|---|
| A step must be absent ≥ 0.25 s before it counts again (B1) | H | S | games/GameScene.kt |
| Start the round when the countdown starts; clear Simon's `lit` (B2) | H | S | games/GameScene.kt, games/SimonGame.kt |
| Stand on START with a filling ring; track START in every phase (B3) | H | S | games/GameScene.kt |
| Lava: full-floor grid, off-grid counts as out, shorter warnings, lives (B4) | H | M | games/FloorIsLavaGame.kt |
| Group-tolerant sequence input (B5) | M | S | games/DefuseBombGame.kt, games/SimonGame.kt |
| Switches and choice chips for settings (`SceneParam` kinds) | M | M | scenes/SceneParam.kt, ui/ControlPanel.kt, games |
| Setup checklist and "Now playing" row | H | M | activity_main.xml, ui/ControlPanel.kt, MainActivity.kt |
| About & help sheet | M | M | new ui/AboutSheet.kt, activity_main.xml, strings.xml |
| Post-party donation card and Play-build flag | M | S | MainActivity.kt, util/Prefs.kt, app/build.gradle.kts |
| Score floaters, countdown easing, "GO!", results card | M | S | games/GameScene.kt |
| HUD readable from both ends | M | S | games/GameScene.kt |
| Results-screen fixes (B6) | L | S | games/GameScene.kt, games/TicTacToeGame.kt |
| Balloon pops need a step or foot motion | L | S | games/BalloonPopGame.kt |
| Tic-Tac-Toe best-of-5 | M | S | games/TicTacToeGame.kt |
| Swarm contrast, smoke edges, firefly brightness | M | S | scenes/SwarmScene.kt, shaders/smoke.frag, scenes/FirefliesScene.kt |
| Idle "invisible walker" | M | S | engine/PhantomRenderer.kt |
| New: Musical Floor, Red Light Green Light, Trail Painter | H | S each | new games/*.kt |
| New: Hot Potato, Bug Herding, Dance Pads | M | M each | new games/*.kt |
| Test hook to seed the game RNG | L | S | games/GameScene.kt |

## Harness changes in this pass

Test and tool code only; no app code changed.

- **`TraceTest.kt`:** the Simon trace with dropped-frame markers, the Lava camper, the accidental-start measurement, and frame dumps.
- **`tools/playtest/game-frames.js`:** renders game moments with the real game shaders into `build/playtest/games/`.
- **`run.sh`:** runs the probes and renders the game frames.
- **`PanelScreenshotTest.kt`:** covers the whole panel, scrolls to the RTSP field, and adds the Tic-Tac-Toe and Coloring settings screenshots.
- **`PlaytestRunner.kt`:** comment corrected about determinism.
