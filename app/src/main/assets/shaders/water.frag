#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// LIVING WATER, pass 2: shading. Combines the ripple simulation and the monster height field
// into one surface, refracts animated caustics through it, and renders the monsters as glassy
// living water with fresnel rims and glowing eyes.

in vec2 vUv;
out vec4 fragColor;

uniform float uFlowTime;
uniform float uCaustics;
uniform float uMonsterGlow;
uniform sampler2D uRipple;
uniform float uRippleEncoded;
uniform sampler2D uShapes;

float caustic(vec2 p, float t) {
    vec2 w = vec2(snoise(vec3(p * 0.8, t * 0.15)), snoise(vec3(p * 0.8 + 5.0, t * 0.15))) * 0.4;
    vec3 v1 = voronoi(p * 4.0 + w, t * 0.9);
    vec3 v2 = voronoi(p * 7.0 - w * 1.5 + 3.1, t * 1.1);
    return exp(-(v1.y - v1.x) * 18.0) * 0.7 + exp(-(v2.y - v2.x) * 22.0) * 0.5;
}

// x = ripple height, y = monster height. Kept apart: they need different normal strengths.
vec2 surface(vec2 uv) {
    return vec2(decodeRipple(texture(uRipple, uv).r, uRippleEncoded), texture(uShapes, uv).r);
}

void main() {
    vec2 p = toWorld(vUv);
    float t = uFlowTime;
    vec3 shape = texture(uShapes, vUv).rgb;

    vec2 e = 1.5 / uResolution;
    float ew = 1.5 / uResolution.y;
    vec2 hx = (surface(vUv + vec2(e.x, 0.0)) - surface(vUv - vec2(e.x, 0.0))) / (2.0 * ew);
    vec2 hy = (surface(vUv + vec2(0.0, e.y)) - surface(vUv - vec2(0.0, e.y))) / (2.0 * ew);
    vec3 n = vec3(-(vec2(hx.x, hy.x) * 0.04 + vec2(hx.y, hy.y) * 0.2), 1.0);
    n.xy += vec2(snoise(vec3(p * 9.0, t * 0.6)), snoise(vec3(p * 9.0 + 7.0, t * 0.6))) * 0.04 * (1.0 + uBass * 1.5);
    n = normalize(n);

    float body = shape.b;
    float c = caustic(p + n.xy * 0.06, t) * uCaustics;

    vec3 col = vec3(0.0, 0.025, 0.04) + vec3(0.25, 0.85, 0.8) * c * 0.35 * (1.0 - body * 0.6);

    vec3 L = normalize(vec3(0.5, 0.7, 0.6));
    vec3 H = normalize(L + vec3(0.0, 0.0, 1.0));
    float spec = pow(max(dot(n, H), 0.0), 120.0);
    float fres = pow(1.0 - max(n.z, 0.0), 3.0);
    col += vec3(0.7, 0.95, 1.0) * spec * 1.2;

    // Monster bodies: water that has stood up. Dense teal with bright fresnel edges.
    vec3 monster = vec3(0.05, 0.35, 0.32) * (0.4 + c * 0.8) + vec3(0.4, 1.0, 0.9) * fres * 0.9;
    col = mix(col, monster, body * 0.75);
    col += vec3(0.3, 1.0, 0.85) * fres * 0.35 * uMonsterGlow * (1.0 + uBeat);
    col += vec3(1.0, 0.22, 0.04) * shape.g * (1.2 + uLevel * 1.5);

    vec2 ev = smoothstep(0.0, 0.1, vUv) * smoothstep(0.0, 0.1, 1.0 - vUv);
    fragColor = vec4(col * ev.x * ev.y, 1.0);
}
