#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// LIVING WATER, pass 1: monster height field (rendered at reduced resolution).
// Each monster first swells up as a round blob of water, then morphs into its shape:
// writhing tentacles, a grasping skeletal hand, or a screaming face.
// Output: R = height, G = emissive (eyes), B = monster body mask.

in vec2 vUv;
out vec4 fragColor;

#define MAX_MONSTERS 4
#define MAX_EYES 6
uniform vec4 uMonA[MAX_MONSTERS]; // world x, world y, facing angle, emerge 0..1
uniform vec4 uMonB[MAX_MONSTERS]; // type, seed, age (s), size (world)
uniform int uMonCount;
uniform vec4 uEyes[MAX_EYES];     // world x, world y, openness 0..1, size
uniform int uEyeCount;

// Height of a tapered round tube along segment a-b.
float tube(vec2 p, vec2 a, vec2 b, float ra, float rb) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    float r = mix(ra, rb, h);
    float x = length(pa - ba * h) / r;
    return x < 1.0 ? sqrt(1.0 - x * x) * r : 0.0;
}

float dome(vec2 p, vec2 r) {
    vec2 q = p / r;
    float x = dot(q, q);
    return x < 1.0 ? sqrt(1.0 - x) * min(r.x, r.y) : 0.0;
}

float rnd(float seed, float i) { return fract(sin(seed * 13.17 + i * 7.73) * 43758.5453); }

float tentacles(vec2 p, float size, float seed, float t) {
    float h = dome(p, vec2(size * 0.17));
    float baseR = size * 0.085;
    float tipR = size * 0.01;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float r = rnd(seed, fi);
        float ang = (fi - 2.0) * 0.55 + (r - 0.5) * 0.4;
        float seg = size * (0.85 + 0.35 * r) / 8.0;
        float curl = (r > 0.5 ? 1.0 : -1.0) * (0.25 + 0.2 * sin(t * 0.9 + fi));
        vec2 pos = vec2(0.0);
        for (int k = 0; k < 8; k++) {
            float s = float(k) / 8.0;
            ang += sin(t * (1.3 + r) + fi * 2.1 + float(k) * 0.7) * 0.22 * (0.3 + s);
            if (k > 4) ang += curl;
            vec2 nxt = pos + vec2(cos(ang), sin(ang)) * seg;
            float th = tube(p, pos, nxt, mix(baseR, tipR, s), mix(baseR, tipR, s + 0.125));
            // Suckers: little ridges along the length.
            th *= 1.0 + 0.18 * sin(s * 50.0 + length(p - pos) * 300.0);
            h = max(h, th);
            pos = nxt;
        }
    }
    return h;
}

float hand(vec2 p, float size, float t) {
    float grasp = 0.5 + 0.5 * sin(t * 1.4);
    float h = dome(p - vec2(-0.1 * size, 0.0), vec2(0.18, 0.16) * size);
    h = max(h, tube(p, vec2(-0.2 * size, 0.0), vec2(-0.8 * size, 0.04 * size), 0.1 * size, 0.12 * size));
    float lens[3] = float[3](0.22, 0.16, 0.12);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 pos = vec2(0.06 * size, (fi - 1.5) * 0.085 * size);
        float ang = (fi - 1.5) * mix(0.28, 0.08, grasp);
        float len = (i == 1 || i == 2) ? 1.1 : 0.9;
        for (int k = 0; k < 3; k++) {
            float bend = grasp * (0.5 + 0.3 * float(k));
            // Curling into the floor foreshortens the joint seen from above.
            vec2 nxt = pos + vec2(cos(ang), sin(ang)) * lens[k] * len * size * cos(bend);
            h = max(h, tube(p, pos, nxt, 0.042 * size, 0.034 * size));
            pos = nxt;
        }
        // Claw
        vec2 tip = pos + vec2(cos(ang), sin(ang)) * 0.06 * size;
        h = max(h, tube(p, pos, tip, 0.026 * size, 0.004 * size) * 1.6);
    }
    vec2 tb = vec2(-0.08 * size, 0.15 * size);
    vec2 tm = tb + vec2(cos(1.0), sin(1.0)) * 0.12 * size;
    vec2 tt = tm + vec2(cos(0.5 - grasp * 0.6), sin(0.5 - grasp * 0.6)) * 0.1 * size;
    h = max(h, tube(p, tb, tm, 0.05 * size, 0.042 * size));
    h = max(h, tube(p, tm, tt, 0.042 * size, 0.03 * size));
    return h;
}

float face(vec2 p, float size, float scream, out float glow) {
    vec2 q = p / size;  // +x = top of the head
    float h = dome(q - vec2(0.05, 0.0), vec2(0.42, 0.33));
    h -= dome(q - vec2(0.12, 0.13), vec2(0.09, 0.075)) * 1.3;
    h -= dome(q - vec2(0.12, -0.13), vec2(0.09, 0.075)) * 1.3;
    h -= dome(q - vec2(-0.02, 0.0), vec2(0.05, 0.03)) * 0.8;
    float mouthOpen = 0.04 + 0.13 * scream;
    h -= dome(q - vec2(-0.19, 0.0), vec2(mouthOpen, 0.11 + 0.03 * scream)) * 1.6;
    h += dome(q - vec2(-0.03, 0.2), vec2(0.08, 0.06)) * 0.3;
    h += dome(q - vec2(-0.03, -0.2), vec2(0.08, 0.06)) * 0.3;
    vec2 eL = q - vec2(0.12, 0.13);
    vec2 eR = q - vec2(0.12, -0.13);
    glow = exp(-dot(eL, eL) / 0.0012) + exp(-dot(eR, eR) / 0.0012);
    vec2 m = (q - vec2(-0.19, 0.0)) / vec2(mouthOpen, 0.1);
    glow += smoothstep(1.0, 0.3, length(m)) * 0.35 * scream;
    return max(h, 0.0) * size;
}

void main() {
    vec2 p = toWorld(vUv);
    float height = 0.0;
    float glow = 0.0;
    float body = 0.0;

    for (int i = 0; i < MAX_MONSTERS; i++) {
        if (i >= uMonCount) break;
        vec4 a = uMonA[i];
        vec4 b = uMonB[i];
        float size = b.w;
        vec2 d = p - a.xy;
        if (dot(d, d) > size * size * 1.4) continue;
        vec2 lp = rotate2(d, -a.z);
        float emerge = a.w;
        float blobAmt = smoothstep(0.0, 0.35, emerge);
        float shapeAmt = smoothstep(0.3, 0.85, emerge);
        float blob = dome(lp, vec2(size * 0.35)) * blobAmt;
        float shape = 0.0;
        float g = 0.0;
        int type = int(b.x + 0.5);
        if (type == 0) {
            shape = tentacles(lp, size, b.y, b.z);
        } else if (type == 1) {
            shape = hand(lp, size, b.z);
        } else {
            shape = face(lp, size, clamp(0.25 + uLevel * 1.5 + 0.3 * sin(b.z * 2.0), 0.0, 1.0), g);
        }
        float h = max(blob * (1.0 - shapeAmt), shape * shapeAmt);
        h *= 1.0 + 0.12 * snoise(vec3(p * 40.0, uTime * 2.0)) * shapeAmt;  // streaming water skin
        height = max(height, h);
        glow += g * shapeAmt;
        body = max(body, smoothstep(0.0, size * 0.02, h));
    }

    // Eyes opening in the water.
    for (int i = 0; i < MAX_EYES; i++) {
        if (i >= uEyeCount) break;
        vec4 e = uEyes[i];
        if (e.z < 0.02) continue;
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -1.0 : 1.0;
            vec2 q = (p - e.xy - vec2(side * e.w * 1.1, 0.0)) / e.w;
            float lid = e.z * 0.42 * max(1.0 - q.x * q.x, 0.0);
            float inside = step(abs(q.x), 1.0) * smoothstep(lid, lid * 0.5, abs(q.y));
            float pupil = exp(-pow(q.x / 0.13, 2.0));
            glow += inside * (1.0 - 0.85 * pupil);
        }
    }

    fragColor = vec4(clamp(height * 6.0, 0.0, 1.0), clamp(glow, 0.0, 1.0), body, 1.0);
}
