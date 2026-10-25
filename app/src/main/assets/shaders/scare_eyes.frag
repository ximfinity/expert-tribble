#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"
#include "scare.glsl"

// EYES IN THE DARK: pairs of glowing eyes open in the black around anyone on the floor, blink, and
// watch them. They creep closer the longer someone lingers, until a ring of teeth opens under the
// victim's feet and snaps shut.

#define MAX_EYES 24

in vec2 vUv;
out vec4 fragColor;

uniform vec4 uEyes[MAX_EYES];   // world x, y, open 0..1, size (eye radius)
uniform vec4 uEyes2[MAX_EYES];  // look-at world x, y, kind (0 amber cat eyes, 1 red), seed
uniform int uEyeCount;
uniform vec4 uMaw;              // world x, y, radius, visibility 0..1
uniform float uFlash;

vec3 eyePair(vec2 w, vec4 e, vec4 e2) {
    float s = e.w;
    vec2 d = w - e.xy;
    if (dot(d, d) > s * s * 30.0) return vec3(0.0);
    // The pair faces the person it watches: "up" points away from them, so they see it upright.
    vec2 up = normalize(e.xy - e2.xy + vec2(1e-4, 0.0));
    vec2 right = vec2(up.y, -up.x);
    vec2 l = vec2(dot(d, right), dot(d, up)) / s;
    vec3 col = vec3(0.0);
    vec3 iris = e2.z < 0.5 ? vec3(1.0, 0.62, 0.08) : vec3(1.0, 0.08, 0.04);
    for (int k = 0; k < 2; k++) {
        float side = k == 0 ? -1.0 : 1.0;
        vec2 p = l - vec2(side * 1.25, 0.0);
        // Angry slant: inner corners lower.
        p = rotate2(p, side * 0.25);
        vec2 q = vec2(p.x, p.y / max(e.z, 0.04));
        float almond = max(length(q - vec2(0.0, -0.55)) - 1.0, length(q - vec2(0.0, 0.55)) - 1.0);
        float inside = cover(almond, 0.06);
        // Pupils look slightly toward the person (down in this frame) and jitter.
        vec2 pc = vec2(0.08 * sin(uTime * 1.3 + e2.w), -0.22);
        float slit = sdEll(q - pc, vec2(0.13, 0.5));
        float r = length(q);
        vec3 c = iris * (1.4 - 0.7 * r) + vec3(1.0, 0.95, 0.6) * exp(-r * r * 6.0) * 0.6;
        c *= 0.75 + 0.25 * fbm2(q * 6.0 + e2.w, 3);
        c *= smoothstep(-0.02, 0.06, slit);
        col += c * inside;
        // Soft glow around each eye, like eyeshine catching the light.
        col += iris * exp(-max(almond, 0.0) * 4.0) * 0.12 * e.z;
    }
    return col;
}

vec3 maw(vec2 w) {
    if (uMaw.w <= 0.0 || uMaw.z <= 0.0) return vec3(0.0);
    vec2 d = w - uMaw.xy;
    float r = length(d);
    float R = uMaw.z;
    if (r > R * 1.6) return vec3(0.0);
    float a = atan(d.y, d.x);
    // Wet, bumpy lips around the pit.
    float lip = R * (1.0 + 0.06 * snoise(vec2(a * 3.0, uTime * 2.0)));
    vec3 col = vec3(0.0);
    float flesh = cover(abs(r - lip * 1.12) - R * 0.14, R * 0.03);
    vec3 fleshCol = vec3(0.45, 0.04, 0.06) * (0.6 + 0.5 * (fbm2(d / R * 4.0, 3) * 0.5 + 0.5));
    fleshCol += vec3(0.9, 0.5, 0.5) * pow(max(0.0, snoise(d / R * 9.0)), 6.0) * 0.6; // wet glints
    col += fleshCol * flesh;
    // Rows of teeth pointing inward from the lip.
    float n = 22.0;
    float t = tri(a / 6.2831853 * n);
    float toothLen = R * 0.42 * (1.0 - t) * (0.8 + 0.2 * fract(sin(floor(a / 6.2831853 * n) * 91.7) * 43.1));
    float tooth = step(lip - toothLen, r) * step(r, lip);
    vec3 enamel = vec3(0.86, 0.8, 0.62) * (0.6 + 0.4 * smoothstep(lip - toothLen, lip, r));
    col = mix(col, enamel, tooth);
    // The void inside, glowing faintly red at the bottom.
    float hole = step(r, lip) * (1.0 - tooth);
    col = mix(col, vec3(0.18, 0.0, 0.01) * smoothstep(0.0, lip, r), hole);
    return col * uMaw.w;
}

void main() {
    vec2 w = toWorld(vUv);
    vec3 col = vec3(0.0);
    for (int i = 0; i < MAX_EYES; i++) {
        if (i >= uEyeCount) break;
        if (uEyes[i].z <= 0.0) continue;
        col += eyePair(w, uEyes[i], uEyes2[i]);
    }
    col = max(col, maw(w));
    col = mix(col, vec3(0.9, 0.05, 0.03), uFlash);
    fragColor = vec4(col, 1.0);
}
