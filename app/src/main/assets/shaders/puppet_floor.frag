#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// Candle-lit floor for the shadow puppets: warm light from a low candle just off the near edge,
// flickering and guttering, falling off across old plaster. Shadows are drawn on top in black.

in vec2 vUv;
out vec4 fragColor;

uniform vec2 uCandle;      // world position of the light (can be off the floor)
uniform float uBrightness;
uniform float uFlicker;    // 0..1 current candle flicker
uniform vec3 uTint;        // candle (warm) or moonlight (cold)

void main() {
    vec2 p = toWorld(vUv);
    float d = length(p - uCandle);
    float fall = 1.0 / (1.0 + d * d * 2.2);
    float wobble = 0.85 + 0.15 * uFlicker + 0.05 * snoise(vec2(uTime * 7.0, 2.0));
    float plaster = 0.82 + 0.18 * (fbm2(p * 6.0, 4) * 0.5 + 0.5);
    float stains = 1.0 - 0.15 * smoothstep(0.3, 0.7, fbm2(p * 1.7 + 4.0, 3) * 0.5 + 0.5);
    float vig = smoothstep(1.35, 0.25, length((vUv - 0.5) * vec2(canvasAspect(), 1.0)));
    vec3 col = uTint * fall * wobble * plaster * stains * vig * uBrightness;
    fragColor = vec4(col, 1.0);
}
