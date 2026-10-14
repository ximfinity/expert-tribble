// Renders the seasonal scenes and Ground Smoke with their real shaders in WebGL2, including the
// real fluid / snow / garden / ripple update passes driven by a scripted walker.
// Usage: PW=<path to playwright> node check-seasons.js <assets/shaders dir> <output dir>
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');
const dir = process.argv[2], outDir = process.argv[3];
function load(p) {
  return fs.readFileSync(path.join(dir, p), 'utf8').split('\n')
    .map(l => l.trim().startsWith('#include') ? load(l.split('"')[1]) : l).join('\n');
}
const src = {}; for (const f of fs.readdirSync(dir)) src[f] = load(f);

(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 640, height: 360 } });
  page.on('console', m => console.log('[page]', m.text()));
  page.on('pageerror', e => console.log('[pageerror]', e.message));
  await page.setContent('<canvas id=c width=640 height=360></canvas>');
  const res = await page.evaluate((src) => {
    const W = 640, H = 360, A = W / H;
    const gl = document.getElementById('c').getContext('webgl2', { preserveDrawingBuffer: true, antialias: false });
    const floatOk = !!gl.getExtension('EXT_color_buffer_float');
    gl.getExtension('OES_texture_float_linear');
    const errors = [], progs = {};
    function sh(t, s, n) { const x = gl.createShader(t); gl.shaderSource(x, s); gl.compileShader(x); if (!gl.getShaderParameter(x, gl.COMPILE_STATUS)) errors.push(n + ': ' + gl.getShaderInfoLog(x)); return x; }
    function prog(v, f) { const p = gl.createProgram(); gl.attachShader(p, sh(gl.VERTEX_SHADER, src[v], v)); gl.attachShader(p, sh(gl.FRAGMENT_SHADER, src[f], f)); gl.linkProgram(p); if (!gl.getProgramParameter(p, gl.LINK_STATUS)) errors.push('link ' + f + ': ' + gl.getProgramInfoLog(p)); progs[f] = p; return p; }
    const FS = ['fluid_advect','fluid_splat','fluid_curl','fluid_vorticity','fluid_divergence','fluid_pressure','fluid_gradient','fluid_scale','smoke','snow_cover','snow_ground','earth_ground','garden_growth','garden','solid','copy','night_lawn','pool','ripple_update'];
    for (const f of FS) prog('fullscreen.vert', f + '.frag');
    for (const f of ['spark','snowflake','leaf']) prog('sprite.vert', f + '.frag');
    if (errors.length) return { errors };

    const vao = gl.createVertexArray();
    let p; const u = n => gl.getUniformLocation(p, n);
    const f1 = (n, v) => gl.uniform1f(u(n), v), f2 = (n, a, b) => gl.uniform2f(u(n), a, b), i1 = (n, v) => gl.uniform1i(u(n), v);
    const use = f => { p = progs[f]; gl.useProgram(p); };
    const tex = (n, unit, t) => { gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, t); i1(n, unit); };
    function target(w, h, flt) {
      w = Math.max(1, w | 0); h = Math.max(1, h | 0);
      const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
      if (flt) gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA16F, w, h, 0, gl.RGBA, gl.HALF_FLOAT, null);
      else gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
      for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
      const fb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fb); gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t, 0);
      return { t, fb, w, h };
    }
    const bind = (tg) => { gl.bindFramebuffer(gl.FRAMEBUFFER, tg ? tg.fb : null); gl.viewport(0, 0, tg ? tg.w : W, tg ? tg.h : H); };
    const clear = (r = 0, g = 0, b = 0, a = 1) => { gl.clearColor(r, g, b, a); gl.clear(gl.COLOR_BUFFER_BIT); };
    const fsq = () => { gl.bindVertexArray(vao); gl.drawArrays(gl.TRIANGLES, 0, 3); };
    const pp = (w, h, flt) => { const o = { a: target(w, h, flt), b: target(w, h, flt) }; o.swap = () => { const t = o.a; o.a = o.b; o.b = t; }; return o; };
    function common(t, people) {
      gl.uniform2f(u('uResolution'), W, H); f1('uTime', t);
      const arr = new Float32Array(32).fill(-10); people.forEach((q, i) => arr.set(q, i * 4));
      gl.uniform4fv(u('uPeople'), arr); i1('uPeopleCount', people.length);
      f1('uLevel', 0); f1('uBass', 0); f1('uMid', 0); f1('uTreble', 0); f1('uBeat', 0);
    }
    const shots = {}; const snap = n => { shots[n] = document.getElementById('c').toDataURL('image/png'); };
    let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
    // A walker crossing the floor in an arc
    const walk = t => [0.15 + 0.7 * t, 0.3 + 0.35 * Math.sin(t * 3.0)];

    // ---------------- GROUND SMOKE: run the real solver ----------------
    {
      const sh = 160, sw = Math.round(sh * A), tx = 1 / sw, ty = 1 / sh;
      const vel = pp(sw, sh, floatOk), dye = pp(sw, sh, floatOk), pr = pp(sw, sh, floatOk), div = target(sw, sh, floatOk), curl = target(sw, sh, floatOk);
      for (const t of [vel.a, vel.b, dye.a, dye.b, pr.a, pr.b, div, curl]) { bind(t); clear(0, 0, 0, 0); }
      const dt = 1 / 30;
      for (let step = 0; step < 220; step++) {
        const time = step * dt;
        const vs = [], ds = [];
        for (let i = 0; i < 3; i++) {
          const along = (i + 0.5) / 3 + 0.08 * Math.sin(time * 0.3 + i * 2.1);
          const s = ((along % 1) + 1) % 1 * 4; const e = Math.min(3, s | 0);
          const pt = [[s, 0.01, 0, 1], [0.99, s - 1, -1, 0], [1 - (s - 2), 0.99, 0, -1], [0.01, 1 - (s - 3), 1, 0]][e];
          const sw2 = 0.6 * Math.sin(time * 0.4 + i * 1.3);
          const dx = pt[2] * Math.cos(sw2) - pt[3] * Math.sin(sw2), dy = pt[2] * Math.sin(sw2) + pt[3] * Math.cos(sw2);
          ds.push([pt[0], pt[1], 0.08, 0], [dt * 7, 0, 0, 0]);
          vs.push([pt[0], pt[1], 0.07, 0], [dx * 600 * dt, dy * 600 * dt, 0, 0]);
        }
        if (step > 120) { const tt = (step - 120) / 100; const [px, py] = walk(tt); const pvx = 0.7, pvy = 0.35 * 3 * Math.cos(tt * 3) * 0.7;
          vs.push([px, py, 0.05, 0], [pvx * sw * 0.6, pvy * sh * 0.6, 0, (20 + 60 * 0.8) * dt * 6]); ds.push([px, py, 0.035, 0], [-dt * 1.5, 0, 0, 0]); }
        const splat = (field, list, clampMin) => { bind(field.b); use('fluid_splat.frag'); f1('uAspect', A); f1('uClampMin', clampMin);
          const P = new Float32Array(64), V = new Float32Array(64); for (let k = 0; k < list.length / 2; k++) { P.set(list[2 * k], k * 4); V.set(list[2 * k + 1], k * 4); }
          i1('uSplatCount', list.length / 2); gl.uniform4fv(u('uSplatPos'), P); gl.uniform4fv(u('uSplatVal'), V); tex('uTarget', 0, field.a.t); fsq(); field.swap(); };
        splat(vel, vs, -1e4); splat(dye, ds, 0);
        bind(curl); use('fluid_curl.frag'); f2('uTexel', tx, ty); tex('uVelocity', 0, vel.a.t); fsq();
        bind(vel.b); use('fluid_vorticity.frag'); f2('uTexel', tx, ty); f1('uDt', dt); f1('uCurlStrength', 25); f1('uBreeze', 30); f1('uTime', time); tex('uVelocity', 0, vel.a.t); tex('uCurl', 1, curl.t); fsq(); vel.swap();
        bind(div); use('fluid_divergence.frag'); f2('uTexel', tx, ty); tex('uVelocity', 0, vel.a.t); fsq();
        bind(pr.b); use('fluid_scale.frag'); f1('uScale', 0.8); tex('uSource', 0, pr.a.t); fsq(); pr.swap();
        use('fluid_pressure.frag'); f2('uTexel', tx, ty); tex('uDivergence', 1, div.t);
        for (let k = 0; k < 20; k++) { bind(pr.b); tex('uPressure', 0, pr.a.t); fsq(); pr.swap(); }
        bind(vel.b); use('fluid_gradient.frag'); f2('uTexel', tx, ty); tex('uPressure', 0, pr.a.t); tex('uVelocity', 1, vel.a.t); fsq(); vel.swap();
        use('fluid_advect.frag'); f2('uTexel', tx, ty); f1('uDt', dt);
        bind(vel.b); f1('uDissipation', 0.85); tex('uVelocity', 0, vel.a.t); tex('uSource', 1, vel.a.t); fsq(); vel.swap();
        bind(dye.b); f1('uDissipation', 0.6 + 0.38 * 0.6); tex('uVelocity', 0, vel.a.t); tex('uSource', 1, dye.a.t); fsq(); dye.swap();
      }
      bind(null); clear(); use('smoke.frag'); const [px, py] = walk(1); common(7, [[px, py, 0.7, 0]]);
      f1('uSimulated', floatOk ? 1 : 0); f2('uSimTexel', tx, ty); gl.uniform3f(u('uTint'), 0.82, 0.85, 0.9); f1('uBrightness', 0.85);
      tex('uDensity', 0, dye.a.t); tex('uVelocity', 1, vel.a.t); fsq(); snap('smoke');
    }

    // ---------------- Sprite helper ----------------
    const sprVao = gl.createVertexArray(), sprBuf = gl.createBuffer();
    gl.bindVertexArray(sprVao); gl.bindBuffer(gl.ARRAY_BUFFER, sprBuf);
    for (let a = 0; a < 3; a++) { gl.enableVertexAttribArray(a); gl.vertexAttribPointer(a, 4, gl.FLOAT, false, 48, a * 16); gl.vertexAttribDivisor(a, 1); }
    function sprites(list, prog, setup) { use(prog); f1('uAspect', A); f2('uOffset', 0, 0); f1('uTime', 3); setup && setup();
      const d = new Float32Array(list.length * 12); list.forEach((s, i) => d.set(s, i * 12));
      gl.bindVertexArray(sprVao); gl.bindBuffer(gl.ARRAY_BUFFER, sprBuf); gl.bufferData(gl.ARRAY_BUFFER, d, gl.DYNAMIC_DRAW); gl.drawArraysInstanced(gl.TRIANGLE_STRIP, 0, 4, list.length); }

    // ---------------- WINTER (stamp field with real update shader) ----------------
    for (const [name, theme] of [['snow', 0], ['christmas', 1], ['hanukkah', 2]]) {
      const cs = 256, cw = Math.round(cs * A); const cov = pp(cw, cs, floatOk);
      for (const t of [cov.a, cov.b]) { bind(t); clear(0.85, 0, 0, 1); }
      for (let step = 0; step < 150; step++) {
        bind(cov.b); use('snow_cover.frag'); tex('uState', 0, cov.a.t); f2('uTexel', 1 / cw, 1 / cs); f1('uAspect', A);
        const [x, y] = walk(step / 150); const st = new Float32Array(64); st.set([x, y, 0.028, 0.16]);
        // stamp only on "steps" to leave distinct footprints
        i1('uStampCount', step % 8 < 2 ? 1 : 0); gl.uniform4fv(u('uStamps'), st); f1('uFill', 0.0); f1('uSoften', 0.0); fsq(); cov.swap();
      }
      bind(null); clear(); use('snow_ground.frag'); common(4, []); tex('uCover', 0, cov.a.t); f2('uCoverTexel', 1 / cw, 1 / cs);
      f1('uTheme', theme); f1('uLights', 1); f1('uNight', 5); fsq();
      const fl = []; for (let i = 0; i < 300; i++) { const z = rnd(), sz = 0.6 + rnd() * 0.8; fl.push([rnd() * A, rnd(), rnd() * 6, 0.006 * sz * (0.6 + 1.6 * z), theme == 2 && rnd() > 0.5 ? 0.7 : 1, theme == 2 ? 0.85 : 1, 1, 0.95, Math.min(1, Math.max(0, (sz - 0.9) * 2)) * (1 - z * 0.5), z, 0, z]); }
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      sprites(fl, 'snowflake.frag', () => { f2('uOffset', 1.2, -1.6); f1('uShadow', 1); });
      sprites(fl, 'snowflake.frag', () => { f1('uShadow', 0); });
      gl.disable(gl.BLEND); snap(name);
    }

    // ---------------- FALL LEAVES ----------------
    {
      bind(null); clear(); use('earth_ground.frag'); common(2, []); f1('uLight', 0.6); fsq();
      const pal = [[0.85, 0.18, 0.05], [0.95, 0.45, 0.08], [0.95, 0.72, 0.15], [0.55, 0.3, 0.1], [0.65, 0.08, 0.1], [0.7, 0.6, 0.15]];
      const L = []; for (let i = 0; i < 450; i++) { let x = rnd() * A, y = rnd(); const dxp = x - 0.6 * A, dyp = y - 0.5; const d = Math.hypot(dxp, dyp);
        if (d < 0.12) { x += dxp / d * 0.1; y += dyp / d * 0.1; } // cleared patch where someone kicked
        const air = i < 25 ? 0.15 + rnd() * 0.3 : 0; L.push({ x: air ? 0.6 * A + (rnd() - .5) * .3 : x, y: air ? 0.5 + (rnd() - .5) * .3 : y, a: rnd() * 6.28, s: 0.7 + rnd() * 0.6, k: rnd() < 0.6 ? 0 : 1, c: pal[(rnd() * 6) | 0], sd: rnd(), h: air }); }
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA); const base = 0.022;
      sprites(L.map(l => [l.x + 0.006 + l.h * 0.25, l.y - 0.008 - l.h * 0.3, l.a, base * l.s, 0, 0, 0, 1, l.k, 0, 1, 0]), 'leaf.frag');
      for (const air of [false, true]) sprites(L.filter(l => (l.h > 0) == air).map(l => { const sh = 0.85 + 0.3 * l.sd; return [l.x, l.y, l.a, base * l.s * (1 + l.h * 1.2), l.c[0] * sh, l.c[1] * sh, l.c[2] * sh, 1, l.k, (l.sd - .5) * 2 + l.h * 3, 0, 0]; }), 'leaf.frag');
      gl.disable(gl.BLEND); snap('leaves');
    }

    // ---------------- SPRING GARDEN (real growth update) ----------------
    {
      const cs = 256, cw = Math.round(cs * A); const g = pp(cw, cs, floatOk);
      for (const t of [g.a, g.b]) { bind(t); clear(0, 0, 0, 1); }
      for (let step = 0; step < 300; step++) {
        bind(g.b); use('garden_growth.frag'); tex('uState', 0, g.a.t); f1('uAspect', A);
        const st = new Float32Array(64); let n = 0;
        if (step < 200) { const [x, y] = walk(step / 200); st.set([x, y, 0.07, 0.2]); n = 1; }
        i1('uStampCount', n); gl.uniform4fv(u('uStamps'), st); f1('uWilt', 0); f1('uGrow', 0.35 / 30); f1('uShrink', 0.2 / 30); fsq(); g.swap();
      }
      bind(null); clear(); use('garden.frag'); common(5, []); tex('uGrowth', 0, g.a.t); f1('uDensity', 0.6); f1('uFlowerSize', 1); f1('uGrass', 0.8); fsq(); snap('garden');
    }

    // ---------------- FIREWORKS (trail buffer with fade, real blend modes) ----------------
    {
      const trail = target(W, H, false); bind(trail); clear();
      const sparks = []; const burst = (x, y, c, n, v, life) => { for (let i = 0; i < n; i++) { const a = rnd() * 6.283, s = v * (0.6 + 0.4 * Math.sqrt(rnd())); sparks.push({ x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s, age: 0, life: life * (0.8 + 0.4 * rnd()), c, gl: rnd() < 0.3 ? rnd() : -1 }); } };
      burst(0.5, 0.5, [1, 0.15, 0.1], 120, 0.45, 1.6); burst(1.2, 0.55, [0.25, 0.4, 1], 120, 0.45, 1.6); burst(0.85, 0.3, [1, 0.95, 0.9], 90, 0.3, 1.8);
      const dt = 1 / 30;
      for (let step = 0; step < 24; step++) {
        bind(trail); gl.enable(gl.BLEND); use('solid.frag');
        gl.blendFunc(gl.ZERO, gl.SRC_ALPHA); gl.uniform4f(u('uColor'), 0, 0, 0, Math.exp(-dt * (18 - 15 * 0.6))); fsq();
        gl.blendEquation(gl.FUNC_REVERSE_SUBTRACT); gl.blendFunc(gl.ONE, gl.ONE); gl.uniform4f(u('uColor'), 1.5 / 255, 1.5 / 255, 1.5 / 255, 0); fsq(); gl.blendEquation(gl.FUNC_ADD);
        for (const s of sparks) { s.age += dt; const k = Math.exp(-2.2 * dt); s.vx *= k; s.vy *= k; s.x += s.vx * dt; s.y += s.vy * dt; }
        gl.blendFunc(gl.ONE, gl.ONE);
        sprites(sparks.filter(s => s.age < s.life).map(s => { const t = s.age / s.life, hot = Math.exp(-s.age * 8), f = (1 - t) * (1 - t);
          return [s.x, s.y, 0, 0.009 * (1 + hot), s.c[0] + (1 - s.c[0]) * hot, s.c[1] + (1 - s.c[1]) * hot, s.c[2] + (1 - s.c[2]) * hot, f, 0.6, s.gl, 0.25, 0]; }), 'spark.frag');
        gl.disable(gl.BLEND);
      }
      bind(null); use('copy.frag'); tex('uSource', 0, trail.t); fsq(); snap('fireworks');
    }

    // ---------------- FIREFLIES ----------------
    {
      bind(null); clear(); use('night_lawn.frag'); common(3, []); f1('uMoon', 0.6); fsq();
      const F = []; for (let i = 0; i < 120; i++) { const near = i < 70; const a = rnd() * 6.283, r = 0.05 + rnd() * 0.13;
        const x = near ? 0.55 * A + Math.cos(a) * r : rnd() * A, y = near ? 0.5 + Math.sin(a) * r : rnd(); const flash = Math.pow(Math.max(Math.cos(rnd() * 6.283), 0), 14); const size = 0.006 + 0.004 * rnd();
        F.push([x, y - 0.004, 0, size * 9, 0.6, 1, 0.25, flash * 0.18, 0, -1, 0, 0]); F.push([x, y, 0, size * (1 + flash), 0.8, 1, 0.35, flash + 0.06, 0.5, -1, 0.5, 0]); }
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE); sprites(F, 'spark.frag'); gl.disable(gl.BLEND); snap('fireflies');
    }

    // ---------------- POOL (real ripple sim, float when available) ----------------
    {
      const rs = 256, rw = Math.round(rs * A); const rp = pp(rw, rs, floatOk); const enc = floatOk ? 0 : 1;
      for (const t of [rp.a, rp.b]) { bind(t); clear(enc ? 0.5 : 0, enc ? 0.5 : 0, 0, 1); }
      for (let step = 0; step < 60; step++) {
        bind(rp.b); use('ripple_update.frag'); tex('uState', 0, rp.a.t); f2('uTexel', 1 / rw, 1 / rs); f1('uDamping', 0.985); f1('uAspect', A); f1('uEncoded', enc);
        const d = new Float32Array(96); let n = 0;
        if (step == 0) { d.set([0.4, 0.5, 0.05, 0.9], 0); n = 1; }
        if (step % 4 == 0) { const [x, y] = walk(0.5 + step / 120); d.set([x, y, 0.016, 0.4], n * 4); n++; }
        i1('uDropCount', n); gl.uniform4fv(u('uDrops'), d); fsq(); rp.swap();
      }
      bind(null); clear(); use('pool.frag'); common(4, []); f1('uFlowTime', 4); f1('uSun', 1); f1('uRippleEncoded', enc); f2('uRippleTexel', 1 / rw, 1 / rs); tex('uRipple', 0, rp.a.t); fsq();
      const D = []; for (let i = 0; i < 40; i++) { const a = rnd() * 6.283, r = 0.04 + rnd() * 0.12; const h = rnd() * 0.15; D.push([0.4 * A + Math.cos(a) * r, 0.5 + Math.sin(a) * r, 0, (0.003 + rnd() * 0.004) * (1 + h * 4), 0.85, 0.95, 1, 0.9, 0.7, -1, 0.15, 0]); }
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE); sprites(D, 'spark.frag'); gl.disable(gl.BLEND); snap('pool');
    }
    return { errors, shots, floatOk, glError: gl.getError() };
  }, src);
  await browser.close();
  if (res.errors && res.errors.length) { console.log('SHADER ERRORS:\n' + res.errors.join('\n')); process.exit(1); }
  fs.mkdirSync(outDir, { recursive: true });
  for (const [k, v] of Object.entries(res.shots)) fs.writeFileSync(path.join(outDir, k + '.png'), Buffer.from(v.split(',')[1], 'base64'));
  console.log('OK floatOk=' + res.floatOk + ' glError=' + res.glError + ' shots=' + Object.keys(res.shots).join(','));
})();
