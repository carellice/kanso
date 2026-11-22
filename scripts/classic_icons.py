"""Ritaglia le tavole 4x3 di branding/classic in 60 maschere alpha (bianco + trasparenza) per res/drawable-nodpi.

Uso: python scripts/classic_icons.py   (dalla radice del progetto)
"""
from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parent.parent
SHEETS = {"kanso": "kanso-sheet-v2", "skeuo": "skeuo-sheet-v2", "metro": "metro-sheet", "nothing": "nothing-sheet", "dueffe": "dueffe-sheet"}
SLOTS = ["phone", "messages", "camera", "gallery", "maps", "music", "browser", "mail", "files", "calculator", "calendar", "alarm"]
SIZE, FILL = 192, 0.9
OUT = ROOT / "app/src/main/res/drawable-nodpi"


def runs(profile, n):
    """Gli n tratti più lunghi di valori non nulli: le colonne (o righe) di icone separate da spazi vuoti."""
    spans, start = [], None
    for i, v in enumerate(list(profile) + [0]):
        if v and start is None:
            start = i
        elif not v and start is not None:
            spans.append((start, i)); start = None
    spans = sorted(sorted(spans, key=lambda s: s[1] - s[0])[-n:])
    assert len(spans) == n, f"attesi {n} gruppi, trovati {len(spans)}"
    return spans


def slice_sheet(path):
    ink = ImageOps.invert(Image.open(path).convert("L")).point(lambda v: 0 if v < 40 else v)
    w, h = ink.size
    px = ink.load()
    cols = runs([sum(px[x, y] > 0 for y in range(0, h, 2)) > 1 for x in range(w)], 4)
    rows = runs([sum(px[x, y] > 0 for x in range(0, w, 2)) > 1 for y in range(h)], 3)
    for r, (y0, y1) in enumerate(rows):
        for c, (x0, x1) in enumerate(cols):
            cell = ink.crop((x0, y0, x1, y1))
            cell = cell.crop(cell.getbbox())
            k = SIZE * FILL / max(cell.size)
            cell = cell.resize((max(1, round(cell.width * k)), max(1, round(cell.height * k))), Image.LANCZOS)
            mask = Image.new("L", (SIZE, SIZE)); mask.paste(cell, ((SIZE - cell.width) // 2, (SIZE - cell.height) // 2))
            icon = Image.new("RGBA", (SIZE, SIZE), (255, 255, 255, 0)); icon.putalpha(mask)
            yield r * 4 + c, icon


OUT.mkdir(parents=True, exist_ok=True)
for style, sheet in SHEETS.items():
    icons = dict(slice_sheet(ROOT / f"branding/classic/classic-{sheet}.png"))
    assert len(icons) == 12
    for i, icon in icons.items():
        icon.save(OUT / f"classic_{style}_{SLOTS[i]}.png", optimize=True)
print("ok", len(list(OUT.glob("classic_*.png"))))
