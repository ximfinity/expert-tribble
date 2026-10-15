import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont
S = sys.argv[1]; out = sys.argv[2]
W, H = 630, 500
def crop(path, w, h):
    im = Image.open(path).convert("RGB")
    r = max(w / im.width, h / im.height)
    im = im.resize((int(im.width * r + 0.5), int(im.height * r + 0.5)), Image.LANCZOS)
    x = (im.width - w) // 2; y = (im.height - h) // 2
    return im.crop((x, y, x + w, y + h))
cover = Image.new("RGB", (W, H), "black")
# Hero: fog across the top
cover.paste(crop(f"{S}/shots1/fog.png", W, 300), (0, 0))
# Season strip
tiles = ["shots2/christmas.png", "shots2/leaves.png", "shots2/garden.png", "shots2/fireworks.png", "shots2/pool.png"]
tw = W // len(tiles)
for i, t in enumerate(tiles):
    cover.paste(crop(f"{S}/{t}", tw, 130), (i * tw, 370))
d = ImageDraw.Draw(cover, "RGBA")
# Dark band for the title, fading into the hero image
for y in range(230, 370):
    a = int(255 * min(1, (y - 230) / 60))
    d.line([(0, y), (W, y)], fill=(5, 5, 10, a))
title = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf", 58)
sub = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 19)
def centered(text, font, y, fill, glow=None):
    w = d.textlength(text, font=font)
    x = (W - w) / 2
    if glow:
        g = Image.new("RGBA", (W, H), (0, 0, 0, 0)); gd = ImageDraw.Draw(g)
        gd.text((x, y), text, font=font, fill=glow)
        g = g.filter(ImageFilter.GaussianBlur(8))
        cover.paste(g, (0, 0), g)
    d.text((x, y), text, font=font, fill=fill)
centered("PhantomFloor", title, 258, (220, 255, 225), glow=(124, 255, 155, 255))
centered("Interactive floor projections that react to every step", sub, 330, (200, 200, 215))
d.line([(0, 369), (W, 369)], fill=(124, 255, 155, 120), width=1)
cover.save(out)
print("saved", cover.size)
