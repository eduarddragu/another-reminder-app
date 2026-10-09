"""Regenerates the README banner and badges (light and dark).

Palette and type follow https://eduarddragu.dev and the app. The banner shows Home's compass the way the
app draws it (ui/components/Beacon.kt). GitHub can't load web fonts, so each SVG carries the fonts it
uses, cut to plain ASCII and fetched from Google Fonts while it's written. Run: python3 docs/assets/banner.py
"""
import base64
import math
import re
import string
import urllib.parse
import urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parent
# Each embedded face has its own family name, so a weight or a style never falls back onto another.
FACES = {
    "serif": ("Cormorant Garamond", "ital,wght@0,500"),
    "serifItalic": ("Cormorant Garamond", "ital,wght@1,500"),
    "sans": ("DM Sans", "wght@400"),
    "sansBold": ("DM Sans", "wght@600"),
    "mono": ("Geist Mono", "wght@500"),
}
SERIF = "'e-serif', 'Cormorant Garamond', Georgia, serif"
SERIF_ITALIC = "'e-serifItalic', 'Cormorant Garamond', Georgia, serif"
SANS = "'e-sans', 'DM Sans', -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif"
SANS_BOLD = "'e-sansBold', 'DM Sans', -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif"
MONO = "'e-mono', 'Geist Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"

_faces = {}


def font_style(*keys):
    """A <style> with the given faces as data URLs (woff2, ASCII only)."""
    rules = []
    for key in keys:
        if key not in _faces:
            family, axes = FACES[key]
            text = urllib.parse.quote("".join(ch for ch in string.printable if ch.isprintable()))
            url = f"https://fonts.googleapis.com/css2?family={family.replace(' ', '+')}:{axes}&text={text}"
            # A modern User-Agent, or Google serves TTF instead of woff2.
            ua = {"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"}
            css = urllib.request.urlopen(urllib.request.Request(url, headers=ua)).read().decode()
            src = re.search(r"url\((https:[^)]+)\)", css).group(1)
            data = base64.b64encode(urllib.request.urlopen(urllib.request.Request(src, headers=ua)).read()).decode()
            _faces[key] = f"@font-face{{font-family:'e-{key}';src:url(data:font/woff2;base64,{data}) format('woff2');}}"
        rules.append(_faces[key])
    return "<style>" + "".join(rules) + "</style>"


def badges(name, t, items, file):
    """A row of badges in the app's labels: the key on the accent, the value on the card colour."""
    h, pad, char, gap = 28, 10, 8.4, 8
    x, parts = 0, []
    for key, value in items:
        kw = len(key) * char + 2 * pad
        vw = len(value) * char + 2 * pad if value else 0
        w = kw + vw
        parts.append(f'<rect x="{x + 0.5}" y="0.5" width="{w - 1}" height="{h - 1}" rx="7" fill="{t["cell"]}" stroke="{t["border"]}"/>')
        parts.append(f'<path d="M{x + 7},0.5 H{x + kw} V{h - 0.5} H{x + 7} A6.5,6.5 0 0 1 {x + 0.5},{h - 7} V7 A6.5,6.5 0 0 1 {x + 7},0.5 Z" fill="{t["accent"]}"/>' if vw else f'<rect x="{x + 0.5}" y="0.5" width="{w - 1}" height="{h - 1}" rx="7" fill="{t["accent"]}"/>')
        parts.append(f'<text x="{x + pad}" y="18.5" font-family="{MONO}" font-size="12" letter-spacing="1.2" fill="{t["bg"]}">{key}</text>')
        if vw:
            parts.append(f'<text x="{x + kw + pad}" y="18.5" font-family="{MONO}" font-size="12" letter-spacing="1.2" fill="{t["fg"]}">{value}</text>')
        x += w + gap
    width = x - gap
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{width:.0f}" height="{h}" viewBox="0 0 {width:.0f} {h}" role="img" aria-label="{", ".join(" ".join(filter(None, i)) for i in items)}">{font_style("mono")}{"".join(parts)}</svg>\n'
    (OUT / f"{file}-{name}.svg").write_text(svg)

THEMES = {
    "light": dict(bg="#faf8f5", border="#e2dbd2", fg="#1a1714", muted="#5a4e42", cell="#ece5db", accent="#b04619"),
    "dark": dict(bg="#1a1714", border="#342c26", fg="#faf8f5", muted="#b3a597", cell="#2a2420", accent="#e8743f"),
}

W, H = 1280, 360
TILT = -12


def compass(t, cx, cy, r):
    """The rim with its arc, a fine bezel, and the rose with the north point half filled."""
    def rot(x, y, deg):
        a = math.radians(deg)
        dx, dy = x - cx, y - cy
        return cx + dx * math.cos(a) - dy * math.sin(a), cy + dx * math.sin(a) + dy * math.cos(a)

    def point(angle, length, w):
        pts = [rot(x, y, angle + TILT) for x, y in [(cx, cy), (cx - w, cy - w), (cx, cy - length), (cx + w, cy - w)]]
        return pts

    parts = [
        f'<circle cx="{cx}" cy="{cy}" r="{r + 26}" fill="none" stroke="{t["accent"]}" stroke-opacity="0.18" stroke-width="2"/>',
        f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{t["accent"]}" stroke-opacity="0.35" stroke-width="3"/>',
    ]
    # What's left before the next reminder, from 12 o'clock, with a bead at its head.
    sweep = 0.72
    end = math.radians(-90 + 360 * sweep)
    ex, ey = cx + r * math.cos(end), cy + r * math.sin(end)
    parts.append(f'<path d="M{cx},{cy - r} A{r},{r} 0 1 1 {ex:.1f},{ey:.1f}" fill="none" stroke="{t["accent"]}" stroke-width="6" stroke-linecap="round"/>')
    parts.append(f'<circle cx="{ex:.1f}" cy="{ey:.1f}" r="9" fill="{t["accent"]}"/>')
    for i in range(48):
        long = i % 12 == 0
        length = 12 if long else (8 if i % 4 == 0 else 4)
        a = rot(cx, cy - r + 10, i * 7.5)
        b = rot(cx, cy - r + 10 + length, i * 7.5)
        parts.append(f'<line x1="{a[0]:.1f}" y1="{a[1]:.1f}" x2="{b[0]:.1f}" y2="{b[1]:.1f}" stroke="{t["muted"]}" stroke-opacity="{0.7 if long else 0.35}" stroke-width="2"/>')
    parts.append(f'<circle cx="{cx}" cy="{cy}" r="{r * 0.76:.1f}" fill="none" stroke="{t["muted"]}" stroke-opacity="0.25" stroke-width="1.5"/>')
    poly = lambda pts: " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
    for a in (45, 135, 225, 315):
        parts.append(f'<polygon points="{poly(point(a, r * 0.3, 6))}" fill="none" stroke="{t["muted"]}" stroke-opacity="0.55" stroke-width="2"/>')
    for a in (90, 180, 270):
        parts.append(f'<polygon points="{poly(point(a, r * 0.46, 8))}" fill="none" stroke="{t["fg"]}" stroke-opacity="0.75" stroke-width="2"/>')
    north = point(0, r * 0.46, 8)
    parts.append(f'<polygon points="{poly([north[0], north[2], north[3]])}" fill="{t["accent"]}" fill-opacity="0.85"/>')
    parts.append(f'<polygon points="{poly(north)}" fill="none" stroke="{t["accent"]}" stroke-width="2"/>')
    nx, ny = rot(cx, cy - r * 0.62, 0)
    parts.append(f'<text x="{nx:.1f}" y="{ny + 10:.1f}" text-anchor="middle" font-family="{SERIF_ITALIC}" font-size="30" font-style="italic" fill="{t["accent"]}" transform="rotate({TILT} {cx} {cy})">N</text>')
    parts.append(f'<circle cx="{cx}" cy="{cy}" r="5" fill="{t["accent"]}"/>')
    parts.append(f'<circle cx="{cx}" cy="{cy}" r="10" fill="none" stroke="{t["fg"]}" stroke-opacity="0.6" stroke-width="2"/>')
    return "\n  ".join(parts)


def banner(name, t):
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-label="(Another) Reminder App">
  {font_style("serif", "serifItalic", "sans", "mono")}
  <rect x="1" y="1" width="{W - 2}" height="{H - 2}" rx="18" fill="{t["bg"]}" stroke="{t["border"]}" stroke-width="2"/>
  <text x="72" y="92" font-family="{MONO}" font-size="15" letter-spacing="4" fill="{t["accent"]}">A REMINDER APP FOR ONE</text>
  <text x="68" y="150" font-family="{SERIF_ITALIC}" font-size="52" font-style="italic" font-weight="500" fill="{t["accent"]}">(Another)</text>
  <text x="66" y="232" font-family="{SERIF}" font-size="92" font-weight="500" letter-spacing="-2" fill="{t["fg"]}">Reminder App</text>
  <text x="72" y="278" font-family="{SANS}" font-size="22" fill="{t["muted"]}">It knows when you're at the shop.</text>
  <text x="72" y="308" font-family="{SANS}" font-size="22" fill="{t["muted"]}">And it remembers the car tax every June.</text>
  {compass(t, 1030, 180, 112)}
</svg>
'''
    (OUT / f"banner-{name}.svg").write_text(svg)


BADGES = [("KOTLIN", "2.4"), ("UI", "JETPACK COMPOSE"), ("ANDROID", "14+"), ("ACCOUNTS", "NONE"), ("LICENSE", "GPL V3")]

for name, t in THEMES.items():
    banner(name, t)
    badges(name, t, BADGES, "badges")
    badges(name, t, [("BY", "EDUARDDRAGU.DEV")], "badge-by")
print("written:", ", ".join(sorted(p.name for p in OUT.glob("*.svg"))))
