#!/usr/bin/env python3
"""Generate deterministic MYO Premium iPhone launcher icons from vector geometry.

No generative assets at build time. Dark cinematic violet background with
solid M/Y/O glyph masks, balanced to remain legible on a small home-screen icon.
Apple itself clips icon corners: the source images must remain opaque squares.
"""
from __future__ import annotations

import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[1] / "ios" / "Movix" / "Images.xcassets" / "AppIcon.appiconset"
SIZES = (20, 29, 40, 58, 60, 76, 80, 87, 120, 152, 167, 180, 1024)
W = 1024

def clamp(a: float) -> int:
    return max(0, min(255, round(a)))

def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    # All source pixels opaque, even at image corners.
    canvas = Image.new("RGB", (W, W))
    pix = canvas.load()
    for y in range(W):
        for x in range(W):
            distance = ((x - W * .55) / (W * .92)) ** 2 + ((y - W * .56) / (W * .95)) ** 2
            violet_haze = math.exp(-distance * 4.8)
            top_glow = math.exp(-(((x - 840)/520)**2 + ((y - 32)/350)**2) * 2.3)
            bottom_glow = math.exp(-(((x - 115)/480)**2 + ((y - 885)/330)**2) * 2.0)
            pix[x,y] = (
                clamp(5 + 12*violet_haze + 15*top_glow + 17*bottom_glow),
                clamp(3 + 3*violet_haze + 2*top_glow),
                clamp(13 + 25*violet_haze + 28*top_glow + 29*bottom_glow)
            )

    # Two restrained reflections, outside the letterforms.
    ribbon = Image.new("RGBA", (W,W))
    rd = ImageDraw.Draw(ribbon)
    rd.polygon([(0,740),(0,915),(390,1024),(590,1024)], fill=(122,38,208,76))
    rd.polygon([(490,0),(1024,0),(1024,160),(740,258)], fill=(132,48,216,45))
    canvas = Image.alpha_composite(canvas.convert("RGBA"), ribbon.filter(ImageFilter.GaussianBlur(65)))

    # Single fixed-size wordmark, independent of fonts installed on the runner.
    mask = Image.new("L",(W,W))
    d = ImageDraw.Draw(mask)
    d.polygon([
        (126,650),(126,385),(168,385),(266,511),(366,385),
        (410,385),(410,650),(346,650),(346,501),(279,581),
        (213,500),(213,650)],fill=255)
    d.polygon([
        (426,385),(493,385),(538,474),(584,385),(651,385),
        (571,539),(571,650),(507,650),(507,539)],fill=255)
    d.ellipse((658,384,900,651),fill=255)
    d.ellipse((723,447,836,587),fill=0)
    glow = Image.new("RGBA",(W,W),(163,65,250,0))
    glow.putalpha(mask.filter(ImageFilter.GaussianBlur(15)).point(lambda v: int(v*.18)))
    canvas = Image.alpha_composite(canvas,glow)
    glyph = Image.new("RGBA",(W,W))
    gp = glyph.load()
    mp = mask.load()
    for y in range(375,660):
        t = (y-375)/285
        for x in range(115,912):
            if mp[x,y] == 0: continue
            highlight = .4 * math.exp(-((y-412)/48)**2)
            gp[x,y] = (
                clamp(203-77*t+28*highlight),
                clamp(176-119*t+25*highlight),
                clamp(255-20*t),
                mp[x,y],
            )
    canvas = Image.alpha_composite(canvas,glyph).convert("RGB")
    for size in SIZES:
        target = canvas if size==W else canvas.resize((size,size), Image.Resampling.LANCZOS)
        target.save(OUT / f"icon-{size}.png", format="PNG", optimize=True)
    print(f"Generated {len(SIZES)} opaque purple MYO iOS icons in {OUT}")

if __name__ == "__main__":
    main()
