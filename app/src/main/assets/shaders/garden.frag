#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// SPRING GARDEN: grass and flowers grow where people have walked. Flowers sit on a jittered
// grid; each one reads the growth field at its root and goes sprout -> leaves -> bud -> bloom,
// swaying in a light breeze. Different cells get different species and colours.

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uGrowth;
uniform float uDensity;
uniform float uFlowerSize;
uniform float uGrass;

vec3 petalColor(float h) {
    float k = floor(h * 6.0);
    if (k < 1.0) return vec3(1.0, 0.45, 0.7);   // pink
    if (k < 2.0) return vec3(1.0, 0.85, 0.2);   // yellow
    if (k < 3.0) return vec3(0.65, 0.4, 1.0);   // violet
    if (k < 4.0) return vec3(1.0, 0.25, 0.2);   // red
    if (k < 5.0) return vec3(0.95, 0.95, 1.0);  // white
    return vec3(0.35, 0.55, 1.0);               // blue
}

void main() {
    float aspect = canvasAspect();
    vec2 p = toWorld(vUv);
    float grown = texture(uGrowth, vUv).g;

    // Soil
    vec3 col = vec3(0.07, 0.05, 0.03) * (0.6 + 0.8 * (fbm2(p * 8.0, 4) * 0.5 + 0.5));

    // Grass: short directional blades that thicken with growth and ripple in the breeze.
    float breeze = sin(uTime * 1.2 + p.x * 3.0 + p.y * 2.0) * 0.004;
    float blades = fbm2(vec2(p.x * 160.0 + breeze * 400.0, p.y * 55.0), 3) * 0.5 + 0.5;
    float grass = smoothstep(0.45, 0.75, blades) * smoothstep(0.04, 0.45, grown) * uGrass;
    col = mix(col, vec3(0.12, 0.45, 0.1) * (0.6 + 0.6 * blades), grass);

    // Flowers
    float scale = 9.0;
    vec2 q = p * scale;
    vec2 cell = floor(q);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 id = cell + vec2(float(i), float(j));
            float h = hash12(id);
            if (h > uDensity) continue;
            vec2 root = (id + 0.2 + 0.6 * hash22(id)) / scale;
            vec2 ruv = vec2(root.x / aspect, root.y);
            if (ruv.x < 0.0 || ruv.x > 1.0 || ruv.y < 0.0 || ruv.y > 1.0) continue;
            float g = texture(uGrowth, ruv).g;
            float delay = hash12(id + 17.0) * 0.3;
            float t = clamp((g - delay) / (1.0 - delay), 0.0, 1.0);
            if (t <= 0.02) continue;

            float size = uFlowerSize * (0.045 + 0.02 * hash12(id + 3.0));
            vec2 sway = vec2(sin(uTime * 1.3 + h * 20.0), cos(uTime * 1.1 + h * 13.0)) * 0.004 * t;
            vec2 head = root + sway;

            // Leaves appear first.
            float leafGrow = smoothstep(0.03, 0.3, t) * 0.75;
            for (int k = 0; k < 2; k++) {
                float a = h * 6.28 + float(k) * 3.14159;
                vec2 dir = vec2(cos(a), sin(a));
                vec2 lq = p - root - dir * size * 0.9 * leafGrow;
                vec2 ll = vec2(dot(lq, dir), dot(lq, vec2(-dir.y, dir.x))) / (vec2(0.9, 0.35) * size * leafGrow + 1e-4);
                float leaf = 1.0 - smoothstep(0.8, 1.0, length(ll));
                col = mix(col, vec3(0.15, 0.55, 0.12) * (0.7 + 0.3 * ll.x), leaf * leafGrow);
            }

            // Then the bloom opens.
            float bloom = smoothstep(0.3, 0.8, t);
            vec2 d = p - head;
            float r = length(d);
            float ang = atan(d.y, d.x) + h * 6.28 + uTime * 0.05;
            float petals = 5.0 + floor(hash12(id + 9.0) * 4.0);
            float shape = 0.55 + 0.45 * pow(abs(cos(ang * petals * 0.5)), 0.7);
            float R = size * bloom * shape;
            float bud = size * 0.25 * smoothstep(0.2, 0.45, t);
            float petal = 1.0 - smoothstep(R * 0.85, R, r);
            vec3 pc = petalColor(hash12(id + 5.0));
            col = mix(col, pc * (0.65 + 0.35 * (1.0 - r / max(R, 1e-4))), petal * step(1e-3, R));
            float centre = 1.0 - smoothstep(max(bud, size * 0.22 * bloom) * 0.8, max(bud, size * 0.22 * bloom), r);
            col = mix(col, mix(vec3(0.2, 0.5, 0.1), vec3(1.0, 0.75, 0.15), bloom), centre);
        }
    }

    col *= 0.9 + uBeat * 0.2 + uLevel * 0.15;
    vec2 e = smoothstep(0.0, 0.03, vUv) * smoothstep(0.0, 0.03, 1.0 - vUv);
    fragColor = vec4(col * e.x * e.y, 1.0);
}
