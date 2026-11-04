"""Kestrel launcher icon: one source of truth -> preview PNGs + Android vector drawables.

Local design space is 0..100; T() maps it into the 108 dp adaptive-icon canvas so the art sits
inside the 66 dp safe zone (radius 33 around 54,54).
"""
import math, json, sys, os

S, TX, TY = 0.72, 18.6, 17.6   # scale and offset into the 108 canvas

def T(x, y):
    return (x * S + TX, y * S + TY)

def fmt(p):
    return f"{p[0]:.2f},{p[1]:.2f}"

def circle(cx, cy, r, ccw=False):
    k = 0.5522847498 * r
    pts = [(cx + r, cy), (cx, cy + r), (cx - r, cy), (cx, cy - r)]
    ctrl = [((cx + r, cy + k), (cx + k, cy + r)), ((cx - k, cy + r), (cx - r, cy + k)),
            ((cx - r, cy - k), (cx - k, cy - r)), ((cx + k, cy - r), (cx + r, cy - k))]
    if ccw:
        pts = [pts[0], pts[3], pts[2], pts[1]]
        ctrl = [((cx + r, cy - k), (cx + k, cy - r)), ((cx - k, cy - r), (cx - r, cy - k)),
                ((cx - r, cy + k), (cx - k, cy + r)), ((cx + k, cy + r), (cx + r, cy + k))]
    d = f"M{fmt(T(*pts[0]))}"
    for i in range(4):
        c1, c2 = ctrl[i]; end = pts[(i + 1) % 4]
        d += f" C{fmt(T(*c1))} {fmt(T(*c2))} {fmt(T(*end))}"
    return d + " Z"

def arc(cx, cy, r, a0, a1, move=False):
    """Arc from angle a0 to a1 (degrees, y-down, clockwise positive) as cubic segments."""
    out = ""
    n = max(1, int(math.ceil(abs(a1 - a0) / 90)))
    step = (a1 - a0) / n
    for i in range(n):
        t0 = math.radians(a0 + i * step); t1 = math.radians(a0 + (i + 1) * step)
        k = 4 / 3 * math.tan((t1 - t0) / 4) * r
        p0 = (cx + r * math.cos(t0), cy + r * math.sin(t0))
        p3 = (cx + r * math.cos(t1), cy + r * math.sin(t1))
        c1 = (p0[0] - k * math.sin(t0), p0[1] + k * math.cos(t0))
        c2 = (p3[0] + k * math.sin(t1), p3[1] - k * math.cos(t1))
        if move and i == 0:
            out += f"M{fmt(T(*p0))}"
        out += f" C{fmt(T(*c1))} {fmt(T(*c2))} {fmt(T(*p3))}"
    return out

def path(*cmds):
    """Free-form path from local points: ('M',x,y) ('L',x,y) ('C',x1,y1,x2,y2,x,y) ('Z',)."""
    d = ""
    for c in cmds:
        if c[0] in "ML":
            d += f" {c[0]}{fmt(T(c[1], c[2]))}"
        elif c[0] == "C":
            d += f" C{fmt(T(c[1], c[2]))} {fmt(T(c[3], c[4]))} {fmt(T(c[5], c[6]))}"
        elif c[0] == "Z":
            d += " Z"
    return d.strip()

HEAD_C, HEAD_R = (46.0, 47.0), 33.0
EYE_C = (60.0, 40.0)

def head_point(angle):
    a = math.radians(angle)
    return (HEAD_C[0] + HEAD_R * math.cos(a), HEAD_C[1] + HEAD_R * math.sin(a))

# Colours
SLATE = "#5A6EA6"     # male kestrel crown: blue-grey
CREAM = "#FFF6EC"
INK = "#252D4B"       # malar stripe, beak, iris
GOLD = "#EC9F36"      # eye-ring and cere (warmer than any marketplace yellow)
CHESTNUT = "#C0582A"  # back and shoulders
BG_TOP, BG_BOTTOM = "#FFE9D3", "#F7C09A"

def design():
    layers = []
    # Shoulders: the rufous back, cropped by the mask.
    layers.append(("shoulders", path(("M", -2, 128), ("C", 0, 94, 20, 79.5, 42, 78.6), ("C", 64, 77.8, 86, 90, 94, 128), ("Z",)), CHESTNUT))
    # Head.
    layers.append(("head", circle(*HEAD_C, HEAD_R), SLATE))
    # Throat and cheek: the pale lower front of the head, bounded below by the head outline.
    a_start, a_end = 10.0, 118.0
    p_start = head_point(a_start); p_end = head_point(a_end)
    cheek = f"M{fmt(T(*p_end))}" + " C" + fmt(T(34, 64)) + " " + fmt(T(42, 55.5)) + " " + fmt(T(53, 53.5)) \
        + " C" + fmt(T(62, 52)) + " " + fmt(T(70, 52.6)) + " " + fmt(T(*p_start)) + arc(*HEAD_C, HEAD_R, a_start, a_end) + " Z"
    layers.append(("cheek", cheek, CREAM))
    # Malar stripe: the kestrel's dark "moustache", a slim comma falling from below the eye.
    malar = path(("M", 55.2, 47.6), ("C", 58.6, 48.2, 60, 50.6, 59.4, 54.6), ("C", 58.8, 59, 56.8, 63.6, 53.6, 68.4),
                 ("C", 52.6, 64.6, 52.2, 60, 52.6, 55.6), ("C", 52.9, 51.6, 53.6, 48.8, 55.2, 47.6), ("Z",))
    layers.append(("malar", malar, INK))
    # Beak: a short hooked upper mandible with the gold cere on top of its base.
    beak = path(("M", 72.6, 37.2), ("C", 80.2, 37.4, 86.6, 42.6, 88.4, 50.4), ("C", 89.4, 54.8, 88, 58.6, 84.8, 60.4),
                ("C", 85, 57, 83.2, 54.8, 80, 54.4), ("L", 74, 54), ("Z",))
    layers.append(("beak", beak, INK))
    cere = path(("M", 71.6, 36.8), ("C", 74.8, 36.2, 77.8, 36.8, 80, 38.4), ("C", 78.6, 39.8, 77.6, 41.4, 77.2, 43.2),
                ("L", 72.4, 43), ("C", 72.1, 40.8, 71.8, 38.8, 71.6, 36.8), ("Z",))
    layers.append(("cere", cere, GOLD))
    # Eye: thin gold ring, dark iris, one catch-light. The glint makes it an eye, never a target.
    layers.append(("eyering", circle(*EYE_C, 8.8), GOLD))
    layers.append(("iris", circle(*EYE_C, 7.0), INK))
    layers.append(("glint", circle(EYE_C[0] + 2.6, EYE_C[1] - 2.6, 1.9), "#FFFFFF"))
    return layers

def monochrome():
    """Single-colour silhouette for themed icons: head + beak with the eye as a ring-shaped hole."""
    head_with_eye = circle(*HEAD_C, HEAD_R) + " " + circle(*EYE_C, 7.4, ccw=True)
    beak = path(("M", 73.0, 37.2), ("C", 80.2, 37.4, 86.6, 42.6, 88.4, 50.4), ("C", 89.4, 54.8, 88, 58.6, 84.8, 60.4),
                ("C", 85, 57, 83.2, 54.8, 80, 54.4), ("L", 74, 54), ("Z",))
    return [("head", head_with_eye), ("beak", beak)]

def vector_xml(paths, even_odd=()):
    body = []
    for name, d, color in paths:
        attrs = f'android:fillColor="{color}" android:pathData="{d}"'
        if name in even_odd:
            attrs += ' android:fillType="evenOdd"'
        body.append(f'    <!-- {name} -->\n    <path {attrs} />')
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n' + "\n".join(body) + "\n</vector>\n")

if __name__ == "__main__":
    out_dir = sys.argv[1]
    layers = design()
    bg = ("bg", "M0,0 L108,0 L108,108 L0,108 Z", ["lin", BG_TOP, BG_BOTTOM])
    json.dump({"size": 540, "layers": [{"d": bg[1], "c": bg[2]}] + [{"d": d, "c": c} for _, d, c in layers]},
              open(os.path.join(out_dir, "preview.json"), "w"))
    mono = monochrome()
    json.dump({"size": 540, "layers": [{"d": "M0,0 L108,0 L108,108 L0,108 Z", "c": "#DDE1FF"}] + [{"d": d, "c": "#374379"} for _, d in mono]},
              open(os.path.join(out_dir, "preview_mono.json"), "w"))
    if len(sys.argv) > 2:
        res = sys.argv[2]
        open(os.path.join(res, "drawable", "ic_launcher_foreground.xml"), "w").write(vector_xml(layers))
        open(os.path.join(res, "drawable", "ic_launcher_monochrome.xml"), "w").write(
            vector_xml([(n, d, "#FFFFFFFF") for n, d in mono], even_odd=("head",)))
        open(os.path.join(res, "drawable", "ic_launcher_background.xml"), "w").write(
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            '    <path android:pathData="M0,0h108v108h-108z">\n'
            '        <aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108"\n'
            f'                android:startColor="{BG_TOP}" android:endColor="{BG_BOTTOM}" />\n'
            '        </aapt:attr>\n    </path>\n</vector>\n')
