#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// Dim, flickering lantern-light on the floor. The spiders are drawn as shadows on top of it,
// which reads far more real on a projector than bright sprites (black = no light = shadow).

in vec2 vUv;
out vec4 fragColor;

uniform float uLight;

void main() {
    vec2 p = toWorld(vUv);
    float aspect = canvasAspect();
    float vig = smoothstep(1.15, 0.15, length((vUv - 0.5) * vec2(aspect, 1.0)));
    float grime = fbm2(p * 5.0, 4) * 0.5 + 0.5;
    float flicker = 0.86 + 0.14 * snoise(vec2(uTime * 2.7, 0.3)) + 0.05 * snoise(vec2(uTime * 13.0, 4.1));
    vec3 col = vec3(0.45, 0.40, 0.26) * (0.55 + 0.45 * grime) * vig * flicker * uLight;
    col += vec3(0.7, 0.8, 1.0) * uBeat * 0.45 * uLight;
    fragColor = vec4(col, 1.0);
}
