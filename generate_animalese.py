"""
Generate animalese voice samples for BubbleChat.
Uses formant synthesis: harmonic-rich source → biquad bandpass formant filter → envelope.
Tuned for subtle, satisfying mumble — not bouncy or annoying.
No external dependencies (uses built-in wave + struct modules).
Converts to OGG via ffmpeg.
"""
import struct
import wave
import math
import subprocess
import os

OUT_BASE = os.path.join(os.path.dirname(__file__),
    "src", "main", "resources", "Common", "Sounds", "BubbleChat")
SAMPLE_RATE = 44100

# Voice parameters — each voice is a distinct character but all are gentle/subtle
VOICES = [
    # Voice 1: Bright — slightly higher, clear
    {"freq": 310, "harmonics": 4, "attack_ms": 12, "sustain": 0.50, "decay": 8, "vib_hz": 5.0, "vib_depth": 0.012, "dur": 0.115, "formant_mix": 0.25, "warmth": 0.60},
    # Voice 2: Warm — medium pitch, round, friendly
    {"freq": 260, "harmonics": 4, "attack_ms": 15, "sustain": 0.55, "decay": 7, "vib_hz": 4.5, "vib_depth": 0.015, "dur": 0.125, "formant_mix": 0.20, "warmth": 0.70},
    # Voice 3: Deep — low, gentle rumble
    {"freq": 175, "harmonics": 5, "attack_ms": 18, "sustain": 0.55, "decay": 6, "vib_hz": 4.0, "vib_depth": 0.010, "dur": 0.140, "formant_mix": 0.20, "warmth": 0.72},
    # Voice 4: Soft — gentle, breathy, pure
    {"freq": 285, "harmonics": 3, "attack_ms": 20, "sustain": 0.50, "decay": 7, "vib_hz": 5.0, "vib_depth": 0.018, "dur": 0.120, "formant_mix": 0.15, "warmth": 0.75},
    # Voice 5: Sharp — slightly higher, a bit more edge
    {"freq": 360, "harmonics": 5, "attack_ms": 10, "sustain": 0.45, "decay": 10, "vib_hz": 5.5, "vib_depth": 0.012, "dur": 0.100, "formant_mix": 0.28, "warmth": 0.55},
    # Voice 6: Mellow — low-medium, very smooth
    {"freq": 220, "harmonics": 4, "attack_ms": 20, "sustain": 0.60, "decay": 5, "vib_hz": 3.5, "vib_depth": 0.015, "dur": 0.145, "formant_mix": 0.18, "warmth": 0.75},
    # Voice 7: Raspy — more harmonics for texture, but still gentle
    {"freq": 250, "harmonics": 7, "attack_ms": 12, "sustain": 0.48, "decay": 9, "vib_hz": 5.0, "vib_depth": 0.012, "dur": 0.110, "formant_mix": 0.22, "warmth": 0.62},
    # Voice 8: Squeaky — higher pitch, light, quick
    {"freq": 420, "harmonics": 3, "attack_ms": 10, "sustain": 0.40, "decay": 12, "vib_hz": 6.0, "vib_depth": 0.015, "dur": 0.090, "formant_mix": 0.22, "warmth": 0.62},
]

# TIGHT pitch range — letters only vary ±5% from base
# Just enough to feel like different syllables, not a melody
LETTER_PITCH = {
    'A': 1.00, 'B': 0.97, 'C': 1.03, 'D': 0.98, 'E': 1.02,
    'F': 1.04, 'G': 0.96, 'H': 1.03, 'I': 1.02, 'J': 1.01,
    'K': 1.03, 'L': 0.99, 'M': 0.96, 'N': 0.97, 'O': 0.99,
    'P': 1.01, 'Q': 0.98, 'R': 0.99, 'S': 1.05, 'T': 1.04,
    'U': 0.97, 'V': 0.98, 'W': 0.96, 'X': 1.04, 'Y': 1.02,
    'Z': 1.05,
}

# Formant frequencies — still give each letter subtle character
# but the mix ratio is now much lower so they're gentle coloring, not harsh filtering
LETTER_FORMANT = {
    'A': (800, 1200),  'B': (400, 1000),  'C': (850, 1800),
    'D': (450, 1400),  'E': (550, 1700),  'F': (1000, 2200),
    'G': (400, 1100),  'H': (750, 2000),  'I': (400, 2100),
    'J': (500, 1600),  'K': (750, 1700),  'L': (500, 1300),
    'M': (400, 950),   'N': (450, 1100),  'O': (500, 850),
    'P': (600, 1300),  'Q': (500, 1000),  'R': (550, 1200),
    'S': (1200, 2800), 'T': (700, 1900),  'U': (400, 800),
    'V': (500, 1300),  'W': (420, 850),   'X': (900, 2200),
    'Y': (480, 1800),  'Z': (1100, 2600),
}

# Duration variation is also tighter — no letter is drastically shorter/longer
LETTER_DUR_MULT = {
    'A': 1.05, 'B': 0.92, 'C': 0.95, 'D': 0.93, 'E': 1.05,
    'F': 0.95, 'G': 0.93, 'H': 0.96, 'I': 1.04, 'J': 0.96,
    'K': 0.92, 'L': 1.02, 'M': 1.04, 'N': 1.02, 'O': 1.05,
    'P': 0.92, 'Q': 0.95, 'R': 1.00, 'S': 0.95, 'T': 0.92,
    'U': 1.04, 'V': 0.97, 'W': 0.98, 'X': 0.94, 'Y': 1.00,
    'Z': 0.95,
}


def biquad_bandpass(samples, center_freq, q):
    """Apply a 2nd-order IIR bandpass filter (biquad)."""
    w0 = 2 * math.pi * center_freq / SAMPLE_RATE
    alpha = math.sin(w0) / (2 * q)
    b0 = alpha
    b1 = 0.0
    b2 = -alpha
    a0 = 1 + alpha
    a1 = -2 * math.cos(w0)
    a2 = 1 - alpha
    b0 /= a0; b1 /= a0; b2 /= a0
    a1 /= a0; a2 /= a0

    out = []
    x1 = x2 = y1 = y2 = 0.0
    for x in samples:
        y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        out.append(y)
        x2, x1 = x1, x
        y2, y1 = y1, y
    return out


def biquad_lowpass(samples, cutoff, q=0.707):
    """Apply a 2nd-order IIR lowpass filter to soften harsh frequencies."""
    w0 = 2 * math.pi * cutoff / SAMPLE_RATE
    alpha = math.sin(w0) / (2 * q)
    b0 = (1 - math.cos(w0)) / 2
    b1 = 1 - math.cos(w0)
    b2 = (1 - math.cos(w0)) / 2
    a0 = 1 + alpha
    a1 = -2 * math.cos(w0)
    a2 = 1 - alpha
    b0 /= a0; b1 /= a0; b2 /= a0
    a1 /= a0; a2 /= a0

    out = []
    x1 = x2 = y1 = y2 = 0.0
    for x in samples:
        y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        out.append(y)
        x2, x1 = x1, x
        y2, y1 = y1, y
    return out


def normalize(samples, target_db=-6.0):
    """Normalize samples to target dB level."""
    peak = max(abs(s) for s in samples) if samples else 1.0
    if peak < 1e-6:
        return samples
    target_amp = 10 ** (target_db / 20.0)
    scale = target_amp / peak
    return [s * scale for s in samples]


def generate_voice_sample(voice, letter):
    """Generate a single animalese sound — gentle, satisfying mumble."""
    base_freq = voice["freq"]
    num_harmonics = voice["harmonics"]
    attack_ms = voice["attack_ms"]
    sustain_pct = voice["sustain"]
    decay_rate = voice["decay"]
    vib_hz = voice["vib_hz"]
    vib_depth = voice["vib_depth"]
    base_dur = voice["dur"]
    formant_mix = voice["formant_mix"]   # how much formant coloring (low = subtle)
    warmth = voice["warmth"]             # how much unfiltered body to keep

    freq = base_freq * LETTER_PITCH[letter]
    formant1, formant2 = LETTER_FORMANT[letter]
    duration = base_dur * LETTER_DUR_MULT[letter]

    num_samples = int(SAMPLE_RATE * duration)
    attack_samples = int(SAMPLE_RATE * attack_ms / 1000.0)
    sustain_end = int(num_samples * sustain_pct)

    phase = 0.0
    raw = []

    for i in range(num_samples):
        t = i / SAMPLE_RATE

        # Gentle vibrato
        vib = 1.0 + vib_depth * math.sin(2 * math.pi * vib_hz * t)
        inst_freq = freq * vib

        phase += 2 * math.pi * inst_freq / SAMPLE_RATE

        # Harmonic-rich signal (sawtooth approximation)
        # Use decreasing harmonic amplitudes with extra rolloff for softness
        signal = 0.0
        for h in range(1, num_harmonics + 1):
            # Extra rolloff: 1/h^1.3 instead of 1/h — softer than pure sawtooth
            signal += math.sin(h * phase) / (h ** 1.3)

        harm_sum = sum(1.0 / (h ** 1.3) for h in range(1, num_harmonics + 1))
        signal /= harm_sum

        # Smooth amplitude envelope — gentle attack, long sustain, slow decay
        if i < attack_samples:
            # Raised cosine attack (no clicks)
            env = 0.5 * (1 - math.cos(math.pi * i / max(attack_samples, 1)))
        elif i < sustain_end:
            env = 1.0
        else:
            # Gentle exponential decay
            decay_t = (i - sustain_end) / SAMPLE_RATE
            env = math.exp(-decay_t * decay_rate)

        raw.append(signal * env)

    # Gentle formant coloring (NOT aggressive filtering)
    f1_filtered = biquad_bandpass(raw, formant1, 1.8)  # wider Q = gentler
    f2_filtered = biquad_bandpass(raw, formant2, 2.2)

    # Mix: mostly warm body + subtle formant color
    # warmth=0.70 + formant_mix=0.20 means: 70% body + 10% F1 + 10% F2
    f1_share = formant_mix * 0.55
    f2_share = formant_mix * 0.45
    body_share = warmth

    mixed = []
    for i in range(len(raw)):
        s = body_share * raw[i] + f1_share * f1_filtered[i] + f2_share * f2_filtered[i]
        mixed.append(s)

    # Lowpass filter to remove harsh high frequencies — makes it feel warm/rounded
    softened = biquad_lowpass(mixed, 2800, q=0.8)

    # Normalize to -6dB (quieter than before, more subtle)
    result = normalize(softened, -6.0)
    return result


def write_wav(filepath, samples):
    """Write float samples to a 16-bit mono WAV file."""
    with wave.open(filepath, 'w') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        for s in samples:
            val = int(s * 32767)
            val = max(-32768, min(32767, val))
            wf.writeframes(struct.pack('<h', val))


def main():
    total = 0
    for v_idx, voice in enumerate(VOICES):
        v_num = v_idx + 1
        voice_dir = os.path.join(OUT_BASE, f"Voice{v_num}")
        os.makedirs(voice_dir, exist_ok=True)

        for letter in "ABCDEFGHIJKLMNOPQRSTUVWXYZ":
            wav_path = os.path.join(voice_dir, f"{letter}.wav")
            ogg_path = os.path.join(voice_dir, f"{letter}.ogg")

            samples = generate_voice_sample(voice, letter)
            write_wav(wav_path, samples)

            result = subprocess.run(
                ["ffmpeg", "-y", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "4", ogg_path],
                capture_output=True, text=True
            )
            if result.returncode != 0:
                print(f"  ERROR converting {letter}: {result.stderr[:200]}")
            else:
                os.remove(wav_path)
                total += 1

        print(f"Voice {v_num} complete (26 samples)")

    print(f"\nDone! Generated {total}/208 animalese samples.")


if __name__ == "__main__":
    main()
