#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// Summer night lawn for the fireflies: barely-there moonlit grass.
in vec2 vUv;
out vec4 fragColor;
uniform float uMoon;

void main() {
    vec2 p = toWorld(vUv);
    float sway = sin(uTime * 0.8 + p.x * 2.0) * 0.003;
    float blades = fbm2(vec2(p.x * 150.0 + sway * 300.0, p.y * 50.0), 3) * 0.5 + 0.5;
    float patches = fbm2(p * 3.0, 4) * 0.5 + 0.5;
    vec3 col = vec3(0.02, 0.06, 0.05) * (0.5 + blades) * (0.6 + 0.6 * patches) * uMoon;
    fragColor = vec4(col, 1.0);
}
