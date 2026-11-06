#!/usr/bin/env python3
"""Synthesises the win celebration's sound, app/src/main/res/raw/celebration.wav (mono, 32 kHz, 16-bit).

Every sound is made here from sine waves and noise, so the track carries no third-party audio. Its moments follow
CelebrationTimeline (ui/celebration/CelebrationScene.kt); change both together. Run from the repository root:
    python3 tools/sound/celebration_sound.py
"""
import math
import struct
import sys
import wave

import numpy as np
from scipy.signal import butter, sosfilt

RATE = 32_000
# CelebrationTimeline, in seconds.
FOLD, STOOP, STRIKE = 2.100, 2.400, 3.050
CLIMB, LAND, PERCH = 3.200, 4.380, 4.620
MANTLE, CALLS = 4.900, (5.000, 5.260, 5.520)
BLOOM_FROM, BLOOM_SPREAD, END = 5.000, 0.900, 7.400

rng = np.random.default_rng(7)
n = int(END * RATE)
t = np.arange(n) / RATE
out = np.zeros(n)


def window(start, length):
    """Indexes and local time of a sound that starts at [start] seconds and lasts [length]."""
    a = max(0, int(start * RATE))
    b = min(n, int((start + length) * RATE))
    return slice(a, b), (np.arange(b - a) / RATE)


def lowpass(signal, cutoff):
    return sosfilt(butter(4, cutoff, btype="lowpass", fs=RATE, output="sos"), signal)


def bandpass(signal, low, high):
    return sosfilt(butter(4, (low, high), btype="bandpass", fs=RATE, output="sos"), signal)


def fade_out(signal, seconds=0.06):
    """Ends a sound at silence, so a cut never clicks."""
    count = min(len(signal), int(seconds * RATE))
    if count:
        signal[-count:] *= np.linspace(1, 0, count)
    return signal


def bell(start, freq, amp, length=2.2):
    """A small bell: inharmonic partials, each fading at its own pace."""
    s, lt = window(start, length)
    tone = np.zeros_like(lt)
    for ratio, weight, decay in ((1.0, 1.0, 0.9), (2.01, 0.45, 0.55), (3.02, 0.22, 0.35), (4.17, 0.1, 0.2)):
        tone += weight * np.sin(2 * math.pi * freq * ratio * lt) * np.exp(-lt / decay)
    tone *= np.minimum(1, lt / 0.004)
    out[s] += amp * fade_out(tone)


def kee(start, length, top, bottom, amp):
    """A kestrel's sharp call: a high note sliding down, roughened by a fast flutter."""
    s, lt = window(start, length)
    glide = top + (bottom - top) * (lt / length)
    flutter = 1 + 0.018 * np.sin(2 * math.pi * 38 * lt)
    phase = 2 * math.pi * np.cumsum(glide * flutter) / RATE
    tone = np.sin(phase) + 0.32 * np.sin(2 * phase) + 0.12 * np.sin(3 * phase)
    envelope = np.minimum(1, lt / 0.008) * np.minimum(1, (length - lt) / 0.045)
    out[s] += amp * tone * envelope


def noise(length):
    # A couple of samples to spare, so a slice to any window of the same length always fits.
    return rng.standard_normal(int(length * RATE) + 2)


# 1. The wind while the kestrel hangs in it.
s, lt = window(0, 2.9)
wind = lowpass(noise(2.9), 500)
out[s] += 0.6 * wind[: len(lt)] * np.minimum(1, lt / 0.7) * np.clip((2.9 - lt) / 0.6, 0, 1)

# 2. The hovering wingbeats, 7.5 a second.
s, lt = window(0.15, FOLD - 0.15)
flutter = bandpass(noise(FOLD), 250, 1100)[: len(lt)]
beats = np.maximum(0, np.sin(2 * math.pi * 7.5 * lt)) ** 3
out[s] += 0.45 * flutter * beats * np.minimum(1, lt / 0.4)

# 3. The fold: one soft "fwip".
s, lt = window(FOLD, 0.22)
out[s] += 0.5 * bandpass(noise(0.22), 900, 3000)[: len(lt)] * np.sin(math.pi * lt / 0.22) ** 2

# 4. The stoop: a whoosh that rises in pitch and grows until the strike.
s, lt = window(STOOP, STRIKE - STOOP)
raw = noise(STRIKE - STOOP)
low = bandpass(raw, 300, 1200)
high = bandpass(raw, 1200, 4200)
p = lt / (STRIKE - STOOP)
out[s] += (0.8 * (1 - p) * low[: len(lt)] + 0.9 * p * high[: len(lt)]) * (p**2)

# 5. The strike: a thump in the grass, a rustle and a bright ring.
s, lt = window(STRIKE, 0.35)
pitch = 95 * np.exp(-lt / 0.08) + 48
out[s] += 0.75 * fade_out(np.sin(2 * math.pi * np.cumsum(pitch) / RATE) * np.exp(-lt / 0.11))
s, lt = window(STRIKE, 0.3)
out[s] += 0.35 * fade_out(bandpass(noise(0.3), 1500, 6000)[: len(lt)] * np.exp(-lt / 0.07))
bell(STRIKE + 0.01, 1318.5, 0.16)

# 6. Carrying the lot: slower, deeper wingbeats.
s, lt = window(CLIMB, LAND - CLIMB)
whump = lowpass(noise(LAND - CLIMB), 420)[: len(lt)]
beats = np.maximum(0, np.sin(2 * math.pi * 4.2 * lt)) ** 4
out[s] += 1.8 * whump * beats * np.clip(1 - lt / (LAND - CLIMB) * 0.6, 0, 1)

# 7. Talons on the post: a small wooden knock.
s, lt = window(PERCH, 0.12)
out[s] += 0.32 * fade_out((np.sin(2 * math.pi * 720 * lt) + 0.6 * np.sin(2 * math.pi * 1130 * lt)) * np.exp(-lt / 0.025), 0.02)

# 8. The mantle: a rising bell arpeggio, then a warm chord that swells and fades.
for offset, freq in ((0.02, 523.25), (0.12, 659.25), (0.22, 783.99), (0.35, 1046.5), (0.5, 1318.5)):
    bell(MANTLE + offset, freq, 0.2)
s, lt = window(MANTLE + 0.25, END - MANTLE - 0.25)
pad = np.zeros_like(lt)
for freq in (261.63, 329.63, 392.0, 587.33):
    for detune in (-0.6, 0.6):
        pad += np.sin(2 * math.pi * (freq + detune) * lt)
length = END - MANTLE - 0.25
out[s] += 0.028 * pad * np.minimum(1, lt / 0.5) * np.clip((length - lt) / 1.4, 0, 1)

# 9. "kee", "kee", "kee!".
for index, at in enumerate(CALLS):
    last = index == len(CALLS) - 1
    kee(at, 0.17 if last else 0.13, 3400 if last else 3250, 2650, 0.23 if last else 0.2)

# 10. The meadow: a tiny pluck for every flower as it opens (C major pentatonic), in the scene's order.
notes = (1046.5, 1174.66, 1318.51, 1567.98, 1760.0)
for index in range(30):
    x = (index * 0.6180339 + 0.04) % 1.0
    at = BLOOM_FROM + abs(x - 0.2) * BLOOM_SPREAD
    s, lt = window(at, 0.18)
    out[s] += 0.05 * fade_out(np.sin(2 * math.pi * notes[index % len(notes)] * lt) * np.exp(-lt / 0.05), 0.03)

# Master: gentle limiting, -3 dBFS peak, short fades at both ends.
out = np.tanh(out * 1.2)
out *= 0.707 / np.max(np.abs(out))
out *= np.minimum(1, t / 0.05) * np.clip((END - t) / 0.2, 0, 1)

path = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw/celebration.wav"
with wave.open(path, "wb") as file:
    file.setnchannels(1)
    file.setsampwidth(2)
    file.setframerate(RATE)
    file.writeframes(struct.pack(f"<{n}h", *(np.round(out * 32767).astype(np.int16))))
print(f"{path}: {END:.1f} s, {RATE} Hz, peak {20 * math.log10(np.max(np.abs(out))):.1f} dBFS")
