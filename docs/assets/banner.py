"""Regenerates the README banner (light and dark) and the icon.

Palette and type follow https://eduarddragu.dev and the app. The banner shows Home's compass the way the
app draws it (ui/components/Beacon.kt). Run: python3 docs/assets/banner.py
"""
import math
from pathlib import Path

OUT = Path(__file__).resolve().parent
SERIF = "'Cormorant Garamond', 'Iowan Old Style', Georgia, 'Times New Roman', serif"
SANS = "'DM Sans', -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif"
MONO = "'Geist Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"

THEMES = {
    "light": dict(bg="#faf8f5", border="#e2dbd2", fg="#1a1714", muted="#5a4e42", accent="#b04619"),
    "dark": dict(bg="#1a1714", border="#342c26", fg="#faf8f5", muted="#b3a597", accent="#e8743f"),
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
    parts.append(f'<text x="{nx:.1f}" y="{ny + 10:.1f}" text-anchor="middle" font-family="{SERIF}" font-size="30" font-style="italic" fill="{t["accent"]}" transform="rotate({TILT} {cx} {cy})">N</text>')
    parts.append(f'<circle cx="{cx}" cy="{cy}" r="5" fill="{t["accent"]}"/>')
    parts.append(f'<circle cx="{cx}" cy="{cy}" r="10" fill="none" stroke="{t["fg"]}" stroke-opacity="0.6" stroke-width="2"/>')
    return "\n  ".join(parts)


def banner(name, t):
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-label="(Another) Reminder App">
  <rect x="1" y="1" width="{W - 2}" height="{H - 2}" rx="18" fill="{t["bg"]}" stroke="{t["border"]}" stroke-width="2"/>
  <text x="72" y="92" font-family="{MONO}" font-size="15" letter-spacing="4" fill="{t["accent"]}">A REMINDER APP FOR ONE</text>
  <text x="68" y="150" font-family="{SERIF}" font-size="52" font-style="italic" font-weight="500" fill="{t["accent"]}">(Another)</text>
  <text x="66" y="232" font-family="{SERIF}" font-size="92" font-weight="500" letter-spacing="-2" fill="{t["fg"]}">Reminder App</text>
  <text x="72" y="278" font-family="{SANS}" font-size="22" fill="{t["muted"]}">It knows when you're at the shop.</text>
  <text x="72" y="308" font-family="{SANS}" font-size="22" fill="{t["muted"]}">And it remembers the car tax every June.</text>
  {compass(t, 1030, 180, 112)}
</svg>
'''
    (OUT / f"banner-{name}.svg").write_text(svg)


for name, t in THEMES.items():
    banner(name, t)
print("written:", ", ".join(sorted(p.name for p in OUT.glob("banner-*.svg"))))
