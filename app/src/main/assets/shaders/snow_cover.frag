#version 300 es
precision highp float;
// Snow depth (R). Fresh snow keeps falling (uFill); shoe-shaped footprints press it down; edges
// of old footprints slowly soften as they fill back in.
in vec2 vUv;
out vec4 fragColor;
#define MAX_STAMPS 16
uniform sampler2D uState;
uniform vec2 uTexel;
uniform float uAspect;
uniform vec4 uStamps[MAX_STAMPS];  // x, y (uv), print length (world), strength
uniform vec4 uStampsB[MAX_STAMPS]; // walking direction xy, side (+1 right / -1 left, 0 = round)
uniform int uStampCount;
uniform float uFill;
uniform float uSoften;
#include "footprint.glsl"

void main() {
    float d = texture(uState, vUv).r;
    float avg = 0.25 * (texture(uState, vUv + vec2(uTexel.x, 0.0)).r + texture(uState, vUv - vec2(uTexel.x, 0.0)).r
                      + texture(uState, vUv + vec2(0.0, uTexel.y)).r + texture(uState, vUv - vec2(0.0, uTexel.y)).r);
    d = mix(d, avg, uSoften);
    d = min(1.0, d + uFill);
    vec2 p = vec2(vUv.x * uAspect, vUv.y);
    for (int i = 0; i < MAX_STAMPS; i++) {
        if (i >= uStampCount) break;
        vec2 c = vec2(uStamps[i].x * uAspect, uStamps[i].y);
        vec2 q = p - c;
        float r = uStamps[i].z;
        float side = uStampsB[i].z;
        if (abs(side) > 0.5) {
            vec2 dir = uStampsB[i].xy;
            vec2 f = vec2(dot(q, vec2(dir.y, -dir.x)), dot(q, dir)) / r;
            float tread;
            float m = footprint(f, side, tread);
            d *= 1.0 - uStamps[i].w * m * (0.8 + 0.2 * tread);
        } else {
            d *= 1.0 - uStamps[i].w * exp(-dot(q, q) / (r * r));
        }
    }
    fragColor = vec4(max(d, 0.0), 0.0, 0.0, 1.0);
}
