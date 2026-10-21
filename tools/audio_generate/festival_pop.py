"""Original 124 BPM instrumental pop loop. No samples, vocals or external music.
Reproduce: python3 tools/audio_generate/festival_pop.py
Requires numpy and ffmpeg. Output: stereo OGG, 16 bars (30.97 seconds).
"""
from pathlib import Path
import subprocess, wave
import numpy as np

RATE = 32000
BEAT = 60 / 124
SECONDS = 64 * BEAT
N = round(SECONDS * RATE)
rng = np.random.default_rng(20261021)
mix = np.zeros((N, 2), dtype=np.float64)

def add(sound, beat, gain=1., pan=0.):
    start = round(beat * BEAT * RATE)
    indices = (start + np.arange(len(sound))) % N
    mix[indices, 0] += sound * gain * np.sqrt((1 - pan) / 2)
    mix[indices, 1] += sound * gain * np.sqrt((1 + pan) / 2)

def note(midi, length, kind='pluck'):
    t = np.arange(round(length * RATE)) / RATE
    f = 440 * 2 ** ((midi - 69) / 12)
    attack = np.minimum(t / .008, 1)
    if kind == 'bass':
        y = np.sin(2*np.pi*f*t) + .27*np.sin(4*np.pi*f*t) + .08*np.sin(6*np.pi*f*t)
        env = attack * np.exp(-t*6) * np.minimum((length-t)/.04, 1)
    elif kind == 'pad':
        y = sum(np.sin(2*np.pi*(f*(1+d))*t) for d in [-.003,0,.003])/3
        y += .16*np.sin(4*np.pi*f*t)
        env = np.minimum(t/.08,1)*np.minimum((length-t)/.2,1)*.65
    else:
        y = np.sin(2*np.pi*f*t) + .4*np.sin(4*np.pi*f*t) + .16*np.sin(6*np.pi*f*t)
        env = attack*np.exp(-t*9)*np.minimum((length-t)/.025,1)
    return y*env

# Four chord pop progression, with syncopated bass, bright arpeggios and an original hook.
chords = [(60,64,67), (55,59,62), (57,60,64), (53,57,60)]
roots = [36,31,33,29]
hook = [76,79,81,79,76,74,72,74, 74,79,83,81,79,74,71,74,
        76,81,84,83,81,79,76,74, 77,81,79,77,76,74,72,79]
for bar in range(16):
    chord=chords[bar%4]
    for m in chord:
        add(note(m,4*BEAT,'pad'),bar*4,.055,(-.35 if m%2 else .35))
    for step in range(8):
        beat=bar*4+step*.5
        add(note(chord[(step+bar)%3]+12,.26),beat,.085,(-.5 if step%2 else .5))
        if step in [0,3,4,6]: add(note(roots[bar%4],.22,'bass'),beat,.4)
        if bar >= 4:
            melody=hook[((bar%4)*8+step)%32] + (12 if bar>=12 and step in [2,6] else 0)
            sound=note(melody,.28)
            add(sound,beat,.14)
            add(sound,beat+.75,.035,.55)
    # Synthesized four-on-floor kick with a pitch drop.
    for k in range(4):
        t=np.arange(round(.25*RATE))/RATE
        kick=np.sin(2*np.pi*(48*t+8*(1-np.exp(-t*35))))*np.exp(-t*20)
        kick += rng.standard_normal(len(t))*.035*np.exp(-t*200)
        add(kick,bar*4+k,.58)
    for k in [1,3]:
        t=np.arange(round(.16*RATE))/RATE
        noise=rng.standard_normal(len(t))
        clap=(noise-np.roll(noise,1))*.45*np.exp(-t*26)
        add(clap,bar*4+k,.19)
    for k in range(8):
        t=np.arange(round(.055*RATE))/RATE
        noise=rng.standard_normal(len(t))
        hat=(noise-np.roll(noise,1))*np.exp(-t*85)
        add(hat,bar*4+k*.5,.032 if k%2==0 else .05,.3)
# Gentle bus compression and seam-safe circular note tails.
mix=np.tanh(mix*1.3)
mix*=.86/max(float(np.max(np.abs(mix))),1e-9)
root=Path(__file__).resolve().parents[2]
out=root/'app/src/main/assets/audio/music/festival_pop.ogg'
out.parent.mkdir(parents=True,exist_ok=True)
import tempfile
with tempfile.TemporaryDirectory() as tmp:
    wav=Path(tmp)/'festival.wav'
    with wave.open(str(wav),'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes((mix*32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','3',str(out)],check=True)
print(f'{out}: {SECONDS:.3f}s, {out.stat().st_size} bytes, peak {20*np.log10(np.max(np.abs(mix))):.2f} dBFS')
