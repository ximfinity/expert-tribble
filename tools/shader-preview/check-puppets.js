// Renders the Shadow Puppets scene (puppet_floor.frag + puppet.frag) in headless WebGL2:
// a gallery of every puppet and a scene with puppets gathering around a person.
// Usage: PW=<path to playwright> node check-puppets.js <assets/shaders dir> <output dir>
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
const A = 16 / 9;
// [type, x, y, size, stretch, phase, reach, facing]
const gallery = [[0, 0.2, 0.1, 0.4, 1.0, 0.6, 0, 1], [1, 0.75, 0.1, 0.4, 1.0, 1.5, 0.3, 1], [2, 1.35, 0.1, 0.4, 1.0, 0.8, 0.2, 1]];
const gallery2 = [[3, 0.35, 0.0, 0.42, 1.0, 1.2, 0.3, 1], [4, 1.2, 0.3, 0.38, 1.0, 1.0, 0, 1]];
const scene = [[2, 0.55, 0.12, 0.18, 1.3, 1.0, 1.0, 1], [1, 1.12, 0.3, 0.17, 1.3, 2.0, 0.8, 0], [0, 1.45, 0.08, 0.17, 1.3, 0.3, 0.2, 0],
               [3, 0.88, -0.02, 0.18, 1.5, 0.7, 1.0, 1], [4, 0.3, 0.68, 0.13, 1.2, 1.4, 0, 1]];
(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 960, height: 540 } });
  await page.setContent('<canvas id=c width=960 height=540></canvas>');
  const out = await page.evaluate(({ src, gallery, gallery2, scene, A }) => {
    const gl = document.getElementById('c').getContext('webgl2', { preserveDrawingBuffer: true });
    const mk = (v, f) => {
      const sh = (t, c, n) => { const x = gl.createShader(t); gl.shaderSource(x, c); gl.compileShader(x); if (!gl.getShaderParameter(x, gl.COMPILE_STATUS)) throw new Error(n + ': ' + gl.getShaderInfoLog(x)); return x; };
      const p = gl.createProgram(); gl.attachShader(p, sh(gl.VERTEX_SHADER, src[v], v)); gl.attachShader(p, sh(gl.FRAGMENT_SHADER, src[f], f)); gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p)); return p;
    };
    const floor = mk('fullscreen.vert', 'puppet_floor.frag'), pup = mk('sprite.vert', 'puppet.frag');
    const res = {};
    let candle = [A / 2, -3.0];
    function draw(name, list) {
      gl.viewport(0, 0, 960, 540);
      gl.useProgram(floor);
      const u = n => gl.getUniformLocation(floor, n);
      gl.uniform2f(u('uResolution'), 960, 540); gl.uniform1f(u('uTime'), 2.0);
      gl.uniform2f(u('uCandle'), A / 2, 0.25); gl.uniform1f(u('uBrightness'), 1.1); gl.uniform1f(u('uFlicker'), 0.7); gl.uniform3f(u('uTint'), 1.0, 0.62, 0.3);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      const data = new Float32Array(list.length * 12);
      list.forEach(([type, x, y, size, stretch, ph, reach, facing], i) => {
        // Up = away from the candle (hands: up = given direction).
        let ux = x - candle[0], uy = y - candle[1];
        if (type === 3) { ux = 0; uy = 1; }
        const l = Math.hypot(ux, uy); ux /= l; uy /= l;
        const ang = Math.atan2(uy, ux) - Math.PI / 2;
        const half = size * stretch;
        data.set([x + ux * half, y + uy * half, ang, half, ph, reach, facing, 1, type, i * 1.7, stretch, 0.035], i * 12);
      });
      const vao = gl.createVertexArray(); gl.bindVertexArray(vao);
      const b = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, b); gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW);
      for (let a = 0; a < 3; a++) { gl.enableVertexAttribArray(a); gl.vertexAttribPointer(a, 4, gl.FLOAT, false, 48, a * 16); gl.vertexAttribDivisor(a, 1); }
      gl.useProgram(pup); gl.uniform1f(gl.getUniformLocation(pup, 'uAspect'), A); gl.uniform2f(gl.getUniformLocation(pup, 'uOffset'), 0, 0);
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      gl.drawArraysInstanced(gl.TRIANGLE_STRIP, 0, 4, list.length);
      gl.disable(gl.BLEND);
      res[name] = document.getElementById('c').toDataURL('image/png');
    }
    draw('puppets_gallery', gallery);
    draw('puppets_gallery2', gallery2);
    draw('puppets', scene);
    return res;
  }, { src, gallery, gallery2, scene, A });
  fs.mkdirSync(outDir, { recursive: true });
  for (const [k, v] of Object.entries(out)) fs.writeFileSync(path.join(outDir, k + '.png'), Buffer.from(v.split(',')[1], 'base64'));
  console.log('rendered', Object.keys(out).join(', '));
  await browser.close();
})().catch(e => { console.error(e.message); process.exit(1); });
