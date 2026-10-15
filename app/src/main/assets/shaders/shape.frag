#version 300 es
precision highp float;
#include "noise.glsl"

// GAME SHAPES (pairs with sprite.vert). vParams.x selects the shape; .y/.z/.w are per-shape
// parameters. Output is premultiplied: alpha covers, rgb adds light.
in vec2 vLocal;
in vec4 vColor;
in vec4 vParams;
out vec4 fragColor;
uniform float uTime;

// Anti-aliasing width in local units. Computed once in main() from vLocal, outside any branch:
// derivatives inside non-uniform control flow are undefined and some GPUs return garbage.
float AA = 0.01;

float fillD(float d) {
    return 1.0 - smoothstep(-AA, AA, d);
}

float sdBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float sdEll(vec2 p, vec2 r) { return (length(p / r) - 1.0) * min(r.x, r.y); }

float spiderD(vec2 p, float gait) {
    float d = smin(sdEll(p - vec2(-0.32, 0.0), vec2(0.3, 0.21)), sdEll(p - vec2(0.09, 0.0), vec2(0.17, 0.13)), 0.06);
    for (int s = 0; s < 2; s++) {
        float side = s == 0 ? 1.0 : -1.0;
        for (int k = 0; k < 4; k++) {
            float fk = float(k);
            float ph = gait + mod(fk + float(s), 2.0) * 3.14159;
            float a = side * (mix(0.75, 2.45, fk / 3.0) + sin(ph) * 0.22);
            vec2 root = vec2(0.14 - fk * 0.05, side * 0.06);
            vec2 knee = root + vec2(cos(a), sin(a)) * 0.4;
            float b = a + side * mix(-0.55, 0.55, fk / 3.0);
            vec2 foot = knee + vec2(cos(b), sin(b)) * 0.45;
            d = min(d, sdSegment(p, root, knee) - 0.03);
            d = min(d, sdSegment(p, knee, foot) - 0.02);
        }
    }
    return d;
}

void main() {
    AA = max(length(fwidth(vLocal)) * 0.75, 1e-4);
    vec2 p = vLocal;
    float r = length(p);
    int type = int(vParams.x + 0.5);
    vec3 col = vColor.rgb;
    float a = 0.0;

    if (type == 0) {            // disc with soft inner shading
        a = fillD(r - 0.96);
        col *= 0.8 + 0.25 * (1.0 - r);
    } else if (type == 1) {     // ring; p1 = thickness (fraction of radius)
        float t = vParams.y;
        a = fillD(abs(r - (0.96 - t * 0.5)) - t * 0.5);
    } else if (type == 2) {     // rounded square pad; p1 = corner radius, p2 = glow
        a = fillD(sdBox(p, vec2(0.96), vParams.y));
        float edge = max(abs(p.x), abs(p.y));
        col *= 0.7 + 0.35 * (1.0 - edge) + vParams.z * 0.6;
    } else if (type == 3) {     // X mark
        float d = min(sdSegment(p, vec2(-0.7), vec2(0.7)), sdSegment(p, vec2(-0.7, 0.7), vec2(0.7, -0.7))) - 0.17;
        a = fillD(d);
    } else if (type == 4) {     // progress ring; p1 = fill 0..1 (clockwise from the top)
        float ang = fract(0.25 - atan(p.y, p.x) / 6.2831853);
        a = fillD(abs(r - 0.85) - 0.11) * step(ang, vParams.y);
    } else if (type == 5) {     // five-point star
        float an = atan(p.y, p.x) - 1.5708;
        float rs = mix(0.42, 1.0, pow(0.5 + 0.5 * cos(5.0 * an), 3.0));
        a = fillD(r - rs * 0.95);
    } else if (type == 6) {     // bomb; p1 = urgency 0..1
        vec2 q = p - vec2(0.0, -0.12);
        float d = min(length(q) - 0.78, sdBox(p - vec2(0.0, 0.72), vec2(0.2, 0.12), 0.04));
        a = fillD(d);
        float hl = exp(-dot(q - vec2(-0.3, 0.3), q - vec2(-0.3, 0.3)) * 9.0);
        col = vec3(0.16, 0.16, 0.2) + vec3(0.5) * hl;
        float pulse = vParams.y * (0.5 + 0.5 * sin(uTime * (6.0 + 14.0 * vParams.y)));
        col = mix(col, vec3(0.9, 0.1, 0.05), pulse * 0.6);
        float spark = exp(-dot(p - vec2(0.0, 0.92), p - vec2(0.0, 0.92)) * 60.0) * (0.7 + 0.3 * sin(uTime * 40.0));
        col += vec3(1.0, 0.7, 0.2) * spark * 2.0;
        a = max(a, spark);
    } else if (type == 7) {     // mole in its hole; p1 = how far up (0..1), p2 = stunned
        float hole = sdEll(p - vec2(0.0, -0.45), vec2(0.85, 0.32));
        float up = vParams.y;
        vec2 c = vec2(0.0, mix(-0.75, -0.05, up));
        float body = (up > 0.02) ? sdEll(p - c, vec2(0.55, 0.62) * (0.4 + 0.6 * up)) : 1.0;
        body = max(body, p.y - (-0.45 + 1.4 * up)); // hidden below the hole rim
        a = max(fillD(hole), fillD(body));
        col = mix(vec3(0.12, 0.07, 0.04), vec3(0.06, 0.03, 0.02), fillD(hole + 0.1));
        if (body < 0.0) {
            col = vec3(0.55, 0.36, 0.2) * (0.8 + 0.3 * (p.y - c.y));
            vec2 e = vec2(0.18, 0.18) * (0.4 + 0.6 * up);
            float eyes = min(length(p - c - vec2(-e.x, e.y)), length(p - c - vec2(e.x, e.y)));
            col = mix(col, vParams.z > 0.5 ? vec3(1.0) : vec3(0.02), 1.0 - smoothstep(0.05, 0.08, eyes));
            col = mix(col, vec3(1.0, 0.5, 0.6), 1.0 - smoothstep(0.07, 0.1, length(p - c - vec2(0.0, 0.0))));
        }
        if (vParams.z > 0.5) { // dizzy stars circling
            for (int i = 0; i < 3; i++) {
                float an = uTime * 4.0 + float(i) * 2.094;
                vec2 sp = c + vec2(cos(an) * 0.45, 0.55 + sin(an) * 0.12);
                float st = exp(-dot(p - sp, p - sp) * 120.0);
                col = mix(col, vec3(1.0, 0.9, 0.2), st);
                a = max(a, st);
            }
        }
    } else if (type == 8) {     // balloon with highlight, knot and string
        vec2 q = p - vec2(0.0, 0.12);
        float body = sdEll(q, vec2(0.68, 0.82));
        float knot = sdBox(p - vec2(0.0, -0.74), vec2(0.07, 0.05), 0.02);
        float string = abs(p.x - 0.06 * sin(p.y * 9.0 + uTime * 3.0)) - 0.015;
        string = max(string, max(p.y + 1.0, -0.76 - p.y));
        a = max(fillD(min(body, knot)), fillD(string) * 0.8);
        float hl = exp(-dot(q - vec2(-0.25, 0.35), q - vec2(-0.25, 0.35)) * 12.0);
        col = col * (0.75 + 0.35 * (1.0 - length(q))) + vec3(1.0) * hl * 0.7;
        if (string < 0.0 && body > 0.0) col = vec3(0.85);
    } else if (type == 9) {     // goo splat; p1 = seed
        float an = atan(p.y, p.x);
        float s = vParams.y * 13.0;
        float rr = 0.55 + 0.12 * sin(an * 5.0 + s) + 0.08 * sin(an * 9.0 + s * 2.3);
        float d = r - rr;
        for (int i = 0; i < 5; i++) {
            float fi = float(i);
            vec2 c = vec2(cos(fi * 1.3 + s), sin(fi * 1.3 + s)) * (0.75 + 0.1 * sin(fi + s));
            d = min(d, length(p - c) - 0.09);
        }
        a = fillD(d);
        col *= 0.75 + 0.35 * (1.0 - r);
    } else if (type == 10) {    // spider in the instance colour, with glowing red eyes; p1 = gait
        float d = spiderD(p, vParams.y);
        a = fillD(d);
        col = vColor.rgb * (0.55 + 0.45 * smoothstep(0.0, -0.08, d));
        float e = min(length(p - vec2(0.205, 0.035)), length(p - vec2(0.205, -0.035)));
        col += vec3(1.0, 0.1, 0.05) * exp(-e * e / 0.0008);
    } else if (type == 11) {    // soft glow (additive)
        float g = exp(-r * r * 4.0) * (1.0 - smoothstep(0.8, 1.0, r));
        fragColor = vec4(col * g * vColor.a, 0.0);
        return;
    } else if (type == 12) {    // lava tile; p1 = how molten (0 safe .. 1 lava), p2 = warning flash
        a = fillD(sdBox(p, vec2(0.94), 0.12));
        vec3 safe = vec3(0.25, 0.3, 0.38) * (0.85 + 0.15 * (1.0 - max(abs(p.x), abs(p.y))));
        float n = fbm3(vec3(p * 1.5 + vParams.w * 3.1, uTime * 0.5), 3) * 0.5 + 0.5;
        vec3 lava = mix(vec3(0.7, 0.08, 0.0), vec3(1.0, 0.75, 0.15), smoothstep(0.35, 0.8, n));
        col = mix(safe, lava * 1.2, vParams.y);
        float warn = vParams.z * (0.5 + 0.5 * sin(uTime * 14.0));
        col = mix(col, vec3(1.0, 0.45, 0.1), warn * 0.5);
    }

    a *= vColor.a;
    fragColor = vec4(col * a, a);
}
