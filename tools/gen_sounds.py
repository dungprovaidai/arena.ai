"""
Procedural sound design for Pathways of the Beyond.

Everything here is synthesized from scratch (no sample libraries), so the whole mod
ships with original audio that shares one sound identity: Victorian occult, close-mic,
low-frequency dread. Design rules (docs/SOUND_BIBLE.md):

  * no jump-scare stingers. The loudest sounds are ritual and boss cues, not scares.
  * horror comes from *uncertainty*: out-of-sync breaths, footsteps with the wrong gait,
    voices at conversational distance that never resolve into words.
  * every system has a signature: ritual = organ + breath + brittle metronome,
    sanity = whispered voice + tape hiss + heartbeat, spirit = cold reverb + wind.

Run:  python3 tools/gen_sounds.py            (all sounds)
      python3 tools/gen_sounds.py bell_ring  (specific sounds)
"""
from __future__ import annotations

import os
import sys

import numpy as np
import soundfile as sf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from content import SOUNDS  # noqa: E402

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "pathwaysofthebeyond",
                   "sounds")

# =======================================================================================
# DSP primitives
# =======================================================================================
def t(dur=1.0):
    return np.linspace(0, dur, int(SR * dur), endpoint=False)


def rng(seed):
    return np.random.default_rng(abs(hash(seed)) % (2 ** 32))


def noise(dur, seed="n", color="white"):
    r = rng(seed)
    n = r.normal(0, 1, int(SR * dur))
    if color == "pink":
        # simple 1/f approximation via cumulative filtering
        b = np.zeros_like(n)
        acc = 0.0
        for i in range(0, len(n), 1):
            acc = 0.98 * acc + 0.02 * n[i]
            b[i] = acc
        n = b / (np.max(np.abs(b)) + 1e-9)
    elif color == "brown":
        n = np.cumsum(n)
        n = n / (np.max(np.abs(n)) + 1e-9)
    return n


def sine(freq, dur, phase=0.0):
    tt = t(dur)
    if callable(freq):
        ph = 2 * np.pi * np.cumsum(freq(tt)) / SR
        return np.sin(ph + phase)
    return np.sin(2 * np.pi * freq * tt + phase)


def saw(freq, dur, detune=0.0):
    tt = t(dur)
    f = freq if not callable(freq) else freq(tt)
    ph = (np.cumsum(f) / SR)
    out = 2 * (ph % 1.0) - 1
    if detune:
        ph2 = (np.cumsum(f * (1 + detune)) / SR)
        out = 0.6 * out + 0.6 * (2 * (ph2 % 1.0) - 1)
    return out


def square(freq, dur, duty=0.5):
    tt = t(dur)
    ph = (np.cumsum(np.full_like(tt, freq) if not callable(freq) else freq(tt)) / SR) % 1.0
    return np.where(ph < duty, 1.0, -1.0)


def env(dur, a=0.005, d=0.1, s=0.0, r=0.2, sustain_level=0.7):
    n = int(SR * dur)
    e = np.zeros(n)
    ai = max(1, int(SR * a))
    di = max(1, int(SR * d))
    ri = max(1, int(SR * r))
    ai = min(ai, n)
    e[:ai] = np.linspace(0, 1, ai)
    d_end = min(n, ai + di)
    e[ai:d_end] = np.linspace(1, sustain_level, d_end - ai)
    if d_end < n:
        if s > 0:
            s_end = max(d_end, n - ri)
            e[d_end:s_end] = sustain_level
            e[s_end:] = np.linspace(sustain_level, 0, n - s_end)
        else:
            e[d_end:] = sustain_level * np.exp(-np.linspace(0, 6, n - d_end))
    return e


def adsr(dur, a, d, s, r):
    """Classic ADSR, sustain given in seconds."""
    n = int(SR * dur)
    e = np.zeros(n)
    ai, di, si = int(SR * a), int(SR * d), int(SR * s)
    ai = min(ai, n)
    e[:ai] = np.linspace(0, 1, ai, endpoint=False) if ai else []
    d_end = min(n, ai + di)
    if d_end > ai:
        e[ai:d_end] = np.linspace(1, 0.65, d_end - ai)
    s_end = min(n, d_end + si)
    if s_end > d_end:
        e[d_end:s_end] = 0.65
    if s_end < n:
        e[s_end:] = np.linspace(0.65, 0, n - s_end)
    return e


def _biquad(x, b0, b1, b2, a1, a2):
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i in range(len(x)):
        v = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, x[i]
        y2, y1 = y1, v
        y[i] = v
    return y


def lowpass(x, cutoff, q=0.707):
    w0 = 2 * np.pi * cutoff / SR
    alpha = np.sin(w0) / (2 * q)
    b0, b1, b2 = (1 - np.cos(w0)) / 2, 1 - np.cos(w0), (1 - np.cos(w0)) / 2
    a0, a1, a2 = 1 + alpha, -2 * np.cos(w0), 1 - alpha
    return _biquad(x, b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)


def highpass(x, cutoff, q=0.707):
    w0 = 2 * np.pi * cutoff / SR
    alpha = np.sin(w0) / (2 * q)
    b0, b1, b2 = (1 + np.cos(w0)) / 2, -(1 + np.cos(w0)), (1 + np.cos(w0)) / 2
    a0, a1, a2 = 1 + alpha, -2 * np.cos(w0), 1 - alpha
    return _biquad(x, b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)


def bandpass(x, center, q=1.0):
    w0 = 2 * np.pi * center / SR
    alpha = np.sin(w0) / (2 * q)
    b0, b1, b2 = alpha, 0.0, -alpha
    a0, a1, a2 = 1 + alpha, -2 * np.cos(w0), 1 - alpha
    return _biquad(x, b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)


def comb(x, delay_s, feedback=0.6):
    """Feedback comb. Feedback is hard-clamped below 1 - an unstable comb turns the
    whole tail into NaNs, which is exactly how 20 sounds shipped silent once."""
    feedback = float(np.clip(feedback, -0.95, 0.95))
    d = max(1, int(SR * delay_s))
    y = np.copy(x)
    for i in range(d, len(y)):
        y[i] += feedback * y[i - d]
    return y


def allpass(x, delay_s, g=0.5):
    """Schroeder allpass: y[n] = -g*x[n] + x[n-D] + g*y[n-D].

    The feedback coefficient must be g (never 1+g) or the filter grows without bound
    and the whole reverb tail becomes NaN.
    """
    g = float(np.clip(g, -0.85, 0.85))
    d = max(1, int(SR * delay_s))
    src = np.copy(x)
    y = np.copy(x)
    for i in range(d, len(y)):
        y[i] = -g * src[i] + src[i - d] + g * y[i - d]
    return y


def reverb(x, decay=1.6, wet=0.35, seed="rv", damp=4500):
    """Schroeder-style reverb: four combs in parallel into two allpasses."""
    dry = x
    w = np.zeros_like(x)
    for (dt, fb) in ((0.0297, 0.78), (0.0371, 0.74), (0.0411, 0.71), (0.0437, 0.68)):
        # longer musical decay -> feedback closer to (but never past) 1.0
        fbe = np.clip(fb * (decay / 1.6) ** 0.5, 0.0, 0.93)
        w += comb(x, dt, fbe)
    w /= 4
    w = allpass(w, 0.005, 0.7)
    w = allpass(w, 0.0017, 0.6)
    w = lowpass(w, damp)
    return (1 - wet) * dry + wet * (w / (np.max(np.abs(w)) + 1e-9)) * (np.max(np.abs(dry)) + 1e-9)


def chorus(x, depth_s=0.002, rate=0.4, mix=0.4):
    tt = t(len(x) / SR)
    mod = (np.sin(2 * np.pi * rate * tt) * depth_s * SR).astype(int)
    y = np.zeros_like(x)
    for i in range(len(x)):
        j = i - mod[i]
        y[i] = x[j] if 0 <= j < len(x) else 0.0
    return (1 - mix) * x + mix * y


def distort(x, amount=0.6):
    return np.tanh(x * (1 + 4 * amount))


def reverse(x):
    return x[::-1].copy()


def fade(x, ms=8):
    x = np.nan_to_num(x)
    n = max(1, int(SR * ms / 1000))
    n = min(n, len(x) // 2)
    e = np.ones_like(x)
    e[:n] = np.linspace(0, 1, n)
    e[-n:] = np.linspace(1, 0, n)
    return x * e


def norm(x, peak=0.85):
    x = np.nan_to_num(np.asarray(x, dtype=float), nan=0.0, posinf=0.0, neginf=0.0)
    m = np.max(np.abs(x))
    if m < 1e-9:
        return x
    return x / m * peak


def mix(*parts):
    parts = [np.nan_to_num(np.asarray(p, dtype=float)) for p in parts]
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def place(target, src, at_s, gain=1.0):
    i = int(SR * float(at_s))
    if i >= len(target):
        return target
    end = min(len(target), i + len(src))
    target[i:end] += gain * src[:end - i]
    return target


def loopable(x, ms=120):
    x = np.nan_to_num(x)
    """Crossfade the tail into the head so an ambient loop has no seam."""
    n = int(SR * ms / 1000)
    n = min(n, len(x) // 4)
    head, tail = x[:n].copy(), x[-n:].copy()
    ramp = np.linspace(0, 1, n)
    x = x[:-n].copy()
    x[:n] = head * ramp + tail * (1 - ramp)
    return x


# =======================================================================================
# Instrument recipes
# =======================================================================================
def organ_chord(dur, freqs=(55, 82.5, 110, 164.8, 220), level=0.3, vibrato=0.4):
    out = np.zeros(int(SR * dur))
    tt = t(dur)
    for f in freqs:
        det = 1 + 0.0016 * np.sin(2 * np.pi * vibrato * tt + f)
        tone = np.sin(2 * np.pi * f * tt * det) * 0.6
        tone += np.sin(2 * np.pi * f * 2 * tt * det) * 0.18       # drawbar second harmonic
        tone += np.sin(2 * np.pi * f * 3 * tt * det) * 0.08
        out += tone * level
    return lowpass(out, 2600)


def bell_partials(dur, base=520.0, ratios=(1.0, 2.76, 5.40, 8.93, 11.3), decays=(1.0, 0.62, 0.44, 0.3, 0.2),
                  level=0.5):
    out = np.zeros(int(SR * dur))
    for r, d in zip(ratios, decays):
        e = np.exp(-np.linspace(0, 9 / d, len(out)))
        out += np.sin(2 * np.pi * base * r * t(dur)) * e * level * (1.0 / (1 + (r - 1) * 0.5))
    return out


def voice_vowel(dur, f0=110.0, vowel="a", seed="v"):
    """Wordless choir voice: sawtooth through three formant bandpasses."""
    FORMANTS = {
        "a": [(730, 1.0), (1090, 0.5), (2440, 0.25)],
        "e": [(530, 1.0), (1840, 0.45), (2480, 0.2)],
        "i": [(270, 1.0), (2290, 0.4), (3010, 0.2)],
        "o": [(570, 1.0), (840, 0.6), (2410, 0.2)],
        "u": [(300, 1.0), (870, 0.5), (2240, 0.15)],
        "m": [(250, 1.0), (1150, 0.3), (1900, 0.1)],   # hummed
    }
    r = rng(seed)
    jitter = 1 + 0.004 * np.cumsum(r.normal(0, 1, len(t(dur)))) * np.sqrt(1 / SR) * 30
    src = saw(f0, dur) * 0.5 + noise(dur, seed) * 0.12
    src *= jitter[:len(src)]
    out = np.zeros_like(src)
    for (f, g) in FORMANTS[vowel]:
        out += bandpass(src, f, q=6.0) * g
    return lowpass(out, 4000)


def footstep(dur=0.28, seed="fs", weight=0.7, pitch=90.0):
    s = noise(dur, seed, "white") * env(dur, a=0.001, d=0.02, s=0.0, r=0.05)
    thump = sine(pitch, dur) * np.exp(-np.linspace(0, 14, int(SR * dur)))
    body = bandpass(s, 900, q=1.2) * 0.5 + thump * weight
    return lowpass(body, 3200)


def wood_knock(dur=0.5, seed="kn", freq=210.0, hits=3, spacing=0.19):
    out = np.zeros(int(SR * dur))
    for i in range(hits):
        k = noise(0.16, f"{seed}{i}") * env(0.16, 0.001, 0.03, 0, 0.06)
        res = mix(bandpass(k, freq, q=9.0), bandpass(k, freq * 2.4, q=11.0) * 0.4)
        place(out, norm(res, 0.7 - 0.08 * i), i * spacing)
    return out


def breath(dur=1.4, seed="br", rate=0.5, tone=800.0):
    s = noise(dur, seed, "pink")
    mod = 0.5 + 0.5 * np.sin(2 * np.pi * rate * t(dur) - 1.2)
    out = bandpass(s, tone, q=1.1) * mod
    return lowpass(out, 3800) * env(dur, 0.25, 0.2, 0.3, 0.4)


def heartbeat(dur=1.2, seed="hb", bpm=62, second_heart=False):
    out = np.zeros(int(SR * dur))
    period = 60.0 / bpm
    thump = lambda s, f: lowpass(sine(f, 0.30) * np.exp(-np.linspace(0, 12, int(SR * 0.30))), 220)
    beat = norm(mix(thump(seed, 58), noise(0.30, seed + "n") * env(0.30, 0.001, 0.02, 0, 0.1) * 0.25), 0.8)
    place(out, beat, 0.02)
    place(out, beat * 0.7, 0.30)                       # the second thump of the pair
    if second_heart:                                   # a second heart, slightly late
        place(out, beat * 0.55, 0.02 + 0.075)
        place(out, beat * 0.4, 0.30 + 0.09)
    return out


def whisper(dur=2.4, seed="wh", voices=3, distance=1.0):
    """Voices at conversational distance that never resolve into words."""
    out = np.zeros(int(SR * dur))
    for i in range(voices):
        r = rng(f"{seed}{i}")
        syl = r.integers(6, 12)
        v = np.zeros(int(SR * dur))
        for s in range(syl):
            start = r.uniform(0, dur - 0.3)
            length = r.uniform(0.10, 0.26)
            vv = voice_vowel(length, f0=95 + r.uniform(-22, 26),
                             vowel=r.choice(["a", "e", "i", "o", "u", "m"]), seed=f"{seed}{i}{s}")
            vv *= env(length, 0.02, 0.05, 0.02, 0.06)
            place(v, vv * r.uniform(0.4, 1.0), start)
        v = bandpass(v, 1400 / distance, q=0.8)
        v = lowpass(v, 2600 / distance)
        out += v * (1.0 / (1 + i))
    out = reverb(out, decay=1.1, wet=0.4 if distance > 1.2 else 0.22, seed=seed)
    return norm(out, 0.72)


def wind(dur=6.0, seed="wd", level=0.4):
    s = noise(dur, seed, "pink")
    tt = t(dur)
    swell = 0.5 + 0.5 * np.sin(2 * np.pi * 0.07 * tt) * np.sin(2 * np.pi * 0.021 * tt + 1.1)
    out = bandpass(s, 420, q=0.5) * swell
    out += bandpass(noise(dur, seed + "2", "brown"), 120, q=0.7) * 0.5
    return norm(out, level)


def metallic_click(dur=0.18, freq=2400.0, seed="mc"):
    k = noise(dur, seed) * env(dur, 0.0005, 0.01, 0, 0.05)
    return mix(highpass(k, 1200), sine(freq, dur) * np.exp(-np.linspace(0, 20, int(SR * dur))) * 0.3)


def low_rumble(dur=4.0, seed="lr", freq=32.0):
    tt = t(dur)
    base = sine(freq, dur) + sine(freq * 1.51, dur) * 0.5 + sine(freq * 0.5, dur) * 0.7
    drift = 1 + 0.02 * np.sin(2 * np.pi * 0.13 * tt)
    return lowpass(base * drift, 120) * env(dur, 0.6, 1.0, 1.5, 1.2)


def glitch_static(dur=1.6, seed="gs"):
    s = noise(dur, seed, "white")
    tt = t(dur)
    gate = (np.sin(2 * np.pi * 11 * tt) > 0.2).astype(float)
    buried = sine(1740, dur) * 0.03 * gate
    return norm(highpass(s, 700) * 0.35 * gate + buried, 0.5)


def organic_wet(dur=0.7, seed="ow", pitch=140.0):
    s = noise(dur, seed, "brown")
    mod = sine(pitch, dur) * 0.5 + 0.5
    return lowpass(s * mod, 1500) * env(dur, 0.02, 0.2, 0.1, 0.3)


# =======================================================================================
# The sound bank: name -> generator
# =======================================================================================
def s_potion_drink():
    d = 1.35
    out = np.zeros(int(SR * d))
    for i in range(3):
        gulp = norm(lowpass(noise(0.16, f"g{i}", "brown") *
                            env(0.16, 0.006, 0.05, 0, 0.06), 1600), 0.6)
        gulp *= 1 + 0.3 * sine(150 + i * 40, 0.16)
        place(out, gulp, 0.05 + i * 0.22)
    place(out, bell_partials(0.9, 190.0, (1, 2.1, 3.4), (1, 0.4, 0.2)) * 0.25, 0.16)
    return norm(reverb(out, 0.9, 0.25, "pd"), 0.8)


def s_potion_brew():
    d = 2.6
    out = np.zeros(int(SR * d))
    r = rng("brew")
    for i in range(26):
        at = r.uniform(0, d - 0.2)
        b = lowpass(noise(0.14, f"b{i}", "brown") * env(0.14, 0.001, 0.03, 0, 0.07),
                    r.uniform(500, 2200))
        place(out, norm(b, r.uniform(0.15, 0.5)), at)
    out += breath(d, "brewbr", rate=0.35, tone=520) * 0.35
    return norm(out, 0.7)


def s_potion_finish():
    d = 1.6
    out = mix(norm(metallic_click(0.2, 2100, "stop"), 0.5),
              np.zeros(int(SR * 1.6)))
    out = place(out, breath(1.3, "sigh", 0.4, 700) * 0.5, 0.25)
    out = place(out, bell_partials(1.2, 320, (1, 1.98, 3.1), (1, 0.5, 0.3)) * 0.3, 0.3)
    return norm(out, 0.75)


def s_codex_open():
    d = 1.5
    out = mix(lowpass(noise(0.55, "leather", "brown") * env(0.55, 0.02, 0.2, 0.2, 0.25), 1400) * 0.5,
              np.zeros(int(SR * d)))
    out = place(out, metallic_click(0.16, 3100, "clasp"), 0.28)
    out = place(out, norm(bandpass(noise(0.4, "page", "white") * env(0.4, 0.05, 0.2, 0.05, 0.15), 4200), 0.3), 0.5)
    out = place(out, whisper(1.0, "openwh", 1, 1.4) * 0.25, 0.55)
    return norm(out, 0.7)


def s_codex_page():
    d = 0.45
    out = norm(bandpass(noise(d, "pg", "white") * env(d, 0.03, 0.2, 0.05, 0.2), 5000), 0.35)
    n = np.zeros(int(SR * d))
    place(n, bandpass(noise(0.3, "pg2", "pink") * env(0.3, 0.1, 0.2, 0.0, 0.15), 1800) * 0.1, 0.05)
    out = out + n
    return norm(fade(out), 0.5)


def s_bell_ring():
    d = 4.0
    out = bell_partials(d, 430.0, (0.86, 1.0, 1.62, 2.76, 5.40, 8.93),
                        (0.55, 1.0, 0.75, 0.6, 0.4, 0.25), level=0.6)
    # a cracked bell has a beat frequency; the clapper was removed, so no strike transient
    out += bell_partials(d, 437.0, (0.86, 1.0, 1.62, 2.76, 5.40, 8.93),
                         (0.5, 0.95, 0.7, 0.5, 0.35, 0.2), level=0.45)
    out *= env(d, 0.01, 1.2, 1.2, 1.4)
    return norm(reverb(out, 2.2, 0.42, "bell", 3000), 0.85)


def s_bell_answer():
    d = 6.0
    out = low_rumble(d, "answer", 26) * 0.8
    out += bell_partials(d, 108.0, (1, 1.5, 2.3, 3.7), (1, 0.8, 0.6, 0.4), level=0.5)
    out += wind(d, "answerw", 0.22)
    return norm(reverb(out, 3.2, 0.5, "answer2", 2000), 0.9)


def s_artifact_activate():
    d = 1.8
    out = np.zeros(int(SR * d))
    place(out, metallic_click(0.2, 1800, "act1"), 0.0, 0.6)
    place(out, metallic_click(0.16, 2600, "act2"), 0.09, 0.4)
    place(out, breath(1.1, "actbr", 0.5, 900) * 0.45, 0.12)
    pad = organ_chord(1.4, (110, 165, 220), level=0.22)
    place(out, pad, 0.2, 0.6)
    place(out, bell_partials(1.4, 660, (1, 2.2, 4.1), (1, 0.5, 0.25)) * 0.3, 0.35)
    return norm(reverb(out, 1.6, 0.3, "act"), 0.8)


def s_artifact_curse():
    d = 2.6
    out = np.zeros(int(SR * d))
    place(out, reverse(bell_partials(1.2, 520, (1, 2.76, 5.4), (1, 0.6, 0.3))), 0.0, 0.5)
    place(out, voice_vowel(1.1, 120, "o", "cursev") * env(1.1, 0.2, 0.3, 0.3, 0.4), 0.7, 0.4)
    place(out, low_rumble(1.6, "curse", 30) * 0.5, 0.9, 0.7)
    place(out, metallic_click(0.3, 900, "cursecl"), 2.3, 0.3)
    return norm(reverb(out, 2.4, 0.45, "curse2", 2600), 0.85)


def s_thread_bind():
    d = 1.1
    out = np.zeros(int(SR * d))
    # fibre tightening: a pitch-rising filtered noise band
    tt = t(0.5)
    rise = norm(bandpass(noise(0.5, "bind", "white") * env(0.5, 0.01, 0.4, 0.05, 0.08),
                        900, q=4.0), 0.4)
    place(out, rise, 0.0, 0.6)
    place(out, organic_wet(0.4, "bind2", 90), 0.42, 0.5)
    place(out, sine(220, 0.3) * np.exp(-np.linspace(0, 10, int(SR * 0.3))) * 0.2, 0.45, 1.0)
    return norm(out, 0.75)


def s_thread_sever():
    d = 1.0
    out = np.zeros(int(SR * d))
    snap = highpass(noise(0.12, "sev", "white") * env(0.12, 0.0005, 0.02, 0, 0.04), 1500)
    place(out, norm(snap, 0.7), 0.0)
    place(out, lowpass(noise(0.3, "sev2", "brown") * env(0.3, 0.001, 0.1, 0, 0.15), 700), 0.02, 0.35)
    place(out, bell_partials(0.9, 340, (1, 2.3, 4.4), (1, 0.4, 0.2)) * 0.22, 0.03)
    return norm(reverb(out, 1.1, 0.25, "sev3"), 0.8)


def s_thread_tension():
    d = 3.0
    tt = t(d)
    drone = 0.0
    for f in (196, 199, 293, 299):
        drone = drone + np.sin(2 * np.pi * f * tt * (1 + 0.004 * np.sin(2 * np.pi * 0.3 * tt)))
    out = lowpass(drone * 0.22, 2200)
    out *= 0.4 + 0.6 * np.clip(np.linspace(0, 1.3, len(out)), 0, 1)
    out += bandpass(noise(d, "tens", "pink") * 0.1, 1600, q=2.0)
    return norm(out, 0.6)


def s_chalk_draw():
    d = 1.4
    out = np.zeros(int(SR * d))
    r = rng("chalk")
    for i in range(9):
        length = r.uniform(0.12, 0.4)
        grain = noise(length, f"ch{i}", "white")
        grain = bandpass(grain, r.uniform(2400, 5200), q=1.4) * env(length, 0.01, 0.1, 0.1, 0.1)
        place(out, norm(grain, r.uniform(0.2, 0.5)), r.uniform(0, d - length))
    out += highpass(noise(d, "chalkb", "pink"), 3000) * 0.06
    return norm(out, 0.55)


def s_dagger_cut():
    d = 1.2
    out = np.zeros(int(SR * d))
    cut = highpass(noise(0.14, "cut", "white") * env(0.14, 0.001, 0.03, 0, 0.05), 2200)
    place(out, norm(cut, 0.55), 0.0)
    # blood hitting stone
    for i in range(3):
        place(out, norm(lowpass(noise(0.12, f"drip{i}", "brown") * env(0.12, 0.001, 0.04, 0, 0.05), 900), 0.25),
              0.28 + i * 0.16)
    place(out, breath(0.8, "cuthr", 0.6, 600) * 0.2, 0.1)
    return norm(out, 0.7)


def s_ritual_start():
    d = 5.0
    out = np.zeros(int(SR * d))
    r = rng("rstart")
    for i in range(8):                    # candles catching, one by one
        place(out, norm(bandpass(noise(0.3, f"cd{i}", "pink") * env(0.3, 0.02, 0.15, 0.05, 0.12), 1600, 1.0), 0.3),
              i * 0.26 + r.uniform(0, 0.06))
    place(out, organ_chord(3.6, (55, 82.5, 110, 138.6, 164.8), level=0.2), 1.5, 0.8)
    place(out, breath(3.2, "rstbr", 0.25, 420) * 0.3, 1.6)
    place(out, bell_partials(2.6, 165, (1, 2.76, 5.4), (1, 0.5, 0.25)) * 0.28, 1.9)
    return norm(reverb(out, 2.6, 0.4, "rstart2", 3000), 0.85)


def s_ritual_loop():
    d = 8.0
    out = organ_chord(d, (55, 82.5, 110, 164.8), level=0.16, vibrato=0.22)
    out += breath(d, "rloop", 0.18, 300) * 0.22
    tt = t(d)
    tick = np.zeros_like(tt)
    for i in range(int(d)):            # brittle metronome
        place(tick, norm(metallic_click(0.07, 3400, f"tick{i}"), 0.18), float(i))
    out += tick
    out += wind(d, "rloopw", 0.12)
    out = reverb(out, 2.0, 0.35, "rloop2", 2600)
    return norm(loopable(out), 0.6)


def s_ritual_pulse():
    d = 2.2
    out = np.zeros(int(SR * d))
    place(out, norm(lowpass(sine(85, 0.5) * np.exp(-np.linspace(0, 9, int(SR * 0.5))), 400), 0.65), 0.0)
    place(out, reverse(norm(highpass(noise(0.9, "pulse", "white") * env(0.9, 0.4, 0.3, 0.1, 0.2), 1800), 0.28)), 0.0)
    place(out, voice_vowel(1.4, 98, "a", "pulsev") * env(1.4, 0.3, 0.3, 0.4, 0.4) * 0.35, 0.15)
    return norm(reverb(out, 2.4, 0.45, "pulse2", 2800), 0.8)


def s_ritual_chant():
    d = 6.0
    out = np.zeros(int(SR * d))
    r = rng("chant")
    for i in range(6):                     # layered wordless chant
        f0 = 92 + i * 7 + r.uniform(-3, 3)
        v = voice_vowel(d, f0, "m" if i % 3 == 0 else "o", f"chant{i}")
        envv = adsr(d, 0.6 + i * 0.05, 0.8, 3.4, 1.1)
        out += v[:len(envv)] * envv * 0.3
    out = bandpass(out, 700, q=0.8)
    return norm(reverb(out, 3.0, 0.5, "chant2", 2400), 0.75)


def s_ritual_complete():
    d = 5.0
    out = np.zeros(int(SR * d))
    # chord resolving upward
    for i, f in enumerate((110, 138.6, 164.8, 220, 277.2)):
        start = 0.9 + i * 0.28
        tone = organ_chord(3.0, (f,), level=0.3)
        place(out, tone * env(3.0, 0.05, 0.6, 1.4, 1.0), start, 0.55)
    place(out, wind(1.4, "cmpl", 0.3), 0.0, 0.4)
    place(out, bell_partials(3.2, 300, (1, 2.0, 3.4, 5.1), (1, 0.6, 0.4, 0.2)) * 0.35, 1.6)
    place(out, norm(lowpass(noise(0.6, "cmpld", "brown") * env(0.6, 0.01, 0.3, 0.1, 0.3), 400), 0.3), 0.1)
    return norm(reverb(out, 3.4, 0.45, "cmpl2", 3200), 0.9)


def s_ritual_fail():
    d = 3.4
    out = np.zeros(int(SR * d))
    # a dropped instrument, then the room exhaling
    place(out, norm(bell_partials(1.6, 240, (1, 1.43, 2.7, 4.9), (1, 0.7, 0.5, 0.3), level=0.5), 0.7), 0.0)
    for i in range(3):
        place(out, norm(lowpass(noise(0.2, f"drop{i}", "brown") * env(0.2, 0.002, 0.06, 0, 0.1), 600), 0.4),
              0.05 + i * 0.13)
    place(out, breath(2.2, "exhale", 0.16, 260) * 0.6, 0.6)
    place(out, low_rumble(2.0, "failr", 28) * 0.5, 0.7)
    place(out, glitch_static(1.2, "failg") * 0.22, 1.1)
    return norm(reverb(out, 2.8, 0.45, "fail2", 2400), 0.85)


def s_whisper_near():
    return whisper(2.6, "whn", voices=2, distance=0.85)


def s_whisper_far():
    return whisper(3.4, "whf", voices=5, distance=2.2)


def s_heartbeat():
    return heartbeat(2.0, "hb", bpm=58)


def s_breathing():
    d = 4.0
    out = np.zeros(int(SR * d))
    for i in range(2):
        place(out, breath(1.7, f"brth{i}", 0.38 + i * 0.05, 560) * 0.55, i * 1.9 + 0.05)
    # deliberately out of sync with the player's own breathing
    place(out, breath(1.6, "brth2b", 0.30, 720) * 0.3, 0.9)
    return norm(out, 0.6)


def s_knock():
    return wood_knock(1.1, "kn", 205.0, 3, 0.21)


def s_rumble():
    return norm(reverb(low_rumble(5.0, "rmb", 30), 3.0, 0.3, "rmb2", 600), 0.8)


def s_static():
    return glitch_static(2.6, "stc")


def s_watcher_stare():
    d = 1.6
    tt = t(d)
    tone = sine(3200, d) * 0.12 + sine(3210, d) * 0.12
    tone *= np.clip(np.linspace(0, 1.4, len(tt)), 0, 1) ** 2
    out = mix(tone, bandpass(noise(d, "stare", "pink") * 0.18, 2600, q=1.0))
    return norm(reverb(out, 1.4, 0.3, "stare2"), 0.55)


def s_watcher_vanish():
    d = 1.2
    out = np.zeros(int(SR * d))
    place(out, reverse(breath(0.9, "vanish", 0.5, 700) * 0.5), 0.0)
    place(out, norm(lowpass(noise(0.3, "vanish2", "brown") * env(0.3, 0.02, 0.2, 0.02, 0.1), 500), 0.25), 0.55)
    return norm(reverb(out, 1.2, 0.3, "vanish3"), 0.6)


def s_hollow_step():
    return norm(footstep(0.4, "hstep", 0.9, 62.0) * 1.0, 0.7)


def s_hollow_idle():
    d = 3.0
    out = np.zeros(int(SR * d))
    r = rng("hidle")
    for i in range(6):                # wet joint adjustments
        place(out, norm(organic_wet(0.3, f"hj{i}", r.uniform(70, 140)), 0.25), r.uniform(0, 2.5))
    place(out, breath(2.0, "hidleb", 0.28, 420) * 0.35, 0.6)
    return norm(reverb(out, 1.6, 0.3, "hidle2"), 0.6)


def s_husk_whisper():
    d = 2.8
    out = whisper(d, "huskw", voices=4, distance=0.7)
    out += voice_vowel(d, 74, "o", "huskv") * env(d, 0.3, 0.5, 1.0, 0.8) * 0.35
    return norm(reverb(out, 1.8, 0.4, "huskw2", 2600), 0.75)


def s_husk_attack():
    d = 1.4
    out = np.zeros(int(SR * d))
    place(out, norm(highpass(noise(0.35, "hak", "white") * env(0.35, 0.004, 0.12, 0.05, 0.15), 900), 0.6), 0.0)
    place(out, voice_vowel(0.7, 130, "a", "hakv") * env(0.7, 0.01, 0.2, 0.1, 0.25) * 0.7, 0.03)
    place(out, norm(lowpass(noise(0.5, "hak2", "brown") * env(0.5, 0.005, 0.2, 0.05, 0.2), 700), 0.35), 0.12)
    return norm(out, 0.8)


def s_marionette_joint():
    d = 1.0
    out = np.zeros(int(SR * d))
    for i in range(4):
        place(out, norm(metallic_click(0.1, 900 + i * 220, f"mj{i}"), 0.3), i * 0.14)
    place(out, norm(bandpass(noise(0.5, "mjw", "white") * env(0.5, 0.02, 0.3, 0.05, 0.2), 1600, q=3.0), 0.2), 0.0)
    return norm(out, 0.6)


def s_choirmaster_chant():
    d = 6.0
    out = np.zeros(int(SR * d))
    r = rng("cm")
    for i in range(5):                     # liturgical melody, slightly flat
        f = np.random.default_rng(i).choice([0, 2, 3, 5, 7, 8]) + 12
        semi = 2 ** (f / 12.0)
        base = 98.0 * semi
        v = voice_vowel(4.5, base, "o" if i % 2 else "a", f"cm{i}")
        e = adsr(4.5, 0.4, 0.6, 2.6, 0.9)
        place(out, v[:len(e)] * e * 0.32, i * 0.9)
    out = bandpass(out, 620, q=0.7)
    return norm(reverb(out, 3.6, 0.5, "cm2", 2400), 0.8)


def s_choirmaster_scream():
    d = 3.2
    out = np.zeros(int(SR * d))
    # the whole choir inhaling at once
    place(out, reverse(breath(1.6, "inhale", 0.22, 900)) * 0.6, 0.0)
    # then breaking
    for i in range(5):
        f = 160 + i * 44
        v = voice_vowel(1.4, f, "a", f"scr{i}") * env(1.4, 0.01, 0.2, 0.3, 0.6)
        place(out, v * 0.32, 1.5 + i * 0.03)
    place(out, norm(highpass(noise(1.0, "scr2", "white") * env(1.0, 0.01, 0.3, 0.2, 0.4), 1200), 0.4), 1.5)
    place(out, low_rumble(1.6, "scr3", 34) * 0.4, 1.6)
    return norm(reverb(out, 2.6, 0.42, "scr4", 3000), 0.9)


def s_unblinking_gaze():
    d = 3.0
    out = np.zeros(int(SR * d))
    place(out, reverse(wind(2.2, "gaze", 0.35)), 0.0)
    tt = t(d)
    drone = np.sin(2 * np.pi * 41 * tt) * 0.4 + np.sin(2 * np.pi * 61.5 * tt) * 0.25
    out += lowpass(drone, 200) * np.clip(np.linspace(0, 1.2, len(tt)), 0, 1)
    place(out, norm(organic_wet(0.8, "gazew", 55), 0.3), 2.0)
    return norm(reverb(out, 3.0, 0.4, "gaze2", 1200), 0.8)


def s_unblinking_hurt():
    d = 1.4
    out = mix(norm(highpass(noise(0.5, "uh", "white") * env(0.5, 0.001, 0.1, 0.05, 0.2), 2200), 0.5),
              np.zeros(int(SR * d)))
    out = place(out, bell_partials(1.0, 720, (1, 2.7, 5.1), (1, 0.5, 0.3)) * 0.35, 0.02)
    out = place(out, lowpass(noise(0.6, "uh2", "brown") * env(0.6, 0.005, 0.3, 0.05, 0.25), 800) * 0.3, 0.05)
    return norm(out, 0.8)


def s_unblinking_death():
    d = 6.0
    out = np.zeros(int(SR * d))
    place(out, norm(wind(2.0, "ud", 0.5), 0.6), 0.0)                 # pressure release
    place(out, bell_partials(4.5, 130, (1, 1.5, 2.3, 3.6), (1, 0.7, 0.5, 0.3), level=0.5), 0.4)
    tt = t(d)
    out += lowpass(np.sin(2 * np.pi * 36 * tt), 140) * np.exp(-np.linspace(0, 5, len(tt))) * 0.5
    place(out, breath(2.6, "ud2", 0.2, 380) * 0.3, 1.4)
    return norm(reverb(out, 4.0, 0.5, "ud3", 2000), 0.9)


def s_tenant_possess():
    d = 2.8
    out = np.zeros(int(SR * d))
    place(out, reverse(whisper(1.4, "tp", 2, 0.8)), 0.0)
    place(out, voice_vowel(1.2, 108, "i", "tpv") * env(1.2, 0.05, 0.3, 0.4, 0.5) * 0.6, 1.2)
    place(out, norm(lowpass(noise(0.8, "tp2", "brown") * env(0.8, 0.02, 0.4, 0.1, 0.3), 500), 0.4), 1.3)
    place(out, heartbeat(1.2, "tph", 74) * 0.4, 1.5)
    return norm(reverb(out, 2.4, 0.45, "tp3", 2600), 0.85)


def s_sequence_advance():
    d = 7.0
    out = np.zeros(int(SR * d))
    # upward surge
    tt = t(2.4)
    surge = np.sin(2 * np.pi * (60 * 2 ** (np.linspace(0, 2.2, len(tt)))) * tt) * env(2.4, 0.2, 0.6, 1.0, 0.5)
    place(out, norm(surge, 0.4), 0.0)
    # bells
    place(out, bell_partials(4.0, 220, (1, 2.0, 3.4, 5.1, 7.9), (1, 0.7, 0.5, 0.35, 0.2), level=0.4), 1.6)
    place(out, organ_chord(4.0, (110, 138.6, 164.8, 220, 329.6), level=0.2), 1.7)
    # and a heartbeat that is not yours
    place(out, heartbeat(2.0, "sahb", 52) * 0.6, 4.6)
    place(out, voice_vowel(2.4, 82, "o", "sav") * env(2.4, 0.4, 0.6, 1.0, 0.6) * 0.25, 3.4)
    return norm(reverb(out, 3.6, 0.45, "sa2", 3200), 0.9)


def s_sequence_digest():
    d = 3.2
    out = np.zeros(int(SR * d))
    for i in range(5):
        place(out, norm(organic_wet(0.5, f"dg{i}", 60 + i * 14), 0.28), i * 0.42)
    place(out, breath(2.0, "dgb", 0.24, 300) * 0.3, 0.4)
    place(out, low_rumble(1.8, "dgr", 26) * 0.35, 0.6)
    return norm(reverb(out, 2.0, 0.35, "dg2", 900), 0.75)


def s_corruption_pulse():
    d = 2.0
    out = np.zeros(int(SR * d))
    tt = t(d)
    crawl = bandpass(noise(d, "corr", "brown"), 320, q=3.0) * (0.3 + 0.7 * np.abs(np.sin(2 * np.pi * 3.2 * tt)))
    place(out, norm(crawl, 0.5), 0.0)
    place(out, norm(lowpass(sine(48, 0.7) * np.exp(-np.linspace(0, 7, int(SR * 0.7))), 200), 0.5), 0.5)
    place(out, glitch_static(0.8, "corr2") * 0.14, 1.0)
    return norm(out, 0.7)


def s_spirit_enter():
    d = 3.2
    out = np.zeros(int(SR * d))
    place(out, reverse(wind(2.4, "spen", 0.45)), 0.0)
    place(out, reverse(bell_partials(1.6, 300, (1, 2.2, 4.4), (1, 0.5, 0.25))) * 0.35, 0.7)
    place(out, breath(1.6, "spen2", 0.4, 900) * 0.3, 1.2)
    return norm(reverb(out, 3.2, 0.5, "spen3", 3600), 0.8)


def s_spirit_exit():
    d = 2.4
    out = np.zeros(int(SR * d))
    place(out, norm(highpass(noise(0.5, "spex", "white") * env(0.5, 0.001, 0.15, 0.05, 0.2), 1800), 0.45), 0.0)
    place(out, bell_partials(1.8, 420, (1, 2.76, 5.4), (1, 0.5, 0.3)) * 0.3, 0.05)
    place(out, wind(1.2, "spex2", 0.25), 0.2)
    return norm(reverb(out, 1.8, 0.35, "spex3", 4200), 0.8)


def s_spirit_ambient():
    d = 10.0
    out = wind(d, "spam", 0.42)
    out += low_rumble(d, "spamr", 24) * 0.35
    place(out, bell_partials(4.0, 150, (1, 1.5, 2.4), (1, 0.6, 0.4)) * 0.22, 2.2)
    place(out, bell_partials(4.0, 148, (1, 1.5, 2.4), (1, 0.6, 0.4)) * 0.18, 6.4)
    out += bandpass(noise(d, "spam2", "pink"), 2400, q=0.7) * 0.05
    return norm(reverb(norm(loopable(out, 400), 0.5), 3.0, 0.3, "spam3", 2000), 0.6)


def s_spirit_strain():
    d = 3.0
    tt = t(d)
    out = np.zeros(int(SR * d))
    # tether stretching: rising string drone that never resolves
    f = 180 + 240 * np.linspace(0, 1, len(tt)) ** 2
    drone = np.sin(2 * np.pi * np.cumsum(f) / SR)
    drone += np.sin(2 * np.pi * np.cumsum(f * 1.004) / SR) * 0.7
    out += drone * (0.25 + 0.6 * np.linspace(0, 1, len(tt)))
    out += heartbeat(d, "strainhb", 88) * 0.35
    place(out, whisper(1.6, "strainwh", 2, 1.1) * 0.3, 1.0)
    return norm(out, 0.8)


def s_spirit_snap():
    d = 2.0
    out = np.zeros(int(SR * d))
    place(out, norm(highpass(noise(0.12, "snap", "white") * env(0.12, 0.0004, 0.03, 0, 0.05), 1000), 0.85), 0.0)
    place(out, breath(1.2, "snapb", 0.2, 600) * 0.5, 0.05)
    place(out, low_rumble(1.4, "snapr", 30) * 0.4, 0.1)
    return norm(reverb(out, 2.2, 0.4, "snap3", 2400), 0.85)


def s_red_moon():
    d = 8.0
    out = np.zeros(int(SR * d))
    # low brass swell
    for f, lvl in ((41, 0.5), (61.5, 0.35), (82, 0.25), (123, 0.15)):
        out += lowpass(np.sin(2 * np.pi * f * t(d)) * np.clip(np.linspace(0, 1.5, int(SR * d)), 0, 1) ** 2 * lvl, 500)
    out += wind(d, "rm", 0.25)
    place(out, bell_partials(5.0, 196, (1, 1.5, 2.2, 3.1), (1, 0.7, 0.5, 0.3), level=0.3), 1.5)
    return norm(reverb(out, 4.0, 0.45, "rm2", 2200), 0.85)


def s_veil_thins():
    d = 7.0
    out = np.zeros(int(SR * d))
    tear = bandpass(noise(d, "vt", "white"), 1800, q=2.2) * np.clip(np.linspace(0, 1, int(SR * d)) * 1.2, 0, 1)
    out += tear * 0.3
    out += wind(d, "vt2", 0.3)
    place(out, whisper(4.0, "vt3", 5, 2.0) * 0.4, 1.5)
    place(out, low_rumble(d, "vt4", 26) * 0.4, 0.5)
    return norm(reverb(out, 4.0, 0.5, "vt5", 2600), 0.8)


def s_whispering_night():
    d = 8.0
    out = np.zeros(int(SR * d))
    # the whole world whispering in one long breath
    v = whisper(d, "wn", voices=7, distance=1.8)
    out += v * 0.75
    out += breath(d, "wn2", 0.12, 400) * 0.35
    out += wind(d, "wn3", 0.18)
    return norm(reverb(out, 3.2, 0.42, "wn4", 2200), 0.75)


def s_beyond_call():
    d = 7.0
    out = np.zeros(int(SR * d))
    # an inverted bell: attack built from the tail of a bell, reversed
    inv = reverse(bell_partials(4.0, 78, (1, 1.5, 2.4, 3.7, 5.2), (1, 0.8, 0.6, 0.4, 0.2), level=0.55))
    place(out, norm(inv, 0.8), 1.0)
    place(out, low_rumble(6.0, "bc", 21) * 0.6, 0.0)
    place(out, whisper(3.0, "bc2", 3, 2.4) * 0.25, 3.0)
    return norm(reverb(out, 4.5, 0.5, "bc3", 1600), 0.9)


def s_beyond_arrival():
    d = 9.0
    out = np.zeros(int(SR * d))
    place(out, norm(reverse(wind(5.0, "ba", 0.5)), 0.7), 0.0)
    place(out, organ_chord(6.0, (27.5, 41, 55, 82.5), level=0.25, vibrato=0.08), 1.0)
    place(out, reverse(bell_partials(3.5, 110, (1, 1.6, 2.7, 4.1), (1, 0.7, 0.5, 0.3))) * 0.5, 3.0)
    place(out, voice_vowel(4.0, 55, "o", "bav") * env(4.0, 1.2, 1.0, 1.4, 1.2) * 0.3, 4.0)
    place(out, heartbeat(3.0, "bahb", 44, True) * 0.5, 5.5)
    return norm(reverb(out, 5.0, 0.5, "ba2", 1400), 0.92)


def s_memory_echo():
    """A place remembering: reversed ambience resolving into one distant event."""
    d = 4.5
    out = np.zeros(int(SR * d))
    place(out, reverse(wind(3.0, "me", 0.35)), 0.0)
    place(out, reverse(voice_vowel(2.0, 96, "o", "mev") * env(2.0, 0.6, 0.5, 0.6, 0.5)), 0.8, 0.4)
    place(out, norm(metallic_click(0.3, 1500, "mec"), 0.35), 2.6)
    place(out, heartbeat(1.6, "meh", 60) * 0.3, 3.0)
    return norm(reverb(out, 2.6, 0.45, "me2", 3200), 0.7)


def s_faceless_shift():
    """Skin and features rearranging, close mic: wet, quiet, intimate."""
    d = 1.8
    out = np.zeros(int(SR * d))
    r = rng("faceless")
    for i in range(7):
        place(out, norm(organic_wet(0.35, f"fs{i}", r.uniform(60, 180)), 0.22), r.uniform(0, 1.3))
    place(out, bandpass(noise(d, "fsn", "pink") * 0.12, 2400, q=2.0), 0.0)
    place(out, breath(1.0, "fsb", 0.5, 700) * 0.25, 0.4)
    return norm(reverb(out, 1.2, 0.25, "fs2", 2800), 0.6)


def s_authority_forbid():
    """A command in a language with no vowels, and everything obeys."""
    d = 3.2
    out = np.zeros(int(SR * d))
    for i, f in enumerate((82, 87, 123, 164)):
        out += lowpass(sine(f, d) * np.clip(np.linspace(0, 1.6, int(SR * d)), 0, 1) ** 2 * 0.25, 400)
    place(out, norm(bandpass(noise(0.6, "af", "white") * env(0.6, 0.02, 0.3, 0.1, 0.2), 1800, q=1.5), 0.4), 0.0)
    place(out, norm(lowpass(noise(0.9, "af2", "brown") * env(0.9, 0.05, 0.4, 0.2, 0.3), 500), 0.5), 0.2)
    place(out, voice_vowel(1.6, 74, "m", "afv") * env(1.6, 0.2, 0.4, 0.5, 0.4) * 0.4, 0.5)
    return norm(reverb(out, 2.4, 0.42, "af3", 2200), 0.8)


BANK = {
    "memory_echo": s_memory_echo,
    "faceless_shift": s_faceless_shift,
    "authority_forbid": s_authority_forbid,
    "potion_drink": s_potion_drink,
    "potion_brew": s_potion_brew,
    "potion_finish": s_potion_finish,
    "codex_open": s_codex_open,
    "codex_page": s_codex_page,
    "bell_ring": s_bell_ring,
    "bell_answer": s_bell_answer,
    "artifact_activate": s_artifact_activate,
    "artifact_curse": s_artifact_curse,
    "thread_bind": s_thread_bind,
    "thread_sever": s_thread_sever,
    "thread_tension": s_thread_tension,
    "chalk_draw": s_chalk_draw,
    "dagger_cut": s_dagger_cut,
    "ritual_start": s_ritual_start,
    "ritual_loop": s_ritual_loop,
    "ritual_pulse": s_ritual_pulse,
    "ritual_chant": s_ritual_chant,
    "ritual_complete": s_ritual_complete,
    "ritual_fail": s_ritual_fail,
    "whisper_near": s_whisper_near,
    "whisper_far": s_whisper_far,
    "heartbeat": s_heartbeat,
    "breathing": s_breathing,
    "knock": s_knock,
    "rumble": s_rumble,
    "static": s_static,
    "watcher_stare": s_watcher_stare,
    "watcher_vanish": s_watcher_vanish,
    "hollow_step": s_hollow_step,
    "hollow_idle": s_hollow_idle,
    "husk_whisper": s_husk_whisper,
    "husk_attack": s_husk_attack,
    "marionette_joint": s_marionette_joint,
    "choirmaster_chant": s_choirmaster_chant,
    "choirmaster_scream": s_choirmaster_scream,
    "unblinking_gaze": s_unblinking_gaze,
    "unblinking_hurt": s_unblinking_hurt,
    "unblinking_death": s_unblinking_death,
    "tenant_possess": s_tenant_possess,
    "sequence_advance": s_sequence_advance,
    "sequence_digest": s_sequence_digest,
    "corruption_pulse": s_corruption_pulse,
    "spirit_enter": s_spirit_enter,
    "spirit_exit": s_spirit_exit,
    "spirit_ambient": s_spirit_ambient,
    "spirit_strain": s_spirit_strain,
    "spirit_snap": s_spirit_snap,
    "red_moon": s_red_moon,
    "veil_thins": s_veil_thins,
    "whispering_night": s_whispering_night,
    "beyond_call": s_beyond_call,
    "beyond_arrival": s_beyond_arrival,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    wanted = sys.argv[1:] or list(BANK)
    declared = {s[0] for s in SOUNDS}
    missing = declared - set(BANK)
    if missing:
        print(f"[sounds] WARNING sound bank is missing declared sounds: {sorted(missing)}")
    total = 0
    problems: list[str] = []
    for name in wanted:
        if name not in BANK:
            print(f"  ! unknown sound {name}")
            continue
        x = BANK[name]()
        x = np.nan_to_num(x)
        if not np.all(np.isfinite(x)):
            problems.append(f"{name}: non-finite samples")
        rms = float(np.sqrt(np.mean(x ** 2))) if len(x) else 0.0
        if rms < 0.01:
            problems.append(f"{name}: effectively silent (rms {rms:.4f})")
        x = np.clip(x, -1, 1)
        sf.write(os.path.join(OUT, f"{name}.ogg"), x.astype(np.float32), SR, subtype="VORBIS")
        total += 1
        print(f"  {name:22s} {len(x)/SR:5.2f}s")
    print(f"[sounds] wrote {total} ogg files -> {OUT}")
    if problems:
        print("[sounds] PROBLEMS:")
        for p_ in problems:
            print("   !", p_)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
