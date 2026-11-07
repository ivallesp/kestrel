"""Makes the Google Play listing images from the screenshot tests' images and the launcher icon's source.

Play wants phone screenshots no more than twice as tall as they are wide, and 1080 x 1920 to be eligible for
promotion; the test images are 1078 x 2422. Each one is framed on Kestrel's page colour with a caption above it.
The feature graphic (1024 x 500) and the 512 px icon are drawn from the same colours and art.

Usage, from the repository root (needs Pillow, and ImageMagick's `convert` for the icon):
    python3 tools/play/store_images.py fastlane/metadata/android/en-GB/images
"""

import json
import pathlib
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
SHOTS = ROOT / "app/src/test/screenshots"
SERIF = ROOT / "app/src/main/res/font/newsreader.ttf"
SANS = ROOT / "app/src/main/res/font/google_sans_flex.ttf"
PAGE = (245, 240, 231)
INK = (31, 27, 22)
INK_SOFT = (96, 89, 80)

# (test image, caption): the order of the listing.
SCREENSHOTS = [
    ("S1-home-light", "Your auctions, soonest first"),
    ("S2-add-light", "Set your max. Kestrel bids it in the last seconds."),
    ("S3-live-light", "A dry run first, then one bid on the second"),
    ("S4-won-light", "Won or lost, in eBay's own words"),
    ("celebration-mantle-light", "And when you win, a kestrel celebrates"),
    ("S5-ready-all-good-light", "It checks your phone is ready to bid"),
]


def wrap(draw, text, font, width):
    """Splits the caption into the fewest lines that fit, with the lines as even as possible (no lone last word)."""
    words = text.split()
    if draw.textlength(text, font=font) <= width:
        return [text]
    best = None
    for cut in range(1, len(words)):
        lines = [" ".join(words[:cut]), " ".join(words[cut:])]
        widest = max(draw.textlength(line, font=font) for line in lines)
        if widest <= width and (best is None or widest < best[0]):
            best = (widest, lines)
    return best[1] if best else [text]


def rounded(image, radius):
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, *image.size), radius, fill=255)
    return mask


def screenshot(name, caption, out):
    canvas = Image.new("RGB", (1080, 1920), PAGE)
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype(str(SERIF), 66)
    y = 96
    for line in wrap(draw, caption, font, 940):
        draw.text((540, y), line, font=font, fill=INK, anchor="ma")
        y += 80
    phone = Image.open(SHOTS / f"{name}.png").convert("RGB")
    height = 1920 - 330 - 56
    phone = phone.resize((round(phone.width * height / phone.height), height), Image.LANCZOS)
    left, top = (1080 - phone.width) // 2, 330
    shadow = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle((left, top + 10, left + phone.width, top + phone.height + 10), 40, fill=70)
    canvas.paste(Image.new("RGB", canvas.size, (150, 138, 120)), (0, 0), shadow.filter(ImageFilter.GaussianBlur(18)))
    canvas.paste(phone, (left, top), rounded(phone, 40))
    canvas.save(out, optimize=True)


def icon(out):
    with tempfile.TemporaryDirectory() as work:
        subprocess.run([sys.executable, "-I", str(ROOT / "design/kestrel_icon.py"), work], check=True)
        layers = json.loads(pathlib.Path(work, "preview.json").read_text())["layers"]
        defs, paths = [], []
        for index, layer in enumerate(layers):
            colour = layer["c"]
            if isinstance(colour, list):
                defs.append(
                    f'<linearGradient id="g{index}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" '
                    f'stop-color="{colour[1]}"/><stop offset="1" stop-color="{colour[2]}"/></linearGradient>'
                )
                colour = f"url(#g{index})"
            paths.append(f'<path d="{layer["d"]}" fill="{colour}"/>')
        svg = pathlib.Path(work, "icon.svg")
        svg.write_text(
            '<svg xmlns="http://www.w3.org/2000/svg" width="2048" height="2048" viewBox="0 0 108 108">'
            f'<defs>{"".join(defs)}</defs>{"".join(paths)}</svg>'
        )
        subprocess.run(["convert", "-background", "none", str(svg), "-resize", "512x512", f"PNG32:{out}"], check=True)


def feature_graphic(icon_path, out):
    canvas = Image.new("RGB", (1024, 500), PAGE)
    art = Image.open(icon_path).convert("RGBA").resize((300, 300), Image.LANCZOS)
    circle = Image.new("L", art.size, 0)
    ImageDraw.Draw(circle).ellipse((0, 0, *art.size), fill=255)
    canvas.paste(art, (96, 100), circle)
    draw = ImageDraw.Draw(canvas)
    draw.text((460, 128), "Kestrel", font=ImageFont.truetype(str(SERIF), 132), fill=INK)
    draw.text((464, 290), "Your bid, in the last seconds.", font=ImageFont.truetype(str(SANS), 38), fill=INK)
    draw.text((464, 344), "No ads. No fees. No server.", font=ImageFont.truetype(str(SANS), 32), fill=INK_SOFT)
    canvas.save(out, optimize=True)


def main(out_dir):
    out = pathlib.Path(out_dir)
    (out / "phoneScreenshots").mkdir(parents=True, exist_ok=True)
    for index, (name, caption) in enumerate(SCREENSHOTS, start=1):
        screenshot(name, caption, out / "phoneScreenshots" / f"{index}.png")
    icon(out / "icon.png")
    feature_graphic(out / "icon.png", out / "featureGraphic.png")


if __name__ == "__main__":
    main(sys.argv[1])
