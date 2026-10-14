#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// SUMMER POOL: sunlit water over a tiled pool floor. The ripple simulation bends the tiles and
// the caustics (refraction), sparkles in the sun, and churns into foam where splashes are big.

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uRipple;
uniform float uRippleEncoded;
uniform vec2 uRippleTexel;
uniform float uFlowTime;
uniform float uSun;

float sdRoundRect(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float ripple(vec2 uv) { return decodeRipple(texture(uRipple, uv).r, uRippleEncoded); }

void main() {
    float aspect = canvasAspect();
    vec2 p = toWorld(vUv);
    vec2 centre = vec2(aspect * 0.5, 0.5);
    float edge = sdRoundRect(p - centre, vec2(aspect * 0.5 - 0.06, 0.44), 0.06);

    // Water surface normal from the ripple field plus a gentle breeze chop.
    vec2 e = uRippleTexel * 1.5;
    float hx = ripple(vUv + vec2(e.x, 0.0)) - ripple(vUv - vec2(e.x, 0.0));
    float hy = ripple(vUv + vec2(0.0, e.y)) - ripple(vUv - vec2(0.0, e.y));
    vec3 n = vec3(-hx * 1.6, -hy * 1.6, 1.0);
    n.xy += vec2(snoise(vec3(p * 7.0, uFlowTime * 0.4)), snoise(vec3(p * 7.0 + 9.0, uFlowTime * 0.4))) * 0.06;
    n = normalize(n);
    float h = ripple(vUv);

    // Pool floor seen through the water: refracted tiles, lane stripe, caustics.
    vec2 fp = p + n.xy * 0.05;
    vec2 tile = abs(fract(fp / 0.045) - 0.5);
    float grout = 1.0 - smoothstep(0.44, 0.48, max(tile.x, tile.y));
    float lane = 1.0 - smoothstep(0.012, 0.016, abs(fp.y - 0.5));
    lane *= step(0.25, fp.x) * step(fp.x, aspect - 0.25);
    float depth = smoothstep(0.0, -0.3, edge);
    vec3 water = mix(vec3(0.35, 0.85, 0.9), vec3(0.08, 0.45, 0.75), depth);
    vec3 col = water * (0.82 + 0.18 * grout);
    col = mix(col, vec3(0.05, 0.18, 0.45), lane * 0.7);
    vec3 v1 = voronoi(fp * 9.0 + uFlowTime * 0.2, uFlowTime * 1.2);
    vec3 v2 = voronoi(fp * 14.0 - uFlowTime * 0.15 + 4.0, uFlowTime * 1.5);
    float caustic = exp(-(v1.y - v1.x) * 16.0) * 0.6 + exp(-(v2.y - v2.x) * 20.0) * 0.4;
    col += vec3(0.85, 1.0, 1.0) * caustic * 0.35 * uSun;

    // Sun glints and foam.
    vec3 H = normalize(normalize(vec3(-0.3, 0.5, 0.8)) + vec3(0.0, 0.0, 1.0));
    float glint = pow(max(dot(n, H), 0.0), 300.0);
    col += vec3(1.0) * glint * 1.5 * uSun;
    float foam = smoothstep(0.25, 0.6, abs(h)) * (0.6 + 0.4 * (fbm2(p * 60.0, 3) * 0.5 + 0.5));
    col = mix(col, vec3(0.95, 1.0, 1.0), foam * 0.8);

    // Stone coping around the pool.
    vec2 st = abs(fract(p / vec2(0.09, 0.06)) - 0.5);
    float joint = 1.0 - smoothstep(0.45, 0.49, max(st.x, st.y));
    vec3 stone = vec3(0.8, 0.77, 0.7) * (0.85 + 0.15 * (fbm2(p * 25.0, 3) * 0.5 + 0.5)) * (0.8 + 0.2 * joint);
    float inPool = 1.0 - smoothstep(-0.003, 0.003, edge);
    col = mix(stone * 0.85 * uSun, col * uSun, inPool);
    col *= 1.0 - 0.25 * exp(-pow(edge / 0.01, 2.0)); // lip shadow

    vec2 ev = smoothstep(0.0, 0.02, vUv) * smoothstep(0.0, 0.02, 1.0 - vUv);
    fragColor = vec4(col * ev.x * ev.y, 1.0);
}
