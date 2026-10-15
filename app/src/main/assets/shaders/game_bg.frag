#version 300 es
precision highp float;
#include "common.glsl"

// Game floor: near-black with a faint grid and a soft accent-coloured glow.
in vec2 vUv;
out vec4 fragColor;
uniform vec3 uAccent;

void main() {
    vec2 p = toWorld(vUv);
    vec2 g = abs(fract(p / 0.1 + 0.5) - 0.5) / fwidth(p / 0.1);
    float grid = 1.0 - min(min(g.x, g.y), 1.0);
    float vig = smoothstep(1.1, 0.1, length((vUv - 0.5) * vec2(canvasAspect(), 1.0)));
    vec3 col = vec3(0.015, 0.015, 0.03) + uAccent * (0.06 * vig + 0.035 * grid);
    col += uAccent * uBeat * 0.08;
    fragColor = vec4(col, 1.0);
}
