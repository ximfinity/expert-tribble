# Update the itch.io page for 1.4.0 (about 10 minutes)

Page: https://ximfinity.itch.io/phantom-floor-interactive-projector-floor-app

itch.io has no API for page content, so this is done in the browser. The APK itself is uploaded
automatically by the Release workflow. Everything else you need is in this folder:

| File | What it's for |
|---|---|
| `cover.png` | Cover image (630 × 500): shown in search, browse and on the page |
| `banner.png` | Header banner (960 × 240) |
| `gifs/*.gif` | **Animated** clips: scenes reacting to a walker, the jump scare, the shadow puppets |
| `features/*.jpg` | 1280 × 720 stills with headlines (13–20 are new in 1.2–1.4) |
| `how-it-works.png` | 4-step setup graphic |
| `screenshots/*.jpg` | Plain 1280 × 720 stills (backup) |

---

## Step 1: Basics (Edit game → top of the form)

| Field | Value |
|---|---|
| Title | `PhantomFloor: Interactive Projector Floor` |
| Short description or tagline | `Turn any floor into a living projection: fog that parts at your feet, spiders that scatter, shadows that follow you, jump scares and stomp-to-play games.` |
| Classification | **Tools** |
| Kind of project | **Downloadable** |
| Release status | **Released** |

## Step 2: Pricing

Keep **$0 or donate** with a suggested donation of **$3.00**, and **"Ask for a donation after
download"** on.

## Step 3: Uploads

Once the 1.4.0 release finishes, the Release workflow has already uploaded `PhantomFloor-1.4.0.apk`
to the `android` channel, replacing 1.3.0. Check it's ticked **Android**. Nothing else to upload.

## Step 4: Description

Select all of the old description and replace it with the text between the lines below. Wherever
you see `[IMAGE: …]`, delete that line and use the editor's **image** button to upload that file
there. GIFs keep animating once they're inserted.

---

[IMAGE: gifs/fog.gif]

**Turn any floor into a living, reacting projection.**

PhantomFloor runs on an Android phone connected to a projector pointed at the floor. Everything it
shows reacts to the people walking on it: fog parts around their feet, spiders scatter from their
shoes, shadows of witches and zombies come for them, and monsters lunge out of the dark.

Nothing is a video loop. Every frame, and every sound, is generated live on your phone, so it never
repeats and always responds.

## 🎃 Halloween

[IMAGE: gifs/swarm.gif]

- **The Swarm**: hundreds of spiders that flee from feet and scurry out of a sweeping flashlight.
  - *New:* realistic hairy spiders, radioactive glowing ones, a mix of tiny to huge, glowing eyes,
    3D shadows cast by the flashlight, and spiders that stop to **spin webs** (walk through one to
    tear it).
  - *New:* a **giant jump-scare spider** that drops right in front of someone.
- **Shadow Puppets** *(new)*: candle-lit floor, and the shadows of witches, ghosts, zombies and long
  clawed hands wander across it, then come for whoever walks on.
- **Ectoplasmic Fog**, **Ground Smoke** (glowing footprints, stir it to change its colour),
  **Cursed Fluid** and **Living Water** (tentacles, grasping hands and screaming faces rise out of it).

[IMAGE: gifs/puppets.gif]

[IMAGE: features/17-spider_webs.jpg]

## 💀 Scary *(new)*

The floor stays **completely black and silent until someone walks on.** Then:

[IMAGE: gifs/jumpscare.gif]

- **Jump Scare**: a heartbeat, then a screaming ghoul or skull lunges up out of the floor in front of
  them with a flash.
- **Eyes in the Dark**: glowing eyes open and watch, whispering. Stand still too long and a ring of
  teeth snaps shut under your feet.
- **Grasping Hands**: the floor cracks open where you stop and rotting hands claw at your feet.

Volume and flash brightness are adjustable for younger guests.

[IMAGE: features/15-hands.jpg]

## 🎮 Floor games

[IMAGE: features/20-games.jpg]

Stand on START and play with your feet: **Stomp the Spider, Whack-a-Mole, Defuse the Bomb,
Tic-Tac-Toe, Simon Says, Balloon Pop, The Floor is Lava** and free-play **Coloring**. Scores, best
scores, countdowns and sound effects included, and they work for groups.

## ❄️ Winter · 🍂 Fall · 🌷 Spring · 🎆 4th of July · ☀️ Summer

[IMAGE: gifs/christmas.gif]

- **Snowfall**, **Christmas** and **Hanukkah**: snow that keeps every shoe print and slowly fills it in.
- **Fall Leaves**, **Spring Garden** (flowers bloom where you walk), **Fireworks**, **Fireflies**
  and **Pool Splash**.

[IMAGE: gifs/fireworks.gif]

## How it works

[IMAGE: how-it-works.png]

- **Camera tracking:** point the phone's camera at the floor and on-device AI finds feet, or whole
  people in groups. Or use a **network / IR camera** (Wyze and other RTSP cameras) mounted overhead,
  which works in the dark.
- **Sound:** every scene has its own sound effects, and thunder, screams or music make the scenes pulse.
- **Touch:** no camera needed. Your fingers on the phone act as feet.
- **Cast button:** send the visuals to your projector or TV and use your phone as the remote.
- **About & help** in the app has setup steps, a problem-report button and update checks.

## What you need

- An Android phone or tablet with **Android 8.0 or newer**.
- A **projector or TV-projector** aimed at the floor, connected by screen mirroring / Miracast, an
  Android TV device that accepts casting, or an HDMI / USB-C adapter. (Chromecast isn't supported.)
- A **dark room**. Black means "no light", so the effects look like they float on the floor.

## Install

1. Download the APK on your Android device and open it.
2. If Android asks, allow installs from your browser or file manager.
3. If you see "Blocked by Play Protect", tap **More details → Install anyway**. It's flagged only
   because it doesn't come from the Play Store.
4. Updates install over the old version and keep your settings.

## Privacy

Camera and microphone are processed **only on your phone**: nothing is recorded or uploaded. A
network camera streams only over your own Wi-Fi. The app uses the internet only to check for a newer
version.

**PhantomFloor is free.** If it made your party, a small donation keeps new scenes coming. ♥

---

## Step 5: Genre, tags and links

| Field | Value |
|---|---|
| Genre | Simulation |
| Tags (itch.io allows 10) | `projection-mapping`, `halloween`, `horror`, `jump-scare`, `interactive`, `android`, `procedural-generation`, `party`, `seasonal`, `local-multiplayer` |
| AI generation disclosure | Answer honestly for the code and the art/text. |
| Community | **Comments** on |

## Step 6: Images

1. **Cover image**: keep `cover.png`.
2. **Screenshots**: replace the list with these, in this order. The first few appear without
   scrolling, so lead with motion and the new scares:
   1. `gifs/jumpscare.gif`
   2. `gifs/swarm.gif`
   3. `gifs/puppets.gif`
   4. `features/19-giant_spider.jpg`
   5. `features/14-eyes.jpg`
   6. `features/16-puppets.jpg`
   7. `features/18-radioactive.jpg`
   8. `features/20-games.jpg`
   9. `gifs/fog.gif`
   10. `gifs/christmas.gif`
   11. `features/13-jumpscare.jpg`
   12. `features/12-keystone_edit.jpg`
3. **Gameplay video / trailer**: a real-room clip of someone triggering the Jump Scare will outsell
   everything above. Upload it to YouTube and paste the link here when you have one.

## Step 7: Theme

No change (banner `banner.png`, background `#07070B`, inner `#0E0E15`, text `#E6E6EE`, links `#7CFF9B`).

## Step 8: Save, then post the devlog

Click **Save**, then **Devlog → Create devlog post** with the text below (title, then body). Attach
`gifs/jumpscare.gif` and `gifs/puppets.gif` to the post.

### Devlog: "PhantomFloor 1.4: things that go bump on the floor"

> Just in time for Halloween, PhantomFloor 1.4 is out, and it's the scariest update yet.
>
> **New: the Scary section.** The floor stays pitch black until someone walks on. Then a ghoul
> lunges out of the dark screaming (**Jump Scare**), glowing eyes gather and a ring of teeth snaps
> shut when you stand still (**Eyes in the Dark**), or the floor cracks open and hands claw at your
> feet (**Grasping Hands**).
>
> **New: Shadow Puppets.** Candlelight, and the shadows of witches, ghosts, zombies and clawed hands
> that wander the floor and come for you.
>
> **The Swarm grew teeth.** Realistic and radioactive spiders, a mix of sizes, glowing eyes, 3D
> shadows from the flashlight, spiders that spin webs you can tear through, and a giant spider that
> drops right in front of your guests.
>
> **Also since 1.1:** eight stomp-to-play floor games, sound effects for every scene, shoe-print
> footprints in the snow, glowing footprints in the smoke, network / IR camera support (Wyze and other
> RTSP cameras), an About & help page, and clearer menus.
>
> It's free; a small donation keeps new scenes coming. Post a video of your setup in the comments.
> I'd love to see your guests jump!
