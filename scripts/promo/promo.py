"""Video promozionale di Kanso (1920x1080, 30 fps): uno con musica e uno muto.

Uso: python scripts/promo/promo.py [cartella di uscita]
Serve: Python 3 con Pillow e numpy, ffmpeg nel PATH. La musica e' sintetizzata qui, niente file esterni.
"""
import math
import subprocess
import sys
import wave
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else ROOT / "scripts" / "promo" / "out"
W, H, FPS, DURATION = 1920, 1080, 30, 40.0

INK = (14, 13, 11)
PAPER = (241, 233, 218)
MUTED = (154, 145, 131)
FAINT = (94, 87, 77)
SHU = (217, 98, 59)

FONT_DIR = ROOT / "app" / "src" / "main" / "res" / "font"
SHOTS = ROOT / "docs" / "screenshots"


# ---------- tempo e curve ----------

def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def calm(x):
    """Stessa idea di Motion.Calm dell'app: parte decisa e si posa piano."""
    x = clamp(x)
    return 1 - (1 - x) ** 4


def smooth(x):
    x = clamp(x)
    return x * x * (3 - 2 * x)


def env(t, start, end, fade_in=0.6, fade_out=0.5):
    """0 prima di start, sale, resta a 1, scende prima di end."""
    if t < start or t > end:
        return 0.0
    return min(calm((t - start) / fade_in), smooth((end - t) / fade_out))


def lerp(a, b, x):
    return a + (b - a) * x


# ---------- font e testo ----------

@lru_cache(None)
def font(kind, size, weight=400):
    if kind == "serif":
        return ImageFont.truetype(str(FONT_DIR / "serif.ttf"), size)
    if kind == "jp":
        return ImageFont.truetype("C:/Windows/Fonts/YuGothL.ttc", size) if Path("C:/Windows/Fonts/YuGothL.ttc").exists() else font("sans", size, 300)
    f = ImageFont.truetype(str(FONT_DIR / "inter.ttf"), size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass  # Pillow senza libraqm/FreeType variabile: resta il peso di default
    return f


@lru_cache(None)
def text_layer(text, kind, size, color, weight=400, tracking=0):
    f = font(kind, size, weight)
    pad = 40
    if tracking:
        widths = [f.getlength(c) + tracking for c in text]
        w = int(sum(widths)) + pad * 2
    else:
        w = int(f.getlength(text)) + pad * 2
    asc, desc = f.getmetrics()
    img = Image.new("RGBA", (w, asc + desc + pad * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if tracking:
        x = pad
        for c, cw in zip(text, widths):
            d.text((x, pad), c, font=f, fill=color + (255,))
            x += cw
    else:
        d.text((pad, pad), text, font=f, fill=color + (255,))
    return img


def with_alpha(img, a):
    if a >= 0.999:
        return img
    out = img.copy()
    out.putalpha(out.getchannel("A").point(lambda v: int(v * a)))
    return out


def put(frame, layer, x, y, alpha=1.0, blur=0.0, anchor="mm"):
    """Incolla un livello RGBA centrato (mm) o allineato a sinistra (lm) su (x, y)."""
    if alpha <= 0.003:
        return
    if blur > 0.3:
        layer = layer.filter(ImageFilter.GaussianBlur(blur))
    layer = with_alpha(layer, alpha)
    w, h = layer.size
    px = int(x - w / 2) if anchor[0] == "m" else int(x - 40)
    py = int(y - h / 2)
    frame.alpha_composite(layer, (max(px, 0), max(py, 0)), (max(-px, 0), max(-py, 0)))


def reveal(frame, text, kind, size, color, x, y, p, alpha=1.0, weight=400, anchor="mm", tracking=0, rise=26):
    """Entrata stile keynote: sale un poco, si mette a fuoco, si accende."""
    e = calm(p)
    put(frame, text_layer(text, kind, size, color, weight, tracking), x, y + (1 - e) * rise, alpha * e, (1 - e) * 10, anchor)


# ---------- sfondo ----------

@lru_cache(None)
def radial(size, color, strength):
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    r = np.hypot(xx - size / 2, yy - size / 2) / (size / 2)
    a = np.clip(1 - r, 0, 1) ** 2.2 * strength * 255
    img = np.zeros((size, size, 4), np.uint8)
    img[..., :3] = color
    img[..., 3] = a.astype(np.uint8)
    return Image.fromarray(img, "RGBA")


GLOW = radial(1500, SHU, 0.16)
GLOW_SOFT = radial(1700, PAPER, 0.035)


def background(t):
    frame = Image.new("RGBA", (W, H), INK + (255,))
    gx = W * (0.72 - 0.44 * smooth(t / DURATION)) + math.sin(t * 0.35) * 60
    gy = H * 0.22 + math.cos(t * 0.27) * 40
    frame.alpha_composite(GLOW, (int(gx - 750), int(gy - 750)))
    frame.alpha_composite(GLOW_SOFT, (int(W - gx - 850), int(H * 0.95 - 850)))
    return frame


# ---------- ensō a pennello ----------

def enso_layer(progress, diameter, tail=True):
    """Cerchio zen tracciato a pennello: spesso all'inizio, sfilacciato in coda."""
    ss = 2
    size = int(diameter * 1.25) * ss
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    r = diameter / 2 * ss
    base = diameter * 0.075 * ss
    steps = int(520 * progress)
    rng = np.random.default_rng(7)
    jitter = rng.normal(0, 1, 600)
    for i in range(steps):
        s = i / 520
        ang = math.radians(-105 + 325 * s)
        rr = r * (1 + 0.018 * math.sin(s * 9) + 0.004 * jitter[i])
        w = base * (0.55 + 0.6 * math.sin(math.pi * min(s * 3.5, 1)) * (1 - s) ** 0.8 + 0.18 * (1 - s))
        x, y = c + rr * math.cos(ang), c + rr * math.sin(ang)
        d.ellipse((x - w, y - w, x + w, y + w), fill=SHU + (255,))
        # Setole asciutte in coda: righe sottili che si staccano dal tratto.
        if tail and s > 0.62:
            for k, off in enumerate((-0.8, 0.85)):
                if (i + k * 3) % 7 < 4:
                    ox, oy = c + (rr + off * w) * math.cos(ang), c + (rr + off * w) * math.sin(ang)
                    bw = w * 0.22
                    d.ellipse((ox - bw, oy - bw, ox + bw, oy + bw), fill=SHU + (200,))
    return img.resize((size // ss, size // ss), Image.LANCZOS)


ENSO_DONE = {}


def enso(progress, diameter):
    if progress >= 1:
        key = diameter
        if key not in ENSO_DONE:
            ENSO_DONE[key] = enso_layer(1, diameter)
        return ENSO_DONE[key]
    return enso_layer(progress, diameter)


# ---------- telefono ----------

def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    return m


@lru_cache(None)
def phone(name, screen_w=420):
    src = Image.open(SHOTS / name if (SHOTS / name).exists() else name).convert("RGB")
    sh = int(screen_w * src.height / src.width)
    screen = src.resize((screen_w, sh), Image.LANCZOS)
    bezel, pad = 13, 70
    bw, bh = screen_w + bezel * 2, sh + bezel * 2
    out = Image.new("RGBA", (bw + pad * 2, bh + pad * 2), (0, 0, 0, 0))
    shadow = Image.new("RGBA", out.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((pad + 10, pad + 40, pad + bw - 10, pad + bh + 10), 64, fill=(0, 0, 0, 190))
    out.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(34)))
    body = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    bd.rounded_rectangle((0, 0, bw - 1, bh - 1), 64, fill=(24, 22, 19, 255), outline=(70, 64, 56, 255), width=2)
    body.paste(screen, (bezel, bezel), rounded_mask(screen.size, 52))
    # Riflesso leggerissimo sul vetro.
    gloss = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    ImageDraw.Draw(gloss).polygon([(0, 0), (bw * 0.55, 0), (0, bh * 0.38)], fill=(255, 255, 255, 10))
    body.alpha_composite(gloss)
    out.alpha_composite(body, (pad, pad))
    return out


def put_phone(frame, name, cx, cy, scale=1.0, alpha=1.0, screen_w=420):
    img = phone(name, screen_w)
    if abs(scale - 1) > 0.002:
        img = img.resize((int(img.width * scale), int(img.height * scale)), Image.BILINEAR)
    put(frame, img, cx, cy, alpha)


# ---------- scene ----------

def scene_intro(f, t):
    # 0 - 4.6: l'ensō si disegna, poi il nome.
    a = env(t, 0.0, 4.6, 0.3, 0.6)
    if a <= 0:
        return
    p = calm((t - 0.25) / 2.1)
    put(f, enso(p, 360), W / 2, H / 2 - 40, a)
    reveal(f, "Kanso", "serif", 92, PAPER, W / 2, H / 2 + 230, (t - 2.0) / 0.9, a)
    reveal(f, "簡素", "jp", 30, MUTED, W / 2, H / 2 + 305, (t - 2.5) / 0.9, a, tracking=14)


def scene_words(f, t):
    # 4.6 - 10.8: tre frasi, una alla volta.
    lines = [("Meno app.", PAPER, 4.7), ("Meno rumore.", PAPER, 6.7), ("Più tempo per te.", SHU, 8.7)]
    for text, color, start in lines:
        a = env(t, start, start + 2.0, 0.5, 0.45)
        if a > 0:
            reveal(f, text, "serif", 130, color, W / 2, H / 2, (t - start) / 0.7, a, rise=34)


def caption(f, t, start, end, eyebrow, title, body, x, y, anchor="lm"):
    a = env(t, start, end, 0.6, 0.5)
    if a <= 0:
        return
    reveal(f, eyebrow.upper(), "sans", 20, MUTED, x, y - 120, (t - start) / 0.8, a, weight=600, anchor=anchor, tracking=4)
    for i, line in enumerate(title):
        reveal(f, line, "serif", 76, PAPER, x, y - 40 + i * 86, (t - start - 0.12 - i * 0.1) / 0.9, a, anchor=anchor)
    for i, line in enumerate(body):
        reveal(f, line, "sans", 28, MUTED, x, y + 30 + len(title) * 86 + i * 42, (t - start - 0.4) / 0.9, a, anchor=anchor)


def scene_home(f, t):
    # 10.8 - 16.8
    s, e = 10.8, 16.8
    a = env(t, s, e, 0.8, 0.5)
    if a <= 0:
        return
    p = calm((t - s) / 1.4)
    put_phone(f, "orologio.jpg", 1300, H / 2 + 20 + (1 - p) * 260 + math.sin(t * 1.1) * 6, lerp(0.92, 1.0, p), a)
    caption(f, t, s + 0.3, e, "Home", ["Solo", "l'essenziale."], ["Pochi riquadri, tutti uguali.", "L'orologio solo se lo vuoi."], 260, H / 2 - 60)


def scene_pages(f, t):
    # 16.8 - 23.4: tre schermate fianco a fianco.
    s, e = 16.8, 23.4
    a = env(t, s, e, 0.6, 0.5)
    if a <= 0:
        return
    reveal(f, "Tre schermate. Nessun cassetto.", "serif", 64, PAPER, W / 2, 120, (t - s) / 0.9, a)
    items = [("agenda.jpg", "Consapevolezza", -1), ("orologio.jpg", "Home", 0), ("libreria.jpg", "Libreria", 1)]
    for name, label, k in items:
        delay = 0.25 + abs(k) * 0.25
        p = calm((t - s - delay) / 1.3)
        cx = W / 2 + k * 470 * lerp(0.6, 1, p)
        cy = H / 2 + 70 + (1 - p) * 120
        focus = 1.0 if k == 0 else 0.88
        put_phone(f, name, cx, cy, lerp(0.9, focus, p), a * p, screen_w=330)
        reveal(f, label, "sans", 24, PAPER if k == 0 else MUTED, cx, H - 52, (t - s - delay - 0.6) / 0.8, a, weight=500)


def scene_breath(f, t):
    # 23.4 - 29.6: attrito consapevole con un respiro dietro al telefono.
    s, e = 23.4, 29.6
    a = env(t, s, e, 0.8, 0.5)
    if a <= 0:
        return
    b = 0.5 - 0.5 * math.cos((t - s) * 2 * math.pi / 4.0)
    ring = Image.new("RGBA", (900, 900), (0, 0, 0, 0))
    rr = lerp(230, 400, b)
    ring.alpha_composite(radial(900, SHU, 0.3).resize((int(rr * 2.3), int(rr * 2.3))), (int(450 - rr * 1.15), int(450 - rr * 1.15)))
    ImageDraw.Draw(ring).ellipse((450 - rr, 450 - rr, 450 + rr, 450 + rr), outline=SHU + (170,), width=3)
    put(f, ring, 620, H / 2 + 10, a)
    p = calm((t - s) / 1.3)
    put_phone(f, "attrito.jpg", 620, H / 2 + 20 + (1 - p) * 200, lerp(0.9, 0.98, p), a * p)
    caption(f, t, s + 0.4, e, "Attrito consapevole", ["Un respiro", "prima di aprire."], ["Social, video e giochi aspettano un po' di più.", "Le altre app la metà."], 1060, H / 2 - 60)


def scene_search(f, t):
    # 29.6 - 35.0
    s, e = 29.6, 35.0
    a = env(t, s, e, 0.7, 0.5)
    if a <= 0:
        return
    p = calm((t - s) / 1.3)
    put_phone(f, "ricerca.jpg", 1300, H / 2 + 20 + (1 - p) * 240, lerp(0.92, 1.0, p), a)
    caption(f, t, s + 0.3, e, "Ricerca universale", ["Tutto, da un", "solo posto."], ["App, contatti, indirizzi e siti.", "Scorri giù per il riassunto delle notifiche."], 260, H / 2 - 60)


def scene_outro(f, t):
    # 35.0 - 40.0
    a1 = env(t, 35.0, 37.0, 0.5, 0.4)
    if a1 > 0:
        reveal(f, "Open source. Nessun tracciamento.", "serif", 84, PAPER, W / 2, H / 2, (t - 35.0) / 0.7, a1)
    a2 = env(t, 37.0, 41.0, 0.6, 0.1)
    if a2 > 0:
        p = calm((t - 37.0) / 1.0)
        put(f, enso(1, 200), W / 2, H / 2 - 120, a2 * p)
        reveal(f, "Kanso", "serif", 84, PAPER, W / 2, H / 2 + 50, (t - 37.2) / 0.9, a2)
        reveal(f, "Il launcher che toglie il superfluo.", "sans", 30, MUTED, W / 2, H / 2 + 125, (t - 37.5) / 0.9, a2)
        reveal(f, "github.com/carellice/kanso", "sans", 24, SHU, W / 2, H / 2 + 200, (t - 37.9) / 0.9, a2, weight=500)


SCENES = [scene_intro, scene_words, scene_home, scene_pages, scene_breath, scene_search, scene_outro]


def render_frame(t):
    f = background(t)
    for s in SCENES:
        s(f, t)
    fade = min(smooth(t / 0.4), smooth((DURATION - t) / 0.8))
    if fade < 1:
        f = Image.blend(Image.new("RGBA", (W, H), (0, 0, 0, 255)), f, fade)
    return f.convert("RGB")


# ---------- musica ----------

SR = 48000


def note(name):
    names = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}
    return 440 * 2 ** ((names[name[:-1]] + 12 * (int(name[-1]) + 1) - 69) / 12)


def music():
    n = int(DURATION * SR)
    t = np.arange(n) / SR
    out = np.zeros((n, 2))

    def add(sig, start, pan=0.0):
        i = int(start * SR)
        sig = sig[: n - i]
        out[i : i + len(sig), 0] += sig * (1 - pan) ** 0.5
        out[i : i + len(sig), 1] += sig * (1 + pan) ** 0.5

    # Tappeto: accordi lenti, quasi senza attacco.
    chords = [
        (0.0, ["D3", "A3", "C#4", "E4", "F#4"]),
        (6.0, ["B2", "F#3", "A3", "D4", "E4"]),
        (11.0, ["G2", "D3", "F#3", "A3", "B3"]),
        (17.0, ["D3", "A3", "C#4", "E4", "F#4"]),
        (23.0, ["E3", "B3", "D4", "F#4", "A4"]),
        (29.0, ["G2", "D3", "F#3", "B3", "E4"]),
        (35.0, ["D3", "A3", "D4", "E4", "F#4"]),
    ]
    for k, (start, notes) in enumerate(chords):
        end = chords[k + 1][0] if k + 1 < len(chords) else DURATION
        length = end - start + 2.5
        tt = np.arange(int(length * SR)) / SR
        amp = np.minimum(1, tt / 2.0) * np.clip((length - tt) / 2.5, 0, 1)
        for j, nm in enumerate(notes):
            fq = note(nm)
            s = np.zeros_like(tt)
            for det, ph in ((0.9985, 0.0), (1.0, 1.3), (1.0016, 2.1)):
                s += np.sin(2 * np.pi * fq * det * tt + ph) + 0.18 * np.sin(4 * np.pi * fq * det * tt + ph)
            s *= 0.5 + 0.5 * np.sin(2 * np.pi * (0.11 + j * 0.03) * tt + j)  # respiro lento
            add(s * amp * 0.016, start, pan=(j - 2) * 0.25)

    # Note di campana sui cambi di scena.
    def bell(fq, dur=3.5):
        tt = np.arange(int(dur * SR)) / SR
        e = np.exp(-tt * 1.6) * np.minimum(1, tt / 0.004)
        return (np.sin(2 * np.pi * fq * tt) + 0.35 * np.sin(2 * np.pi * fq * 2.76 * tt) * np.exp(-tt * 3) + 0.2 * np.sin(2 * np.pi * fq * 5.4 * tt) * np.exp(-tt * 6)) * e

    for start, nm, pan in [
        (2.0, "A5", 0.0), (4.7, "F#5", -0.3), (6.7, "E5", 0.3), (8.7, "D6", 0.0),
        (11.1, "A5", -0.2), (17.1, "C#6", 0.2), (23.8, "B5", -0.2), (29.9, "E6", 0.2),
        (35.0, "F#5", 0.0), (37.2, "A5", -0.1), (37.25, "D6", 0.1),
    ]:
        add(bell(note(nm)) * 0.075, start, pan)

    # Colpo basso e morbido sulle due comparse del nome.
    def boom(dur=2.5):
        tt = np.arange(int(dur * SR)) / SR
        fq = 55 * np.exp(-tt * 0.6) + 32
        return np.sin(2 * np.pi * np.cumsum(fq) / SR) * np.exp(-tt * 2.2) * np.minimum(1, tt / 0.01)

    add(boom() * 0.35, 2.0)
    add(boom() * 0.3, 37.2)

    # Riverbero: convoluzione con una coda di rumore che si spegne.
    rng = np.random.default_rng(3)
    ir_t = np.arange(int(2.8 * SR)) / SR
    for ch in range(2):
        ir = rng.normal(0, 1, len(ir_t)) * np.exp(-ir_t * 2.3)
        ir[0] = 0
        wet = np.fft.irfft(np.fft.rfft(out[:, ch], n + len(ir)) * np.fft.rfft(ir, n + len(ir)))[:n]
        out[:, ch] = out[:, ch] + wet * 0.012
    out *= np.minimum(1, t / 0.3)[:, None] * np.clip((DURATION - t) / 2.0, 0, 1)[:, None]
    out /= np.abs(out).max() / 0.8
    return (out * 32767).astype(np.int16)


def write_wav(path, data):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


# ---------- uscita ----------

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    silent = OUT / "kanso-promo-muto.mp4"
    with_audio = OUT / "kanso-promo.mp4"
    wav = OUT / "kanso-promo.wav"

    ff = subprocess.Popen(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
         "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(silent)],
        stdin=subprocess.PIPE,
    )
    frames = int(DURATION * FPS)
    for i in range(frames):
        ff.stdin.write(render_frame(i / FPS).tobytes())
        if i % 60 == 0:
            print(f"fotogramma {i}/{frames}", flush=True)
    ff.stdin.close()
    if ff.wait() != 0:
        sys.exit("ffmpeg non riuscito (video)")

    write_wav(wav, music())
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(silent), "-i", str(wav), "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                        "-shortest", "-movflags", "+faststart", str(with_audio)])
    wav.unlink(missing_ok=True)
    if r.returncode != 0:
        sys.exit("ffmpeg non riuscito (audio)")
    print(f"Fatto: {with_audio}\n       {silent}")


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--frame":
        # Anteprima di un solo fotogramma: python promo.py --frame 12.5
        OUT.mkdir(parents=True, exist_ok=True)
        render_frame(float(sys.argv[2])).save(OUT / f"frame-{sys.argv[2]}.png")
    else:
        main()
