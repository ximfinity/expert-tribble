#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// Dim earthy ground for Fall Leaves: soil and old grass under a warm, low light.
in vec2 vUv;
out vec4 fragColor;
uniform float uLight;

void main() {
    vec2 p = toWorld(vUv);
    float aspect = canvasAspect();
    float vig = smoothstep(1.2, 0.2, length((vUv - 0.5) * vec2(aspect, 1.0)));
    float soil = fbm2(p * 6.0, 5) * 0.5 + 0.5;
    float grass = smoothstep(0.55, 0.8, fbm2(p * vec2(40.0, 70.0), 3) * 0.5 + 0.5);
    vec3 col = mix(vec3(0.16, 0.10, 0.05), vec3(0.22, 0.2, 0.08), grass) * (0.6 + 0.6 * soil);
    col *= vig * uLight * (0.9 + 0.1 * snoise(vec2(uTime * 0.5, 2.0)));
    fragColor = vec4(col, 1.0);
}
