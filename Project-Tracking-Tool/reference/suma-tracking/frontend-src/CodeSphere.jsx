import { useEffect, useRef } from 'react'

// Step 40: the "access granted" background. Decorative code (not real code that runs) on a 3D sphere
// with orbit rings. It turns by itself, follows the mouse, and can be dragged in any direction, 360 degrees.

const SNIPPETS = [
  'ticket.assign(agent)', 'sla.check(124628)', 'escalate(TIER_2)', 'role === "L1_SUPPORT"', 'await import.sheet("Nov26")',
  'SELECT * FROM tickets', 'status: RESOLVED', 'auth.grant(token)', '200 OK', 'ACCESS_GRANTED', 'zendesk.sync()',
  'keypanion.fetch()', 'notify(lead, BREACH)', 'workload.balance()', 'if (sla < 0.8) warn()', 'project: Codility',
  'project: SouthCo', 'audit.append(event)', 'permissions.has(TICKET_VIEW)', 'shift: 09:00-18:00', 'queue.length = 0',
  'resolve(ticket)', 'const uptime = 99.98', '0x7F3A9C', 'hash: sha256', 'tls 1.3', 'priority: CRITICAL',
  'route /api/tickets', 'kb.search("password")', 'export default Suma', 'first_response < 60m', 'git push origin main',
  '{ ok: true }', 'new Ticket()', 'sla.met++', 'team.on_shift()', 'L2.handover()', '=> dashboard', 'encrypt(AES-256)',
]

function fibonacciSphere(n) {
  const pts = []
  const golden = Math.PI * (3 - Math.sqrt(5))
  for (let i = 0; i < n; i++) {
    const y = 1 - (i / (n - 1)) * 2
    const r = Math.sqrt(1 - y * y)
    const t = golden * i
    pts.push({ x: Math.cos(t) * r, y, z: Math.sin(t) * r, text: SNIPPETS[i % SNIPPETS.length], hot: i % 11 === 0 })
  }
  return pts
}

export default function CodeSphere({ accent = '#3EE6C1', second = '#FF7A45', intensity = 1, count = 220, className = '' }) {
  const canvasRef = useRef(null)

  useEffect(() => {
    const canvas = canvasRef.current
    const g = canvas.getContext('2d')
    const reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const points = fibonacciSphere(count)
    let w = 0
    let h = 0
    let dpr = 1
    let raf = 0
    // rotation: auto spin + mouse aim + drag with momentum
    const rot = { yaw: 0.6, pitch: -0.25, vYaw: reduce ? 0 : 0.0032, vPitch: 0 }
    const aim = { yaw: 0, pitch: 0 }
    const drag = { on: false, x: 0, y: 0 }

    function resize() {
      dpr = Math.min(window.devicePixelRatio || 1, 2)
      w = canvas.clientWidth
      h = canvas.clientHeight
      canvas.width = Math.round(w * dpr)
      canvas.height = Math.round(h * dpr)
      g.setTransform(dpr, 0, 0, dpr, 0, 0)
    }

    function onMove(e) {
      const p = e.touches ? e.touches[0] : e
      if (drag.on) {
        const dx = p.clientX - drag.x
        const dy = p.clientY - drag.y
        drag.x = p.clientX
        drag.y = p.clientY
        rot.yaw += dx * 0.008
        rot.pitch += dy * 0.008
        rot.vYaw = dx * 0.0009
        rot.vPitch = dy * 0.0009
      } else {
        // aiming: moving across the screen turns the sphere a full circle left-right and up-down
        aim.yaw = ((p.clientX / Math.max(1, window.innerWidth)) - 0.5) * Math.PI * 2
        aim.pitch = ((p.clientY / Math.max(1, window.innerHeight)) - 0.5) * Math.PI * 2
      }
    }
    function onDown(e) {
      const p = e.touches ? e.touches[0] : e
      drag.on = true
      drag.x = p.clientX
      drag.y = p.clientY
    }
    function onUp() {
      drag.on = false
    }

    function project(x, y, z, cy, sy, cp, sp, radius) {
      // turn around Y (yaw), then around X (pitch)
      const x1 = x * cy + z * sy
      const z1 = -x * sy + z * cy
      const y2 = y * cp - z1 * sp
      const z2 = y * sp + z1 * cp
      const depth = 2.6
      const s = depth / (depth - z2)
      return { sx: w / 2 + x1 * radius * s, sy: h / 2 + y2 * radius * s, z: z2, s }
    }

    function ring(tilt, spin, radiusK, color, cy, sy, cp, sp, radius) {
      g.beginPath()
      for (let i = 0; i <= 96; i++) {
        const a = (i / 96) * Math.PI * 2
        // a circle, tilted
        let x = Math.cos(a) * radiusK
        let y = Math.sin(a) * radiusK * Math.cos(tilt)
        let z = Math.sin(a) * radiusK * Math.sin(tilt)
        const xs = x * Math.cos(spin) - y * Math.sin(spin)
        const ys = x * Math.sin(spin) + y * Math.cos(spin)
        x = xs
        y = ys
        const p = project(x, y, z, cy, sy, cp, sp, radius)
        if (i === 0) g.moveTo(p.sx, p.sy)
        else g.lineTo(p.sx, p.sy)
      }
      g.strokeStyle = color
      g.lineWidth = 1.2
      g.stroke()
    }

    let t = 0
    function frame() {
      t += 1
      if (!drag.on) {
        rot.yaw += rot.vYaw
        rot.pitch += rot.vPitch
        // ease towards where the mouse points, on top of the slow spin
        rot.aimYaw = (rot.aimYaw || 0) + (aim.yaw - (rot.aimYaw || 0)) * 0.05
        rot.aimPitch = (rot.aimPitch || 0) + (aim.pitch - (rot.aimPitch || 0)) * 0.05
        rot.vYaw += ((reduce ? 0 : 0.0032) - rot.vYaw) * 0.02
        rot.vPitch *= 0.95
      }
      const yaw = rot.yaw + (rot.aimYaw || 0)
      const pitch = rot.pitch + (rot.aimPitch || 0)
      const cy = Math.cos(yaw)
      const sy = Math.sin(yaw)
      const cp = Math.cos(pitch)
      const sp = Math.sin(pitch)
      const radius = Math.min(w, h) * 0.36

      g.clearRect(0, 0, w, h)
      // soft glow in the middle
      const glow = g.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, radius * 1.3)
      glow.addColorStop(0, hexA(accent, 0.16 * intensity))
      glow.addColorStop(1, 'rgba(0,0,0,0)')
      g.fillStyle = glow
      g.fillRect(0, 0, w, h)

      ring(1.1, t * 0.004, 1.18, hexA(accent, 0.35 * intensity), cy, sy, cp, sp, radius)
      ring(-0.6, -t * 0.003 + 1, 1.3, hexA(second, 0.28 * intensity), cy, sy, cp, sp, radius)
      ring(0.2, t * 0.002 + 2, 1.42, hexA(accent, 0.18 * intensity), cy, sy, cp, sp, radius)

      const projected = points.map((p) => ({ ...p, ...project(p.x, p.y, p.z, cy, sy, cp, sp, radius) }))
      projected.sort((a, b) => a.z - b.z)
      for (const p of projected) {
        const front = (p.z + 1) / 2 // 0 at the back, 1 at the front
        const size = 9 + 7 * front
        g.font = `${p.hot ? 600 : 500} ${size.toFixed(1)}px "JetBrains Mono", ui-monospace, monospace`
        const color = p.hot ? second : accent
        g.fillStyle = hexA(color, (0.12 + 0.85 * front * front) * intensity)
        g.fillText(p.text, p.sx - g.measureText(p.text).width / 2, p.sy)
      }
      raf = requestAnimationFrame(frame)
    }

    resize()
    window.addEventListener('resize', resize)
    window.addEventListener('mousemove', onMove)
    window.addEventListener('touchmove', onMove, { passive: true })
    canvas.addEventListener('mousedown', onDown)
    canvas.addEventListener('touchstart', onDown, { passive: true })
    window.addEventListener('mouseup', onUp)
    window.addEventListener('touchend', onUp)
    raf = requestAnimationFrame(frame)
    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('resize', resize)
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('touchmove', onMove)
      canvas.removeEventListener('mousedown', onDown)
      canvas.removeEventListener('touchstart', onDown)
      window.removeEventListener('mouseup', onUp)
      window.removeEventListener('touchend', onUp)
    }
  }, [accent, second, intensity, count])

  return <canvas ref={canvasRef} className={`code-sphere ${className}`} aria-hidden="true" />
}

function hexA(hex, a) {
  const n = parseInt(hex.slice(1), 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${Math.max(0, Math.min(1, a)).toFixed(3)})`
}