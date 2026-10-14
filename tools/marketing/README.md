# Marketing asset generators

Regenerate the itch.io assets in `docs/itch/` from the real shaders. This needs Node with
Playwright (Chromium) and Python with Pillow.

```bash
export PW=/path/to/node_modules/playwright
node tools/marketing/anim.js app/src/main/assets/shaders /tmp/frames   # animation frames for 8 scenes
python3 tools/marketing/gif.py /tmp/frames docs/itch/gifs              # GIFs, each kept under 3 MB
```

`market.py` (feature images, banner, how-it-works) and `cover.py` take a folder of 1280×720 scene
renders. Produce them with `tools/shader-preview/check.js` and `check-seasons.js` after changing the
canvas size to 1280×720.
