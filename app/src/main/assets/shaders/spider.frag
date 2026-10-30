#version 300 es
precision highp float;
#include "noise.glsl"

// Procedural top-down spider, built from signed distance fields: abdomen + cephalothorax,
// 8 legs walking in an alternating tetrapod gait, pedipalps, and eyes.
// Options: glowing eyes, radioactive glow, a realistic mode (3-segment hairy legs, shaded and
// patterned body lit by the flashlight), and soft or 3D cast shadows.

in vec2 vLocal;
in float vGait;
in float vPanic;
in float vHeight;
in float vSeed;
in vec2 vWorld;
in vec2 vLightDir;
in vec2 vShadowDir;
in float vStretch;
in float vBlur;
out vec4 fragColor;

uniform float uStyle;       // 0 = shadow silhouette, 1 = spectral glow
uniform float uShadowPass;  // 1 while drawing the drop shadow
uniform float uGlowPass;    // 1 while drawing the radioactive halo
uniform float uEyeGlow;     // 0..1
uniform float uRadioactive; // 0 / 1
uniform float uRealism;     // 0 / 1
uniform float uLantern;     // ambient floor light 0..1
uniform vec2 uLightPos;     // flashlight, world units
uniform float uLightRadius;
uniform float uLightPower;
uniform float uTime;

float sdSeg(vec2 p, vec2 a, vec2 b, out float h) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

float sdEllipse(vec2 p, vec2 r) {
    return (length(p / r) - 1.0) * min(r.x, r.y);
}

vec2 dir(float a) { return vec2(cos(a), sin(a)); }

const vec2 ABDOMEN_C = vec2(-0.32, 0.0);
const vec2 ABDOMEN_R = vec2(0.30, 0.21);
const vec2 HEAD_C = vec2(0.09, 0.0);
const vec2 HEAD_R = vec2(0.17, 0.13);

// Returns distance; body = distance to the body alone; legBand = position along legs (0..3).
float spider(vec2 p, float gait, float realism, out float body, out float legBand, out float eyes) {
    float abdomen = sdEllipse(p - ABDOMEN_C, ABDOMEN_R);
    float head = sdEllipse(p - HEAD_C, HEAD_R);
    body = smin(abdomen, head, 0.06);
    float d = body;

    float legs = 1e5;
    legBand = 0.0;
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? 1.0 : -1.0;
        for (int k = 0; k < 4; k++) {
            float fk = float(k);
            // Alternating tetrapod: legs L1 R2 L3 R4 swing together, then the other four.
            float phase = gait + mod(fk + float(s), 2.0) * 3.14159265;
            float swing = sin(phase) * 0.22;
            float lift = max(0.0, cos(phase));
            float a = side * (mix(0.75, 2.45, fk / 3.0) + swing);
            float knee = side * mix(-0.55, 0.55, fk / 3.0);
            vec2 root = vec2(0.14 - fk * 0.05, side * 0.06);
            vec2 kneeP = root + dir(a) * 0.40;
            float h;
            if (realism > 0.5) {
                // Femur, tibia and tarsus, tapering, with knobbly joints.
                vec2 ankle = kneeP + dir(a + knee * 0.8) * (0.33 - lift * 0.05);
                vec2 foot = ankle + dir(a + knee * 1.4) * 0.2;
                float l1 = sdSeg(p, root, kneeP, h) - mix(0.036, 0.03, h);
                float b1 = h;
                float l2 = sdSeg(p, kneeP, ankle, h) - mix(0.028, 0.02, h);
                float b2 = 1.0 + h;
                float l3 = sdSeg(p, ankle, foot, h) - mix(0.018, 0.01, h);
                float b3 = 2.0 + h;
                float j = min(length(p - kneeP) - 0.04, length(p - ankle) - 0.03);
                float leg = min(min(l1, l2), min(l3, j));
                if (leg < legs) {
                    legs = leg;
                    legBand = l1 < l2 && l1 < l3 ? b1 : (l2 < l3 ? b2 : b3);
                }
            } else {
                vec2 foot = kneeP + dir(a + knee) * (0.48 - lift * 0.08);
                legs = min(legs, sdSeg(p, root, kneeP, h) - 0.032);
                legs = min(legs, sdSeg(p, kneeP, foot, h) - 0.020);
            }
        }
    }
    float h;
    legs = min(legs, sdSeg(p, vec2(0.22, 0.04), vec2(0.34, 0.075), h) - 0.022);
    legs = min(legs, sdSeg(p, vec2(0.22, -0.04), vec2(0.34, -0.075), h) - 0.022);
    // Fine bristles on realistic legs.
    if (realism > 0.5) legs -= 0.009 * smoothstep(0.02, -0.01, legs) * (0.5 + 0.5 * snoise(p * 90.0));
    d = min(d, legs);

    // Eyes: a big front pair and a smaller pair beside them (eight in realism, as dots).
    float e = min(length(p - vec2(0.205, 0.035)), length(p - vec2(0.205, -0.035)));
    float e2 = min(length(p - vec2(0.17, 0.07)), length(p - vec2(0.17, -0.07)));
    eyes = exp(-e * e / 0.0007) + exp(-e2 * e2 / 0.0003) * 0.6;
    return d;
}

// Torch light reaching this point on the floor (same falloff as swarm_floor.frag, simplified).
float torchAt(vec2 w) {
    float r = length(w - uLightPos) / max(uLightRadius, 1e-3);
    return pow(smoothstep(1.0, 0.0, r), 1.4) * uLightPower;
}

void main() {
    float body, band, eyes;

    if (uShadowPass > 0.5) {
        // Undo the stretch along the shadow direction so the silhouette elongates.
        vec2 q = vLocal;
        float along = dot(q, vShadowDir);
        q -= vShadowDir * along * (1.0 - 1.0 / vStretch);
        float d = spider(q, vGait, 0.0, body, band, eyes);
        float a = (1.0 - smoothstep(-vBlur * 0.5, vBlur, d)) * 0.5 * (1.0 - uStyle) / (1.0 + vHeight * 1.5);
        fragColor = vec4(0.0, 0.0, 0.0, a);
        return;
    }

    float pulse = 0.65 + 0.35 * sin(uTime * 3.0 + vSeed * 6.28);
    vec3 toxic = vec3(0.35, 1.0, 0.25);

    if (uGlowPass > 0.5) {
        // Radioactive halo: green light spilling onto the floor around each spider.
        float d = sdEllipse(vLocal - vec2(-0.12, 0.0), vec2(0.5, 0.35));
        float g = exp(-max(d, 0.0) * 4.0) * 0.35 * pulse;
        fragColor = vec4(toxic * g, 0.0);
        return;
    }

    float d = spider(vLocal, vGait, uRealism, body, band, eyes);
    float aa = fwidth(d) * 1.2;
    float cov = 1.0 - smoothstep(-aa, aa, d);
    vec3 eyeCol = uRadioactive > 0.5 ? vec3(0.5, 1.0, 0.3) : mix(vec3(0.9, 0.04, 0.02), vec3(1.0, 0.45, 0.08), vPanic);
    float glowAmt = 0.25 + 1.6 * uEyeGlow;
    float glow = eyes * (0.55 + 0.45 * vPanic) * glowAmt;
    // A soft eyeshine halo grows with the glow setting.
    vec2 ec = vLocal - vec2(0.2, 0.0);
    float halo = exp(-dot(ec, ec) / (0.002 + 0.02 * uEyeGlow)) * uEyeGlow * (0.4 + 0.6 * vPanic);

    vec3 emissive = eyeCol * (glow * 1.6 + halo * 0.6);

    // Radioactive markings: a glowing hourglass/stripe pattern and veins that pulse.
    if (uRadioactive > 0.5) {
        vec2 ap = (vLocal - ABDOMEN_C) / ABDOMEN_R;
        float hour = smoothstep(0.35, 0.2, abs(ap.y) + abs(ap.x) * 0.25 - 0.15 * abs(sin(ap.x * 3.0)));
        float veins = smoothstep(0.08, 0.0, abs(snoise(ap * 3.0 + vSeed)));
        float inside = smoothstep(0.01, -0.02, body);
        float rim = exp(-abs(d) * 40.0);
        emissive += toxic * (inside * (hour * 0.9 + veins * 0.5) + rim * 0.35) * pulse;
    }

    if (uStyle > 0.5) {
        // Spectral: translucent ghost spider.
        float rim = exp(-abs(d) * 30.0);
        vec3 ghost = uRadioactive > 0.5 ? toxic : vec3(0.55, 1.0, 0.75);
        fragColor = vec4(ghost * (cov * 0.22 + rim * 0.8) + emissive, cov * 0.2);
        return;
    }

    vec3 col = vec3(0.0);
    if (uRealism > 0.5) {
        // Lit by the lantern and the torch: a rounded, patterned body with a glossy highlight.
        float light = uLantern * 0.35 + torchAt(vWorld) * 1.1;
        vec3 L = normalize(vec3(vLightDir, 0.9));
        vec3 albedo = vec3(0.19, 0.13, 0.08);
        float isBody = smoothstep(0.01, -0.01, body);
        vec3 n = vec3(0.0, 0.0, 1.0);
        if (vLocal.x < -0.05) {
            vec2 q = (vLocal - ABDOMEN_C) / ABDOMEN_R;
            n = normalize(vec3(q * 0.9, sqrt(max(0.05, 1.0 - dot(q, q)))));
            // Wolf-spider chevrons and speckles on the abdomen.
            float chev = smoothstep(0.55, 0.45, abs(fract(q.x * 2.2 + abs(q.y) * 1.3) - 0.5) * 2.0) * smoothstep(0.75, 0.3, abs(q.y));
            albedo = mix(albedo, vec3(0.42, 0.33, 0.22), chev * 0.7);
        } else {
            vec2 q = (vLocal - HEAD_C) / HEAD_R;
            n = normalize(vec3(q * 0.7, sqrt(max(0.05, 1.0 - dot(q, q)))));
            // Radial grooves on the carapace and a pale midline.
            float groove = 0.5 + 0.5 * cos(atan(q.y, q.x) * 8.0);
            albedo *= 0.8 + 0.3 * groove;
            albedo = mix(albedo, vec3(0.45, 0.36, 0.24), smoothstep(0.18, 0.05, abs(q.y)) * 0.6);
        }
        albedo *= 0.85 + 0.3 * (fbm2(vLocal * 22.0 + vSeed * 9.0, 3) * 0.5 + 0.5);
        // Banded legs: darker joints, lighter segment middles.
        vec3 legCol = mix(vec3(0.12, 0.08, 0.05), vec3(0.32, 0.24, 0.16), smoothstep(0.15, 0.5, abs(fract(band) - 0.5) * 2.0 * -1.0 + 1.0));
        vec3 base = mix(legCol, albedo, isBody);
        float lambert = max(dot(n, L), 0.0);
        float spec = pow(max(dot(reflect(-L, n), vec3(0.0, 0.0, 1.0)), 0.0), 24.0) * isBody;
        col = base * (0.25 + 0.9 * lambert) * light + vec3(1.0, 0.95, 0.85) * spec * light * 0.6;
    }

    // Premultiplied output: alpha darkens the floor, rgb adds light.
    fragColor = vec4(col * cov + emissive, cov * 0.97);
}
