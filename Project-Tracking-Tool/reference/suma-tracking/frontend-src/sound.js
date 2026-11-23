// Step 40: a short bell for a successful sign-in, made in the browser (no sound file needed).
// Browsers allow sound only after a click or key press; pressing "Sign in" counts.
let ctx = null

export function playBell() {
  try {
    const AC = window.AudioContext || window.webkitAudioContext
    if (!AC) return
    ctx = ctx || new AC()
    if (ctx.state === 'suspended') ctx.resume()
    const now = ctx.currentTime
    const master = ctx.createGain()
    master.gain.value = 0.22
    master.connect(ctx.destination)
    // a bell = a few partials that fade at different speeds
    const partials = [[880, 1, 1.9], [1760, 0.45, 1.2], [2637, 0.28, 0.8], [3520, 0.14, 0.5], [1318.5, 0.35, 1.5]]
    partials.forEach(([freq, level, decay], i) => {
      const osc = ctx.createOscillator()
      const g = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = freq
      g.gain.setValueAtTime(0.0001, now)
      g.gain.exponentialRampToValueAtTime(level, now + 0.008 + i * 0.002)
      g.gain.exponentialRampToValueAtTime(0.0001, now + decay)
      osc.connect(g)
      g.connect(master)
      osc.start(now)
      osc.stop(now + decay + 0.05)
    })
  } catch {
    /* no sound is fine */
  }
}

// Step 41: a very loud bell for punch in and punch out, and an alarm for a break that runs over.
// It is as loud as the browser allows: three strikes, a compressor to push the level up, and bright tones.
let loudCtx = null

function audio() {
  const AC = window.AudioContext || window.webkitAudioContext
  if (!AC) return null
  loudCtx = loudCtx || new AC()
  if (loudCtx.state === 'suspended') loudCtx.resume()
  return loudCtx
}

function chain(ac) {
  const comp = ac.createDynamicsCompressor()
  comp.threshold.value = -30
  comp.knee.value = 6
  comp.ratio.value = 12
  comp.attack.value = 0.002
  comp.release.value = 0.25
  const master = ac.createGain()
  master.gain.value = 1.0
  comp.connect(master)
  master.connect(ac.destination)
  return comp
}

function strike(ac, out, at, base, length) {
  const partials = [[1, 1, 1], [2.0, 0.6, 0.7], [2.76, 0.45, 0.55], [5.4, 0.25, 0.35], [0.5, 0.5, 1.2]]
  partials.forEach(([mult, level, decay]) => {
    ;['sine', 'triangle'].forEach((type, j) => {
      const osc = ac.createOscillator()
      const g = ac.createGain()
      osc.type = type
      osc.frequency.value = base * mult * (j ? 1.003 : 1)
      g.gain.setValueAtTime(0.0001, at)
      g.gain.exponentialRampToValueAtTime(level, at + 0.005)
      g.gain.exponentialRampToValueAtTime(0.0001, at + length * decay)
      osc.connect(g)
      g.connect(out)
      osc.start(at)
      osc.stop(at + length * decay + 0.05)
    })
  })
}

/** kind "in": rising ding-ding-DONG. kind "out": falling. */
export function playLoudBell(kind = 'in') {
  try {
    const ac = audio()
    if (!ac) return
    const out = chain(ac)
    const now = ac.currentTime + 0.02
    const notes = kind === 'out' ? [1318.5, 1046.5, 784] : [784, 1046.5, 1318.5]
    notes.forEach((f, i) => strike(ac, out, now + i * 0.32, f, i === 2 ? 2.6 : 1.4))
  } catch {
    /* no sound is fine */
  }
}

/** An alarm: fast loud beeps, for a break over time. */
export function playAlarm(times = 6) {
  try {
    const ac = audio()
    if (!ac) return
    const out = chain(ac)
    const now = ac.currentTime + 0.02
    for (let i = 0; i < times; i++) {
      const osc = ac.createOscillator()
      const g = ac.createGain()
      osc.type = 'square'
      osc.frequency.setValueAtTime(i % 2 ? 1046 : 1568, now + i * 0.22)
      g.gain.setValueAtTime(0.0001, now + i * 0.22)
      g.gain.exponentialRampToValueAtTime(0.5, now + i * 0.22 + 0.01)
      g.gain.exponentialRampToValueAtTime(0.0001, now + i * 0.22 + 0.18)
      osc.connect(g)
      g.connect(out)
      osc.start(now + i * 0.22)
      osc.stop(now + i * 0.22 + 0.2)
    }
  } catch {
    /* no sound is fine */
  }
}
