#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"
#include "scare.glsl"

// GRASPING HANDS: the black floor splits open under anyone who walks on, glowing cracks race
// outward, and rotting hands claw up out of them, reaching for the person's feet.

#define MAX_HANDS 12
#define MAX_CRACKS 8

in vec2 vUv;
out vec4 fragColor;

uniform vec4 uHands[MAX_HANDS];   // root world x, y, rise 0..1, angle (direction it reaches)
uniform vec4 uHands2[MAX_HANDS];  // grip 0..1, seed, size (world length), unused
uniform int uHandCount;
uniform vec4 uCracks[MAX_CRACKS]; // world x, y, radius, glow 0..1
uniform int uCrackCount;
uniform float uFlash;

float finger(vec2 p, vec2 base, float dir, float len, float curl, float r0, out float nail) {
    // Two bones; the second bends by [curl]. Returns distance; [nail] is distance to the claw.
    vec2 a = base;
    vec2 b = a + vec2(sin(dir), cos(dir)) * len * 0.55;
    // Seen from above, a curling finger fans in a little and its last bone foreshortens.
    float dir2 = dir * (1.0 + 0.8 * curl);
    vec2 c = b + vec2(sin(dir2), cos(dir2)) * len * 0.45 * (1.0 - 0.55 * curl);
    float d = min(sdSegment(p, a, b) - r0, sdSegment(p, b, c) - r0 * 0.8);
    d = min(d, length(p - b) - r0 * 1.15); // knuckle
    vec2 tip = c + vec2(sin(dir2), cos(dir2)) * len * 0.16;
    nail = sdSegment(p, c, tip) - r0 * 0.55;
    return d;
}

vec3 hand(vec2 w, vec4 h, vec4 h2, out float alpha) {
    alpha = 0.0;
    float size = h2.z;
    vec2 l = toLocal(w, h.xy, h.w, size);
    if (dot(l, l) > 4.0) return vec3(0.0);
    float rise = h.z;
    // Emerge from the hole at the local origin: the whole hand slides up out of it, and only what
    // is past the hole's edge shows.
    float above = smoothstep(-0.16, -0.04, l.y);
    l.y += (1.0 - rise) * 1.1;
    float grip = h2.x;
    float sway = 0.12 * sin(uTime * 3.0 + h2.y);
    vec2 lp = rotate2(l, sway);
    float arm = sdSegment(lp, vec2(0.0, -1.2), vec2(0.0, 0.32)) - mix(0.11, 0.085, smoothstep(-1.2, 0.3, lp.y));
    float palm = sdEll(lp - vec2(0.0, 0.48), vec2(0.2, 0.2));
    float d = smin(arm, palm, 0.08);
    float nails = 9.0;
    for (int k = 0; k < 4; k++) {
        float fk = float(k);
        float x = -0.15 + fk * 0.1;
        float dir = (fk - 1.5) * 0.22;
        float len = fk == 1.0 || fk == 2.0 ? 0.55 : 0.45;
        float nl;
        float f = finger(lp, vec2(x, 0.6), dir, len, grip, 0.04, nl);
        d = smin(d, f, 0.03);
        nails = min(nails, nl);
    }
    float nt;
    float thumb = finger(lp, vec2(0.17, 0.42), 1.0 - grip * 0.6, 0.38, grip, 0.045, nt);
    d = smin(d, thumb, 0.04);
    nails = min(nails, nt);

    float visible = above;
    alpha = cover(d, 0.02) * visible;
    float depth = clamp(-d * 9.0, 0.0, 1.0);
    vec3 skin = mix(vec3(0.16, 0.18, 0.14), vec3(0.6, 0.64, 0.52), sqrt(depth));
    skin *= 0.7 + 0.4 * (fbm2(lp * 6.0 + h2.y, 4) * 0.5 + 0.5);
    // Rot: dark patches and exposed bone along the forearm.
    float rot = smoothstep(0.2, 0.6, fbm2(lp * 3.0 + h2.y * 2.0, 3));
    skin = mix(skin, vec3(0.22, 0.12, 0.1), rot * 0.6);
    float bone = (1.0 - smoothstep(0.0, 0.025, abs(lp.x + 0.02 * sin(lp.y * 9.0)))) * step(lp.y, 0.2) * rot;
    skin = mix(skin, vec3(0.85, 0.82, 0.7), bone);
    float nailA = cover(nails, 0.01) * visible;
    skin = mix(skin, vec3(0.05, 0.04, 0.03), nailA);
    alpha = max(alpha, nailA);
    return skin;
}

vec3 cracks(vec2 w, vec4 c) {
    vec2 d = w - c.xy;
    float r = length(d);
    if (r > c.z * 1.3 || c.w <= 0.0) return vec3(0.0);
    float a = atan(d.y, d.x);
    float reach = c.z * (0.65 + 0.35 * (snoise(vec2(a * 2.0, c.x * 7.0)) * 0.5 + 0.5));
    float mask = 1.0 - smoothstep(reach * 0.7, reach, r);
    // Voronoi cells stretched along the radius (in warped polar coordinates) give jagged fissures
    // that mostly run outward from the centre; some cell edges are dropped so it doesn't look like a net.
    float rn = r / c.z;
    vec2 pp = vec2(a * 3.0, sqrt(rn) * 7.0) + 0.25 * vec2(snoise(d * 30.0), snoise(d * 30.0 + 9.0)) + c.x * 5.0;
    vec3 v = voronoi(pp, 0.0);
    float edge = 1.0 - smoothstep(0.0, 0.07 + 0.08 * (1.0 - rn), v.y - v.x);
    edge *= step(0.3, fract(v.z * 7.13)) * 0.8 + 0.2;
    float heat = edge * mask;
    vec3 glow = mix(vec3(0.5, 0.03, 0.0), vec3(1.0, 0.45, 0.08), heat * (1.0 - rn));
    // A dark, broken pit right at the centre.
    float pit = cover(r - c.z * 0.18 * (1.0 + 0.3 * snoise(vec2(a * 3.0, 1.0))), 0.01);
    vec3 col = glow * heat * c.w * (0.8 + 0.2 * sin(uTime * 5.0 + r * 30.0));
    col = mix(col, vec3(0.12, 0.0, 0.0) * c.w, pit);
    return col;
}

void main() {
    vec2 w = toWorld(vUv);
    vec3 col = vec3(0.0);
    for (int i = 0; i < MAX_CRACKS; i++) {
        if (i >= uCrackCount) break;
        col += cracks(w, uCracks[i]);
    }
    for (int i = 0; i < MAX_HANDS; i++) {
        if (i >= uHandCount) break;
        if (uHands[i].z <= 0.0) continue;
        // A dark hole where each hand breaks through.
        vec2 dh = w - uHands[i].xy;
        col *= 1.0 - 0.9 * cover(length(dh) - uHands2[i].z * 0.16 * min(1.0, uHands[i].z * 3.0), 0.005);
        float a;
        vec3 h = hand(w, uHands[i], uHands2[i], a);
        col = mix(col, h, a);
    }
    col = mix(col, vec3(0.9, 0.2, 0.05), uFlash);
    fragColor = vec4(col, 1.0);
}
