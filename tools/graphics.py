"""Draws the Play Store icon (512x512) and feature graphics (1024x500, EN and FR).

The remote is the launcher icon's foreground (app/src/main/res/drawable/ic_launcher_foreground.xml),
drawn from the same 108-unit geometry. Text is set in Roboto (SIL Open Font License).

Run from the repository root:
    python3 -m venv build/venv && build/venv/bin/pip install pillow
    curl -sSfL -o build/Roboto.ttf 'https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/Roboto%5Bwdth,wght%5D.ttf'
    build/venv/bin/python tools/graphics.py
"""
from PIL import Image, ImageDraw, ImageFont

NAVY = (0x1E, 0x2A, 0x44)
NAVY_LIGHT = (0x2E, 0x40, 0x6B)
WHITE = (255, 255, 255)
SS = 4  # supersampling factor
META = "fastlane/metadata/android"


def draw_remote(draw, ox, oy, unit):
    """The launcher foreground's remote, with its 108-unit viewport origin at (ox, oy)."""
    def box(x0, y0, x1, y1):
        return [ox + x0 * unit, oy + y0 * unit, ox + x1 * unit, oy + y1 * unit]

    draw.rounded_rectangle(box(38, 26, 70, 82), radius=6 * unit, fill=WHITE)
    draw.ellipse(box(47, 29, 61, 43), fill=NAVY)
    for y in (52, 60, 68):
        for x in (47, 57):
            draw.rectangle(box(x, y, x + 4, y + 4), fill=NAVY)


def gradient(size, top, bottom):
    w, h = size
    image = Image.new("RGB", size)
    draw = ImageDraw.Draw(image)
    for y in range(h):
        t = y / max(h - 1, 1)
        draw.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return image


def icon():
    size = 512 * SS
    image = Image.new("RGBA", (size, size), NAVY + (255,))
    draw_remote(ImageDraw.Draw(image), 0, 0, size / 108)
    return image.resize((512, 512), Image.LANCZOS)


def font(size, weight):
    f = ImageFont.truetype("build/Roboto.ttf", size * SS)
    f.set_variation_by_name(weight)
    return f


def feature_graphic(tagline):
    w, h = 1024 * SS, 500 * SS
    image = gradient((w, h), NAVY, NAVY_LIGHT)
    draw = ImageDraw.Draw(image)
    unit = h / 108 * 0.95
    draw_remote(draw, 70 * SS - 38 * unit + 60 * SS, (h - 108 * unit) / 2, unit)
    x = 400 * SS
    draw.text((x, 150 * SS), "Webmote", font=font(112, "Bold"), fill=WHITE)
    y = 300 * SS
    for line in tagline:
        draw.text((x, y), line, font=font(38, "Regular"), fill=(0xD6, 0xDE, 0xF2))
        y += 52 * SS
    return image.resize((1024, 500), Image.LANCZOS)


icon().save(f"{META}/en-US/images/icon.png")
icon().save(f"{META}/fr-FR/images/icon.png")
feature_graphic(["Your TV remote,", "right on your phone."]).save(f"{META}/en-US/images/featureGraphic.png")
feature_graphic(["La télécommande de votre TV,", "sur votre téléphone."]).save(f"{META}/fr-FR/images/featureGraphic.png")
print("icon and feature graphics written")
