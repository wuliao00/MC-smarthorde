# -*- coding: utf-8 -*-
"""
SmartHorde 程序化资源生成器（恐怖风 v2）：
- 合成 5 个 .ogg 音效（Vorbis，44.1kHz 单声道）：阴森低语/心跳/不协和音簇风格
- 绘制 5 张 64x64 实体贴图（经典 humanoid UV 布局）

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


# ---------------------------------------------------------------- 基础 DSP

def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def osc(freq_array, wave="sine"):
    """freq_array: 逐采样频率（Hz）。"""
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
    """指数滑频并补齐到指定采样数（尾段保持在 f1）。"""
    seg = glide(f0, f1, min(n_samples / SR, (n_samples) / SR), tau)
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
    """宽带噪声减去更低频的低通 => 中高频带噪声（呼啸/耳语质感）。"""
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
    """双响心跳：咚-咚。"""
    for offset, a in ((0.0, 1.0), (0.34, 0.75)):
        dur = 0.22
        t = t_axis(dur)
        thump = osc(thump_hz * np.exp(-t / 0.3) + 32, "sine") \
            * np.exp(-t / 0.07) * amp * a
        click_n = int(SR * 0.006)
        click = lowpass(np.random.default_rng(int(at * 100 + offset * 7)).normal(0, 1, click_n)) \
            * np.exp(-np.arange(click_n) / (SR * 0.002)) * amp * a * 0.4
        add_at(dst, thump, at + offset)
        add_at(dst, click, at + offset)


# ---------------------------------------------------------------- 恐怖音效

def synth_horde_wave_start():
    """尸潮来袭：心跳渐密 + 小二度不协和音号由远及近 + 耳语声浪 + 高频诡异滑音。"""
    dur = 3.6
    out = np.zeros(int(SR * dur))

    # 心跳：三次，间隔收紧制造紧张
    heartbeat(out, 0.15, 50, 0.55)
    heartbeat(out, 1.15, 54, 0.65)
    heartbeat(out, 2.05, 58, 0.8)

    # 不协和战号：C2 与 C#2 小二度拍频 + 三全音陪衬，缓起，尾段下滑
    t = t_axis(dur)
    env = np.clip((t - 1.0) / 1.2, 0, 1) ** 2
    env[-int(SR * 0.9):] *= np.linspace(1, 0, int(SR * 0.9))
    drop = np.where(t > dur - 1.0, 1.0 - 0.18 * (t - (dur - 1.0)), 1.0)
    horn = (osc(65.41 * drop, "saw") * 0.42
            + osc(69.30 * drop, "saw") * 0.38
            + osc(92.50 * drop, "saw") * 0.22)
    horn = np.tanh(horn * 2.2) * env  # 过驱失真
    out += horn * 0.85

    # 耳语声浪：随机幅度起伏的带通噪声
    whisper = band_noise(len(t), 0.03, 0.007, seed=13, gain=1.0)
    flicker = lowpass(np.random.default_rng(99).normal(0, 1, len(t)), 0.0006)
    flicker = np.abs(flicker) / (np.max(np.abs(flicker)) + 1e-9)
    out += whisper * flicker * env * 0.5

    # 尾部高频诡异滑音（1600 -> 2500 Hz）+ 颤音
    gt = t_axis(1.0)
    gf = 1600 * (1 + 0.9 * (gt / 1.0))
    vib = 1 + 0.02 * np.sin(2 * np.pi * 11 * gt)
    shriek = osc(gf * vib, "sine") * np.sin(np.pi * gt / 1.0) ** 2 * 0.10
    add_at(out, shriek, dur - 1.05)

    return finish(out, peak=0.88)


def synth_boss_phase_change():
    """Boss 狂暴：轰然冲击 + 变调低吼下坠 + 金属尖啸残响 + 次声波尾巴。"""
    dur = 2.6
    t = t_axis(dur)

    # 冲击：噪声爆 + 低频 boom
    impact_noise = lowpass(np.random.default_rng(7).normal(0, 1, len(t)), 0.12) * np.exp(-t / 0.14)
    boom = osc(glide(120, 40, dur, 0.35), "sine") * np.exp(-t / 0.55)
    impact = np.tanh((impact_noise * 0.9 + boom * 1.1) * 2.0)

    # 低吼：锯齿强滑落（140 -> 42 Hz），AM 粗糙化
    growl_f = glide_n(140, 42, len(t), 0.55)
    growl_am = 0.6 + 0.4 * np.sin(2 * np.pi * 11 * t)
    growl = np.tanh(osc(growl_f, "saw") * 2.5 * growl_am) * np.exp(-t / 0.95) * 0.8

    # 金属尖啸：三个带独立随机颤音的高频部分和
    screech = np.zeros_like(t)
    for base, seed in ((1244, 31), (1783, 32), (2312, 33)):
        rng = np.random.default_rng(seed)
        jitter = np.cumsum(rng.normal(0, 0.9, len(t)))
        vib = 1 + 0.01 * np.sin(2 * np.pi * 6 * t + jitter / SR * 50)
        screech += osc(base * vib, "sine") * 0.06
    screech *= np.exp(-t / 1.1)

    # 次声尾巴
    sub = osc(glide(36, 30, dur, 1.0), "sine") * np.exp(-t / 1.5) * 0.5

    return finish(impact + growl + screech + sub, peak=0.92)


def synth_smart_attack():
    """攻击前摇：喉音咆哮短促爆发 + 骨裂脆响 + 闷锤。"""
    dur = 0.55
    t = t_axis(dur)

    # 咆哮：双振荡器 AM 失真，5ms 快起
    attack_env = np.minimum(t / 0.005, 1) * np.exp(-t / 0.28)
    snarl = (osc(96 * (1 + 0.03 * np.sin(2 * np.pi * 28 * t)), "saw") * 0.7
             + osc(193, "tri") * 0.35)
    snarl = np.tanh(snarl * 2.6) * attack_env

    # 骨裂：6ms 白噪爆点（置于起始）
    crack_n = int(SR * 0.006)
    crack_full = np.zeros_like(t)
    crack_full[:min(crack_n, len(crack_full))] = (
        np.random.default_rng(5).normal(0, 1, min(crack_n, len(crack_full)))
        * np.exp(-np.arange(min(crack_n, len(crack_full))) / (SR * 0.0015)) * 0.85)

    # 闷锤
    thump = osc(glide(78, 48, dur, 0.09), "sine") * np.exp(-t / 0.16) * 0.9

    return finish(snarl + crack_full + thump, peak=0.82)


def synth_smart_dodge():
    """闪避：倒吸凉气（反向噪声上扬骤停）+ 下坠气声 + 一丝高频冷颤。"""
    dur = 0.45
    out = np.zeros(int(SR * dur))

    # 反向包络噪声：渐渐拉满后瞬间切断
    n_suck = int(SR * 0.24)
    suck = band_noise(n_suck, 0.05, 0.010, seed=21, gain=1.0)
    suck *= np.linspace(0, 1, n_suck) ** 2
    out[:n_suck] += suck

    # 切断后的呼气与气声下滑（900 -> 300 Hz）
    t2 = t_axis(0.20)
    exhale = band_noise(len(t2), 0.04, 0.008, seed=22, gain=0.8) * np.exp(-t2 / 0.08)
    gasp = osc(glide(900, 300, 0.20, 0.07), "sine") * np.exp(-t2 / 0.09) * 0.22
    tail = exhale + gasp
    add_at(out, tail, 0.24)

    # 高频冷颤点缀
    ct = t_axis(0.12)
    chill = osc(3200 * (1 + 0.03 * np.sin(2 * np.pi * 19 * ct)), "sine") \
        * np.sin(np.pi * ct / 0.12) ** 2 * 0.06
    add_at(out, chill, 0.26)

    return finish(out, peak=0.72)


def synth_horde_wave_clear():
    """清波：丧钟余韵而非欢呼——冷冽钟声（非谐泛音组）+ 寒风退散 + 收束单音。"""
    dur = 2.4
    t = t_axis(dur)

    # 钟：基频 + 非谐泛音组（1 / 2.76 / 5.40），各具衰减
    bell = (osc(np.full(len(t), 220.0), "sine") * np.exp(-t / 0.9)
            + osc(np.full(len(t), 607.2), "sine") * np.exp(-t / 0.55) * 0.5
            + osc(np.full(len(t), 1188.0), "sine") * np.exp(-t / 0.30) * 0.28)
    strike = lowpass(np.random.default_rng(17).normal(0, 1, len(t)), 0.4) * np.exp(-t / 0.02) * 0.5

    # 寒风淡出
    wind = band_noise(len(t), 0.012, 0.003, seed=29, gain=1.0)
    wind *= np.linspace(1, 0, len(t)) ** 1.5 * 0.35

    # 结尾孤高小字五组 A 单音（微弱、空洞）
    nt = t_axis(0.8)
    lone = osc(np.full(len(nt), 880.0), "sine") * np.exp(-nt / 0.45) * 0.12
    tone = np.zeros_like(t)
    add_at(tone, lone, dur - 0.85)

    return finish(bell * 0.75 + strike + wind + tone, peak=0.75)


def write_sounds():
    os.makedirs(SOUND_DIR, exist_ok=True)
    sounds = {
        "horde_wave_start": synth_horde_wave_start(),
        "horde_wave_clear": synth_horde_wave_clear(),
        "boss_phase_change": synth_boss_phase_change(),
        "smart_dodge": synth_smart_dodge(),
        "smart_attack": synth_smart_attack(),
    }
    for name, data in sounds.items():
        path = os.path.join(SOUND_DIR, name + ".ogg")
        sf.write(path, data, SR, format="OGG", subtype="VORBIS")
        print(f"ogg: {path} ({len(data)/SR:.2f}s)")


# ---------------------------------------------------------------- 贴图绘制

def paint_skin(base, dark, eye, eye_size=1, mouth_w=3, blotch=None, eye_glow=None):
    """64x64 经典 humanoid UV：头部(0,0,32,16)、身体(16,16,24,16)、
    右臂(40,16,16,16)、左臂(32,48,16,16)、右腿(0,16,16,16)、左腿(16,48,16,16)。"""
    rng = np.random.default_rng(42)
    img = np.zeros((64, 64, 4), dtype=np.uint8)

    def jitter_fill(x0, y0, w, h, color, jit=10):
        noise = rng.integers(-jit, jit + 1, (h, w, 3))
        img[y0:y0 + h, x0:x0 + w, :3] = np.clip(np.array(color) + noise, 0, 255)
        img[y0:y0 + h, x0:x0 + w, 3] = 255

    # 头部整条 + 帽层留透明
    jitter_fill(0, 0, 32, 16, base)
    # 面部（front face 8,8,8,8）
    es = eye_size
    if eye_glow is not None:  # 先画眼周一圈微光
        for ex in (9, 13):
            img[11:12 + es, ex - 1:ex + es + 1, :3] = eye_glow
    img[12:12 + es, 9:9 + es, :3] = eye
    img[12:12 + es, 13:13 + es, :3] = eye
    img[14:15, 12 - mouth_w // 2:12 - mouth_w // 2 + mouth_w, :3] = dark

    # 四肢与躯干
    jitter_fill(16, 16, 24, 16, base)   # 身体
    jitter_fill(40, 16, 16, 16, base)   # 右臂
    jitter_fill(32, 48, 16, 16, base)   # 左臂
    jitter_fill(0, 16, 16, 16, base)    # 右腿
    jitter_fill(16, 48, 16, 16, base)   # 左腿

    # 破损下摆：四肢底部三行压暗
    for y0 in (29, 61):
        img[y0:y0 + 3, 0:16, :3] = np.clip(img[y0:y0 + 3, 0:16, :3] * 0.55, 0, 255)
        img[y0:y0 + 3, 16:40, :3] = np.clip(img[y0:y0 + 3, 16:40, :3] * 0.55, 0, 255)
        img[y0:y0 + 3, 40:56, :3] = np.clip(img[y0:y0 + 3, 40:56, :3] * 0.55, 0, 255)
        img[y0:y0 + 3, 32:48, :3] = np.clip(img[y0:y0 + 3, 32:48, :3] * 0.55, 0, 255)

    # 躯干暗斑（疫病/余烬风格）
    if blotch:
        for _ in range(14):
            bx = int(rng.integers(18, 38))
            by = int(rng.integers(18, 30))
            s = int(rng.integers(1, 3))
            img[by:by + s, bx:bx + s, :3] = blotch
    return Image.fromarray(img, "RGBA")


def write_textures():
    os.makedirs(TEX_DIR, exist_ok=True)
    textures = {
        # 智能僵尸：暗绿 + 红眼
        "smart_zombie": paint_skin((92, 138, 74), (46, 70, 38), (255, 70, 60), eye_glow=(120, 30, 26)),
        # 蛮横：土绿 + 大红眼
        "horde_boss_brute": paint_skin((74, 96, 66), (30, 44, 28), (255, 50, 40), eye_size=2, mouth_w=4,
                                       eye_glow=(110, 24, 20)),
        # 瘟疫：病黄绿 + 紫眼 + 深色疮斑
        "horde_boss_plague": paint_skin((118, 130, 58), (52, 58, 26), (198, 92, 255), eye_size=2, mouth_w=4,
                                        blotch=(74, 88, 34), eye_glow=(80, 40, 110)),
        # 寒霜：苍白蓝 + 冰蓝眼
        "horde_boss_frost": paint_skin((152, 182, 204), (66, 92, 116), (120, 240, 255), eye_size=2, mouth_w=4,
                                       blotch=(196, 224, 240), eye_glow=(60, 130, 150)),
        # 炼狱：暗红 + 黄眼 + 余烬橙斑
        "horde_boss_inferno": paint_skin((140, 62, 50), (60, 24, 20), (255, 224, 90), eye_size=2, mouth_w=4,
                                         blotch=(232, 120, 40), eye_glow=(160, 110, 30)),
    }
    for name, image in textures.items():
        path = os.path.join(TEX_DIR, name + ".png")
        image.save(path)
        print(f"png: {path}")


if __name__ == "__main__":
    write_sounds()
    write_textures()
    print("done")
