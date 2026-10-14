#version 300 es
precision highp float;
#include "noise.glsl"
// Vorticity confinement (re-injects the small curls numerical diffusion smears out) plus a
// slow curl-noise breeze so the smoke never sits still.
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uVelocity;
uniform sampler2D uCurl;
uniform vec2 uTexel;
uniform float uDt;
uniform float uCurlStrength;
uniform float uBreeze;
uniform float uTime;

void main() {
    float l = texture(uCurl, vUv - vec2(uTexel.x, 0.0)).x;
    float r = texture(uCurl, vUv + vec2(uTexel.x, 0.0)).x;
    float b = texture(uCurl, vUv - vec2(0.0, uTexel.y)).x;
    float t = texture(uCurl, vUv + vec2(0.0, uTexel.y)).x;
    float c = texture(uCurl, vUv).x;
    vec2 force = 0.5 * vec2(abs(t) - abs(b), abs(r) - abs(l));
    force /= length(force) + 1e-4;
    force *= uCurlStrength * c * vec2(1.0, -1.0);

    // Curl of a scalar noise field is divergence-free: a natural, swirling breeze.
    vec2 q = vUv * vec2(3.0, 2.0);
    float e = 0.01;
    float n0 = snoise(vec3(q, uTime * 0.05));
    float nx = snoise(vec3(q + vec2(e, 0.0), uTime * 0.05));
    float ny = snoise(vec3(q + vec2(0.0, e), uTime * 0.05));
    vec2 breeze = vec2(ny - n0, -(nx - n0)) / e;

    vec2 vel = texture(uVelocity, vUv).xy;
    vel += uDt * (force + breeze * uBreeze);
    fragColor = vec4(clamp(vel, vec2(-1000.0), vec2(1000.0)), 0.0, 1.0);
}
