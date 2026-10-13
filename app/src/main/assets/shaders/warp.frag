#version 300 es
precision highp float;
#include "noise.glsl"

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uCurrent;
uniform sampler2D uPrevious;
uniform float uTransition; // 0 = previous scene, 1 = current scene
uniform float uTime;
uniform float uBrightness;
uniform float uFeather;
uniform float uEdit;

void main() {
    vec3 col = texture(uCurrent, vUv).rgb;

    if (uTransition < 1.0) {
        // Ectoplasmic dissolve: a noise threshold sweeps from old scene to new, with a glowing seam.
        vec3 prev = texture(uPrevious, vUv).rgb;
        float n = fbm2(vUv * vec2(4.0, 3.0) + uTime * 0.15, 4) * 0.5 + 0.5;
        float t = uTransition * 1.3 - 0.15;
        float m = smoothstep(t + 0.05, t - 0.05, n);
        float seam = exp(-pow((n - t) / 0.025, 2.0));
        col = mix(prev, col, m) + vec3(0.35, 1.0, 0.55) * seam * 0.9;
    }

    float f = max(uFeather, 1e-4);
    vec2 e = smoothstep(vec2(0.0), vec2(f), vUv) * smoothstep(vec2(0.0), vec2(f), 1.0 - vUv);
    col *= e.x * e.y * uBrightness;

    if (uEdit > 0.5) {
        // Keystone grid: visualizes the warp while the user drags corners.
        vec2 g = vUv * 8.0;
        vec2 w = fwidth(g);
        vec2 lines = 1.0 - smoothstep(vec2(0.0), w * 1.5, abs(fract(g + 0.5) - 0.5));
        float grid = max(lines.x, lines.y);
        vec2 bw = fwidth(vUv) * 4.0;
        vec2 border = 1.0 - smoothstep(vec2(0.0), bw, min(vUv, 1.0 - vUv));
        col = max(col * 0.5, vec3(0.2, 0.8, 1.0) * grid * 0.55);
        col = max(col, vec3(0.5, 1.0, 0.6) * max(border.x, border.y));
    }

    fragColor = vec4(col, 1.0);
}
