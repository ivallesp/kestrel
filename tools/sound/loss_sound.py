#!/usr/bin/env python3
"""Synthesises the loss scene's sound, app/src/main/res/raw/loss.wav (mono, 32 kHz, 16-bit): the miss and the rain,
then the sun coming back and the kestrel's bright call.

Every sound is made here from sine waves and noise, so the track carries no third-party audio. Its moments follow
LossTimeline (ui/celebration/LossScene.kt), whose flight is CelebrationTimeline's up to the strike; change them
together. Run from the repository root:
    python3 tools/sound/loss_sound.py
"""
import math
import struct
import sys
import wave

import numpy as np
from scipy.signal import butter, sosfilt

RATE = 32_000
# LossTimeline, in seconds.
FOLD, STOOP, STRIKE = 2.100, 2.400, 3.050
GUST_FROM, GUST_TO = 2.620, 3.700
CLIMB, LAND, PERCH = 3.200, 4.380, 4.620
RAIN_FROM, RAIN_FULL = 4.500, 5.700
SHIVER_FROM, SHIVER_TO = 5.950, 6.350
CALL_AT = 6.450
RAIN_EASE_FROM, RAIN_EASE_TO = 7.100, 8.200
SUN_FROM, BLOOM_FROM, BLOOM_GAP = 7.700, 8.500, 0.110
STRETCH_FROM, STRETCH_TO = 8.700, 9.200
READY_CALLS, END = (9.150, 9.380), 10.000

rng = np.random.default_rng(11)
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


def highpass(signal, cutoff):
    return sosfilt(butter(4, cutoff, btype="highpass", fs=RATE, output="sos"), signal)


def bandpass(signal, low, high):
    return sosfilt(butter(4, (low, high), btype="bandpass", fs=RATE, output="sos"), signal)


def fade_out(signal, seconds=0.06):
    """Ends a sound at silence, so a cut never clicks."""
    count = min(len(signal), int(seconds * RATE))
    if count:
        signal[-count:] *= np.linspace(1, 0, count)
    return signal


def noise(length):
    # A couple of samples to spare, so a slice to any window of the same length always fits.
    return rng.standard_normal(int(length * RATE) + 2)


def bell(start, freq, amp, length=2.0):
    """A small bell, as in the win: inharmonic partials, each fading at its own pace."""
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


def note(start, freq, amp, length=1.6):
    """A soft, wooden note: a sine with a little of its octave, a quick start and a long fade."""
    s, lt = window(start, length)
    tone = np.sin(2 * math.pi * freq * lt) + 0.18 * np.sin(2 * math.pi * 2 * freq * lt) * np.exp(-lt / 0.25)
    tone *= np.minimum(1, lt / 0.01) * np.exp(-lt / 0.7)
    out[s] += amp * fade_out(tone, 0.1)


# 1. The wind while the kestrel hangs in it, greyer than on a winning day.
s, lt = window(0, 2.9)
wind = lowpass(noise(2.9), 380)
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

# 5. The gust that takes the tag: a broad gust that swells and passes.
s, lt = window(GUST_FROM, GUST_TO - GUST_FROM)
length = GUST_TO - GUST_FROM
gust = bandpass(noise(length), 350, 2400)[: len(lt)]
out[s] += 0.55 * gust * np.sin(math.pi * lt / length) ** 1.5

# 6. The empty strike: a dull thump in the grass and a rustle, and no bell.
s, lt = window(STRIKE, 0.3)
pitch = 70 * np.exp(-lt / 0.07) + 42
out[s] += 0.55 * fade_out(np.sin(2 * math.pi * np.cumsum(pitch) / RATE) * np.exp(-lt / 0.09))
s, lt = window(STRIKE, 0.25)
out[s] += 0.25 * fade_out(bandpass(noise(0.25), 1200, 4500)[: len(lt)] * np.exp(-lt / 0.06))

# 7. Back to the post with nothing: slower, weaker wingbeats.
s, lt = window(CLIMB, LAND - CLIMB)
whump = lowpass(noise(LAND - CLIMB), 380)[: len(lt)]
beats = np.maximum(0, np.sin(2 * math.pi * 3.4 * lt)) ** 4
out[s] += 1.3 * whump * beats * np.clip(1 - lt / (LAND - CLIMB) * 0.7, 0, 1)

# 8. Talons on the post: a small wooden knock, softer than a winner's.
s, lt = window(PERCH, 0.12)
knock = (np.sin(2 * math.pi * 640 * lt) + 0.5 * np.sin(2 * math.pi * 990 * lt)) * np.exp(-lt / 0.02)
out[s] += 0.24 * fade_out(knock, 0.02)

# 9. A falling phrase in A minor, then a low sigh, over a quiet minor chord.
for at, freq, amp in ((3.25, 659.25, 0.15), (3.75, 523.25, 0.14), (4.3, 440.0, 0.14), (4.95, 349.23, 0.13),
                      (5.6, 329.63, 0.13)):
    note(at, freq, amp)
s, lt = window(4.6, 3.6)
pad = np.zeros_like(lt)
for freq in (220.0, 261.63, 329.63):
    for detune in (-0.5, 0.5):
        pad += np.sin(2 * math.pi * (freq + detune) * lt)
out[s] += 0.022 * pad * np.minimum(1, lt / 0.8) * np.clip((3.6 - lt) / 1.0, 0, 1)

# 10. The rain: a hiss that grows with the drops and eases off before the sun, and single drops ticking on the grass
# and the post while it lasts.
s, lt = window(RAIN_FROM, RAIN_EASE_TO - RAIN_FROM)
grow = np.clip(lt / (RAIN_FULL - RAIN_FROM), 0, 1)
ease = np.clip((lt - (RAIN_EASE_FROM - RAIN_FROM)) / (RAIN_EASE_TO - RAIN_EASE_FROM), 0, 1)
hiss = highpass(noise(RAIN_EASE_TO - RAIN_FROM), 1800)[: len(lt)]
out[s] += 0.13 * hiss * grow * (1 - ease)
for _ in range(140):
    at = RAIN_FROM + (RAIN_EASE_FROM - RAIN_FROM) * rng.random() ** 0.7
    s, lt = window(at, 0.03)
    drop = np.sin(2 * math.pi * rng.uniform(2200, 4200) * lt) * np.exp(-lt / 0.006)
    out[s] += 0.05 * fade_out(drop, 0.005)

# 11. The shiver: the kestrel shakes the rain off its feathers.
s, lt = window(SHIVER_FROM, SHIVER_TO - SHIVER_FROM)
length = SHIVER_TO - SHIVER_FROM
rustle = bandpass(noise(length), 2000, 6000)[: len(lt)]
out[s] += 0.3 * rustle * (0.5 + 0.5 * np.sin(2 * math.pi * 18 * lt)) * np.sin(math.pi * lt / length)

# 12. One low, slow "kee...".
s, lt = window(CALL_AT, 0.42)
glide = 2300 + (1700 - 2300) * (lt / 0.42)
vibrato = 1 + 0.012 * np.sin(2 * math.pi * 6 * lt)
phase = 2 * math.pi * np.cumsum(glide * vibrato) / RATE
tone = np.sin(phase) + 0.25 * np.sin(2 * phase)
out[s] += 0.12 * tone * np.minimum(1, lt / 0.03) * np.minimum(1, (0.42 - lt) / 0.15)

# 13. The sun comes back: a warm major chord swells under a rising bell phrase.
s, lt = window(SUN_FROM, END - SUN_FROM)
pad = np.zeros_like(lt)
for freq in (261.63, 329.63, 392.0, 523.25):
    for detune in (-0.6, 0.6):
        pad += np.sin(2 * math.pi * (freq + detune) * lt)
length = END - SUN_FROM
out[s] += 0.024 * pad * np.minimum(1, lt / 0.9) * np.clip((length - lt) / 1.0, 0, 1)
for at, freq in ((8.05, 523.25), (8.3, 659.25), (8.55, 783.99), (8.9, 1046.5)):
    bell(at, freq, 0.14)

# 14. A few flowers open by the post, one pluck each (C major pentatonic).
for index, freq in enumerate((1046.5, 1174.66, 1318.51, 1567.98, 1760.0, 1318.51, 1567.98)):
    s, lt = window(BLOOM_FROM + index * BLOOM_GAP, 0.18)
    out[s] += 0.045 * fade_out(np.sin(2 * math.pi * freq * lt) * np.exp(-lt / 0.05), 0.03)

# 15. The stretch: one soft "fwip" of a wing opening and folding.
s, lt = window(STRETCH_FROM, STRETCH_TO - STRETCH_FROM)
length = STRETCH_TO - STRETCH_FROM
out[s] += 0.4 * bandpass(noise(length), 900, 3000)[: len(lt)] * np.sin(math.pi * lt / length) ** 2

# 16. "kee", "kee!": the kestrel is ready for the next one.
kee(READY_CALLS[0], 0.13, 3250, 2650, 0.18)
kee(READY_CALLS[1], 0.17, 3400, 2650, 0.21)

# Master: gentle limiting, -4 dBFS peak (a quieter track than the win's), short fades at both ends.
out = np.tanh(out * 1.2)
out *= 0.63 / np.max(np.abs(out))
out *= np.minimum(1, t / 0.05) * np.clip((END - t) / 0.5, 0, 1)

path = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw/loss.wav"
with wave.open(path, "wb") as file:
    file.setnchannels(1)
    file.setsampwidth(2)
    file.setframerate(RATE)
    file.writeframes(struct.pack(f"<{n}h", *(np.round(out * 32767).astype(np.int16))))
print(f"{path}: {END:.1f} s, {RATE} Hz, peak {20 * math.log10(np.max(np.abs(out))):.1f} dBFS")
