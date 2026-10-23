# -*- coding: utf-8 -*-
"""
SmartHorde 程序化资源生成器（v4 — 128x128 多部件立体像素画 + 全亮度自发光贴图 + 恐怖战术音效）：
- 合成 6 个 .ogg 音效（Vorbis，44.1kHz 单声道）
- 精确按 SmartZombieModel 与 HordeBossModel 的 6 面 Box-UV 拓扑绘制 9 张 128x128 主贴图 + 9 张 _glow.png 发光贴图

依赖：pip install soundfile pillow numpy
运行：python tools/gen_assets.py（在项目根目录）
"""
import os
import numpy as np
import soundfile as sf
from PIL import Image

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUND_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "smarthorde", "sounds")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "smarthorde", "textures", "entity")
ITEM_TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "smarthorde", "textures", "item")
ARMOR_TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "smarthorde", "textures", "models", "armor")


# ================================================================ 1. 音效合成 DSP

def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def osc(freq_array, wave="sine"):
    phase = 2 * np.pi * np.cumsum(freq_array) / SR
    if wave == "sine":
        return np.sin(phase)
    if wave == "saw":
        return 2 * ((phase / (2 * np.pi)) % 1.0) - 1
    if wave == "tri":
        return 2 * np.abs(2 * ((phase / (2 * np.pi)) % 1.0) - 1) - 1
    raise ValueError(wave)


def glide(f0, f1, dur, tau):
    t = t_axis(dur)
    return f0 + (f1 - f0) * (1 - np.exp(-t / tau))


def glide_n(f0, f1, n_samples, tau):
    seg = glide(f0, f1, n_samples / SR, tau)
    if len(seg) < n_samples:
        seg = np.concatenate([seg, np.full(n_samples - len(seg), f1)])
    return seg[:n_samples]


def lowpass(x, alpha=0.05, stages=1):
    y = x
    for _ in range(stages):
        out = np.empty_like(y)
        acc = 0.0
        for i, v in enumerate(y):
            acc = acc * (1 - alpha) + v * alpha
            out[i] = acc
        y = out
    return y


def band_noise(n, alpha_hi=0.02, alpha_lo=0.004, seed=0, gain=1.0):
    rng = np.random.default_rng(seed)
    raw = rng.normal(0, 1, n)
    hi = lowpass(raw, alpha_hi)
    lo = lowpass(raw, alpha_lo)
    return (hi - lo) * gain


def add_at(dst, seg, at_seconds):
    start = int(SR * at_seconds)
    end = min(len(dst), start + len(seg))
    dst[start:end] += seg[:end - start]


def finish(y, peak=0.85, fade=None):
    y = y / (np.max(np.abs(y)) + 1e-9) * peak
    fade = fade or min(256, len(y) // 8)
    y[:fade] *= np.linspace(0, 1, fade)
    y[-fade:] *= np.linspace(1, 0, fade)
    return y.astype(np.float32)


def heartbeat(dst, at, thump_hz=52.0, amp=0.5):
    for offset, a in ((0.0, 1.0), (0.34, 0.75)):
        dur = 0.22
        t = t_axis(dur)
        thump = osc(thump_hz * np.exp(-t / 0.3) + 32, "sine") * np.exp(-t / 0.07) * amp * a
        click_n = int(SR * 0.006)
        click = lowpass(np.random.default_rng(int(at * 100 + offset * 7)).normal(0, 1, click_n)) \
            * np.exp(-np.arange(click_n) / (SR * 0.002)) * amp * a * 0.4
        add_at(dst, thump, at + offset)
        add_at(dst, click, at + offset)


def synth_horde_wave_start():
    dur = 3.6
    out = np.zeros(int(SR * dur))
    heartbeat(out, 0.15, 50, 0.55)
    heartbeat(out, 1.15, 54, 0.65)
    heartbeat(out, 2.05, 58, 0.8)

    t = t_axis(dur)
    env = np.clip((t - 1.0) / 1.2, 0, 1) ** 2
    env[-int(SR * 0.9):] *= np.linspace(1, 0, int(SR * 0.9))
    drop = np.where(t > dur - 1.0, 1.0 - 0.18 * (t - (dur - 1.0)), 1.0)
    horn = (osc(65.41 * drop, "saw") * 0.42
            + osc(69.30 * drop, "saw") * 0.38
            + osc(92.50 * drop, "saw") * 0.22)
    horn = np.tanh(horn * 2.2) * env
    out += horn * 0.85

    whisper = band_noise(len(t), 0.03, 0.007, seed=13, gain=1.0)
    flicker = lowpass(np.random.default_rng(99).normal(0, 1, len(t)), 0.0006)
    flicker = np.abs(flicker) / (np.max(np.abs(flicker)) + 1e-9)
    out += whisper * flicker * env * 0.5

    gt = t_axis(1.0)
    gf = 1600 * (1 + 0.9 * (gt / 1.0))
    vib = 1 + 0.02 * np.sin(2 * np.pi * 11 * gt)
    shriek = osc(gf * vib, "sine") * np.sin(np.pi * gt / 1.0) ** 2 * 0.10
    add_at(out, shriek, dur - 1.05)
    return finish(out, peak=0.88)


def synth_boss_phase_change():
    dur = 2.6
    t = t_axis(dur)
    impact_noise = lowpass(np.random.default_rng(7).normal(0, 1, len(t)), 0.12) * np.exp(-t / 0.14)
    boom = osc(glide(120, 40, dur, 0.35), "sine") * np.exp(-t / 0.55)
    impact = np.tanh((impact_noise * 0.9 + boom * 1.1) * 2.0)

    growl_f = glide_n(140, 42, len(t), 0.55)
    growl_am = 0.6 + 0.4 * np.sin(2 * np.pi * 11 * t)
    growl = np.tanh(osc(growl_f, "saw") * 2.5 * growl_am) * np.exp(-t / 0.95) * 0.8

    screech = np.zeros_like(t)
    for base, seed in ((1244, 31), (1783, 32), (2312, 33)):
        rng = np.random.default_rng(seed)
        jitter = np.cumsum(rng.normal(0, 0.9, len(t)))
        vib = 1 + 0.01 * np.sin(2 * np.pi * 6 * t + jitter / SR * 50)
        screech += osc(base * vib, "sine") * 0.06
    screech *= np.exp(-t / 1.1)
    sub = osc(glide(36, 30, dur, 1.0), "sine") * np.exp(-t / 1.5) * 0.5
    return finish(impact + growl + screech + sub, peak=0.92)


def synth_smart_attack():
    dur = 0.55
    t = t_axis(dur)
    attack_env = np.minimum(t / 0.005, 1) * np.exp(-t / 0.28)
    snarl = (osc(96 * (1 + 0.03 * np.sin(2 * np.pi * 28 * t)), "saw") * 0.7
             + osc(193, "tri") * 0.35)
    snarl = np.tanh(snarl * 2.6) * attack_env

    crack_n = int(SR * 0.006)
    crack_full = np.zeros_like(t)
    crack_full[:min(crack_n, len(crack_full))] = (
        np.random.default_rng(5).normal(0, 1, min(crack_n, len(crack_full)))
        * np.exp(-np.arange(min(crack_n, len(crack_full))) / (SR * 0.0015)) * 0.85)

    thump = osc(glide(78, 48, dur, 0.09), "sine") * np.exp(-t / 0.16) * 0.9
    return finish(snarl + crack_full + thump, peak=0.82)


def synth_smart_dodge():
    dur = 0.45
    out = np.zeros(int(SR * dur))
    n_suck = int(SR * 0.24)
    suck = band_noise(n_suck, 0.05, 0.010, seed=21, gain=1.0)
    suck *= np.linspace(0, 1, n_suck) ** 2
    out[:n_suck] += suck

    t2 = t_axis(0.20)
    exhale = band_noise(len(t2), 0.04, 0.008, seed=22, gain=0.8) * np.exp(-t2 / 0.08)
    gasp = osc(glide(900, 300, 0.20, 0.07), "sine") * np.exp(-t2 / 0.09) * 0.22
    add_at(out, exhale + gasp, 0.24)

    ct = t_axis(0.12)
    chill = osc(3200 * (1 + 0.03 * np.sin(2 * np.pi * 19 * ct)), "sine") \
        * np.sin(np.pi * ct / 0.12) ** 2 * 0.06
    add_at(out, chill, 0.26)
    return finish(out, peak=0.72)


def synth_horde_wave_clear():
    dur = 2.4
    t = t_axis(dur)
    bell = (osc(np.full(len(t), 220.0), "sine") * np.exp(-t / 0.9)
            + osc(np.full(len(t), 607.2), "sine") * np.exp(-t / 0.55) * 0.5
            + osc(np.full(len(t), 1188.0), "sine") * np.exp(-t / 0.30) * 0.28)
    strike = lowpass(np.random.default_rng(17).normal(0, 1, len(t)), 0.4) * np.exp(-t / 0.02) * 0.5
    wind = band_noise(len(t), 0.012, 0.003, seed=29, gain=1.0) * np.linspace(1, 0, len(t)) ** 1.5 * 0.35

    nt = t_axis(0.8)
    lone = osc(np.full(len(nt), 880.0), "sine") * np.exp(-nt / 0.45) * 0.12
    tone = np.zeros_like(t)
    add_at(tone, lone, dur - 0.85)
    return finish(bell * 0.75 + strike + wind + tone, peak=0.75)


def synth_war_cry():
    dur = 1.35
    t = t_axis(dur)
    env = np.minimum(t / 0.08, 1.0) * np.exp(-np.maximum(0.0, t - 0.35) / 0.48)
    f_root = glide_n(82.41, 110.0, len(t), 0.18)
    brass = (osc(f_root, "saw") * 0.55
             + osc(f_root * 1.5, "saw") * 0.35
             + osc(f_root * 2.01, "tri") * 0.22)
    brass = lowpass(np.tanh(brass * 2.4), alpha=0.14, stages=2) * env
    roar_mod = 0.65 + 0.35 * np.sin(2 * np.pi * 23 * t)
    roar = band_noise(len(t), 0.045, 0.008, seed=41, gain=0.9) * roar_mod * env
    sub_hit = osc(glide(95, 42, dur, 0.14), "sine") * np.exp(-t / 0.24) * 0.65
    return finish(brass + roar * 0.55 + sub_hit, peak=0.86)


def write_sounds():
    os.makedirs(SOUND_DIR, exist_ok=True)
    sounds = {
        "horde_wave_start": synth_horde_wave_start(),
        "horde_wave_clear": synth_horde_wave_clear(),
        "boss_phase_change": synth_boss_phase_change(),
        "smart_dodge": synth_smart_dodge(),
        "smart_attack": synth_smart_attack(),
        "war_cry": synth_war_cry(),
    }
    for name, data in sounds.items():
        path = os.path.join(SOUND_DIR, name + ".ogg")
        sf.write(path, data, SR, format="OGG", subtype="VORBIS")
        print(f"ogg: {path} ({len(data)/SR:.2f}s)")


# ================================================================ 2. 6-Face Box-UV 像素画引擎

def scale_rgb(rgb, factor):
    return tuple(int(np.clip(round(c * factor), 0, 255)) for c in rgb)


def mix_rgb(a, b, t):
    return tuple(int(np.clip(round(a[i] * (1.0 - t) + b[i] * t), 0, 255)) for i in range(3))


def fill_rect(img, x0, y0, w, h, color, rng=None, jit=0, vgrad=(1.04, 0.90), bevel=False, trim_col=None):
    if w <= 0 or h <= 0:
        return
    arr = np.zeros((h, w, 4), dtype=np.float32)
    base = np.array(color[:3], dtype=np.float32)
    if vgrad is not None and h > 1:
        gy = np.linspace(vgrad[0], vgrad[1], h, dtype=np.float32)[:, None, None]
        arr[:, :, :3] = base * gy
    else:
        arr[:, :, :3] = base

    if rng is not None and jit > 0:
        arr[:, :, :3] += rng.integers(-jit, jit + 1, size=(h, w, 3))

    if bevel and w >= 3 and h >= 3:
        hi = np.array(trim_col if trim_col else scale_rgb(color[:3], 1.24), dtype=np.float32)
        lo = np.array(scale_rgb(color[:3], 0.68), dtype=np.float32)
        arr[0, :, :3] = arr[0, :, :3] * 0.45 + hi * 0.55
        arr[-1, :, :3] = arr[-1, :, :3] * 0.45 + lo * 0.55
        arr[:, 0, :3] = arr[:, 0, :3] * 0.6 + hi * 0.4
        arr[:, -1, :3] = arr[:, -1, :3] * 0.6 + lo * 0.4

    arr[:, :, :3] = np.clip(arr[:, :, :3], 0, 255)
    arr[:, :, 3] = color[3] if len(color) > 3 else 255
    img[y0:y0 + h, x0:x0 + w] = arr.astype(np.uint8)


def paint_box_uv(img, u, v, w, h, d, color, rng, jit=5, bevel=False, trim_col=None, tip_color=None):
    """
    严格按照 Minecraft CubeListBuilder Box UV 展开绘制 6 个面：
      top:    (u+d,     v,   w, d)
      bottom: (u+d+w,   v,   w, d)
      right:  (u,       v+d, d, h)
      front:  (u+d,     v+d, w, h)
      left:   (u+d+w,   v+d, d, h)
      back:   (u+2d+w,  v+d, w, h)
    """
    top_c = tip_color if tip_color else scale_rgb(color, 1.16)
    bot_c = scale_rgb(color, 0.64)
    right_c = scale_rgb(color, 0.86)
    front_c = color
    left_c = scale_rgb(color, 0.84)
    back_c = scale_rgb(color, 0.76)

    fill_rect(img, u + d, v, w, d, top_c, rng, jit=max(2, jit - 2), vgrad=None, bevel=bevel, trim_col=trim_col)
    fill_rect(img, u + d + w, v, w, d, bot_c, rng, jit=2, vgrad=None)

    for fx, fw, fc in (
        (u, d, right_c),
        (u + d, w, front_c),
        (u + d + w, d, left_c),
        (u + 2 * d + w, w, back_c),
    ):
        fill_rect(img, fx, v + d, fw, h, fc, rng, jit=jit, vgrad=(1.06, 0.86), bevel=bevel, trim_col=trim_col)
        if tip_color is not None and h >= 3 and fw > 0:
            for row in range(min(3, h)):
                t = 1.0 - (row / 3.0)
                blended = mix_rgb(fc, tip_color, t * 0.85)
                img[v + d + row, fx:fx + fw, :3] = blended


def set_px(img, x, y, rgba):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        if len(rgba) == 3:
            img[y, x, :3] = rgba
            img[y, x, 3] = 255
        else:
            img[y, x] = rgba


def add_glow_rect(glow, x0, y0, w, h, rgb, halo_rgb=None):
    if halo_rgb is not None:
        hx0, hy0 = max(0, x0 - 1), max(0, y0 - 1)
        hx1, hy1 = min(glow.shape[1], x0 + w + 1), min(glow.shape[0], y0 + h + 1)
        sub = glow[hy0:hy1, hx0:hx1]
        mask = sub[:, :, 3] == 0
        sub[mask, :3] = halo_rgb
        sub[mask, 3] = 180
    glow[y0:y0 + h, x0:x0 + w, :3] = rgb
    glow[y0:y0 + h, x0:x0 + w, 3] = 255


# ================================================================ 3. SmartZombie 128x128 兵种皮肤与发光图

def build_smart_zombie_pair(role, seed):
    rng = np.random.default_rng(seed)
    img = np.zeros((128, 128, 4), dtype=np.uint8)
    glow = np.zeros((128, 128, 4), dtype=np.uint8)

    palettes = {
        "standard": {
            "skin": (88, 126, 74), "dark": (42, 62, 36), "cloth": (56, 66, 58),
            "pants": (46, 52, 62), "leather": (66, 50, 36), "metal": (122, 130, 136),
            "accent": (185, 62, 52), "eye": (255, 76, 62), "halo": (140, 28, 22),
        },
        "raider": {
            "skin": (82, 114, 108), "dark": (34, 52, 56), "cloth": (38, 52, 64),
            "pants": (30, 38, 48), "leather": (44, 58, 68), "metal": (148, 210, 226),
            "accent": (58, 210, 238), "eye": (96, 245, 255), "halo": (28, 125, 148),
        },
        "vanguard": {
            "skin": (86, 104, 80), "dark": (42, 48, 46), "cloth": (68, 76, 86),
            "pants": (54, 60, 68), "leather": (72, 54, 38), "metal": (152, 160, 172),
            "accent": (236, 152, 48), "eye": (255, 184, 64), "halo": (150, 82, 18),
        },
        "berserker": {
            "skin": (126, 92, 82), "dark": (58, 24, 24), "cloth": (76, 30, 28),
            "pants": (52, 32, 30), "leather": (82, 36, 30), "metal": (198, 184, 162),
            "accent": (220, 42, 42), "eye": (255, 48, 56), "halo": (165, 18, 24),
        },
        "commander": {
            "skin": (84, 104, 74), "dark": (40, 34, 52), "cloth": (66, 38, 78),
            "pants": (46, 34, 58), "leather": (88, 62, 32), "metal": (232, 192, 68),
            "accent": (196, 38, 48), "eye": (255, 226, 78), "halo": (160, 118, 22),
        },
    }
    p = palettes[role]

    # --- A. 基础人形 6 部件 ---
    paint_box_uv(img, 0, 0, 8, 8, 8, p["skin"], rng, jit=5)
    # 头部战痕纹理层 hat (32,0) 留极少量破损绷带/战纹，其余透明避免遮挡面部立体感
    paint_box_uv(img, 16, 16, 8, 12, 4, p["cloth"], rng, jit=5, bevel=True)
    paint_box_uv(img, 40, 16, 4, 12, 4, p["skin"], rng, jit=5)
    paint_box_uv(img, 32, 48, 4, 12, 4, p["skin"], rng, jit=5)
    paint_box_uv(img, 0, 16, 4, 12, 4, p["pants"], rng, jit=4)
    paint_box_uv(img, 16, 48, 4, 12, 4, p["pants"], rng, jit=4)

    # 短袖肩部衣物 + 战靴细节
    fill_rect(img, 40, 20, 16, 4, p["cloth"], rng, jit=4, bevel=True)
    fill_rect(img, 32, 52, 16, 4, p["cloth"], rng, jit=4, bevel=True)
    fill_rect(img, 0, 27, 16, 5, scale_rgb(p["leather"], 0.78), rng, jit=3, bevel=True)
    fill_rect(img, 16, 59, 16, 5, scale_rgb(p["leather"], 0.78), rng, jit=3, bevel=True)

    # 胸腔肋骨与肌肉高光阴影（正面 x=20..28, y=20..32）
    for ry in (22, 24, 26):
        img[ry, 21:23, :3] = scale_rgb(p["cloth"], 1.22)
        img[ry, 25:27, :3] = scale_rgb(p["cloth"], 1.22)
        img[ry + 1, 21:27, :3] = scale_rgb(p["cloth"], 0.72)
    if role == "berserker":
        # 狂战士赤膊露出焦褐肌肉与发光血脉裂纹
        paint_box_uv(img, 16, 16, 8, 12, 4, p["skin"], rng, jit=5)
        for ry in (22, 25, 28):
            img[ry, 21:23, :3] = p["accent"]
            img[ry, 25:27, :3] = p["accent"]
            add_glow_rect(glow, 21, ry, 2, 1, p["eye"])
            add_glow_rect(glow, 25, ry, 2, 1, p["eye"])

    # 面部立体刻画（正面 x=8..16, y=8..16）
    img[10, 8:16, :3] = scale_rgb(p["dark"], 0.85)  # 眉下深阴影槽
    img[11:13, 9:11, :3] = p["dark"]
    img[11:13, 13:15, :3] = p["dark"]
    # 发光瞳孔
    img[12, 9:11, :3] = p["eye"]
    img[12, 13:15, :3] = p["eye"]
    add_glow_rect(glow, 9, 12, 2, 1, p["eye"], p["halo"])
    add_glow_rect(glow, 13, 12, 2, 1, p["eye"], p["halo"])
    # 鼻腔与咧嘴獠牙
    set_px(img, 11, 13, p["dark"])
    set_px(img, 12, 13, p["dark"])
    img[14, 10:14, :3] = scale_rgb(p["dark"], 0.65)
    set_px(img, 10, 14, (215, 210, 190))
    set_px(img, 13, 14, (215, 210, 190))

    # --- B. 通用 3D 雕塑部件：眉骨、战术腰封、双臂护腕、双腿护膝胫甲 ---
    paint_box_uv(img, 64, 0, 8, 2, 1, scale_rgb(p["skin"], 0.82), rng, jit=4, bevel=True)
    paint_box_uv(img, 64, 4, 8, 2, 4, p["leather"], rng, jit=3, bevel=True, trim_col=p["metal"])
    # 腰封正面金属扣 (u+d=68, v+d=8, w=8, h=2) -> 中心 x=71..73
    img[8:10, 71:73, :3] = p["metal"]
    paint_box_uv(img, 64, 11, 4, 4, 4, p["leather"], rng, jit=3, bevel=True, trim_col=p["metal"])
    paint_box_uv(img, 82, 11, 4, 4, 4, p["leather"], rng, jit=3, bevel=True, trim_col=p["metal"])
    greave_base = p["metal"] if role in ("vanguard", "commander") else p["leather"]
    paint_box_uv(img, 50, 96, 4, 5, 4, greave_base, rng, jit=3, bevel=True, trim_col=p["metal"])
    paint_box_uv(img, 68, 96, 4, 5, 4, greave_base, rng, jit=3, bevel=True, trim_col=p["metal"])
    # 护膝正面金属凸缘高光 (正面 u+d=54/72, v+d=100, w=4, h=5)
    img[100:102, 55:57, :3] = scale_rgb(p["metal"], 1.15)
    img[100:102, 73:75, :3] = scale_rgb(p["metal"], 1.15)

    # --- C. 四大精英专属 3D 部件 UV ---
    # 1) RAIDER 兜帽 (64,20,8,8,8) + 飘带 (98,20,6,10,1) + 双袖刃 (98,32,1,8,2), (106,32,1,8,2)
    paint_box_uv(img, 64, 20, 8, 8, 8, scale_rgb(p["cloth"], 0.92), rng, jit=4, bevel=True, trim_col=p["accent"])
    # 镂空兜帽正面内窗 (u+d=72, v+d=28, 8x8) -> 挖空 (73..79, 30..36) 露出内部发光面容！
    img[30:36, 73:79] = (0, 0, 0, 0)
    img[29, 73:79, :3] = p["accent"]
    paint_box_uv(img, 98, 20, 6, 10, 1, p["cloth"], rng, jit=4, bevel=True, trim_col=p["accent"])
    # 飘带下摆燕尾分叉透明镂空
    img[29:31, 101:103] = (0, 0, 0, 0)
    paint_box_uv(img, 98, 32, 1, 8, 2, p["metal"], rng, jit=3, bevel=True, trim_col=(220, 250, 255))
    paint_box_uv(img, 106, 32, 1, 8, 2, p["metal"], rng, jit=3, bevel=True, trim_col=(220, 250, 255))
    if role == "raider":
        add_glow_rect(glow, 99, 35, 3, 6, p["eye"])
        add_glow_rect(glow, 107, 35, 3, 6, p["eye"])

    # 2) VANGUARD 重装条顿面罩头盔 (64,38,8,8,8) + 顶冠 (98,44,2,2,9) + 重胸甲 (0,64,8,10,4) + 双肩甲 (26,64 / 48,64)
    steel = (146, 154, 166) if role != "vanguard" else p["metal"]
    paint_box_uv(img, 64, 38, 8, 8, 8, steel, rng, jit=4, bevel=True, trim_col=(210, 218, 228))
    # T 字铁血面罩缝与炽橙目镜 (正面 x=72..80, y=46..54)
    img[49:51, 73:79, :3] = (28, 24, 24)
    img[50, 73:79, :3] = p["eye"]
    img[51:54, 75:77, :3] = (36, 38, 42)
    if role == "vanguard":
        add_glow_rect(glow, 73, 50, 6, 1, p["eye"], p["halo"])
    paint_box_uv(img, 98, 44, 2, 2, 9, p["accent"], rng, jit=4, bevel=True)
    paint_box_uv(img, 0, 64, 8, 10, 4, steel, rng, jit=4, bevel=True, trim_col=p["accent"])
    paint_box_uv(img, 86, 96, 8, 3, 4, steel, rng, jit=3, bevel=True, trim_col=p["accent"])
    if role == "vanguard":
        img[70:73, 7:9, :3] = p["eye"]
        add_glow_rect(glow, 7, 70, 2, 3, p["eye"], p["halo"])
    paint_box_uv(img, 26, 64, 5, 5, 5, steel, rng, jit=4, bevel=True, trim_col=p["accent"])
    paint_box_uv(img, 48, 64, 5, 5, 5, steel, rng, jit=4, bevel=True, trim_col=p["accent"])

    # 3) BERSERKER 双弯骨角 (70,64 / 80,64, 2x6x2) + 尖刺护肩 (90,64,5,4,5) + 左臂骨爪 (112,96,1,7,2) + 背脊骨刺 (112,64,2,9,2)
    bone_base = (148, 132, 112)
    bone_tip = (245, 70, 60) if role == "berserker" else (228, 218, 198)
    paint_box_uv(img, 70, 64, 2, 6, 2, bone_base, rng, jit=4, tip_color=bone_tip)
    paint_box_uv(img, 80, 64, 2, 6, 2, bone_base, rng, jit=4, tip_color=bone_tip)
    paint_box_uv(img, 90, 64, 5, 4, 5, (92, 40, 36), rng, jit=4, bevel=True, trim_col=bone_base)
    paint_box_uv(img, 112, 96, 1, 7, 2, bone_base, rng, jit=4, tip_color=bone_tip)
    paint_box_uv(img, 112, 64, 2, 9, 2, bone_base, rng, jit=4, bevel=True)
    if role == "berserker":
        add_glow_rect(glow, 72, 66, 2, 2, p["eye"])
        add_glow_rect(glow, 82, 66, 2, 2, p["eye"])
        add_glow_rect(glow, 113, 99, 3, 4, p["eye"])

    # 4) COMMANDER 鎏金战冠 (0,80,8,4,8) + 横羽冠饰 (34,80,2,4,10) + 重肩甲 (60,80 / 82,80) + 统帅胸甲 (0,96) + 皇家战袍披风 (26,96,9,15,1)
    gold = (228, 186, 58)
    crimson = (168, 28, 38)
    paint_box_uv(img, 0, 80, 8, 4, 8, gold, rng, jit=4, bevel=True, trim_col=(255, 236, 130))
    # 皇冠顶沿锯齿镂空 + 正面红宝石徽记 (正面 x=8..16, y=88..92)
    for cx in range(0, 32, 2):
        img[88, cx] = (0, 0, 0, 0)
    img[89:91, 11:13, :3] = (255, 52, 68)
    paint_box_uv(img, 34, 80, 2, 4, 10, crimson, rng, jit=5, tip_color=(255, 210, 72))
    paint_box_uv(img, 60, 80, 5, 5, 5, (72, 44, 86), rng, jit=4, bevel=True, trim_col=gold)
    paint_box_uv(img, 82, 80, 5, 5, 5, (72, 44, 86), rng, jit=4, bevel=True, trim_col=gold)
    paint_box_uv(img, 0, 96, 8, 10, 4, (68, 42, 82), rng, jit=4, bevel=True, trim_col=gold)
    img[102:106, 6:10, :3] = gold
    paint_box_uv(img, 26, 96, 9, 15, 1, crimson, rng, jit=4, bevel=True, trim_col=gold)
    # 披风下摆破损齿边镂空
    for kx in (28, 31, 34, 38, 41, 44):
        img[111, kx] = (0, 0, 0, 0)
    if role == "commander":
        add_glow_rect(glow, 11, 89, 2, 2, (255, 80, 90))
        add_glow_rect(glow, 7, 103, 2, 2, p["eye"], p["halo"])

    # 5) STANDARD 斥候专属 3D 镂空顶战术头箍与单目传感器 (0,112,8,3,8) + 左肩轻型护肩 (34,112,5,4,5) + 战术背囊 (56,112,6,7,2)
    paint_box_uv(img, 0, 112, 8, 3, 8, p["leather"], rng, jit=3, bevel=True, trim_col=p["metal"])
    # 镂空头箍顶面中心 (9..15, 113..119) 与底面 (16..24, 112..120)，露出僵尸绿色头顶，形成真正的立体环状战术头箍！
    img[113:119, 9:15] = (0, 0, 0, 0)
    img[112:120, 16:24] = (0, 0, 0, 0)
    # 额前铁箍与右侧战术侦测镜 (正面 x=8..16, y=120..123)
    img[121:123, 9:15, :3] = p["metal"]
    visor_gem = (84, 238, 132) if role == "standard" else p["eye"]
    img[121:123, 13:15, :3] = visor_gem
    paint_box_uv(img, 34, 112, 5, 4, 5, p["leather"], rng, jit=3, bevel=True, trim_col=p["metal"])
    img[118:120, 40:43, :3] = p["metal"]
    paint_box_uv(img, 56, 112, 6, 7, 2, scale_rgb(p["leather"], 0.90), rng, jit=3, bevel=True, trim_col=p["metal"])
    # 背囊外侧面 (back face x=66..72, y=114..121) 金属搭扣与信号灯
    img[117:119, 68:70, :3] = p["metal"]
    img[115, 68:70, :3] = p["eye"]
    if role == "standard":
        add_glow_rect(glow, 13, 121, 2, 2, visor_gem, (28, 122, 58))
        add_glow_rect(glow, 68, 115, 2, 1, p["eye"])

    # 6) COMMANDER 百夫长鎏金战裙护条 (74,112,8,3,4) + BERSERKER 右臂对称血骨刃 (100,112,1,7,2)
    paint_box_uv(img, 74, 112, 8, 3, 4, crimson, rng, jit=4, bevel=True, trim_col=gold)
    # 正面战裙条分片镂槽 (正面 x=78..86, y=116..119)
    for sx in (80, 82, 84):
        img[117:119, sx, :3] = (34, 20, 32)
    paint_box_uv(img, 100, 112, 1, 7, 2, bone_base, rng, jit=4, tip_color=bone_tip)
    if role == "berserker":
        add_glow_rect(glow, 101, 115, 3, 4, p["eye"])

    return Image.fromarray(img, "RGBA"), Image.fromarray(glow, "RGBA")


# ================================================================ 4. HordeBoss 128x128 泰坦巨兽皮肤与发光图

def build_horde_boss_pair(variant, seed):
    rng = np.random.default_rng(seed)
    img = np.zeros((128, 128, 4), dtype=np.uint8)
    glow = np.zeros((128, 128, 4), dtype=np.uint8)

    palettes = {
        "brute": {
            "skin": (76, 94, 68), "dark": (32, 42, 30), "armor": (78, 76, 74),
            "gauntlet": (58, 56, 56), "trim": (162, 148, 126),
            "horn": (122, 110, 92), "horn_tip": (230, 218, 192),
            "core": (255, 72, 48), "eye": (255, 58, 42), "halo": (148, 24, 18),
        },
        "plague": {
            "skin": (106, 122, 56), "dark": (44, 52, 24), "armor": (62, 70, 42),
            "gauntlet": (50, 58, 34), "trim": (146, 174, 68),
            "horn": (84, 72, 48), "horn_tip": (196, 238, 86),
            "core": (198, 92, 255), "eye": (214, 108, 255), "halo": (98, 36, 138),
        },
        "frost": {
            "skin": (136, 168, 194), "dark": (48, 72, 98), "armor": (68, 98, 128),
            "gauntlet": (54, 82, 112), "trim": (206, 242, 255),
            "horn": (130, 206, 242), "horn_tip": (235, 252, 255),
            "core": (96, 238, 255), "eye": (140, 248, 255), "halo": (38, 128, 162),
        },
        "inferno": {
            "skin": (92, 42, 38), "dark": (38, 16, 16), "armor": (52, 32, 32),
            "gauntlet": (42, 24, 24), "trim": (245, 136, 38),
            "horn": (58, 28, 26), "horn_tip": (255, 208, 68),
            "core": (255, 168, 42), "eye": (255, 232, 92), "halo": (182, 84, 18),
        },
    }
    p = palettes[variant]

    # --- A. 泰坦头颅 (0,0,8,7,8) + 突出眉弓 (34,0,9,2,1) + 铰接咆哮下颚 (34,5,7,2,4) ---
    paint_box_uv(img, 0, 0, 8, 7, 8, p["skin"], rng, jit=5, bevel=True, trim_col=p["trim"])
    paint_box_uv(img, 34, 0, 9, 2, 1, scale_rgb(p["armor"], 1.05), rng, jit=4, bevel=True, trim_col=p["trim"])
    paint_box_uv(img, 34, 5, 7, 2, 4, scale_rgb(p["skin"], 0.85), rng, jit=4, bevel=True)

    # 头颅正面凶目与上排利齿 (正面 x=8..16, y=8..15)
    img[10:13, 9:11, :3] = p["dark"]
    img[10:13, 13:15, :3] = p["dark"]
    img[11:13, 9:11, :3] = p["eye"]
    img[11:13, 13:15, :3] = p["eye"]
    add_glow_rect(glow, 9, 11, 2, 2, p["eye"], p["halo"])
    add_glow_rect(glow, 13, 11, 2, 2, p["eye"], p["halo"])
    img[13:15, 9:15, :3] = p["dark"]
    for fx in (9, 11, 13, 14):
        set_px(img, fx, 14, (232, 224, 204))

    # 下颚獠牙与喉内余烬 (正面 x=38..45, y=9..11; 顶面口腔 x=38..45, y=5..9)
    fill_rect(img, 39, 6, 5, 2, p["core"], rng, jit=3)
    add_glow_rect(glow, 39, 6, 5, 2, p["core"])
    for fx in (38, 40, 42, 44):
        set_px(img, fx, 9, (236, 228, 206))

    # --- B. 倒三角巨兽胸腔 (0,16,11,7,6) + 腹肌核心 (0,30,8,5,5) + 战带 (28,30,9,2,6) + 背脊龙骨 (36,16,2,10,2) ---
    paint_box_uv(img, 0, 16, 11, 7, 6, p["skin"], rng, jit=5, bevel=True, trim_col=p["trim"])
    # 胸腔正中元素炉心与肋骨装甲护栏 (正面 x=6..17, y=22..29)
    fill_rect(img, 9, 23, 5, 4, p["core"], rng, jit=4)
    add_glow_rect(glow, 9, 23, 5, 4, p["core"], p["halo"])
    # 肋骨横梁横跨炉心产生强烈立体层次感
    for ry in (24, 26):
        img[ry, 8:15, :3] = p["armor"]
        glow[ry, 9:14] = (0, 0, 0, 0)
    img[23:27, 11, :3] = p["trim"]

    paint_box_uv(img, 0, 30, 8, 5, 5, scale_rgb(p["skin"], 0.88), rng, jit=4, bevel=True)
    paint_box_uv(img, 28, 30, 9, 2, 6, p["armor"], rng, jit=3, bevel=True, trim_col=p["trim"])
    img[36:38, 37:40, :3] = p["core"]
    add_glow_rect(glow, 38, 36, 1, 2, p["core"])
    paint_box_uv(img, 36, 16, 2, 10, 2, p["horn"], rng, jit=4, bevel=True, trim_col=p["trim"])

    # --- C. 宽肩粗壮上臂 (60,0 / 86,0, 5x6x5) + 粉碎重拳套 (60,12 / 86,12, 6x8x6) + 泰坦肩甲 (0,52 / 30,52, 7x5x7) ---
    paint_box_uv(img, 60, 0, 5, 6, 5, p["skin"], rng, jit=5)
    paint_box_uv(img, 86, 0, 5, 6, 5, p["skin"], rng, jit=5)
    paint_box_uv(img, 60, 12, 6, 8, 6, p["gauntlet"], rng, jit=4, bevel=True, trim_col=p["trim"])
    paint_box_uv(img, 86, 12, 6, 8, 6, p["gauntlet"], rng, jit=4, bevel=True, trim_col=p["trim"])
    # 拳套能量符文刻槽 (正面 x=66..72 / 92..98, y=20..22)
    add_glow_rect(glow, 67, 20, 4, 1, p["core"])
    add_glow_rect(glow, 93, 20, 4, 1, p["core"])
    img[20, 67:71, :3] = p["core"]
    img[20, 93:97, :3] = p["core"]

    paint_box_uv(img, 0, 52, 7, 5, 7, p["armor"], rng, jit=4, bevel=True, trim_col=p["trim"])
    paint_box_uv(img, 30, 52, 7, 5, 7, p["armor"], rng, jit=4, bevel=True, trim_col=p["trim"])

    # 凸出式 3D 元素胸核 (0,96,4,4,1) + 双拳套外侧装甲刃脊 (14,96 / 26,96, 1x7x3) + 铰接护胯战裙 (38,96,6,5,1)
    paint_box_uv(img, 0, 96, 4, 4, 1, p["armor"], rng, jit=3, bevel=True, trim_col=p["trim"])
    fill_rect(img, 2, 98, 2, 2, p["eye"], rng, jit=2)
    add_glow_rect(glow, 2, 98, 2, 2, p["eye"], p["core"])

    paint_box_uv(img, 14, 96, 1, 7, 3, p["trim"], rng, jit=3, bevel=True, trim_col=p["horn_tip"])
    paint_box_uv(img, 26, 96, 1, 7, 3, p["trim"], rng, jit=3, bevel=True, trim_col=p["horn_tip"])
    add_glow_rect(glow, 17, 100, 1, 4, p["core"])
    add_glow_rect(glow, 29, 100, 1, 4, p["core"])

    paint_box_uv(img, 38, 96, 6, 5, 1, p["armor"], rng, jit=3, bevel=True, trim_col=p["trim"])
    img[98:101, 41:43, :3] = p["core"]
    add_glow_rect(glow, 41, 98, 2, 2, p["core"])

    # 腰带立体兽颅战扣 (56,96,4,4,2) + 背部重装护脊甲板 (72,96,7,6,2)
    paint_box_uv(img, 56, 96, 4, 4, 2, p["horn"], rng, jit=3, bevel=True, trim_col=p["trim"])
    # 兽颅战扣双目发光 (正面 x=58..62, y=98..102)
    set_px(img, 59, 99, p["eye"])
    set_px(img, 60, 99, p["eye"])
    add_glow_rect(glow, 59, 99, 2, 1, p["eye"])
    paint_box_uv(img, 72, 96, 7, 6, 2, p["armor"], rng, jit=4, bevel=True, trim_col=p["trim"])
    # 背部护脊甲外侧面 (back face x=83..90, y=98..104) 能量纹槽
    img[100:102, 85:88, :3] = p["core"]
    add_glow_rect(glow, 85, 100, 3, 2, p["core"])

    # --- D. 重装胫甲双腿 (60,28 / 86,28, 5x12x5) + 立体护膝 (60,46 / 86,46, 5x3x1) ---
    paint_box_uv(img, 60, 28, 5, 12, 5, p["armor"], rng, jit=4, bevel=True, trim_col=p["trim"])
    paint_box_uv(img, 86, 28, 5, 12, 5, p["armor"], rng, jit=4, bevel=True, trim_col=p["trim"])
    paint_box_uv(img, 60, 46, 5, 3, 1, p["trim"], rng, jit=3, bevel=True)
    paint_box_uv(img, 86, 46, 5, 3, 1, p["trim"], rng, jit=3, bevel=True)

    # --- E. 四变体专属 3D 附件 ---
    # 1) BRUTE 巨牛角 (60,52 / 70,52, 2x7x2) + 肩刺 (80,52 / 90,52, 2x4x2)
    paint_box_uv(img, 60, 52, 2, 7, 2, p["horn"], rng, jit=4, tip_color=p["horn_tip"])
    paint_box_uv(img, 70, 52, 2, 7, 2, p["horn"], rng, jit=4, tip_color=p["horn_tip"])
    paint_box_uv(img, 80, 52, 2, 4, 2, p["trim"], rng, jit=3, tip_color=p["horn_tip"])
    paint_box_uv(img, 90, 52, 2, 4, 2, p["trim"], rng, jit=3, tip_color=p["horn_tip"])

    # 2) PLAGUE 腐化双叉鹿角 (0,68,2,6,2) + (114,84,2,3,2) + 背部/肩部剧毒脉动孢囊 (10,68,6,6,4) / (32,68,4,4,4)
    paint_box_uv(img, 0, 68, 2, 6, 2, p["horn"], rng, jit=4, tip_color=p["horn_tip"])
    paint_box_uv(img, 114, 84, 2, 3, 2, p["horn"], rng, jit=4, tip_color=p["horn_tip"])
    sac_col = (146, 198, 58)
    paint_box_uv(img, 10, 68, 6, 6, 4, sac_col, rng, jit=6, bevel=True, trim_col=p["core"])
    paint_box_uv(img, 32, 68, 4, 4, 4, sac_col, rng, jit=6, bevel=True, trim_col=p["core"])
    if variant == "plague":
        add_glow_rect(glow, 2, 70, 2, 2, p["horn_tip"])
        add_glow_rect(glow, 116, 86, 2, 1, p["horn_tip"])
        add_glow_rect(glow, 15, 73, 4, 4, (176, 240, 74), p["halo"])
        add_glow_rect(glow, 37, 73, 2, 2, (176, 240, 74), p["halo"])

    # 3) FROST 冰晶王冠基座 (50,68,8,2,8) + 五根参差冰棱尖塔 (64,84..104,84) + 双肩冰棱 (84,68 / 96,68, 2x7x3)
    paint_box_uv(img, 50, 68, 8, 2, 8, p["armor"], rng, jit=4, bevel=True, trim_col=p["horn_tip"])
    paint_box_uv(img, 64, 84, 2, 6, 2, p["horn"], rng, jit=3, tip_color=p["horn_tip"])
    paint_box_uv(img, 74, 84, 2, 4, 2, p["horn"], rng, jit=3, tip_color=p["horn_tip"])
    paint_box_uv(img, 84, 84, 2, 4, 2, p["horn"], rng, jit=3, tip_color=p["horn_tip"])
    paint_box_uv(img, 94, 84, 2, 3, 2, p["horn"], rng, jit=3, tip_color=p["horn_tip"])
    paint_box_uv(img, 104, 84, 2, 3, 2, p["horn"], rng, jit=3, tip_color=p["horn_tip"])
    paint_box_uv(img, 84, 68, 2, 7, 3, p["horn"], rng, jit=4, tip_color=p["horn_tip"])
    paint_box_uv(img, 96, 68, 2, 7, 3, p["horn"], rng, jit=4, tip_color=p["horn_tip"])
    if variant == "frost":
        add_glow_rect(glow, 66, 86, 2, 4, p["core"], p["halo"])
        add_glow_rect(glow, 76, 86, 2, 3, p["core"])
        add_glow_rect(glow, 86, 86, 2, 3, p["core"])
        add_glow_rect(glow, 87, 71, 2, 5, p["core"])
        add_glow_rect(glow, 99, 71, 2, 5, p["core"])

    # 4) INFERNO 后掠熔岩魔角 (0,84 / 10,84, 2x8x2) + 烈焰顶冠 (20,84,2,3,8) + 背部熔炉排口 (42,84,7,6,2)
    paint_box_uv(img, 0, 84, 2, 8, 2, p["horn"], rng, jit=4, tip_color=p["horn_tip"])
    paint_box_uv(img, 10, 84, 2, 8, 2, p["horn"], rng, jit=4, tip_color=p["horn_tip"])
    paint_box_uv(img, 20, 84, 2, 3, 8, (255, 128, 32), rng, jit=5, tip_color=(255, 238, 110))
    paint_box_uv(img, 42, 84, 7, 6, 2, p["armor"], rng, jit=4, bevel=True, trim_col=p["trim"])
    if variant == "inferno":
        add_glow_rect(glow, 2, 86, 2, 3, p["eye"])
        add_glow_rect(glow, 12, 86, 2, 3, p["eye"])
        add_glow_rect(glow, 28, 92, 2, 2, p["eye"], p["halo"])
        # 熔炉背部排烟散热格栅发光 (back face of 42,84,7,6,2 -> u+2d+w = 53, v+d = 86, 7x6)
        for vy in (87, 89, 91):
            img[vy, 54:59, :3] = p["eye"]
            add_glow_rect(glow, 54, vy, 5, 1, p["core"], p["halo"])
            img[vy, 45:50, :3] = p["eye"]
            add_glow_rect(glow, 45, vy, 5, 1, p["core"], p["halo"])

    return Image.fromarray(img, "RGBA"), Image.fromarray(glow, "RGBA")


def build_horde_horn_item():
    """生成 16x16 智潮战号像素图标（雕纹弯牛角 + 紫铜喇叭口 + 绿宝石战徽）。"""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    art = [
        "........OOOOO...",
        ".......OCMMCCO..",
        "......OCMIIIMCO.",
        "......OCMIICCCO.",
        ".......OCHBBCO..",
        "........OBDBCO..",
        "........OEEBO...",
        ".......OBGEBO...",
        ".....OOBHHBO....",
        "...OOCBHHBDO....",
        "..OCBBHHBDO.....",
        ".OCHHHBBDO......",
        ".OCBBDDOO.......",
        "..OOOOO.........",
        "................",
        "................",
    ]
    pal = {
        "O": (26, 22, 24, 255),     # 深色外轮廓
        "I": (38, 26, 24, 255),     # 号角喇叭口深邃内腔
        "M": (236, 156, 88, 255),   # 紫铜喇叭口高光边
        "C": (188, 102, 52, 255),   # 紫铜箍圈主色
        "B": (198, 184, 152, 255),  # 雕纹骨角主色
        "H": (236, 226, 200, 255),  # 骨角受光高光
        "D": (140, 126, 98, 255),   # 骨角背光阴影
        "E": (38, 174, 88, 255),    # 绿宝石镶座
        "G": (126, 255, 168, 255),  # 绿宝石璀璨高光芯
    }
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch in pal:
                img[y, x] = pal[ch]
    return Image.fromarray(img, "RGBA")


def build_smart_helmet_item():
    """生成 16x16 智潮战术头盔像素图标（暗钢重盔 + 绿宝石战术目镜 + 钻石顶脊）。"""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    art = [
        "................",
        ".....OOOOOO.....",
        "...OOMCCCCMOO...",
        "..OMSHDDDDHSMO..",
        ".OMSHHDDDDHHSMO.",
        ".OMSSSMMMMSSSMO.",
        ".OMDGGGGGGGGDMO.",
        ".OMDEEEEEEEEDMO.",
        ".OMSSOOOOOOSSMO.",
        ".OMSSO....OSSMO.",
        ".OMSO......OSMO.",
        "..OO........OO..",
        "................",
        "................",
        "................",
        "................",
    ]
    pal = {
        "O": (20, 24, 30, 255),     # 深色轮廓
        "M": (68, 78, 92, 255),     # 暗钢外缘
        "S": (108, 122, 138, 255),  # 精钢主色
        "H": (162, 180, 198, 255),  # 精钢受光高光
        "C": (82, 224, 232, 255),   # 钻石顶冠高光
        "D": (46, 172, 188, 255),   # 钻石镶甲
        "G": (132, 255, 174, 255),  # 绿宝石战术目镜高光
        "E": (38, 184, 96, 255),    # 绿宝石目镜基底
    }
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch in pal:
                img[y, x] = pal[ch]
    return Image.fromarray(img, "RGBA")


def build_smart_sword_item():
    """生成 16x16 智潮破阵之刃像素图标（手持斜向剑身 + 绿宝石血槽核 + 钻石刃口）。"""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    art = [
        "............OOO.",
        "...........OCCO.",
        "..........OCHDO.",
        ".........OCHDEO.",
        "........OCGDEOO.",
        ".......OCGDEOO..",
        "......OCGDEOO...",
        "...OO.OCDEOO....",
        "..OMMOODEOO.....",
        "..OOGMMOOO......",
        "...OOMGOO.......",
        "...OBBMOO.......",
        "..OBHB.OO.......",
        ".OOBO...........",
        ".OOO............",
        "................",
    ]
    pal = {
        "O": (20, 24, 30, 255),     # 深色剑刃轮廓
        "C": (196, 248, 252, 255),  # 钻石锋刃极高光
        "H": (118, 228, 238, 255),  # 钻石刃面亮青
        "D": (52, 162, 182, 255),   # 剑脊深青钢
        "G": (126, 255, 168, 255),  # 绿宝石血槽能量芯
        "E": (36, 172, 88, 255),    # 绿宝石暗部
        "M": (118, 132, 148, 255),  # 战术十字护手精钢
        "B": (106, 74, 46, 255),    # 握柄皮革
    }
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch in pal:
                img[y, x] = pal[ch]
    return Image.fromarray(img, "RGBA")


def build_smart_tactical_armor_layer():
    """生成 64x32 玩家穿戴 3D 智潮战术头盔贴图（head: 0,0,8,8,8 + hat visor: 32,0,8,8,8）。"""
    rng = np.random.default_rng(707)
    img = np.zeros((32, 64, 4), dtype=np.uint8)
    steel = (88, 102, 118)
    trim = (72, 212, 224)
    emerald = (96, 248, 152)

    # 内层头盔主体 (0, 0, 8, 8, 8)，挖空正面下半部露出玩家口鼻 (x=9..15, y=12..16)
    paint_box_uv(img, 0, 0, 8, 8, 8, steel, rng, jit=3, bevel=True, trim_col=trim)
    img[16:24, 8:16] = (0, 0, 0, 0)  # 底面镂空不挡脖子
    img[12:16, 9:15] = (0, 0, 0, 0)  # 正面下半露脸
    img[10:12, 9:15] = (38, 184, 96, 255)
    img[10, 9:15] = (*emerald, 255)

    # 外层立体战术目镜面罩 (32, 0, 8, 8, 8)：仅保留额冠护条与侧面传感器，层次感鲜明
    img[9:12, 40:48] = (*trim, 255)
    img[10:12, 41:47] = (*emerald, 255)
    img[10:13, 34:38] = (*steel, 255)
    img[11, 35:37] = (*emerald, 255)
    img[10:13, 50:54] = (*steel, 255)
    img[11, 51:53] = (*emerald, 255)
    return Image.fromarray(img, "RGBA")


def write_textures():
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(ITEM_TEX_DIR, exist_ok=True)
    zombie_roles = [
        ("smart_zombie", "standard", 42),
        ("smart_zombie_raider", "raider", 101),
        ("smart_zombie_vanguard", "vanguard", 102),
        ("smart_zombie_berserker", "berserker", 103),
        ("smart_zombie_commander", "commander", 104),
    ]
    for filename, role, seed in zombie_roles:
        tex, glow = build_smart_zombie_pair(role, seed)
        tex_path = os.path.join(TEX_DIR, filename + ".png")
        glow_path = os.path.join(TEX_DIR, filename + "_glow.png")
        tex.save(tex_path)
        glow.save(glow_path)
        print(f"png: {tex_path} + _glow.png (128x128)")

    boss_variants = [
        ("horde_boss_brute", "brute", 201),
        ("horde_boss_plague", "plague", 202),
        ("horde_boss_frost", "frost", 203),
        ("horde_boss_inferno", "inferno", 204),
    ]
    for filename, variant, seed in boss_variants:
        tex, glow = build_horde_boss_pair(variant, seed)
        tex_path = os.path.join(TEX_DIR, filename + ".png")
        glow_path = os.path.join(TEX_DIR, filename + "_glow.png")
        tex.save(tex_path)
        glow.save(glow_path)
        print(f"png: {tex_path} + _glow.png (128x128)")

    horn_path = os.path.join(ITEM_TEX_DIR, "horde_horn.png")
    build_horde_horn_item().save(horn_path)
    print(f"png: {horn_path} (16x16)")

    helm_path = os.path.join(ITEM_TEX_DIR, "smart_helmet.png")
    build_smart_helmet_item().save(helm_path)
    print(f"png: {helm_path} (16x16)")

    sword_path = os.path.join(ITEM_TEX_DIR, "smart_sword.png")
    build_smart_sword_item().save(sword_path)
    print(f"png: {sword_path} (16x16)")

    os.makedirs(ARMOR_TEX_DIR, exist_ok=True)
    armor_path = os.path.join(ARMOR_TEX_DIR, "smart_tactical_layer_1.png")
    build_smart_tactical_armor_layer().save(armor_path)
    print(f"png: {armor_path} (64x32)")


if __name__ == "__main__":
    write_sounds()
    write_textures()
    print("done")
