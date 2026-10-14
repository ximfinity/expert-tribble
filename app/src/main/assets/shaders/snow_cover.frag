#version 300 es
precision highp float;
// Snow depth (R). Fresh snow keeps falling (uFill); footsteps press it down; edges of old
// footprints slowly soften as they fill back in.
in vec2 vUv;
out vec4 fragColor;
#define MAX_STAMPS 16
uniform sampler2D uState;
uniform vec2 uTexel;
uniform float uAspect;
uniform vec4 uStamps[MAX_STAMPS];
uniform int uStampCount;
uniform float uFill;
uniform float uSoften;

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
        d *= 1.0 - uStamps[i].w * exp(-dot(q, q) / (r * r));
    }
    fragColor = vec4(max(d, 0.0), 0.0, 0.0, 1.0);
}
