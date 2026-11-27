"""
StudyCity çalışma müzikleri ve arayüz sesleri: koddan sentezlenir (örnek ses yok).

Çalıştırma:  python tools/music/study.py <çıktı klasörü>
Üretilen .wav dosyaları ffmpeg ile .ogg'ye çevrilip app/src/main/res/raw içine konur.

Döngüler dikişsizdir: süzgeçler FFT ile dairesel uygulanır, yankı kuyruğu başa sarılır.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np  # noqa: E402
from compose import SR, Track, bell, hz, midi, reverb, save, tvec  # noqa: E402

rng = np.random.default_rng(15)


def circ_filter(x, lo=None, hi=None, tilt=0.0):
    """Dairesel (döngü dostu) FFT süzgeci. lo/hi: Hz, tilt: dB/oktav (negatif = koyu)."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    g = np.ones_like(f)
    if hi:
        g *= 1 / np.sqrt(1 + (f / hi) ** 4)
    if lo:
        g *= 1 / np.sqrt(1 + (lo / np.maximum(f, 1e-3)) ** 4)
    if tilt:
        g *= (np.maximum(f, 20) / 1000) ** (tilt / 6.02)
    return np.fft.irfft(X * g, len(x))


def wrap(track, length):
    """Parçanın döngü sonunu aşan kısmını başa ekler."""
    buf = track.buf
    out = buf[:length].copy()
    over = buf[length:]
    out[: len(over)] += over
    return out


def epiano(f, dur, vel=1.0):
    """Elektrikli piyano (FM): yumuşak çan + tını, hızla sönen parlaklık."""
    t = tvec(dur)
    mod = np.sin(2 * np.pi * f * 1.0 * t) * (1.6 * np.exp(-t * 6) + 0.25)
    car = np.sin(2 * np.pi * f * t + mod)
    tine = np.sin(2 * np.pi * f * 7.0 * t) * np.exp(-t * 18) * 0.12
    env = np.exp(-t * 1.1) * np.minimum(1, t / 0.005)
    rel = np.clip((dur - t) / 0.15, 0, 1)
    return (car + tine) * env * rel * 0.25 * vel


def soft_kick(dur=0.35):
    t = tvec(dur)
    f = 55 * (1 + 2.2 * np.exp(-t * 40))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9) * 0.8


def soft_snare(dur=0.3):
    t = tvec(dur)
    n = rng.normal(0, 1, len(t))
    n = np.convolve(n, np.ones(4) / 4, "same")
    return (n * np.exp(-t * 16) * 0.25 + np.sin(2 * np.pi * 180 * t) * np.exp(-t * 25) * 0.2)


def hat(dur=0.06, vol=1.0):
    t = tvec(dur)
    n = rng.normal(0, 1, len(t))
    n = n - np.concatenate([[0], n[:-1]])
    return n * np.exp(-t * 70) * 0.05 * vol


def sub_bass(f, dur):
    t = tvec(dur)
    return np.sin(2 * np.pi * f * t) * np.minimum(1, t / 0.02) * np.clip((dur - t) / 0.08, 0, 1) * 0.35


def crackle(n):
    x = np.zeros(n)
    idx = rng.integers(0, n, n // 900)
    x[idx] = rng.normal(0, 1, len(idx)) * rng.uniform(0.2, 1, len(idx))
    x = np.convolve(x, np.array([1, -0.6, 0.2]), "same")
    hiss = rng.normal(0, 1, n) * 0.012
    return circ_filter(x * 0.25 + hiss, lo=900, hi=7000)


# ------------------------------------------------------------------ çalışma müzikleri

def lofi():
    """72 bpm, sakin lo-fi: elektrikli piyano akorları, yumuşak davul, bas ve plak cızırtısı."""
    bpm = 72
    beat = 60 / bpm
    bars = 16
    L = int(bars * 4 * beat * SR)
    tr = Track(bars * 4 * beat)
    prog = [
        ["D3", "F3", "A3", "C4", "E4"], ["G2", "F3", "A3", "B3", "E4"],
        ["C3", "E3", "G3", "B3", "D4"], ["A2", "E3", "G3", "C4", "E4"],
    ]
    roots = ["D2", "G2", "C2", "A1"]
    for bar in range(bars):
        ch = prog[bar % 4]
        t0 = bar * 4 * beat
        for k, n in enumerate(ch):
            tr.add(epiano(hz(midi(n)), beat * 3.6, 0.8), t0 + k * 0.018 + 0.02, 1.0)
        # ikinci yarıda hafif tekrar
        for k, n in enumerate(ch[1:4]):
            tr.add(epiano(hz(midi(n)), beat * 1.3, 0.45), t0 + 2.5 * beat + k * 0.02, 1.0)
        tr.add(sub_bass(hz(midi(roots[bar % 4])), beat * 2.6), t0, 1.0)
        tr.add(sub_bass(hz(midi(roots[bar % 4]) + 7), beat * 0.9), t0 + 3 * beat, 0.7)
        # davul (sallantılı)
        tr.add(soft_kick(), t0, 1.0)
        tr.add(soft_kick(), t0 + 2.55 * beat, 0.7)
        tr.add(soft_snare(), t0 + beat, 0.8)
        tr.add(soft_snare(), t0 + 3 * beat, 0.8)
        for e in range(8):
            sw = 0.09 * beat if e % 2 else 0
            tr.add(hat(vol=0.6 if e % 2 else 1.0), t0 + e * beat / 2 + sw, 1.0)
        # ara sıra melodi
        if bar % 4 == 3:
            for k, n in enumerate(["A4", "G4", "E4", "D4"]):
                tr.add(epiano(hz(midi(n)), beat * 0.9, 0.55), t0 + (k * 0.75 + 0.5) * beat, 1.0)
    x = wrap(tr, L)
    x = circ_filter(x, lo=35, hi=5200)
    x = x + crackle(L) * 0.6
    return reverb(x, 1.4, 0.18, loop_len=L)


def rain():
    """Yağmur: geniş bantlı hışırtı, yavaş dalgalanma ve tek tük damla."""
    secs = 48
    L = secs * SR
    base = rng.normal(0, 1, L)
    base = circ_filter(base, lo=250, hi=6500, tilt=-2.0)
    t = np.arange(L) / SR
    swell = 0.8 + 0.2 * np.sin(2 * np.pi * t / secs * 3) + 0.08 * np.sin(2 * np.pi * t / secs * 11)
    x = base * swell
    drops = np.zeros(L)
    for _ in range(secs * 9):
        i = rng.integers(0, L)
        d = tvec(0.03)
        f = rng.uniform(1800, 4200)
        sig = np.sin(2 * np.pi * f * d * (1 - d * 8)) * np.exp(-d * 160) * rng.uniform(0.2, 1)
        j = min(L, i + len(sig))
        drops[i:j] += sig[: j - i]
    rumble = circ_filter(rng.normal(0, 1, L), hi=120) * 1.5
    return x * 0.5 + drops * 0.25 + rumble * 0.3


def piano():
    """Yavaş, boşluklu piyano ve pad: odak için ezgisi belirsiz, tekrarı fark edilmeyen döngü."""
    secs = 64
    L = secs * SR
    tr = Track(secs)
    chords = [["C3", "G3", "E4"], ["A2", "E3", "C4"], ["F2", "C3", "A3"], ["G2", "D3", "B3"]]
    for i in range(16):
        ch = chords[i % 4]
        t0 = i * 4.0
        for k, n in enumerate(ch):
            tr.add(epiano(hz(midi(n)), 3.8, 0.5), t0 + k * 0.03, 1.0)
        mel = rng.choice(["E5", "G5", "C5", "D5", "A4", "B4"], size=2)
        tr.add(bell(hz(midi(mel[0])), 2.5, 0.6), t0 + 1.0 + rng.uniform(0, 0.4), 1.0)
        if i % 2:
            tr.add(bell(hz(midi(mel[1])), 2.5, 0.45), t0 + 2.6 + rng.uniform(0, 0.4), 1.0)
    x = wrap(tr, L)
    x = circ_filter(x, lo=60, hi=4000)
    return reverb(x, 2.6, 0.35, loop_len=L)


def brown():
    """Kahverengi gürültü: derin, düz uğultu (dairesel, dikişsiz)."""
    L = 30 * SR
    x = circ_filter(rng.normal(0, 1, L), lo=20, tilt=-6.0)
    return x


def forest():
    """Orman: hafif rüzgâr, uzak kuş cıvıltıları."""
    secs = 48
    L = secs * SR
    t = np.arange(L) / SR
    wind = circ_filter(rng.normal(0, 1, L), lo=150, hi=1400) * (0.7 + 0.3 * np.sin(2 * np.pi * t / secs * 2))
    tr = Track(secs)
    for _ in range(26):
        at = rng.uniform(0, secs - 2)
        f0 = rng.uniform(2400, 4200)
        for k in range(rng.integers(2, 6)):
            d = tvec(rng.uniform(0.06, 0.14))
            sweep = f0 * (1 + 0.25 * np.sin(np.pi * d / d[-1]) * rng.choice([-1, 1]))
            sig = np.sin(2 * np.pi * np.cumsum(sweep) / SR) * np.sin(np.pi * d / d[-1]) ** 2
            tr.add(sig * 0.2, at + k * rng.uniform(0.12, 0.22), 1.0)
    birds = wrap(tr, L)
    return reverb(wind * 0.35 + birds, 1.8, 0.3, loop_len=L)


# ------------------------------------------------------------------ arayüz sesleri

def chime(notes, gap, dur, vol=0.6, decay=1.0):
    tr = Track(dur + gap * len(notes))
    for i, n in enumerate(notes):
        tr.add(bell(hz(midi(n)), dur), i * gap, vol)
    return reverb(tr.buf[: int((dur + gap * len(notes)) * SR)], decay, 0.25)


def marimba(f, dur=0.8):
    t = tvec(dur)
    x = np.sin(2 * np.pi * f * t) * np.exp(-t * 7) + 0.3 * np.sin(2 * np.pi * f * 4 * t) * np.exp(-t * 20)
    return x * np.minimum(1, t / 0.002) * 0.5


def break_start():
    tr = Track(1.6)
    for i, n in enumerate(["G5", "E5", "C5"]):
        tr.add(marimba(hz(midi(n))), i * 0.16, 0.7)
    return reverb(tr.buf[: int(1.6 * SR)], 1.0, 0.25)


def coin_tick():
    t = tvec(0.16)
    x = (np.sin(2 * np.pi * 2637 * t) * 0.6 + np.sin(2 * np.pi * 3951 * t) * 0.4) * np.exp(-t * 30)
    return x * np.minimum(1, t / 0.001)


def streak():
    tr = Track(2.4)
    t = tvec(1.0)
    whoosh = circ_filter(rng.normal(0, 1, len(t)), lo=300, hi=3000) * np.sin(np.pi * t / t[-1]) ** 2 * 0.5
    tr.add(whoosh, 0.0, 1.0)
    for i, n in enumerate(["C5", "E5", "G5", "C6", "E6"]):
        tr.add(bell(hz(midi(n)), 1.6), 0.35 + i * 0.09, 0.55)
    return reverb(tr.buf[: int(2.4 * SR)], 1.5, 0.3)


def pop():
    t = tvec(0.12)
    f = 600 * (1 + 2 * np.exp(-t * 60))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 35) * 0.6


if __name__ == "__main__":
    out = sys.argv[1]
    save(f"{out}/music_study_lofi.wav", lofi(), peak=0.7)
    save(f"{out}/music_study_rain.wav", rain(), peak=0.6)
    save(f"{out}/music_study_piano.wav", piano(), peak=0.7)
    save(f"{out}/music_study_brown.wav", brown(), peak=0.55)
    save(f"{out}/music_study_forest.wav", forest(), peak=0.6)
    save(f"{out}/cue_work_start.wav", chime(["C5", "E5", "G5", "C6"], 0.11, 1.2), peak=0.75)
    save(f"{out}/cue_break_start.wav", break_start(), peak=0.75)
    save(f"{out}/cue_round_end.wav", chime(["E5", "C5"], 0.22, 1.2, decay=1.2), peak=0.7)
    save(f"{out}/cue_session_done.wav", chime(["C5", "E5", "G5", "C6", "G5", "C6", "E6"], 0.1, 1.5), peak=0.8)
    save(f"{out}/cue_focus_ask.wav", chime(["A5", "E6"], 0.14, 0.8), peak=0.75)
    save(f"{out}/cue_focus_warn.wav", chime(["E6", "E6", "E6"], 0.18, 0.6), peak=0.85)
    save(f"{out}/cue_coin.wav", coin_tick(), peak=0.6)
    save(f"{out}/cue_streak.wav", streak(), peak=0.8)
    save(f"{out}/cue_pop.wav", pop(), peak=0.6)
