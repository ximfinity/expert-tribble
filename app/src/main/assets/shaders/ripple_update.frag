#version 300 es
precision highp float;

// GPU wave equation (ping-pong). R = height now, G = height last step.
// next = (sum of 4 neighbours) / 2 - previous, then damped. Drops add gaussian impulses.

in vec2 vUv;
out vec4 fragColor;

#define MAX_DROPS 24
uniform sampler2D uState;
uniform vec2 uTexel;
uniform float uDamping;
uniform float uAspect;
uniform float uEncoded;           // 1 = 8-bit fallback (heights stored biased)
uniform vec4 uDrops[MAX_DROPS];   // x, y (uv), radius (world), strength
uniform int uDropCount;

float dec(float v) { return uEncoded > 0.5 ? v * 2.0 - 1.0 : v; }
float enc(float v) { return uEncoded > 0.5 ? clamp(v * 0.5 + 0.5, 0.0, 1.0) : v; }

void main() {
    vec2 s = texture(uState, vUv).rg;
    float now = dec(s.r);
    float prev = dec(s.g);
    float l = dec(texture(uState, vUv - vec2(uTexel.x, 0.0)).r);
    float r = dec(texture(uState, vUv + vec2(uTexel.x, 0.0)).r);
    float d = dec(texture(uState, vUv - vec2(0.0, uTexel.y)).r);
    float u = dec(texture(uState, vUv + vec2(0.0, uTexel.y)).r);
    float next = (l + r + d + u) * 0.5 - prev;
    next *= uDamping;

    vec2 p = vec2(vUv.x * uAspect, vUv.y);
    for (int i = 0; i < MAX_DROPS; i++) {
        if (i >= uDropCount) break;
        vec2 c = vec2(uDrops[i].x * uAspect, uDrops[i].y);
        float rad = uDrops[i].z;
        vec2 dd = p - c;
        next += uDrops[i].w * exp(-dot(dd, dd) / (rad * rad));
    }
    next = clamp(next, -1.0, 1.0);
    fragColor = vec4(enc(next), enc(now), 0.0, 1.0);
}
