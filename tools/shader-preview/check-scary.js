// Renders the Scary scenes' shaders (scare_*.frag) at key moments in headless WebGL2.
// Usage: PW=<path to playwright> node check-scary.js <assets/shaders dir> <output dir>
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');

const dir = process.argv[2], outDir = process.argv[3];
function load(p) {
  return fs.readFileSync(path.join(dir, p), 'utf8').split('\n')
    .map(l => l.trim().startsWith('#include') ? load(l.split('"')[1]) : l).join('\n');
}
const src = {};
for (const f of fs.readdirSync(dir)) src[f] = load(f);

// Each shot: shader, time, uniforms. People are canvas UV (x, y, vx, vy).
const A = 16 / 9;
const shots = [
  { name: 'scare_ghoul', frag: 'scare_face.frag', u: { uActive: 1, uFace: [0.95, 0.55, 0.32, 0.0], uFaceAnim: [0.4, 0, 1, 0], uFlash: 0, uRedPulse: 0.3 }, people: [[0.53, 0.25]] },
  { name: 'scare_ghoul_flash', frag: 'scare_face.frag', u: { uActive: 1, uFace: [0.95, 0.5, 0.22, 0.0], uFaceAnim: [0.05, 0, 0.6, 0], uFlash: 0.55, uRedPulse: 0 } },
  { name: 'scare_skull', frag: 'scare_face.frag', u: { uActive: 1, uFace: [0.95, 0.55, 0.32, 0.3], uFaceAnim: [0.5, 1, 1, 0], uFlash: 0, uRedPulse: 0.2 } },
  { name: 'scare_ghoul_burn', frag: 'scare_face.frag', u: { uActive: 1, uFace: [0.95, 0.55, 0.32, 0.0], uFaceAnim: [2.0, 0, 0.5, 0.55], uFlash: 0, uRedPulse: 0.1 } },
];
if (fs.existsSync(path.join(dir, 'scare_eyes.frag'))) {
  const eyes = [], eyes2 = [];
  const rnd = (i) => (Math.sin(i * 12.9898) * 43758.5453) % 1;
  for (let i = 0; i < 16; i++) {
    const a = i * 0.9 + 0.3, r = 0.22 + (Math.abs(rnd(i)) * 0.3);
    eyes.push(0.9 + Math.cos(a) * r * 1.3, 0.5 + Math.sin(a) * r, i % 5 === 0 ? 0.3 : 1, 0.022 + 0.012 * Math.abs(rnd(i + 3)));
    eyes2.push(0.9, 0.5, i % 3 === 0 ? 1 : 0, i);
  }
  shots.push({ name: 'scare_eyes', frag: 'scare_eyes.frag', u: { uEyes: eyes, uEyes2: eyes2, uEyeCount: 16, uMaw: [0.9, 0.5, 0, 0], uFlash: 0 }, people: [[0.9 / A, 0.5]] });
  shots.push({ name: 'scare_eyes_maw', frag: 'scare_eyes.frag', u: { uEyes: eyes, uEyes2: eyes2, uEyeCount: 16, uMaw: [0.9, 0.5, 0.18, 0.55], uFlash: 0.1 }, people: [[0.9 / A, 0.5]] });
}
if (fs.existsSync(path.join(dir, 'scare_hands.frag'))) {
  const hands = [], hands2 = [];
  for (let i = 0; i < 6; i++) {
    const a = i * 1.05 + 0.2;
    const x = 0.9 + Math.cos(a) * 0.24, y = 0.5 + Math.sin(a) * 0.2;
    hands.push(x, y, i === 5 ? 0.5 : 1, Math.atan2(0.5 - y, 0.9 - x) - Math.PI / 2);
    hands2.push(i % 2 ? 0.7 : 0.2, i * 1.7, 0.13, 0);
  }
  shots.push({ name: 'scare_hands', frag: 'scare_hands.frag', u: { uHands: hands, uHands2: hands2, uHandCount: 6, uCracks: [0.9, 0.5, 0.32, 1], uCrackCount: 1, uFlash: 0 }, people: [[0.9 / A, 0.5]] });
  shots.push({ name: 'scare_cracks', frag: 'scare_hands.frag', u: { uHands: hands, uHands2: hands2, uHandCount: 0, uCracks: [0.9, 0.5, 0.18, 0.6], uCrackCount: 1, uFlash: 0 }, people: [[0.9 / A, 0.5]] });
}

// A jump-scare sequence for an animated preview (same timing as JumpScareScene): black floor,
// then the ghoul lunges in with a flash, screams, and burns away.
if (process.env.SEQUENCE) {
  shots.length = 0;
  const easeOutBack = x => { const c1 = 2.2, c3 = c1 + 1, k = x - 1; return 1 + c3 * k * k * k + c1 * k * k; };
  for (let f = 0; f < 66; f++) {
    const t = f / 20 - 0.5;
    if (t < 0) { shots.push({ name: `seq_${String(f).padStart(3, '0')}`, frag: 'scare_face.frag', u: { uActive: 0, uFlash: 0, uRedPulse: 0 } }); continue; }
    const lunge = easeOutBack(Math.min(1, t / 0.2));
    const shake = t < 1.4 ? 0.012 : 0;
    const x = 0.95 + Math.sin(t * 63) * shake, y = 0.92 + (0.55 - 0.92) * Math.min(1, lunge) + Math.cos(t * 71) * shake;
    const scream = t < 0.08 ? t / 0.08 : t < 1.3 ? 1 - 0.15 * Math.max(0, Math.sin(t * 9)) : Math.max(0.2, 1 - (t - 1.3) / 0.8);
    const fade = Math.min(1, Math.max(0, (t - 1.7) / 1.1));
    const flash = 0.6 * (t < 0.12 ? 1 - t / 0.12 : (t >= 0.22 && t <= 0.3) ? 0.4 : 0);
    shots.push({ name: `seq_${String(f).padStart(3, '0')}`, frag: 'scare_face.frag', u: { uActive: 1, uFace: [x, y, 0.32 * (0.25 + 0.75 * lunge), Math.sin(t * 37) * shake * 3], uFaceAnim: [t, 0, scream, fade], uFlash: flash, uRedPulse: Math.max(0, 1 - t / 3) * 0.6 } });
  }
}

(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 960, height: 540 } });
  page.on('console', m => console.log('[page]', m.text()));
  await page.setContent('<canvas id=c width=960 height=540></canvas>');
  const out = await page.evaluate(({ src, shots }) => {
    const gl = document.getElementById('c').getContext('webgl2', { preserveDrawingBuffer: true });
    const result = {};
    for (const s of shots) {
      const sh = (t, code) => { const x = gl.createShader(t); gl.shaderSource(x, code); gl.compileShader(x); if (!gl.getShaderParameter(x, gl.COMPILE_STATUS)) throw new Error(s.frag + ': ' + gl.getShaderInfoLog(x)); return x; };
      const p = gl.createProgram();
      gl.attachShader(p, sh(gl.VERTEX_SHADER, src['fullscreen.vert']));
      gl.attachShader(p, sh(gl.FRAGMENT_SHADER, src[s.frag]));
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
      gl.useProgram(p);
      const u = n => gl.getUniformLocation(p, n);
      gl.uniform2f(u('uResolution'), 960, 540);
      gl.uniform1f(u('uTime'), 3.3);
      const people = new Float32Array(32).fill(-10);
      (s.people || []).forEach((q, i) => { people[i * 4] = q[0]; people[i * 4 + 1] = q[1]; people[i * 4 + 2] = 0; people[i * 4 + 3] = 0; });
      gl.uniform4fv(u('uPeople'), people);
      gl.uniform1i(u('uPeopleCount'), (s.people || []).length);
      for (const [k, v] of Object.entries(s.u)) {
        const loc = u(k);
        if (loc === null) continue;
        if (k.endsWith('Count')) gl.uniform1i(loc, v);
        else if (Array.isArray(v) && v.length > 4) gl.uniform4fv(loc, new Float32Array(v));
        else if (Array.isArray(v)) gl.uniform4f(loc, ...v);
        else gl.uniform1f(loc, v);
      }
      gl.viewport(0, 0, 960, 540);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      result[s.name] = document.getElementById('c').toDataURL('image/png');
    }
    return result;
  }, { src, shots });
  fs.mkdirSync(outDir, { recursive: true });
  for (const [k, v] of Object.entries(out)) fs.writeFileSync(path.join(outDir, k + '.png'), Buffer.from(v.split(',')[1], 'base64'));
  console.log('rendered', Object.keys(out).join(', '));
  await browser.close();
})().catch(e => { console.error(e.message); process.exit(1); });
