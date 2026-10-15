#version 300 es
precision highp float;
// Glowing footprints left in Ground Smoke. R = glow, GBA = colour. Each print fades out (uFade
// is subtracted per step); new prints are stamped in shoe shape with their own colour.
in vec2 vUv;
out vec4 fragColor;
#define MAX_STAMPS 16
uniform sampler2D uState;
uniform float uAspect;
uniform vec4 uStamps[MAX_STAMPS];  // x, y (uv), length (world), strength
uniform vec4 uStampsB[MAX_STAMPS]; // direction xy, side, hue
uniform int uStampCount;
uniform float uFade;
#include "footprint.glsl"

vec3 hue2rgb(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

void main() {
    vec4 s = texture(uState, vUv);
    s.r = max(s.r - uFade, 0.0);
    vec2 p = vec2(vUv.x * uAspect, vUv.y);
    for (int i = 0; i < MAX_STAMPS; i++) {
        if (i >= uStampCount) break;
        vec2 q = p - vec2(uStamps[i].x * uAspect, uStamps[i].y);
        vec2 dir = uStampsB[i].xy;
        vec2 f = vec2(dot(q, vec2(dir.y, -dir.x)), dot(q, dir)) / uStamps[i].z;
        float tread;
        float m = footprint(f, uStampsB[i].z, tread) * uStamps[i].w * (0.75 + 0.25 * tread);
        if (m > s.r) {
            s.gba = mix(s.gba, hue2rgb(uStampsB[i].w), clamp(m * 2.0, 0.0, 1.0));
            s.r = m;
        }
    }
    fragColor = s;
}
