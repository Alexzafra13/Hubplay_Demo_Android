#!/usr/bin/env python3
"""Genera los assets gráficos que Play Store exige para HubPlay TV.

Salidas:
  - app/src/main/res/drawable-xhdpi/tv_banner.png   (320x180, banner Leanback)
  - docs/store-assets/icon-512.png                  (512x512, icono de ficha)
  - docs/store-assets/feature-graphic-1024x500.png  (gráfico de funciones)

El arte (mando de TV) se reutiliza de web/public para que el cliente Android
sea idéntico al web. La paleta sale de ui/theme/Color.kt.

Reproducible: `python3 scripts/gen_store_assets.py` (necesita Pillow).
"""
from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ─── rutas ───────────────────────────────────────────────────────────────────
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
WEB_PUBLIC = os.path.abspath(os.path.join(ROOT, "..", "HubPlay_demo", "web", "public"))
RES_XHDPI = os.path.join(ROOT, "app", "src", "main", "res", "drawable-xhdpi")
STORE = os.path.join(ROOT, "docs", "store-assets")

# ─── paleta (de ui/theme/Color.kt) ─────────────────────────────────────────────
BRAND_BG = (0x0D, 0x12, 0x20, 255)   # BrandMarkBg
BG_BASE = (0x07, 0x09, 0x0E, 255)    # BgBase
ACCENT = (0x2A, 0x9C, 0xF0, 255)     # Accent azure
ACCENT_GLOW = (0x2A, 0x9C, 0xF0)     # para el halo
WORDMARK_PLAY = (0x0D, 0xBF, 0xFF)   # cian "Play"
TEXT_PRIMARY = (0xE8, 0xEA, 0xF0)
TEXT_SECONDARY = (0x8B, 0x92, 0xA5)

FONT_BOLD = "/mnt/skills/examples/canvas-design/canvas-fonts/WorkSans-Bold.ttf"
FONT_REG = "/mnt/skills/examples/canvas-design/canvas-fonts/WorkSans-Regular.ttf"
if not os.path.exists(FONT_BOLD):
    FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def brand_tile(size: int) -> Image.Image:
    """Tile redondeado del icono (mando blanco sobre navy), reusa pwa-512.

    Idéntico al launcher icon → el banner y el feature graphic muestran
    exactamente la misma marca que ve el usuario en su TV.
    """
    src = Image.open(os.path.join(WEB_PUBLIC, "pwa-512x512.png")).convert("RGBA")
    return src.resize((size, size), Image.LANCZOS)


def rounded(img: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0], img.size[1]], radius, fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out


def vertical_gradient(size, top, bottom) -> Image.Image:
    w, h = size
    base = Image.new("RGB", size, top)
    grad = Image.new("L", (1, h))
    for y in range(h):
        grad.putpixel((0, y), int(255 * y / max(1, h - 1)))
    grad = grad.resize(size)
    bottom_img = Image.new("RGB", size, bottom)
    return Image.composite(bottom_img, base, grad)


def accent_glow(canvas: Image.Image, center, radius, color, alpha=70):
    """Pinta un halo radial accent (capa aditiva suave)."""
    w, h = canvas.size
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    cx, cy = center
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color + (alpha,))
    glow = glow.filter(ImageFilter.GaussianBlur(radius // 2))
    canvas.alpha_composite(glow)


def draw_wordmark(draw, x, y, font, scale_letter_gap=0):
    """Dibuja 'HubPlay' con 'Play' en cian. Devuelve el ancho total."""
    parts = [("Hub", TEXT_PRIMARY), ("Play", WORDMARK_PLAY)]
    cx = x
    for text, color in parts:
        draw.text((cx, y), text, font=font, fill=color)
        cx += draw.textlength(text, font=font)
    return cx - x


# ─── 1. TV banner 320x180 ──────────────────────────────────────────────────────
def gen_banner():
    W, H = 320, 180
    canvas = vertical_gradient((W, H), (0x10, 0x16, 0x28), BG_BASE[:3]).convert("RGBA")
    accent_glow(canvas, (70, H // 2), 120, ACCENT_GLOW, alpha=55)

    tile = brand_tile(96)
    my = (H - tile.size[1]) // 2
    canvas.alpha_composite(tile, (24, my))

    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype(FONT_BOLD, 37)
    # centra verticalmente el wordmark respecto al mark
    ascent, descent = font.getmetrics()
    ty = (H - (ascent + descent)) // 2 - 3
    draw_wordmark(draw, 134, ty, font)

    os.makedirs(RES_XHDPI, exist_ok=True)
    out = os.path.join(RES_XHDPI, "tv_banner.png")
    canvas.convert("RGB").save(out, "PNG")
    print("wrote", out, canvas.size)


# ─── 2. Icono 512x512 (reusa el arte web, re-render limpio) ────────────────────
def gen_icon():
    src = Image.open(os.path.join(WEB_PUBLIC, "pwa-512x512.png")).convert("RGBA")
    if src.size != (512, 512):
        src = src.resize((512, 512), Image.LANCZOS)
    os.makedirs(STORE, exist_ok=True)
    out = os.path.join(STORE, "icon-512.png")
    src.save(out, "PNG")
    print("wrote", out, src.size)


# ─── 3. Feature graphic 1024x500 ───────────────────────────────────────────────
def gen_feature():
    W, H = 1024, 500
    canvas = vertical_gradient((W, H), (0x10, 0x17, 0x2B), BG_BASE[:3]).convert("RGBA")
    accent_glow(canvas, (300, 250), 360, ACCENT_GLOW, alpha=60)
    accent_glow(canvas, (900, 120), 220, (0x0D, 0xBF, 0xFF), alpha=35)

    tile = brand_tile(248)
    canvas.alpha_composite(tile, (88, (H - tile.size[1]) // 2))

    draw = ImageDraw.Draw(canvas)
    word_font = ImageFont.truetype(FONT_BOLD, 112)
    tag_font = ImageFont.truetype(FONT_REG, 33)

    tx = 372
    ty = 158
    draw_wordmark(draw, tx, ty, word_font)
    draw.text((tx + 4, ty + 146), "Tu servidor de cine y TV, en tu televisor.",
              font=tag_font, fill=TEXT_SECONDARY)

    os.makedirs(STORE, exist_ok=True)
    out = os.path.join(STORE, "feature-graphic-1024x500.png")
    canvas.convert("RGB").save(out, "PNG")
    print("wrote", out, canvas.size)


if __name__ == "__main__":
    gen_banner()
    gen_icon()
    gen_feature()
    print("done")
