#!/usr/bin/env python3
"""Synthesises the kestrel's call for the celebration's Easter egg, app/src/main/res/raw/kestrel_chirp.wav.

Two sharp "kee" notes made from sine waves, like the three in the celebration track (celebration_sound.py), so it
carries no third-party audio. Run from the repository root:
    python3 tools/sound/kestrel_chirp.py
"""
import math
import struct
import sys
import wave

import numpy as np

RATE = 32_000
LENGTH = 0.42


def kee(out, start, length, top, bottom, amp):
    """A high note sliding down, roughened by a fast flutter, with soft edges so it never clicks."""
    a = int(start * RATE)
    lt = np.arange(int(length * RATE)) / RATE
    glide = top + (bottom - top) * (lt / length)
    flutter = 1 + 0.018 * np.sin(2 * math.pi * 38 * lt)
    phase = 2 * math.pi * np.cumsum(glide * flutter) / RATE
    tone = np.sin(phase) + 0.32 * np.sin(2 * phase) + 0.12 * np.sin(3 * phase)
    envelope = np.minimum(1, lt / 0.008) * np.minimum(1, (length - lt) / 0.045)
    out[a : a + len(lt)] += amp * tone * envelope


n = int(LENGTH * RATE)
out = np.zeros(n)
kee(out, 0.01, 0.12, 3300, 2650, 0.5)
kee(out, 0.2, 0.17, 3450, 2600, 0.6)
out *= 0.707 / np.max(np.abs(out))

path = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw/kestrel_chirp.wav"
with wave.open(path, "wb") as file:
    file.setnchannels(1)
    file.setsampwidth(2)
    file.setframerate(RATE)
    file.writeframes(struct.pack(f"<{n}h", *(np.round(out * 32767).astype(np.int16))))
print(f"{path}: {LENGTH:.2f} s, {RATE} Hz, peak {20 * math.log10(np.max(np.abs(out))):.1f} dBFS")
