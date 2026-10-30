// Compiles every PhantomFloor shader program in WebGL2 (GLSL ES 3.00 = the GLES 3.0 dialect)
// and renders each scene to PNG. Usage: node check.js <assets/shaders dir> <output dir>
// Needs Playwright with Chromium (NODE_PATH pointing at a global install works).
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const dir = process.argv[2];
const outDir = process.argv[3];
function load(p, stack = []) {
  const text = fs.readFileSync(path.join(dir, p), 'utf8');
  return text.split('\n').map(l => l.trim().startsWith('#include') ? load(l.split('"')[1], [...stack, p]) : l).join('\n');
}
const files = fs.readdirSync(dir);
const src = {};
for (const f of files) src[f] = load(f);

(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 640, height: 360 } });
  page.on('console', m => console.log('[page]', m.text()));
  await page.setContent('<canvas id=c width=640 height=360></canvas>');
  const result = await page.evaluate(async (src) => {
    const W = 640, H = 360;
    const c = document.getElementById('c');
    const gl = c.getContext('webgl2', { preserveDrawingBuffer: true, antialias: false });
    if (!gl) return { fatal: 'no webgl2' };
    const errors = [];
    const progs = {};
    function compile(type, s, name) {
      const sh = gl.createShader(type); gl.shaderSource(sh, s); gl.compileShader(sh);
      if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) errors.push(name + ': ' + gl.getShaderInfoLog(sh));
      return sh;
    }
    function program(v, f) {
      const p = gl.createProgram();
      gl.attachShader(p, compile(gl.VERTEX_SHADER, src[v], v));
      gl.attachShader(p, compile(gl.FRAGMENT_SHADER, src[f], f));
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) errors.push(`link ${v}+${f}: ` + gl.getProgramInfoLog(p));
      progs[f] = p; return p;
    }
    const pairs = [['fullscreen.vert','fog.frag'],['fullscreen.vert','swarm_floor.frag'],['spider.vert','spider.frag'],['sprite.vert','web.frag'],
      ['fullscreen.vert','ripple_update.frag'],['fullscreen.vert','fluid.frag'],['fullscreen.vert','water_shapes.frag'],
      ['fullscreen.vert','water.frag'],['warp.vert','warp.frag'],['overlay.vert','overlay.frag']];
    for (const [v, f] of pairs) program(v, f);
    if (errors.length) return { errors };

    const vao = gl.createVertexArray();
    let p;
    const u = (n) => gl.getUniformLocation(p, n);
    const f1 = (n, v) => gl.uniform1f(u(n), v);
    function common(t, people) {
      gl.uniform2f(u('uResolution'), W, H); f1('uTime', t);
      const arr = new Float32Array(32).fill(-10); people.forEach((q, i) => arr.set(q, i * 4));
      gl.uniform4fv(u('uPeople'), arr); gl.uniform1i(u('uPeopleCount'), people.length);
      f1('uLevel', 0.3); f1('uBass', 0.3); f1('uMid', 0.2); f1('uTreble', 0.2); f1('uBeat', 0.0);
    }
    function target(w, h) {
      const tex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      const fb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
      return { tex, fb, w, h };
    }
    const fsq = () => { gl.bindVertexArray(vao); gl.drawArrays(gl.TRIANGLES, 0, 3); };
    const shots = {};
    const snap = (name) => { shots[name] = c.toDataURL('image/png'); };
    const screen = () => { gl.bindFramebuffer(gl.FRAMEBUFFER, null); gl.viewport(0, 0, W, H); gl.clearColor(0,0,0,1); gl.clear(gl.COLOR_BUFFER_BIT); };
    const people = [[0.35, 0.5, 0.3, 0.0], [0.7, 0.35, 0.0, 0.0]];

    // FOG
    screen(); p = progs['fog.frag']; gl.useProgram(p); common(3.0, people);
    f1('uFlowTime', 6.0); f1('uDensity', 0.6); f1('uScale', 1.6); f1('uHue', 0.55); f1('uClearRadius', 0.17); f1('uWisps', 0.6);
    fsq(); snap('fog');

    // RIPPLES (8-bit encoded fallback path)
    const rip = [target(256 * W / H | 0, 256), target(256 * W / H | 0, 256)];
    for (const t of rip) { gl.bindFramebuffer(gl.FRAMEBUFFER, t.fb); gl.clearColor(0.5, 0.5, 0, 1); gl.clear(gl.COLOR_BUFFER_BIT); }
    let cur = 0;
    p = progs['ripple_update.frag']; gl.useProgram(p);
    for (let i = 0; i < 40; i++) {
      const dst = rip[1 - cur]; gl.bindFramebuffer(gl.FRAMEBUFFER, dst.fb); gl.viewport(0, 0, dst.w, dst.h);
      gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, rip[cur].tex); gl.uniform1i(u('uState'), 0);
      gl.uniform2f(u('uTexel'), 1 / dst.w, 1 / dst.h); f1('uDamping', 0.985); f1('uAspect', W / H); f1('uEncoded', 1);
      const drops = new Float32Array(96); drops.set([0.35, 0.5, 0.02, 0.5, 0.6, 0.6, 0.02, 0.5]);
      gl.uniform4fv(u('uDrops'), drops); gl.uniform1i(u('uDropCount'), i % 10 == 0 ? 2 : 0);
      fsq(); cur = 1 - cur;
    }

    // FLUID: blood and lava
    for (const [name, mode] of [['blood', 0], ['lava', 1]]) {
      screen(); p = progs['fluid.frag']; gl.useProgram(p); common(4.0, people);
      f1('uFlowTime', 4.0); f1('uMode', mode); f1('uPool', 0.45); f1('uBubbles', 0.35); f1('uGlow', 0.5); f1('uRippleEncoded', 1);
      gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, rip[cur].tex); gl.uniform1i(u('uRipple'), 0);
      fsq(); snap(name);
    }

    // WATER: monsters of every type at full emergence + eyes
    const shapes = target(W / 2, H / 2);
    gl.bindFramebuffer(gl.FRAMEBUFFER, shapes.fb); gl.viewport(0, 0, W / 2, H / 2); gl.clearColor(0,0,0,1); gl.clear(gl.COLOR_BUFFER_BIT);
    p = progs['water_shapes.frag']; gl.useProgram(p); common(5.0, people);
    const W2 = W / H;
    gl.uniform4fv(u('uMonA'), new Float32Array([0.45, 0.62, 0.3, 1.0,  1.05, 0.35, 2.6, 1.0,  1.45, 0.68, -1.2, 1.0,  0.3, 0.2, 0, 0.5]));
    gl.uniform4fv(u('uMonB'), new Float32Array([0, 12.3, 5.0, 0.28,  1, 4.2, 5.0, 0.3,  2, 7.7, 5.0, 0.3,  0, 1.0, 1.0, 0.2]));
    gl.uniform1i(u('uMonCount'), 4);
    gl.uniform4fv(u('uEyes'), new Float32Array([1.5, 0.2, 1.0, 0.025,  0.2, 0.85, 0.8, 0.02, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0]));
    gl.uniform1i(u('uEyeCount'), 2);
    fsq();
    screen(); p = progs['water.frag']; gl.useProgram(p); common(5.0, people);
    f1('uFlowTime', 5.0); f1('uCaustics', 1.0); f1('uMonsterGlow', 1.0); f1('uRippleEncoded', 1);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, rip[cur].tex); gl.uniform1i(u('uRipple'), 0);
    gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, shapes.tex); gl.uniform1i(u('uShapes'), 1);
    fsq(); snap('water');

    // SWARM: floor + webs + instanced spiders, in each look the scene offers.
    // opts: style, realism, radioactive, eyeGlow, shadow3d, sizeMix, webs, giant, closeup
    function swarm(name, o) {
      screen();
      const flash = [1.25, 0.55];
      p = progs['swarm_floor.frag']; gl.useProgram(p); common(2.0, o.closeup ? [] : people); f1('uLight', o.style ? 0.0 : (o.closeup ? 0.8 : 0.6)); gl.uniform2f(u('uFlash'), flash[0], flash[1]); f1('uFlashRadius', 0.2); f1('uFlashPower', 0.9); fsq();
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      if (o.webs) {
        const wd = new Float32Array([0.45, 0.7, 1.0, 0.11, 0.9, 0.9, 0.95, 1, 1.0, 3.1, 0, 0,
                                     1.3, 0.3, 2.0, 0.09, 1.3, 1.3, 1.4, 1, 0.6, 7.7, 0, 0,
                                     0.9, 0.25, 0.5, 0.08, 0.9, 0.9, 0.95, 1, 1.0, 1.3, 0.45, 0]);
        const wv = gl.createVertexArray(); gl.bindVertexArray(wv);
        const wb = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, wb); gl.bufferData(gl.ARRAY_BUFFER, wd, gl.STATIC_DRAW);
        for (let a = 0; a < 3; a++) { gl.enableVertexAttribArray(a); gl.vertexAttribPointer(a, 4, gl.FLOAT, false, 48, a * 16); gl.vertexAttribDivisor(a, 1); }
        p = progs['web.frag']; gl.useProgram(p); f1('uAspect', W2); gl.uniform2f(u('uOffset'), 0, 0);
        gl.drawArraysInstanced(gl.TRIANGLE_STRIP, 0, 4, 3);
      }
      let data;
      let n;
      if (o.closeup) { data = new Float32Array([W2 / 2, 0.5, 0.4, 0.42, 1.0, 0.5, 0.02, 1.0]); n = 1; }
      else {
        n = 120; data = new Float32Array((n + 1) * 8);
        let seed = 1; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
        for (let i = 0; i < n; i++) {
          let sz = 0.75 + rnd() * 0.5;
          if (o.sizeMix) { const h = rnd(); sz *= h < 0.55 ? 0.55 : h < 0.9 ? 1.2 : 2.6; }
          data.set([0.1 + rnd() * (W2 - 0.2), 0.1 + rnd() * 0.8, rnd() * 6.28, 0.028 * sz, rnd() * 6.28, rnd() < 0.2 ? 1 : 0, 0.02, rnd() * 10], i * 8);
        }
        if (o.webs) { data.set([0.45, 0.7, 0.3, 0.03, 1.0, 0.0, 0.02, 2.0], 0); }
        if (o.giant) { data.set([0.95, 0.5, 3.6, 0.2 * (1 + o.giant * 0.6), 2.0, 1.0, o.giant, 3.3], n * 8); n++; }
      }
      const ivao = gl.createVertexArray(); gl.bindVertexArray(ivao);
      const buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf); gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW);
      gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 4, gl.FLOAT, false, 32, 0); gl.vertexAttribDivisor(0, 1);
      gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 4, gl.FLOAT, false, 32, 16); gl.vertexAttribDivisor(1, 1);
      p = progs['spider.frag']; gl.useProgram(p);
      f1('uAspect', W2); f1('uStyle', o.style || 0); gl.uniform2f(u('uShadowOffset'), 0.25, -0.3); f1('uTime', 2.0);
      f1('uShadow3D', o.shadow3d ? 1 : 0); gl.uniform2f(u('uLightPos'), o.closeup ? W2 / 2 - 0.3 : flash[0], o.closeup ? 0.75 : flash[1]); f1('uLightRadius', o.closeup ? 0.6 : 0.2); f1('uLightPower', 0.9);
      f1('uLantern', o.style ? 0 : 0.6); f1('uEyeGlow', o.eyeGlow ?? 0.4); f1('uRadioactive', o.radioactive ? 1 : 0); f1('uRealism', o.realism ? 1 : 0);
      f1('uGlowPass', 0); f1('uQuadScale', 1);
      if (!o.style) { f1('uShadowPass', 1); gl.drawArraysInstanced(gl.TRIANGLE_STRIP, 0, 4, n); }
      f1('uShadowPass', 0);
      if (o.radioactive) { f1('uGlowPass', 1); f1('uQuadScale', 2.6); gl.drawArraysInstanced(gl.TRIANGLE_STRIP, 0, 4, n); f1('uGlowPass', 0); f1('uQuadScale', 1); }
      gl.drawArraysInstanced(gl.TRIANGLE_STRIP, 0, 4, n);
      gl.disable(gl.BLEND); snap(name);
    }
    swarm('swarm', { shadow3d: true, sizeMix: true });
    swarm('swarm_spectral', { style: 1 });
    swarm('spider_closeup', { closeup: true });
    swarm('spider_realistic', { closeup: true, realism: true, shadow3d: true, eyeGlow: 0.8 });
    swarm('swarm_realistic_webs', { realism: true, shadow3d: true, webs: true, sizeMix: true });
    swarm('swarm_radioactive', { radioactive: true, eyeGlow: 1.0, shadow3d: true });
    swarm('swarm_giant', { giant: 0.4, realism: true, shadow3d: true, eyeGlow: 1.0 });

    // WARP: fog canvas through a keystone quad with edit grid + handles, and a dissolve transition
    const fogT = target(W, H); gl.bindFramebuffer(gl.FRAMEBUFFER, fogT.fb); gl.viewport(0, 0, W, H);
    p = progs['fog.frag']; gl.useProgram(p); common(3.0, people);
    f1('uFlowTime', 6.0); f1('uDensity', 0.6); f1('uScale', 1.6); f1('uHue', 0.55); f1('uClearRadius', 0.17); f1('uWisps', 0.6); fsq();
    const lavaT = target(W, H); gl.bindFramebuffer(gl.FRAMEBUFFER, lavaT.fb); gl.viewport(0, 0, W, H);
    p = progs['fluid.frag']; gl.useProgram(p); common(4.0, people);
    f1('uFlowTime', 4.0); f1('uMode', 1); f1('uPool', 0.7); f1('uBubbles', 0.35); f1('uGlow', 0.5); f1('uRippleEncoded', 1);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, rip[cur].tex); gl.uniform1i(u('uRipple'), 0); fsq();
    // Heckbert square->quad, same as Homography.kt
    function s2q(q) {
      const [x0,y0,x1,y1,x2,y2,x3,y3] = q; const dx3 = x0-x1+x2-x3, dy3 = y0-y1+y2-y3;
      const dx1 = x1-x2, dx2 = x3-x2, dy1 = y1-y2, dy2 = y3-y2; const den = dx1*dy2 - dx2*dy1;
      const g = (dx3*dy2 - dx2*dy3)/den, h = (dx1*dy3 - dx3*dy1)/den;
      const a = x1-x0+g*x1, b = x3-x0+h*x3, d = y1-y0+g*y1, e = y3-y0+h*y3;
      return new Float32Array([a, d, g, b, e, h, x0, y0, 1]); // column-major
    }
    const quad = [-0.85, -0.95, 0.9, -0.8, 0.7, 0.9, -0.6, 0.75];
    for (const [name, trans, edit] of [['keystone_edit', 1, 1], ['transition', 0.5, 0]]) {
      screen(); p = progs['warp.frag']; gl.useProgram(p);
      gl.uniformMatrix3fv(u('uHomography'), false, s2q(quad));
      gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, fogT.tex); gl.uniform1i(u('uCurrent'), 0);
      gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, lavaT.tex); gl.uniform1i(u('uPrevious'), 1);
      f1('uTransition', trans); f1('uTime', 1.0); f1('uBrightness', 1.0); f1('uFeather', 0.03); f1('uEdit', edit);
      gl.bindVertexArray(vao); gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      if (edit) {
        gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA); p = progs['overlay.frag']; gl.useProgram(p);
        for (let i = 0; i < 4; i++) {
          gl.uniform2f(u('uCenter'), quad[i*2], quad[i*2+1]); gl.uniform2f(u('uRadius'), 28*2/W, 28*2/H);
          gl.uniform4f(u('uColor'), 0.3, 0.9, 1, 0.95); f1('uRing', 0.22); gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
        }
        gl.disable(gl.BLEND);
      }
      snap(name);
    }
    return { errors, shots, glError: gl.getError() };
  }, src);
  await browser.close();
  if (result.fatal) { console.log('FATAL', result.fatal); process.exit(2); }
  if (result.errors.length) { console.log('SHADER ERRORS:\n' + result.errors.join('\n')); process.exit(1); }
  fs.mkdirSync(outDir, { recursive: true });
  for (const [k, v] of Object.entries(result.shots)) fs.writeFileSync(path.join(outDir, k + '.png'), Buffer.from(v.split(',')[1], 'base64'));
  console.log('All programs compiled and linked. glError=' + result.glError + '. Shots: ' + Object.keys(result.shots).join(', '));
})();
