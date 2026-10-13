#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// ECTOPLASMIC FOG
// Two levels of domain-warped simplex fBm make thick, curling fog. A second, offset density
// sample along a light direction fakes volumetric self-shadowing. People push the fog away,
// swirl it with their motion, and leave a clear pocket with a glowing ectoplasm rim.

in vec2 vUv;
out vec4 fragColor;

uniform float uFlowTime;    // time integrated with the speed slider (no jumps when it changes)
uniform float uDensity;
uniform float uScale;
uniform float uHue;         // 0 = purple, 1 = toxic green
uniform float uClearRadius;
uniform float uWisps;

float fogField(vec2 p, float t, out vec2 warp) {
    vec3 q = vec3(p, t * 0.07);
    vec2 w1 = vec2(fbm3(q, 4), fbm3(q + vec3(5.2, 1.3, 2.7), 4));
    vec3 r = vec3(p + 1.8 * w1, t * 0.11);
    vec2 w2 = vec2(fbm3(r + vec3(1.7, 9.2, 0.0), 4), fbm3(r + vec3(8.3, 2.8, 4.1), 4));
    warp = w2;
    return fbm3(vec3(p + 2.2 * w2, t * 0.15), 5);
}

void main() {
    float aspect = canvasAspect();
    vec2 p = toWorld(vUv);
    float t = uFlowTime;

    // --- Interaction: displacement field + clear pockets around feet -------------------------
    vec2 disp = vec2(0.0);
    float hole = 1.0;
    float rim = 0.0;
    float r = uClearRadius * (1.0 + uBass * 0.25);
    for (int i = 0; i < MAX_PEOPLE; i++) {
        if (i >= uPeopleCount) break;
        vec2 pp = toWorld(uPeople[i].xy);
        vec2 vel = uPeople[i].zw * vec2(aspect, 1.0);
        vec2 d = p - pp;
        float dist = length(d);
        vec2 dir = d / max(dist, 1e-4);
        vec2 tangent = vec2(-dir.y, dir.x);
        float infl = exp(-(dist * dist) / (r * r * 1.6));
        // Moving past someone curls the fog around them (sign follows which side they pass).
        float spin = clamp(dot(tangent, vel) * 5.0, -1.5, 1.5);
        disp += dir * infl * r * 0.9;
        disp += tangent * infl * r * spin;
        disp += vel * infl * 0.15;            // fog is dragged along in their wake
        hole *= smoothstep(r * 0.3, r * 1.05, dist);
        rim += exp(-pow((dist - r * 0.8) / (r * 0.18), 2.0));
    }

    // --- Fog density ------------------------------------------------------------------------
    vec2 sp = (p - disp) * uScale;
    vec2 warp;
    float n = fogField(sp, t, warp);
    float thresh = mix(0.62, 0.2, uDensity) - uBass * 0.12;
    float dens = smoothstep(thresh, thresh + 0.38, n * 0.5 + 0.5);

    // Fake volumetric lighting: compare with a sample nudged toward the light.
    vec2 lightDir = normalize(vec2(-0.6, 0.8));
    float n2 = fbm3(vec3(sp + lightDir * 0.06 + 2.2 * warp, t * 0.15), 5);
    float lit = clamp((n - n2) * 4.0 + 0.5, 0.0, 1.0);

    // --- Color ------------------------------------------------------------------------------
    float region = smoothstep(-0.35, 0.35, snoise(vec3(sp * 0.35, t * 0.03)) + (uHue * 2.0 - 1.0) * 1.2);
    vec3 deep = mix(vec3(0.10, 0.01, 0.20), vec3(0.01, 0.16, 0.06), region);
    vec3 hi = mix(vec3(0.55, 0.20, 0.95), vec3(0.35, 1.0, 0.45), region);
    vec3 col = mix(deep, hi, lit * dens);

    // Glowing filaments threaded through the thick parts.
    float ridge = pow(1.0 - abs(snoise(vec3(sp * 2.6 + warp * 1.5, t * 0.25))), 10.0);
    col += hi * ridge * uWisps * dens * (1.4 + uTreble * 1.5);
    col *= dens;

    // Thunder / screams: lightning flicker inside the fog.
    col += vec3(0.6, 0.9, 1.0) * uBeat * 0.5 * dens * lit;
    col *= 1.0 + uLevel * 0.6;

    // Ectoplasm clinging to the edge of each clear pocket.
    col += hi * rim * 0.22 * dens;
    col *= hole;

    vec2 e = smoothstep(0.0, 0.08, vUv) * smoothstep(0.0, 0.08, 1.0 - vUv);
    fragColor = vec4(col * e.x * e.y, 1.0);
}
