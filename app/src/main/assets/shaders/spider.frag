#version 300 es
precision highp float;

// Procedural top-down spider, built from signed distance fields: abdomen + cephalothorax,
// 8 two-segment legs walking in an alternating tetrapod gait, pedipalps, and glowing eyes.

in vec2 vLocal;
in float vGait;
in float vPanic;
out vec4 fragColor;

uniform float uStyle;       // 0 = shadow silhouette, 1 = spectral glow
uniform float uShadowPass;  // 1 while drawing the soft drop shadow

float sdSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

float sdEllipse(vec2 p, vec2 r) {
    return (length(p / r) - 1.0) * min(r.x, r.y);
}

float sminf(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

vec2 dir(float a) { return vec2(cos(a), sin(a)); }

float spider(vec2 p, float gait, out float eyes) {
    float abdomen = sdEllipse(p - vec2(-0.32, 0.0), vec2(0.30, 0.21));
    float head = sdEllipse(p - vec2(0.09, 0.0), vec2(0.17, 0.13));
    float d = sminf(abdomen, head, 0.06);

    float legs = 1e5;
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
            vec2 foot = kneeP + dir(a + knee) * (0.48 - lift * 0.08);
            legs = min(legs, sdSeg(p, root, kneeP) - 0.032);
            legs = min(legs, sdSeg(p, kneeP, foot) - 0.020);
        }
    }
    legs = min(legs, sdSeg(p, vec2(0.22, 0.04), vec2(0.34, 0.075)) - 0.022);
    legs = min(legs, sdSeg(p, vec2(0.22, -0.04), vec2(0.34, -0.075)) - 0.022);
    d = min(d, legs);

    float e1 = length(p - vec2(0.205, 0.035));
    float e2 = length(p - vec2(0.205, -0.035));
    float e = min(e1, e2);
    eyes = exp(-e * e / 0.0007);
    return d;
}

void main() {
    float eyes;
    float d = spider(vLocal, vGait, eyes);

    if (uShadowPass > 0.5) {
        float a = (1.0 - smoothstep(-0.05, 0.14, d)) * 0.45 * (1.0 - uStyle);
        fragColor = vec4(0.0, 0.0, 0.0, a);
        return;
    }

    float aa = fwidth(d) * 1.2;
    float cov = 1.0 - smoothstep(-aa, aa, d);
    vec3 eyeCol = mix(vec3(0.9, 0.04, 0.02), vec3(1.0, 0.45, 0.08), vPanic);
    float glow = eyes * (0.55 + 0.45 * vPanic);

    // Premultiplied output: alpha darkens the floor, rgb adds light.
    if (uStyle < 0.5) {
        fragColor = vec4(eyeCol * glow * 1.6, cov * 0.97);
    } else {
        float rim = exp(-abs(d) * 30.0);
        vec3 ghost = vec3(0.55, 1.0, 0.75);
        fragColor = vec4(ghost * (cov * 0.22 + rim * 0.8) + eyeCol * glow * 2.0, cov * 0.2);
    }
}
