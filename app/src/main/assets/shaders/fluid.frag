#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// CURSED FLUID
// A pool of blood (or lava) that creeps toward people. The surface is a height field built
// from flow-displaced Voronoi cells, bubbles that swell and pop, slow swells, and a live
// GPU ripple simulation driven by footsteps. Normals come from finite differences, then
// wet specular (blood) or glowing cracks (lava) are shaded.

in vec2 vUv;
out vec4 fragColor;

uniform float uFlowTime;
uniform float uMode;        // 0 = blood, 1 = lava
uniform float uPool;        // pool size, 1 = whole floor
uniform float uBubbles;
uniform float uGlow;
uniform sampler2D uRipple;
uniform float uRippleEncoded;

float bubbleField(vec2 p, float t, float rate) {
    vec2 g = p * 7.0;
    vec2 n = floor(g);
    float h = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 c = n + vec2(float(i), float(j));
            float rnd = hash12(c);
            if (rnd > rate) continue;
            vec2 o = hash22(c + 7.3) * 0.6 + 0.2;
            float speed = 0.25 + hash12(c + 3.1) * 0.5;
            float ph = fract(t * speed + rnd * 13.0);
            float R = 0.45 * smoothstep(0.0, 0.85, ph);
            float d = length(c + o - g);
            if (ph < 0.88) {
                h = max(h, sqrt(max(R * R - d * d, 0.0)));
            } else {
                float k = (ph - 0.88) / 0.12;        // popped: expanding ring
                float ringR = R + k * 0.5;
                h += exp(-pow((d - ringR) * 14.0, 2.0)) * (1.0 - k) * 0.25;
            }
        }
    }
    return h;
}

float heightAt(vec2 q, vec2 uv, float t, float rate, out float bubble) {
    vec3 v = voronoi(q * 5.0, t * 0.6);
    float cells = smoothstep(0.0, 0.25, v.y - v.x);
    float swell = fbm3(vec3(q * 2.0, t * 0.2), 3) * 0.15;
    bubble = bubbleField(q, t, rate);
    float ripple = decodeRipple(texture(uRipple, uv).r, uRippleEncoded);
    return swell + cells * 0.06 + bubble * 0.35 + ripple * 0.3;
}

void main() {
    float aspect = canvasAspect();
    vec2 p = toWorld(vUv);
    float t = uFlowTime;

    // --- Pool shape: noisy blob + tendrils that reach toward each person ---------------------
    vec2 c = vec2(0.5 * aspect, 0.5);
    float baseR = mix(0.12, 1.25, uPool);
    float dPool = length((p - c) * vec2(1.0, 1.15)) - baseR + fbm3(vec3(p * 2.5, t * 0.05), 3) * 0.12;
    for (int i = 0; i < MAX_PEOPLE; i++) {
        if (i >= uPeopleCount) break;
        vec2 pp = toWorld(uPeople[i].xy);
        vec2 toP = pp - c;
        float reach = min(length(toP), baseR + 0.35);
        vec2 tip = c + normalize(toP + 1e-5) * reach;
        float wob = 0.035 + 0.015 * sin(t * 2.0 + float(i) * 1.7);
        dPool = smin(dPool, sdSegment(p, c, tip) - wob, 0.12);
    }
    float mask = 1.0 - smoothstep(-0.008, 0.008, dPool);
    float rim = exp(-pow(dPool / 0.012, 2.0));
    if (mask <= 0.0 && rim < 0.01) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // --- Surface ----------------------------------------------------------------------------
    vec2 flow = vec2(snoise(vec3(p * 1.3, t * 0.1)), snoise(vec3(p * 1.3 + 11.0, t * 0.1))) * 0.12;
    vec2 q = p + flow;
    float rate = clamp(uBubbles * (1.0 + uBass * 1.5), 0.0, 1.0);
    float e = 1.5 / uResolution.y;
    vec2 uvE = vec2(e / aspect, e);
    float b0, bx, by;
    float h0 = heightAt(q, vUv, t, rate, b0);
    float hx = heightAt(q + vec2(e, 0.0), vUv + vec2(uvE.x, 0.0), t, rate, bx);
    float hy = heightAt(q + vec2(0.0, e), vUv + vec2(0.0, uvE.y), t, rate, by);
    vec3 n = normalize(vec3((h0 - hx) / e * 0.12, (h0 - hy) / e * 0.12, 1.0));

    vec3 L = normalize(vec3(-0.4, 0.6, 0.7));
    vec3 H = normalize(L + vec3(0.0, 0.0, 1.0));
    float diff = max(dot(n, L), 0.0);
    float spec = pow(max(dot(n, H), 0.0), 80.0);
    float fres = pow(1.0 - n.z, 2.0);

    // Blood: deep, glossy, darker toward the middle.
    float depth = smoothstep(0.0, -0.25, dPool);
    vec3 blood = mix(vec3(0.42, 0.01, 0.025), vec3(0.18, 0.0, 0.01), depth) * (0.5 + 0.65 * diff);
    blood += vec3(1.0, 0.75, 0.75) * spec * 1.3 + vec3(0.5, 0.02, 0.03) * fres;

    // Lava: dark crust with glowing cracks; bubbles are molten.
    vec3 vc = voronoi(q * 5.0, t * 0.6);
    float crack = 1.0 - smoothstep(0.0, 0.08 + uGlow * 0.06, vc.y - vc.x);
    float heat = crack * (0.7 + 0.3 * snoise(vec3(q * 3.0, t * 0.5))) + b0 * 2.0;
    heat *= 0.6 + uGlow + uBass * 0.6;
    vec3 hot = mix(vec3(1.0, 0.25, 0.02), vec3(1.0, 0.85, 0.35), clamp(heat * heat, 0.0, 1.0));
    vec3 crust = vec3(0.07, 0.03, 0.03) * (0.5 + diff) + vec3(0.9, 0.5, 0.3) * spec * 0.4;
    vec3 lava = mix(crust, hot * 1.3, clamp(heat, 0.0, 1.0));

    vec3 col = mix(blood, lava, uMode);
    col *= mask;
    col += mix(vec3(0.8, 0.08, 0.08), vec3(1.0, 0.5, 0.1), uMode) * rim * 0.35;
    col *= 1.0 + uBeat * 0.35;

    vec2 ev = smoothstep(0.0, 0.05, vUv) * smoothstep(0.0, 0.05, 1.0 - vUv);
    fragColor = vec4(col * ev.x * ev.y, 1.0);
}
