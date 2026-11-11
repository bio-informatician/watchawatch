import struct
import zlib
import math
import os

def create_png(width, height, get_pixel_aa, supersample=2):
    raw = bytearray()
    if supersample == 1:
        ss_offsets = [(0.5, 0.5)]
    elif supersample == 2:
        ss_offsets = [(0.25, 0.25), (0.75, 0.25), (0.25, 0.75), (0.75, 0.75)]
    elif supersample == 3:
        ss_offsets = []
        for sy in (0.166, 0.5, 0.833):
            for sx in (0.166, 0.5, 0.833):
                ss_offsets.append((sx, sy))
    
    n_samples = len(ss_offsets)
    
    for y in range(height):
        raw.append(0) # filter None
        for x in range(width):
            tot_r = 0.0
            tot_g = 0.0
            tot_b = 0.0
            tot_a = 0.0
            for ox, oy in ss_offsets:
                r, g, b, a = get_pixel_aa(x + ox, y + oy, width, height)
                tot_r += r * (a / 255.0)
                tot_g += g * (a / 255.0)
                tot_b += b * (a / 255.0)
                tot_a += a
            
            avg_a = tot_a / n_samples
            if avg_a > 0.001:
                avg_r = (tot_r / n_samples) / (avg_a / 255.0)
                avg_g = (tot_g / n_samples) / (avg_a / 255.0)
                avg_b = (tot_b / n_samples) / (avg_a / 255.0)
            else:
                avg_r, avg_g, avg_b = 0.0, 0.0, 0.0
                
            raw.extend([
                max(0, min(255, int(round(avg_r)))),
                max(0, min(255, int(round(avg_g)))),
                max(0, min(255, int(round(avg_b)))),
                max(0, min(255, int(round(avg_a))))
            ])
            
    compressed = zlib.compress(bytes(raw), 9)
    def chunk(tag, data):
        crc = zlib.crc32(tag + data) & 0xffffffff
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', crc)
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', compressed) + chunk(b'IEND', b'')

def render_stat_clock(x, y, w, h):
    # Pure crisp white clock silhouette for notification bar
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
    if dist <= stroke * 0.85:
        return (255, 255, 255, 255)

    # 10:10 hands stance
    # Hour hand (305 deg from 12 = -145 deg math): points to 10 o'clock
    hw = max(1.0, stroke * 0.75)
    u_hx = -math.cos(math.radians(35))
    u_hy = -math.sin(math.radians(35))
    proj_h = dx * u_hx + dy * u_hy
    perp_h = abs(dx * (-u_hy) + dy * u_hx)
    if 0 <= proj_h <= r * 0.46 and perp_h <= hw / 2.0:
        return (255, 255, 255, 255)

    # Minute hand (60 deg from 12 = -30 deg math): points to 2 o'clock
    u_mx = math.cos(math.radians(30))
    u_my = -math.sin(math.radians(30))
    proj_m = dx * u_mx + dy * u_my
    perp_m = abs(dx * (-u_my) + dy * u_mx)
    if 0 <= proj_m <= r * 0.68 and perp_m <= hw / 2.0:
        return (255, 255, 255, 255)

    return (0, 0, 0, 0)

def render_luxury_launcher(x, y, w, h, is_round=False):
    cx = (w - 1) / 2.0
    cy = (h - 1) / 2.0
    dx = x - cx
    dy = y - cy
    dist = math.hypot(dx, dy)
    
    R_icon = min(w, h) * 0.48
    if is_round and dist > R_icon + 0.5:
        return (0, 0, 0, 0)
        
    # Launcher background: Deep Midnight Teal gradient
    bg_t = max(0.0, min(1.0, (dy / (h * 0.5) + 1.0) * 0.5))
    bg_r = int(0x09 * (1.0 - bg_t) + 0x12 * bg_t)
    bg_g = int(0x20 * (1.0 - bg_t) + 0x38 * bg_t)
    bg_b = int(0x27 * (1.0 - bg_t) + 0x43 * bg_t)
    bg_color = (bg_r, bg_g, bg_b, 255)
    
    # Watch Case Radius
    R = min(w, h) * 0.435
    
    # Ambient soft shadow around watch case
    if dist > R:
        if dist < R + R * 0.08:
            sh_t = (dist - R) / (R * 0.08)
            factor = 0.55 + 0.45 * sh_t
            return (int(bg_r * factor), int(bg_g * factor), int(bg_b * factor), 255)
        return bg_color

    # 1. Bezel (R * 0.885 to R)
    outer_bezel_r = R
    inner_bezel_r = R * 0.885
    track_r = R * 0.77
    
    light_proj = (-dx * 0.7 - dy * 0.7) / (R + 1e-5)
    
    if dist > inner_bezel_r:
        norm_t = max(0.0, min(1.0, (light_proj + 1.0) * 0.5))
        if norm_t > 0.5:
            st = (norm_t - 0.5) * 2.0
            br = int(0xF3 * (1.0 - st) + 0xFF * st)
            bg = int(0xBF * (1.0 - st) + 0xE7 * st)
            bb = int(0x59 * (1.0 - st) + 0xA8 * st)
        else:
            st = norm_t * 2.0
            br = int(0x9C * (1.0 - st) + 0xF3 * st)
            bg = int(0x69 * (1.0 - st) + 0xBF * st)
            bb = int(0x1C * (1.0 - st) + 0x59 * st)
            
        if abs(dist - outer_bezel_r) < 1.0:
            glint = max(0.0, light_proj)
            br = min(255, int(br + 45 * glint))
            bg = min(255, int(bg + 35 * glint))
            bb = min(255, int(bb + 25 * glint))
            
        if abs(dist - inner_bezel_r) < 1.1:
            return (0xFF, 0xF5, 0xD0, 255)
            
        return (br, bg, bb, 255)
        
    # 2. Dial Face
    dial_t = dist / inner_bezel_r
    dr = int(0x10 * (1.0 - dial_t) + 0x06 * dial_t)
    dg = int(0x44 * (1.0 - dial_t) + 0x1A * dial_t)
    db = int(0x50 * (1.0 - dial_t) + 0x22 * dial_t)
    
    if dist > inner_bezel_r * 0.94:
        rim_sh = (dist - inner_bezel_r * 0.94) / (inner_bezel_r * 0.06)
        dr = int(dr * (1.0 - 0.4 * rim_sh))
        dg = int(dg * (1.0 - 0.4 * rim_sh))
        db = int(db * (1.0 - 0.4 * rim_sh))
        
    if abs(dist - track_r) < 0.7:
        return (0x19, 0x4E, 0x58, 255)
        
    angle_deg = math.degrees(math.atan2(dy, dx))
    clock_deg = (angle_deg + 90.0) % 360.0
    
    # Active Focus Arc (0 deg / 12h to 135 deg / 4:30h)
    focus_r = (inner_bezel_r + track_r) * 0.505
    focus_w = inner_bezel_r - track_r
    half_fw = focus_w * 0.42
    
    in_focus_arc = False
    focus_color = None
    
    if 0.0 <= clock_deg <= 135.0:
        arc_dist = abs(dist - focus_r)
        if arc_dist <= half_fw:
            in_focus_arc = True
            arc_t = clock_deg / 135.0
            fr = int(0x10 * (1.0 - arc_t) + 0x2D * arc_t)
            fg = int(0xB9 * (1.0 - arc_t) + 0xE0 * arc_t)
            fb = int(0x81 * (1.0 - arc_t) + 0xD0 * arc_t)
            core_i = 1.0 - (arc_dist / half_fw)
            fr = min(255, int(fr + 65 * core_i))
            fg = min(255, int(fg + 35 * core_i))
            fb = min(255, int(fb + 45 * core_i))
            focus_color = (fr, fg, fb, 255)
            
    start_cap_dist = math.hypot(dx, dy - (-focus_r))
    if start_cap_dist <= half_fw:
        in_focus_arc = True
        focus_color = (0x10, 0xB9, 0x81, 255)
        
    pip_rad = math.radians(135.0 - 90.0)
    pip_cx = math.cos(pip_rad) * focus_r
    pip_cy = math.sin(pip_rad) * focus_r
    pip_dist = math.hypot(dx - pip_cx, dy - pip_cy)
    if pip_dist <= half_fw * 1.3:
        in_focus_arc = True
        if pip_dist <= half_fw * 0.6:
            focus_color = (0xFF, 0xFF, 0xFF, 255)
        else:
            focus_color = (0x34, 0xD3, 0x99, 255)
            
    if in_focus_arc and focus_color is not None:
        return focus_color
        
    # Hour & Minute Indices
    tick_mod = clock_deg % 30.0
    diff_tick = min(tick_mod, 30.0 - tick_mod)
    tick_index = int(round(clock_deg / 30.0)) % 12
    
    if tick_index == 0 and diff_tick < 6.0:
        if -inner_bezel_r * 0.94 <= dy <= -track_r * 0.90:
            if abs(dx - R * 0.042) <= R * 0.015 or abs(dx + R * 0.042) <= R * 0.015:
                return (0xFF, 0xE2, 0x92, 255)
    elif tick_index in (3, 6, 9) and diff_tick < 6.0:
        ang_rad = math.radians(tick_index * 30.0 - 90.0)
        ux = math.cos(ang_rad)
        uy = math.sin(ang_rad)
        proj = dx * ux + dy * uy
        perp = abs(dx * (-uy) + dy * ux)
        if track_r * 0.90 <= proj <= inner_bezel_r * 0.94 and perp <= R * 0.022:
            return (0xFF, 0xE2, 0x92, 255)
    elif diff_tick < 4.0:
        ang_rad = math.radians(tick_index * 30.0 - 90.0)
        ux = math.cos(ang_rad)
        uy = math.sin(ang_rad)
        proj = dx * ux + dy * uy
        perp = abs(dx * (-uy) + dy * ux)
        if track_r * 0.94 <= proj <= inner_bezel_r * 0.93 and perp <= R * 0.013:
            return (0xFF, 0xEF, 0xC4, 220)
            
    # "W" Monogram below 12
    w_cx = 0.0
    w_cy = -R * 0.40
    w_dx = dx - w_cx
    w_dy = dy - w_cy
    if abs(w_dx) <= R * 0.13 and abs(w_dy) <= R * 0.08:
        hw = R * 0.10
        hh = R * 0.060
        thick = max(1.2, R * 0.020)
        def dist_seg(px, py, x1, y1, x2, y2):
            vx = x2 - x1
            vy = y2 - y1
            l2 = vx*vx + vy*vy
            if l2 == 0: return math.hypot(px - x1, py - y1)
            t = max(0.0, min(1.0, ((px - x1)*vx + (py - y1)*vy) / l2))
            return math.hypot(px - (x1 + t*vx), py - (y1 + t*vy))
        d1 = dist_seg(w_dx, w_dy, -hw, -hh, -hw * 0.48, hh)
        d2 = dist_seg(w_dx, w_dy, -hw * 0.48, hh, 0.0, -hh * 0.25)
        d3 = dist_seg(w_dx, w_dy, 0.0, -hh * 0.25, hw * 0.48, hh)
        d4 = dist_seg(w_dx, w_dy, hw * 0.48, hh, hw, -hh)
        if min(d1, d2, d3, d4) <= thick * 0.5:
            return (0xFF, 0xDE, 0x8A, 240)
            
    is_center_pin = (dist <= R * 0.125)
    
    # Hour Hand (10:10 stance -> 304.5 deg)
    h_clock_deg = 304.5
    h_rad = math.radians(h_clock_deg - 90.0)
    h_ux = math.cos(h_rad)
    h_uy = math.sin(h_rad)
    h_proj = dx * h_ux + dy * h_uy
    h_perp = dx * (-h_uy) + dy * h_ux
    if -R * 0.06 <= h_proj <= R * 0.52:
        t_hand = max(0.0, h_proj / (R * 0.52))
        hw = R * (0.046 * (1.0 - t_hand * 0.38))
        if abs(h_perp) <= hw and not is_center_pin:
            return (0xFF, 0xEB, 0xA2, 255) if h_perp < 0 else (0xD4, 0x93, 0x22, 255)
            
    # Minute Hand (62 deg)
    m_clock_deg = 62.0
    m_rad = math.radians(m_clock_deg - 90.0)
    m_ux = math.cos(m_rad)
    m_uy = math.sin(m_rad)
    m_proj = dx * m_ux + dy * m_uy
    m_perp = dx * (-m_uy) + dy * m_ux
    if -R * 0.06 <= m_proj <= R * 0.74:
        t_mhand = max(0.0, m_proj / (R * 0.74))
        mw = R * (0.040 * (1.0 - t_mhand * 0.42))
        if abs(m_perp) <= mw and not is_center_pin:
            return (255, 255, 255, 255) if m_perp < 0 else (0xE2, 0xF0, 0xF3, 255)
            
    # Seconds Needle (225 deg)
    s_clock_deg = 225.0
    s_rad = math.radians(s_clock_deg - 90.0)
    s_ux = math.cos(s_rad)
    s_uy = math.sin(s_rad)
    s_proj = dx * s_ux + dy * s_uy
    s_perp = abs(dx * (-s_uy) + dy * s_ux)
    if -R * 0.18 <= s_proj <= R * 0.80:
        needle_w = max(0.9, R * (0.014 if s_proj < 0 else 0.011))
        if s_perp <= needle_w and not is_center_pin:
            return (0x2D, 0xD4, 0xBF, 255)
            
    # Center Pinion & Jewel Dome
    if is_center_pin:
        if dist > R * 0.08:
            pin_norm = (dist - R * 0.08) / (R * 0.045)
            return (0xB3, 0x79, 0x24, 255) if pin_norm > 0.8 else (0xFF, 0xDF, 0x8C, 255)
        elif dist > R * 0.038:
            dome_light = max(0.0, (-dx * 0.7 - dy * 0.7) / (R * 0.08 + 1e-5))
            return (min(255, int(0xF0 + 15 * dome_light)), min(255, int(0xB8 + 50 * dome_light)), min(255, int(0x45 + 80 * dome_light)), 255)
        else:
            if math.hypot(dx + R * 0.012, dy + R * 0.012) <= R * 0.012:
                return (255, 255, 255, 255)
            return (0x0F, 0x76, 0x6E, 255)
            
    return (dr, dg, db, 255)

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
        data = create_png(sz, sz, render_stat_clock, supersample=2)
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
        data_sq = create_png(sz, sz, lambda x, y, w, h: render_luxury_launcher(x, y, w, h, is_round=False), supersample=2)
        with open(path_sq, "wb") as f:
            f.write(data_sq)

        # Round
        path_rd = os.path.join(out_dir, "ic_launcher_round.png")
        data_rd = create_png(sz, sz, lambda x, y, w, h: render_luxury_launcher(x, y, w, h, is_round=True), supersample=2)
        with open(path_rd, "wb") as f:
            f.write(data_rd)

        print(f"Generated {path_sq} and {path_rd} ({sz}x{sz})")

if __name__ == "__main__":
    main()
