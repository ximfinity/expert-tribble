// Renders game frames dumped by TraceTest.dumpFrames (build/playtest/frames/*.json) with the real
// game_bg.frag + sprite.vert/shape.frag, and labels drawn like TextRenderer (bold, black shadow).
// Feet of the bots are drawn as small white-outlined dots so you can see where players stand.
// Usage: node game-frames.js <assets/shaders dir> <frames dir> <output dir>
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const [dir, framesDir, outDir] = process.argv.slice(2);
function load(p) {
  const text = fs.readFileSync(path.join(dir, p), 'utf8');
  return text.split('\n').map(l => l.trim().startsWith('#include') ? load(l.split('"')[1]) : l).join('\n');
}
const src = {};
for (const f of ['fullscreen.vert', 'game_bg.frag', 'sprite.vert', 'shape.frag']) src[f] = load(f);
const frames = fs.readdirSync(framesDir).filter(f => f.endsWith('.json')).sort()
  .map(f => ({ name: f.replace('.json', ''), data: JSON.parse(fs.readFileSync(path.join(framesDir, f), 'utf8')) }));

(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 960, height: 540 } });
  page.on('console', m => console.log('[page]', m.text()));
  await page.setContent('<canvas id=c width=960 height=540></canvas><canvas id=o width=960 height=540></canvas>');
  const shots = await page.evaluate(async ({ src, frames }) => {
    const W = 960, H = 540;
    const c = document.getElementById('c');
    const gl = c.getContext('webgl2', { preserveDrawingBuffer: true, antialias: true, premultipliedAlpha: false });
    const errs = [];
    function prog(v, f) {
      const p = gl.createProgram();
      for (const [t, s, n] of [[gl.VERTEX_SHADER, src[v], v], [gl.FRAGMENT_SHADER, src[f], f]]) {
        const sh = gl.createShader(t); gl.shaderSource(sh, s); gl.compileShader(sh);
        if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) errs.push(n + ': ' + gl.getShaderInfoLog(sh));
        gl.attachShader(p, sh);
      }
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) errs.push('link ' + f + ': ' + gl.getProgramInfoLog(p));
      return p;
    }
    const bg = prog('fullscreen.vert', 'game_bg.frag');
    const shape = prog('sprite.vert', 'shape.frag');
    if (errs.length) return { errs };
    const vao = gl.createVertexArray();
    const svao = gl.createVertexArray();
    const buf = gl.createBuffer();
    gl.bindVertexArray(svao);
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    for (let i = 0; i < 3; i++) {
      gl.enableVertexAttribArray(i);
      gl.vertexAttribPointer(i, 4, gl.FLOAT, false, 48, i * 16);
      gl.vertexAttribDivisor(i, 1);
    }
    const o = document.getElementById('o').getContext('2d');
    const out = {};
    for (const { name, data } of frames) {
      gl.viewport(0, 0, W, H);
      gl.disable(gl.BLEND);
      gl.useProgram(bg);
      gl.uniform2f(gl.getUniformLocation(bg, 'uResolution'), W, H);
      gl.uniform1f(gl.getUniformLocation(bg, 'uTime'), 3.0);
      gl.uniform1f(gl.getUniformLocation(bg, 'uBeat'), 0.0);
      gl.uniform3f(gl.getUniformLocation(bg, 'uAccent'), ...data.accent);
      gl.uniform1i(gl.getUniformLocation(bg, 'uPeopleCount'), 0);
      gl.bindVertexArray(vao);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      gl.useProgram(shape);
      gl.uniform1f(gl.getUniformLocation(shape, 'uAspect'), data.aspect);
      gl.uniform1f(gl.getUniformLocation(shape, 'uTime'), 3.0);
      gl.uniform2f(gl.getUniformLocation(shape, 'uOffset'), 0, 0);
      const arr = new Float32Array(data.sprites.flat());
      gl.bindVertexArray(svao);
      gl.bindBuffer(gl.ARRAY_BUFFER, buf);
      gl.bufferData(gl.ARRAY_BUFFER, arr, gl.DYNAMIC_DRAW);
      gl.drawArraysInstanced(gl.TRIANGLE_STRIP, 0, 4, data.sprites.length);
      // Compose GL + labels on the 2D canvas.
      o.globalCompositeOperation = 'source-over';
      o.clearRect(0, 0, W, H);
      o.drawImage(c, 0, 0);
      const k = H; // world unit = floor height
      for (const t of data.texts) {
        const [x, y, h, r, g, b, a] = t.v;
        o.font = `bold ${Math.round(0.975 * h * k)}px Roboto, Arial, sans-serif`;
        o.textAlign = 'center';
        o.textBaseline = 'alphabetic';
        o.shadowColor = 'black'; o.shadowBlur = 10 * h * k / 96;
        o.fillStyle = `rgba(${r * 255 | 0},${g * 255 | 0},${b * 255 | 0},${a})`;
        o.fillText(t.s, x / data.aspect * W, (1 - y) * H + 0.346 * h * k);
      }
      o.shadowBlur = 0;
      for (const [x, y] of data.people) {
        o.beginPath(); o.arc(x / data.aspect * W, (1 - y) * H, 7, 0, 6.283);
        o.fillStyle = 'rgba(255,255,255,0.9)'; o.fill();
        o.lineWidth = 2; o.strokeStyle = 'black'; o.stroke();
      }
      out[name] = document.getElementById('o').toDataURL('image/png');
    }
    return { out };
  }, { src, frames });
  await browser.close();
  if (shots.errs) { console.error(shots.errs.join('\n')); process.exit(1); }
  fs.mkdirSync(outDir, { recursive: true });
  for (const [n, url] of Object.entries(shots.out)) fs.writeFileSync(path.join(outDir, n + '.png'), Buffer.from(url.split(',')[1], 'base64'));
  console.log('Rendered', Object.keys(shots.out).join(', '));
})();
