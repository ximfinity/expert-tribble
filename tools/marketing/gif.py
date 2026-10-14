import glob, os, sys
from PIL import Image
src, out = sys.argv[1], sys.argv[2]
LIMIT = 2.9e6
os.makedirs(out, exist_ok=True)

def build(raw, width, step, path):
    h = round(width * 9 / 16)
    frames = [f.resize((width, h), Image.LANCZOS) for f in raw[::step]][:60]
    sample = Image.new("RGB", (width, h * 4))
    for i, k in enumerate([0, len(frames) // 3, 2 * len(frames) // 3, len(frames) - 1]):
        sample.paste(frames[k], (0, i * h))
    pal = sample.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    q = [f.quantize(palette=pal, dither=Image.Dither.NONE) for f in frames]
    q[0].save(path, save_all=True, append_images=q[1:], duration=90 * step, loop=0, optimize=True, disposal=1)
    return frames

for scene in sorted(os.listdir(src)):
    raw = [Image.open(f).convert("RGB") for f in sorted(glob.glob(f"{src}/{scene}/*.png"))]
    path = f"{out}/{scene}.gif"
    base_step = max(1, len(raw) // 60)
    for width, step in [(400, base_step), (360, base_step), (320, base_step), (320, base_step * 2)]:
        frames = build(raw, width, step, path)
        if os.path.getsize(path) < LIMIT:
            break
    sheet = Image.new("RGB", (frames[0].width * 4, frames[0].height))
    for i, k in enumerate([0, len(frames) // 3, 2 * len(frames) // 3, len(frames) - 1]):
        sheet.paste(frames[k], (i * frames[0].width, 0))
    sheet.save(f"{out}/{scene}_sheet.jpg", quality=80)
    print(f"{scene}: {width}px, {len(frames)} frames, {os.path.getsize(path)/1e6:.2f} MB")
