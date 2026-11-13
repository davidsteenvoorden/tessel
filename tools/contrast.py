#!/usr/bin/env python3
"""Design token check: prints WCAG contrast for every text/ground pair and
converts OKLCH zone accents to sRGB hex. Run before changing any color."""
import math

def oklch_to_hex(L, C, h):
    a = C * math.cos(math.radians(h))
    b = C * math.sin(math.radians(h))
    l_ = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m_ = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s_ = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    r = 4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_
    g = -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_
    bl = -0.0041960863 * l_ - 0.7034186147 * m_ + 1.7076147010 * s_
    def enc(v):
        v = max(0.0, min(1.0, v))
        return 12.92 * v if v <= 0.0031308 else 1.055 * v ** (1 / 2.4) - 0.055
    return "#%02X%02X%02X" % tuple(round(enc(v) * 255) for v in (r, g, bl))

def lum(hexs):
    h = hexs.lstrip("#")
    rgb = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    lin = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in rgb]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]

def contrast(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)

DARK = dict(ground="#14171B", surface="#1D2127", raised="#262B32",
            ink="#F2F4F6", ink2="#A9B0BA", ink3="#8A929C",
            danger="#FF6B4A", coin="#F2C14E")
LIGHT = dict(ground="#ECEEF0", surface="#F8F9FA", raised="#FFFFFF",
             ink="#16191D", ink2="#4C535C", ink3="#6A727C",
             danger="#C2381B", coin="#8F5F00")

# Hues 10..100 are reserved: hostile bullets (danger) and coins live there.
ZONE_HUES = [185, 150, 110, 225, 265, 305, 345, None]  # zone 8 is neutral

def zone_accents(dark):
    L, C = (0.78, 0.13) if dark else (0.50, 0.14)
    out = []
    for h in ZONE_HUES:
        out.append(oklch_to_hex(L, 0.02, 250) if h is None else oklch_to_hex(L, C, h))
    return out

def report(name, pal, dark):
    print(f"== {name} ==")
    for fg in ("ink", "ink2", "ink3", "danger", "coin"):
        for bg in ("ground", "surface"):
            r = contrast(pal[fg], pal[bg])
            need = 4.5 if fg in ("ink", "ink2") else 3.0
            flag = "ok " if r >= need else "LOW"
            print(f"  {fg:7s} on {bg:8s} {r:5.2f}:1  need {need}  {flag}")
    for i, hexv in enumerate(zone_accents(dark), 1):
        r = contrast(hexv, pal["ground"])
        print(f"  zone {i} accent {hexv}  on ground {r:5.2f}:1 {'ok ' if r >= 3 else 'LOW'}")

if __name__ == "__main__":
    report("dark", DARK, True)
    report("light", LIGHT, False)
