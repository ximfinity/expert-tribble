import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont
S, O = sys.argv[1], sys.argv[2]
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
GREEN = (124, 255, 155)

def glow_text(img, xy, text, font, fill, glow, radius=10):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, text, font=font, fill=glow)
    layer = layer.filter(ImageFilter.GaussianBlur(radius))
    img.paste(layer, (0, 0), layer)
    ImageDraw.Draw(img).text(xy, text, font=font, fill=fill)

def feature(src, out, headline, tag):
    im = Image.open(src).convert("RGB").resize((1280, 720), Image.LANCZOS)
    d = ImageDraw.Draw(im, "RGBA")
    for y in range(470, 720):  # gradient band so text is readable on any scene
        a = int(235 * min(1, (y - 470) / 140))
        d.line([(0, y), (1280, y)], fill=(4, 4, 8, a))
    hf = ImageFont.truetype(BOLD, 54)
    tf = ImageFont.truetype(REG, 26)
    glow_text(im, (60, 590), headline, hf, (255, 255, 255), (0, 0, 0, 255), 6)
    d = ImageDraw.Draw(im, "RGBA")
    d.text((62, 660), tag, font=tf, fill=GREEN + (255,))
    im.save(out, quality=90)

H = f"{S}/hd"
items = [
    ("shots1/fog", "Fog that parts around your feet", "Ectoplasmic Fog · Halloween"),
    ("shots1/swarm", "Hundreds of spiders flee every step", "The Swarm · Halloween"),
    ("shots1/water", "Monsters rise out of the water", "Living Water · Halloween"),
    ("shots2/smoke", "Ground smoke that flows like the real thing", "Ground Smoke · Halloween"),
    ("shots2/christmas", "Snow that remembers your footprints", "Christmas · Winter"),
    ("shots2/hanukkah", "A menorah lit for every night", "Hanukkah · Winter"),
    ("shots2/leaves", "Kick through a carpet of autumn leaves", "Fall Leaves · Fall"),
    ("shots2/garden", "Flowers bloom wherever you walk", "Spring Garden · Spring"),
    ("shots2/fireworks", "A firework bursts under every step", "Fireworks · 4th of July"),
    ("shots2/fireflies", "Fireflies follow you through the night", "Fireflies · Summer"),
    ("shots2/pool", "Make a splash, no towel needed", "Pool Splash · Summer"),
    ("shots1/keystone_edit", "Fits any floor: just drag the corners", "Built-in keystone correction"),
]
for i, (src, head, tag) in enumerate(items, 1):
    feature(f"{H}/{src}.png", f"{O}/features/{i:02d}-{src.split('/')[1]}.jpg", head, tag)

# Banner (960 x 240): title over a strip of seasons
banner = Image.new("RGB", (960, 240), (7, 7, 11))
strip = ["shots1/fog", "shots1/swarm", "shots2/christmas", "shots2/garden", "shots2/fireworks", "shots2/pool"]
tw = 960 // len(strip)
for i, s in enumerate(strip):
    im = Image.open(f"{H}/{s}.png").convert("RGB")
    r = 240 / im.height
    im = im.resize((int(im.width * r), 240), Image.LANCZOS)
    x0 = (im.width - tw) // 2
    banner.paste(im.crop((x0, 0, x0 + tw, 240)), (i * tw, 0))
d = ImageDraw.Draw(banner, "RGBA")
d.rectangle([0, 0, 960, 240], fill=(4, 4, 8, 120))
tf = ImageFont.truetype(SERIF, 64)
w = d.textlength("PhantomFloor", font=tf)
glow_text(banner, ((960 - w) / 2, 62), "PhantomFloor", tf, (220, 255, 225), GREEN + (255,), 10)
d = ImageDraw.Draw(banner, "RGBA")
sf = ImageFont.truetype(REG, 22)
sub = "Interactive floor projections for every season"
d.text(((960 - d.textlength(sub, font=sf)) / 2, 150), sub, font=sf, fill=(225, 225, 235))
banner.save(f"{O}/banner.png")

# "How it works" graphic (1280 x 520)
hw = Image.new("RGB", (1280, 520), (9, 9, 14))
d = ImageDraw.Draw(hw, "RGBA")
title = ImageFont.truetype(BOLD, 40)
body = ImageFont.truetype(REG, 22)
num = ImageFont.truetype(BOLD, 30)
t = "How it works"
d.text(((1280 - d.textlength(t, font=title)) / 2, 30), t, font=title, fill=(255, 255, 255))
steps = [
    ("1", "Install on Android", "Any phone or tablet\nwith Android 8.0+"),
    ("2", "Cast to a projector", "Screen mirroring, Miracast\nor an HDMI adapter"),
    ("3", "Fit it to the floor", "Drag 4 corners;\noptional camera calibration"),
    ("4", "Walk on it", "The camera finds feet;\nevery scene reacts"),
]
cw = 1280 // 4
for i, (n, head, text) in enumerate(steps):
    cx = i * cw + cw // 2
    d.ellipse([cx - 42, 130, cx + 42, 214], outline=GREEN + (255,), width=4)
    d.text((cx - d.textlength(n, font=num) / 2, 152), n, font=num, fill=GREEN + (255,))
    hf = ImageFont.truetype(BOLD, 26)
    d.text((cx - d.textlength(head, font=hf) / 2, 250), head, font=hf, fill=(255, 255, 255))
    for j, line in enumerate(text.split("\n")):
        d.text((cx - d.textlength(line, font=body) / 2, 300 + j * 32), line, font=body, fill=(190, 190, 205))
    if i < 3:
        ax = cx + cw // 2
        d.line([(ax - 30, 172), (ax + 30, 172)], fill=(124, 255, 155, 160), width=3)
        d.polygon([(ax + 30, 164), (ax + 44, 172), (ax + 30, 180)], fill=(124, 255, 155, 160))
foot = "Black means no light, so in a dark room the effects look like they float on the floor."
d.text(((1280 - d.textlength(foot, font=body)) / 2, 440), foot, font=body, fill=(150, 150, 165))
hw.save(f"{O}/how-it-works.png")
print("done")
