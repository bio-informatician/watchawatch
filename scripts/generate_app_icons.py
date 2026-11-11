import struct
import zlib
import math
import os

def create_png(width, height, get_pixel):
    raw = bytearray()
    for y in range(height):
        raw.append(0) # filter None
        for x in range(width):
            r, g, b, a = get_pixel(x, y, width, height)
            raw.extend([
                max(0, min(255, int(r))),
                max(0, min(255, int(g))),
                max(0, min(255, int(b))),
                max(0, min(255, int(a)))
            ])
    compressed = zlib.compress(bytes(raw), 9)
    def chunk(tag, data):
        crc = zlib.crc32(tag + data) & 0xffffffff
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', crc)
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', compressed) + chunk(b'IEND', b'')

def render_stat_clock(x, y, w, h):
    # Pure white clock silhouette for notification bar
    cx = (w - 1) / 2.0
    cy = (h - 1) / 2.0
    dx = x - cx
    dy = y - cy
    dist = math.hypot(dx, dy)
    r = min(w, h) * 0.44
    stroke = max(1.2, min(w, h) * 0.09)

    # Outer circle ring
    ring_dist = abs(dist - (r - stroke / 2.0))
    if ring_dist <= stroke / 2.0:
        alpha = min(1.0, (stroke / 2.0 - ring_dist) + 0.5)
        return (255, 255, 255, int(alpha * 255))

    # Center dot
    if dist <= stroke * 0.8:
        return (255, 255, 255, 255)

    # Hour hand (pointing to 10 o'clock: angle = -150 deg or -2.617 rad)
    # Minute hand (pointing to 12 o'clock or 2 o'clock)
    # Minute hand: up along y-axis (dx near 0, dy < 0, dy > -r * 0.65)
    hw = max(1.0, stroke * 0.7)
    if abs(dx) <= hw / 2.0 and -r * 0.65 <= dy <= 0:
        return (255, 255, 255, 255)

    # Hour hand: pointing to ~10 o'clock (angle 210 deg from +x = dx < 0, dy < 0)
    # Project (dx, dy) onto unit vector (-cos(30), -sin(30))
    u_x = -math.cos(math.radians(30))
    u_y = -math.sin(math.radians(30))
    proj = dx * u_x + dy * u_y
    perp = abs(dx * (-u_y) + dy * u_x)
    if 0 <= proj <= r * 0.45 and perp <= hw / 2.0:
        return (255, 255, 255, 255)

    return (0, 0, 0, 0)

def render_launcher(x, y, w, h, is_round=False):
    cx = (w - 1) / 2.0
    cy = (h - 1) / 2.0
    dx = x - cx
    dy = y - cy
    dist = math.hypot(dx, dy)
    r = min(w, h) * 0.46

    # Mask for round icon
    if is_round and dist > r:
        return (0, 0, 0, 0)

    # Base background: Deep Teal #103B43
    bg_r, bg_g, bg_b = 0x10, 0x3B, 0x43

    # Inside clock dial
    dial_r = r * 0.86
    if dist <= dial_r:
        # Radial gradient from #0D9488 to #15536A
        t = dist / dial_r
        d_r = int(0x0D * (1 - t) + 0x15 * t)
        d_g = int(0x94 * (1 - t) + 0x53 * t)
        d_b = int(0x88 * (1 - t) + 0x6A * t)

        # Golden outer bezel ring
        bezel_w = max(1.5, min(w, h) * 0.04)
        if abs(dist - (dial_r - bezel_w / 2.0)) <= bezel_w / 2.0:
            return (0xFF, 0xBE, 0x71, 255)

        # Inner golden thin ring
        inner_ring = dial_r * 0.78
        if abs(dist - inner_ring) <= bezel_w * 0.4:
            return (0xFF, 0xF1, 0xDA, 255)

        # Hour markers (12, 3, 6, 9)
        angle = math.atan2(dy, dx)
        # Check 12 ticks
        deg = (math.degrees(angle) + 90) % 360
        near_tick = min(abs(deg - round(deg / 30) * 30), abs(deg - 360))
        if near_tick < 3.0 and dial_r * 0.58 <= dist <= dial_r * 0.72:
            is_major = (round(deg / 30) % 3 == 0)
            return (255, 255, 255, 255 if is_major else 180)

        # Center pin
        if dist <= dial_r * 0.12:
            return (0xFF, 0xBE, 0x71, 255)

        # Minute hand (white, pointing at 12 o'clock)
        mw = max(1.2, min(w, h) * 0.045)
        if abs(dx) <= mw / 2.0 and -dial_r * 0.62 <= dy <= 0:
            return (255, 255, 255, 255)

        # Hour hand (golden, pointing at ~10 o'clock)
        hw = max(1.2, min(w, h) * 0.05)
        u_x = -math.cos(math.radians(35))
        u_y = -math.sin(math.radians(35))
        proj = dx * u_x + dy * u_y
        perp = abs(dx * (-u_y) + dy * u_x)
        if 0 <= proj <= dial_r * 0.44 and perp <= hw / 2.0:
            return (0xFF, 0xBE, 0x71, 255)

        return (d_r, d_g, d_b, 255)

    return (bg_r, bg_g, bg_b, 255)

def main():
    res_dir = "/home/agent/agent-work/projects/watchawatch/app/src/main/res"

    # 1. Generate ic_stat_watch PNGs
    stat_sizes = {
        "drawable-mdpi": 24,
        "drawable-hdpi": 36,
        "drawable-xhdpi": 48,
        "drawable-xxhdpi": 72,
        "drawable-xxxhdpi": 96,
        "drawable": 24,
    }
    for folder, sz in stat_sizes.items():
        out_dir = os.path.join(res_dir, folder)
        os.makedirs(out_dir, exist_ok=True)
        path = os.path.join(out_dir, "ic_stat_watch.png")
        data = create_png(sz, sz, render_stat_clock)
        with open(path, "wb") as f:
            f.write(data)
        print(f"Generated {path} ({sz}x{sz}, {len(data)} bytes)")

    # 2. Generate ic_launcher and ic_launcher_round PNGs
    launcher_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, sz in launcher_sizes.items():
        out_dir = os.path.join(res_dir, folder)
        os.makedirs(out_dir, exist_ok=True)
        # Square / Adaptive fallback
        path_sq = os.path.join(out_dir, "ic_launcher.png")
        data_sq = create_png(sz, sz, lambda x, y, w, h: render_launcher(x, y, w, h, is_round=False))
        with open(path_sq, "wb") as f:
            f.write(data_sq)

        # Round
        path_rd = os.path.join(out_dir, "ic_launcher_round.png")
        data_rd = create_png(sz, sz, lambda x, y, w, h: render_launcher(x, y, w, h, is_round=True))
        with open(path_rd, "wb") as f:
            f.write(data_rd)

        print(f"Generated {path_sq} and {path_rd} ({sz}x{sz})")

if __name__ == "__main__":
    main()
