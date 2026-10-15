# Build the itch.io page (about 10 minutes)

Page: https://ximfinity.itch.io/phantom-floor-interactive-projector-floor-app

itch.io has no API for page content, so this is done in the browser. Everything you need is in
this folder:

| File | What it's for |
|---|---|
| `cover.png` | Cover image (630 × 500): shown in search, browse and on the page |
| `banner.png` | Header banner (960 × 240) |
| `gifs/*.gif` | **Animated** clips of the real scenes reacting to a walker, each under 3 MB |
| `features/*.jpg` | 1280 × 720 stills with headlines |
| `how-it-works.png` | 4-step setup graphic |
| `screenshots/*.jpg` | Plain 1280 × 720 stills (backup) |

---

## Step 1: Basics (Edit game → top of the form)

| Field | Value |
|---|---|
| Title | `PhantomFloor: Interactive Projector Floor` |
| Short description or tagline | `Turn any floor into a living projection: fog that parts at your feet, spiders that scatter, snow that keeps your footprints.` |
| Classification | **Tools** |
| Kind of project | **Downloadable** |
| Release status | **Released** |

## Step 2: Pricing

- Choose **$0 or donate**. Set the suggested donation to **$3.00**.
- Turn on **"Ask for a donation after download"** if it's offered.

## Step 3: Uploads

The Release workflow already uploaded `PhantomFloor-1.1.0.apk`. On that file, tick **Android**.
Leave "This file will be played in the browser" off.

## Step 4: Description

Paste the text between the lines below into the **Description** box. Wherever you see
`[IMAGE: …]`, delete that line and use the editor's **image** button to upload that file there.
GIFs keep animating once they're inserted.

---

[IMAGE: gifs/fog.gif]

**Turn any floor into a living, reacting projection.**

PhantomFloor runs on an Android phone connected to a projector pointed at the floor. Everything it
shows reacts to the people walking on it: fog parts around their feet, spiders scatter from their
shoes, snow keeps their footprints, and flowers grow where they walk.

Nothing is a video loop. Every frame is generated live on your phone's GPU, so it never repeats and
always responds.

## 🎃 Halloween

[IMAGE: gifs/swarm.gif]

- **The Swarm**: hundreds of spiders that flee from feet, spread panic through the swarm, and scurry out of a sweeping flashlight beam.
- **Ectoplasmic Fog**: thick, swirling green-and-purple fog that clears around feet and curls in people's wake.
- **Ground Smoke**: a real fluid simulation of fog-machine smoke that pours in and flows around legs.
- **Cursed Fluid**: a bubbling pool of blood or lava that creeps toward guests.
- **Living Water**: dark water that rises into tentacles, grasping hands and screaming faces.

[IMAGE: gifs/water.gif]

## ❄️ Winter

[IMAGE: gifs/christmas.gif]

**Snowfall**, **Christmas** and **Hanukkah** (with a menorah lit for the night you choose). Falling
snow settles into a snowy floor that keeps every footprint and slowly fills it back in.

## 🌷 Spring · 🍂 Fall

[IMAGE: gifs/garden.gif]

- **Spring Garden**: grass and flowers sprout and bloom wherever people walk.
- **Fall Leaves**: kick through a carpet of autumn leaves that flutter on the wind.

## 🎆 4th of July · ☀️ Summer

[IMAGE: gifs/fireworks.gif]

- **Fireworks**: a burst of color under every step.
- **Pool Splash**: ripples as you wade, and a big splash when you jump in.
- **Fireflies**: lightning bugs that follow you and slowly blink in sync.

[IMAGE: gifs/pool.gif]

## How it works

[IMAGE: how-it-works.png]

- **Camera tracking:** point the phone's camera at the floor. On-device AI finds feet, or whole people in groups.
- **Sound:** thunder, screams or music make the scenes pulse and flash.
- **Touch:** no camera needed. Your fingers on the phone act as feet.
- **Cast button:** send the visuals to your projector or TV and use your phone as the remote.

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
4. Future updates install over the old version and keep your settings.

## Privacy

Camera and microphone are processed **only on your phone**: nothing is recorded or uploaded. The
app uses the internet only to check for a newer version.

**PhantomFloor is free.** If it made your party, a small donation keeps new scenes coming. ♥

---

## Step 5: Genre, tags and links

| Field | Value |
|---|---|
| Genre | Simulation |
| Tags | `projection-mapping`, `halloween`, `christmas`, `interactive`, `android`, `procedural-generation`, `shaders`, `party`, `seasonal`, `particles` |
| AI generation disclosure | Answer honestly for the code and the art/text. |
| Community | **Comments** on (people love posting their setups) |

## Step 6: Images

1. **Cover image**: upload `cover.png`.
2. **Screenshots**: upload in this order. The first few appear without scrolling, so lead with motion:
   1. `gifs/swarm.gif`
   2. `gifs/fog.gif`
   3. `gifs/christmas.gif`
   4. `gifs/garden.gif`
   5. `gifs/fireworks.gif`
   6. `features/03-water.jpg`
   7. `features/06-hanukkah.jpg`
   8. `features/04-smoke.jpg`
   9. `features/11-pool.jpg`
   10. `features/12-keystone_edit.jpg`
3. **Gameplay video / trailer**: once you've filmed the app in a real room, upload it to YouTube
   and paste the link here. Footage of real feet scattering real-looking spiders sells it better
   than anything else.

## Step 7: Theme (on the page, click **Edit theme**)

| Setting | Value |
|---|---|
| Banner | `banner.png` |
| Background | `#07070B` |
| Inner background | `#0E0E15` |
| Text | `#E6E6EE` |
| Links / buttons | `#7CFF9B` |
| Font | A clean sans-serif (e.g. Lato) |

## Step 8: Publish

Set **Visibility & access** to **Public**, click **Save**, then post the devlog below.

## Launch devlog: "PhantomFloor 1.1.0 is out"

> PhantomFloor 1.1.0 is here, and it isn't just for Halloween anymore. New in this release:
> **Ground Smoke** (a real fluid simulation), **Snowfall / Christmas / Hanukkah**, **Fall Leaves**,
> **Spring Garden**, **Fireworks**, **Fireflies** and **Pool Splash**. There's also a **Cast**
> button that sends the visuals to your projector or TV and turns your phone into the remote. Post
> a video of your setup in the comments. I'd love to see it!
