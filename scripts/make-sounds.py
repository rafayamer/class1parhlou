"""Parhlou's original UI sounds, synthesised from sine partials (no samples). Run: python3 scripts/make-sounds.py <out-dir>"""
import sys, wave
import numpy as np

SR = 22050
out = sys.argv[1]

def env(n, a=0.004, d=0.12, curve=4.0):
    t = np.arange(n) / SR
    att = np.clip(t / a, 0, 1)
    return att * np.exp(-t / d * (curve / 4))

def bell(f, dur, d=0.25, vol=0.5, partials=((1, 1), (2.0, 0.35), (3.01, 0.18), (4.2, 0.08))):
    n = int(SR * dur); t = np.arange(n) / SR
    s = sum(a * np.sin(2 * np.pi * f * k * t) * np.exp(-t / (d / k ** 0.6)) for k, a in partials)
    return vol * s * env(n, 0.003, d * 4)

def tone(f0, f1, dur, vol=0.5, shape="sine", d=0.2):
    n = int(SR * dur); t = np.arange(n) / SR
    f = np.linspace(f0, f1, n); ph = 2 * np.pi * np.cumsum(f) / SR
    w = np.sin(ph) if shape == "sine" else (np.sin(ph) + 0.25 * np.sin(3 * ph) + 0.1 * np.sin(5 * ph))
    return vol * w * env(n, 0.005, d)

def seq(parts):
    total = max(int(SR * at) + len(x) for at, x in parts)
    o = np.zeros(total)
    for at, x in parts:
        i = int(SR * at); o[i:i + len(x)] += x
    return o

def save(name, x):
    x = x / max(1e-9, np.abs(x).max()) * 0.85
    fade = min(len(x), int(SR * 0.01)); x[-fade:] *= np.linspace(1, 0, fade)
    with wave.open(f"{out}/{name}.wav", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())

N = lambda m: 440 * 2 ** ((m - 69) / 12)   # MIDI note -> Hz
rng = np.random.default_rng(3)

# A soft wooden tap for buttons.
click = tone(1150, 700, 0.05, 0.6, d=0.012) + 0.15 * rng.standard_normal(int(SR * 0.05)) * env(int(SR * 0.05), 0.001, 0.004)
save("ui_tap", click)
# A bubbly pop when an answer is picked.
save("ui_select", tone(520, 980, 0.09, 0.6, d=0.04) + 0.3 * bell(N(84), 0.09, 0.03))
# Correct: two bright bell notes rising a major sixth.
save("ui_correct", seq([(0, bell(N(79), 0.5, 0.18)), (0.09, bell(N(88), 0.6, 0.25))]))
# Wrong: a gentle low two-note fall, never harsh.
save("ui_wrong", seq([(0, tone(N(58), N(57), 0.18, 0.6, "warm", 0.1)), (0.13, tone(N(53), N(51), 0.28, 0.6, "warm", 0.14))]))
# Combo: a quick rising arpeggio.
save("ui_combo", seq([(i * 0.06, bell(N(n), 0.35, 0.12, 0.45)) for i, n in enumerate([72, 76, 79, 84])]))
# Lesson complete: arpeggio into a held, sparkling chord.
chord = sum(bell(N(n), 1.4, 0.5, 0.35) for n in [72, 76, 79, 84])
sparkle = seq([(0.55 + i * 0.05, bell(N(96 + (i % 3) * 2), 0.25, 0.06, 0.18)) for i in range(8)])
save("ui_complete", seq([(0, bell(N(60), 0.3, 0.12)), (0.1, bell(N(64), 0.3, 0.12)), (0.2, bell(N(67), 0.3, 0.12)), (0.32, chord), (0, sparkle)]))
# Streak: a warm rising swell with crackle.
n = int(SR * 0.7); t = np.arange(n) / SR
swell = 0.5 * np.sin(2 * np.pi * np.cumsum(np.linspace(300, 900, n)) / SR) * np.sin(np.pi * t / 0.7)
crackle = (rng.random(n) > 0.995) * rng.standard_normal(n) * 0.6
save("ui_streak", seq([(0, swell + crackle), (0.45, bell(N(84), 0.3, 0.15, 0.4))]))
# Level up: a glissando into a major chord.
gl = tone(N(60), N(84), 0.35, 0.4, d=0.4)
save("ui_levelup", seq([(0, gl), (0.33, sum(bell(N(n), 0.9, 0.35, 0.35) for n in [72, 76, 79, 88]))]))
# XP tick: a tiny coin.
save("ui_xp", bell(N(96), 0.07, 0.02, 0.5, ((1, 1), (1.5, 0.4))))
# Unlock: pop plus a sparkle.
save("ui_unlock", seq([(0, tone(400, 900, 0.08, 0.5, d=0.04)), (0.06, bell(N(91), 0.3, 0.1, 0.4))]))
