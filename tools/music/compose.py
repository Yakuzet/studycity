"""
StudyCity müzikleri: tamamen koddan bestelenip sentezlenir (örnek ses kullanılmaz).

Çalıştırma:  python tools/music/compose.py <çıktı klasörü>
Her parça için .wav üretir; ardından ffmpeg ile .ogg'ye çevrilir.

Enstrümanlar toplamsal sentezle yapılır: lavta (sönümlenen harmonikler), flüt (sinüs + vibrato + nefes),
bakır (parlaklığı zarfla değişen testere), yaylı ostinato, pad, tef/taiko/trampet ve çan.
Döngülü parçalarda yankı kuyruğu başa sarılır, böylece döngü dikişsiz olur.
"""
import sys
import wave
import numpy as np

SR = 44100
rng = np.random.default_rng(7)

NOTE = {"C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5, "F#": 6, "Gb": 6,
        "G": 7, "G#": 8, "Ab": 8, "A": 9, "A#": 10, "Bb": 10, "B": 11}


def midi(name):
    """'A4' -> 69"""
    n = name[:-1]
    o = int(name[-1])
    return 12 * (o + 1) + NOTE[n]


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def tvec(dur):
    return np.arange(int(dur * SR)) / SR


def adsr(n, a, d, s, r, total=None):
    t = np.arange(n) / SR
    total = total if total is not None else n / SR
    env = np.ones(n) * s
    env[t < a] = t[t < a] / max(a, 1e-4)
    dm = (t >= a) & (t < a + d)
    env[dm] = 1 - (1 - s) * (t[dm] - a) / max(d, 1e-4)
    rel = t > total - r
    env[rel] *= np.clip((total - t[rel]) / max(r, 1e-4), 0, 1)
    return env


# ------------------------------------------------------------------ enstrümanlar

def pluck(f, dur, bright=1.0):
    """Lavta/arp: yüksek harmonikleri daha hızlı sönen toplamsal tel."""
    t = tvec(dur)
    out = np.zeros_like(t)
    for k in range(1, 14):
        if f * k > SR / 2.2:
            break
        amp = (1.0 / k ** 1.15) * (bright if k > 3 else 1.0)
        out += amp * np.sin(2 * np.pi * f * k * t + rng.uniform(0, 6.28)) * np.exp(-t * (1.8 + 0.9 * k))
    out *= np.minimum(1, t / 0.004)
    return out * 0.5


def flute(f, dur, vib=0.006):
    t = tvec(dur)
    v = 1 + vib * np.sin(2 * np.pi * 5.2 * t) * np.clip((t - 0.18) / 0.3, 0, 1)
    ph = 2 * np.pi * np.cumsum(f * v) / SR
    tone = np.sin(ph) + 0.22 * np.sin(2 * ph) + 0.08 * np.sin(3 * ph)
    breath = rng.normal(0, 1, len(t))
    breath = np.convolve(breath, np.ones(18) / 18, mode="same") * 0.05 * np.exp(-t * 8)
    return (tone * adsr(len(t), 0.05, 0.1, 0.85, 0.08) + breath) * 0.35


def brass(f, dur, bright=1.0):
    t = tvec(dur)
    env = adsr(len(t), 0.04, 0.15, 0.75, 0.1)
    bright_env = 0.35 + 0.65 * np.minimum(1, t / 0.08) * np.exp(-t * 1.2) + 0.25
    out = np.zeros_like(t)
    for k in range(1, 18):
        if f * k > SR / 2.2:
            break
        w = np.exp(-k / (1.5 + 7 * bright_env * bright))
        out += w * np.sin(2 * np.pi * f * k * t * (1 + 0.0008 * k)) / k
    return out * env * 0.45


def strings(f, dur):
    t = tvec(dur)
    out = np.zeros_like(t)
    for det in (-0.004, 0.004):
        for k in range(1, 10):
            out += np.sin(2 * np.pi * f * (1 + det) * k * t) / k * np.exp(-k * 0.25)
    return out * adsr(len(t), 0.01, 0.05, 0.6, 0.03) * 0.18


def pad(f, dur):
    t = tvec(dur)
    out = np.zeros_like(t)
    for det in (-0.006, 0, 0.007):
        for k in range(1, 6):
            out += np.sin(2 * np.pi * f * (1 + det) * k * t) / (k * k)
    return out * adsr(len(t), 0.6, 0.4, 0.8, 0.6) * 0.12


def frame_drum(dur=0.45, f0=95, vol=1.0):
    t = tvec(dur)
    f = f0 * (1 + 0.7 * np.exp(-t * 35))
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7)
    click = rng.normal(0, 1, len(t)) * np.exp(-t * 90) * 0.3
    return (body + click) * 0.6 * vol


def taiko(dur=0.7, f0=62, vol=1.0):
    t = tvec(dur)
    f = f0 * (1 + 1.6 * np.exp(-t * 28))
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 4.5)
    skin = np.convolve(rng.normal(0, 1, len(t)), np.ones(6) / 6, "same") * np.exp(-t * 40) * 0.45
    return (body + skin) * 0.9 * vol


def snare(dur=0.25, vol=1.0):
    t = tvec(dur)
    n = rng.normal(0, 1, len(t))
    n = n - np.concatenate([[0], n[:-1]]) * 0.6  # biraz tiz
    tone = np.sin(2 * np.pi * 190 * t) * np.exp(-t * 30) * 0.5
    return (n * 0.35 * np.exp(-t * 18) + tone) * 0.55 * vol


def shaker(dur=0.08, vol=1.0):
    t = tvec(dur)
    n = rng.normal(0, 1, len(t))
    n = n - np.concatenate([[0], n[:-1]])
    return n * np.exp(-t * 60) * 0.12 * vol


def bell(f, dur=1.6, vol=1.0):
    t = tvec(dur)
    out = np.zeros_like(t)
    for ratio, amp, dec in [(1, 1, 2.2), (2.0, 0.5, 3.0), (2.76, 0.35, 4.0), (5.4, 0.2, 6.0), (8.9, 0.08, 9.0)]:
        out += amp * np.sin(2 * np.pi * f * ratio * t) * np.exp(-t * dec)
    return out * np.minimum(1, t / 0.002) * 0.3 * vol


# ------------------------------------------------------------------ yardımcılar

class Track:
    def __init__(self, seconds):
        self.buf = np.zeros(int(seconds * SR) + SR * 3)
        self.len = int(seconds * SR)

    def add(self, sig, at, vol=1.0):
        i = int(at * SR)
        j = min(len(self.buf), i + len(sig))
        self.buf[i:j] += sig[: j - i] * vol


def reverb(x, seconds=1.6, mix=0.22, loop_len=None):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = rng.normal(0, 1, n) * np.exp(-t / (seconds / 5))
    ir = np.convolve(ir, np.ones(30) / 30, "same")  # biraz karanlık yankı
    ir[0] = 0
    ir /= np.sqrt(np.sum(ir ** 2))
    size = 1
    while size < len(x) + n:
        size *= 2
    wet = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[: len(x) + n]
    if loop_len is not None:
        # Döngü için: döngü sonunu aşan yankıyı başa ekle.
        tail = wet[loop_len:]
        wet = wet[:loop_len].copy()
        wet[: len(tail)] += tail
        dry = x[:loop_len].copy()
        over = x[loop_len:]
        dry[: len(over)] += over
        return dry * (1 - mix) + wet * mix * 3.0
    return np.concatenate([x, np.zeros(n)]) * (1 - mix) + wet * mix * 3.0


def save(path, x, peak=0.89):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    pcm = (x * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(f"{path}: {len(x) / SR:.1f} sn, rms {np.sqrt(np.mean(x ** 2)):.3f}")


CHORDS = {
    "Dm": ["D3", "A3", "D4", "F4"], "C": ["C3", "G3", "C4", "E4"], "Bb": ["Bb2", "F3", "Bb3", "D4"],
    "F": ["F2", "C3", "F3", "A3"], "G": ["G2", "D3", "G3", "B3"], "A": ["A2", "E3", "A3", "C#4"],
    "Am": ["A2", "E3", "A3", "C4"], "E": ["E2", "B2", "E3", "G#3"],
}

# ------------------------------------------------------------------ köy teması (Re minör, 96 bpm)

def village():
    bpm = 96
    beat = 60 / bpm
    bar = 4 * beat
    prog = ["Dm", "C", "Bb", "C", "Dm", "F", "G", "A", "F", "C", "Dm", "Am", "Bb", "F", "G", "A"]
    mel_a = [
        [("A4", 1), ("F4", .5), ("G4", .5), ("A4", 1), ("D5", 1)],
        [("C5", 1.5), ("B4", .5), ("A4", 1), ("G4", 1)],
        [("D5", 1), ("C5", .5), ("Bb4", .5), ("A4", 1), ("F4", 1)],
        [("G4", 1.5), ("A4", .5), ("G4", 1), ("E4", 1)],
        [("F4", 1), ("A4", 1), ("D5", 1), ("F5", 1)],
        [("E5", .5), ("D5", .5), ("C5", 1), ("A4", 1), ("C5", 1)],
        [("B4", 1), ("D5", 1), ("C5", .5), ("B4", .5), ("A4", 1)],
        [("E5", 1), ("C#5", 1), ("A4", 2)],
    ]
    mel_b = [
        [("A4", 1), ("C5", 1), ("F5", 1.5), ("E5", .5)],
        [("E5", 1), ("D5", .5), ("C5", .5), ("G4", 2)],
        [("F4", .5), ("G4", .5), ("A4", 1), ("D5", 1), ("A4", 1)],
        [("C5", 1.5), ("A4", .5), ("E4", 2)],
        [("D5", 1), ("F5", 1), ("D5", .5), ("C5", .5), ("Bb4", 1)],
        [("A4", 1), ("C5", .5), ("A4", .5), ("F4", 2)],
        [("G4", .5), ("A4", .5), ("B4", 1), ("D5", 1), ("B4", 1)],
        [("C#5", 1), ("E5", 1), ("A4", 2)],
    ]
    bars = 32
    tr = Track(bars * bar)
    arp = [0, 1, 2, 3, 2, 1, 0, 1]
    for b in range(bars):
        ch = CHORDS[prog[b % 16]]
        t0 = b * bar
        # bas: kökten pad
        tr.add(pad(hz(midi(ch[0]) - 12 + 12), bar + 0.5), t0, 0.9)
        # lavta arpeji (sekizlikler)
        for i, k in enumerate(arp):
            n = midi(ch[k]) + (12 if b >= 16 and i % 4 == 3 else 0)
            tr.add(pluck(hz(n), 1.6, bright=0.9), t0 + i * beat / 2, 0.55 if i % 2 == 0 else 0.42)
        # tef: 5. ölçüden itibaren
        if b >= 4:
            tr.add(frame_drum(f0=92), t0, 0.55)
            tr.add(frame_drum(f0=110, vol=0.6), t0 + 2 * beat, 0.45)
            tr.add(shaker(), t0 + 1.5 * beat, 0.8)
            tr.add(shaker(), t0 + 3.5 * beat, 0.8)
        # melodi
        mel = (mel_a + mel_b)[b % 16]
        if b >= 2:
            pos = 0.0
            for name, d in mel:
                m = midi(name) + (12 if 16 <= b < 24 else 0)
                vol = 0.55 if 16 <= b < 24 else 0.7
                tr.add(flute(hz(m), d * beat * 0.98), t0 + pos * beat, vol)
                if b >= 24:  # son bölümde alttan üçlü armoni
                    tr.add(flute(hz(m - 4 if name[0] in "ADE" else m - 3), d * beat * 0.98, vib=0.004), t0 + pos * beat, 0.28)
                pos += d
    x = reverb(tr.buf, 1.8, 0.25, loop_len=tr.len)
    return x


# ------------------------------------------------------------------ savaş teması (La minör, 140 bpm)

def battle():
    bpm = 140
    beat = 60 / bpm
    bar = 4 * beat
    prog = ["Am", "Am", "F", "G", "Am", "Am", "Dm", "E", "F", "G", "Am", "Am", "F", "G", "E", "E"]
    lead = [
        [("A4", 1.5), ("E5", .5), ("D5", 1), ("C5", 1)], [("B4", 1), ("C5", 1), ("A4", 2)],
        [("F5", 1.5), ("E5", .5), ("D5", 1), ("C5", 1)], [("D5", 1), ("B4", 1), ("G4", 2)],
        [("A4", 1), ("C5", 1), ("E5", 1), ("A5", 1)], [("G5", 1), ("E5", 1), ("C5", 2)],
        [("D5", 1), ("F5", 1), ("A5", 1), ("F5", 1)], [("E5", 2), ("G#4", 2)],
    ]
    bars = 16
    tr = Track(bars * bar)
    for b in range(bars):
        name = prog[b]
        ch = CHORDS[name] if name in CHORDS else CHORDS["Am"]
        if name == "Dm":
            ch = CHORDS["Dm"]
        t0 = b * bar
        root = midi(ch[0])
        fifth = midi(ch[1])
        # yaylı ostinato (onaltılıklar)
        for i in range(16):
            n = root + 12 if i % 2 == 0 else fifth + 12
            if i % 8 == 6:
                n += 12
            tr.add(strings(hz(n), beat / 4 * 0.95), t0 + i * beat / 4, 0.55)
        # taiko kalıbı
        for p, v in [(0, 1.0), (1.5, 0.7), (2, 0.9), (3.5, 0.6)]:
            tr.add(taiko(), t0 + p * beat, v)
        tr.add(snare(), t0 + 1 * beat, 0.7)
        tr.add(snare(), t0 + 3 * beat, 0.75)
        if b % 8 == 7:  # ara geçiş
            for k in range(4):
                tr.add(snare(vol=0.6 + 0.1 * k), t0 + (3 + k / 4) * beat, 0.7)
        # bakır vuruşları
        for p in (0, 2.5):
            for n in ch[1:]:
                tr.add(brass(hz(midi(n) + 12), beat * 0.8, bright=0.9), t0 + p * beat, 0.32)
        # ikinci yarıda kahramanca melodi
        if b >= 8:
            pos = 0.0
            for nm, d in lead[b - 8]:
                tr.add(brass(hz(midi(nm)), d * beat * 0.95, bright=1.2), t0 + pos * beat, 0.6)
                pos += d
        # bas
        tr.add(brass(hz(root - 12), bar * 0.9, bright=0.4), t0, 0.45)
    return reverb(tr.buf, 1.4, 0.18, loop_len=tr.len)


# ------------------------------------------------------------------ kısa parçalar

def victory():
    tr = Track(4.2)
    beat = 0.16
    for i, n in enumerate(["G4", "C5", "E5", "G5"]):
        tr.add(brass(hz(midi(n)), beat * 1.2, bright=1.3), i * beat, 0.7)
    for n in ["C4", "E4", "G4", "C5", "E5"]:
        tr.add(brass(hz(midi(n)), 2.6, bright=1.1), 4 * beat, 0.45)
    tr.add(brass(hz(midi("C6")), 2.4, bright=1.4), 4 * beat, 0.5)
    for k in range(10):
        tr.add(snare(vol=0.3 + 0.07 * k), k * 0.06, 0.6)
    tr.add(taiko(1.2), 4 * beat, 1.0)
    for i, n in enumerate(["C6", "E6", "G6", "C7"]):
        tr.add(bell(hz(midi(n)), 1.6), 4 * beat + 0.4 + i * 0.12, 0.35)
    return reverb(tr.buf[: int(4.2 * SR)], 1.6, 0.28)


def defeat():
    tr = Track(3.8)
    for i, n in enumerate(["E4", "D4", "C4", "B3"]):
        tr.add(brass(hz(midi(n)), 0.9 if i < 3 else 2.2, bright=0.5), i * 0.55, 0.6)
    tr.add(pad(hz(midi("A2")), 3.5), 0, 0.9)
    tr.add(taiko(1.4, f0=48), 1.65, 0.8)
    return reverb(tr.buf[: int(3.8 * SR)], 1.8, 0.3)


def chest():
    tr = Track(1.9)
    for i, n in enumerate(["C6", "E6", "G6", "C7", "E7"]):
        tr.add(bell(hz(midi(n)), 1.4), i * 0.07, 0.5)
    t = tvec(1.2)
    sparkle = rng.normal(0, 1, len(t)) * np.exp(-t * 4) * 0.08
    sparkle = sparkle - np.concatenate([[0], sparkle[:-1]])
    tr.add(sparkle, 0.05, 1.0)
    return reverb(tr.buf[: int(1.9 * SR)], 1.2, 0.3)


def build_done():
    tr = Track(1.3)
    for i, n in enumerate(["G5", "C6", "E6"]):
        tr.add(bell(hz(midi(n)), 1.1), i * 0.09, 0.55)
    return reverb(tr.buf[: int(1.3 * SR)], 1.0, 0.25)


if __name__ == "__main__":
    out = sys.argv[1]
    save(f"{out}/music_village.wav", village())
    save(f"{out}/music_battle.wav", battle())
    save(f"{out}/jingle_victory.wav", victory())
    save(f"{out}/jingle_defeat.wav", defeat())
    save(f"{out}/jingle_chest.wav", chest(), peak=0.8)
    save(f"{out}/jingle_build.wav", build_done(), peak=0.75)
