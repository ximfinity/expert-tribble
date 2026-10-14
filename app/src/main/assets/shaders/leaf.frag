#version 300 es
precision highp float;
// Autumn leaf seen from above. p0 = shape (0 maple, 1 oak-ish oval), p1 = curl shading,
// p2 = shadow pass (1 = draw soft dark shadow only).
in vec2 vLocal;
in vec4 vColor;
in vec4 vParams;
out vec4 fragColor;

float sdSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    vec2 p = vLocal; // +y = tip, stem at -y
    float d;
    if (vParams.x < 0.5) {
        // Maple: five pointed lobes from a polar outline.
        vec2 q = p - vec2(0.0, -0.05);
        float a = atan(q.x, q.y);
        float lobes = pow(abs(cos(a * 2.5)), 0.6);
        float rad = 0.42 + 0.38 * lobes - 0.12 * smoothstep(2.2, 3.1, abs(a));
        d = length(q) - rad;
    } else {
        // Oval leaf with wavy (oak-like) edge.
        vec2 q = p / vec2(0.48, 0.85);
        float wave = 0.06 * sin(atan(q.y, q.x) * 9.0);
        d = (length(q) - 1.0 - wave) * 0.45;
    }
    float stem = sdSeg(p, vec2(0.0, -0.95), vec2(0.0, -0.1)) - 0.03;
    d = min(d, stem);

    if (vParams.z > 0.5) {
        float a = (1.0 - smoothstep(-0.05, 0.2, d)) * 0.5 * vColor.a;
        fragColor = vec4(0.0, 0.0, 0.0, a);
        return;
    }

    float aa = fwidth(d) * 1.2;
    float cov = 1.0 - smoothstep(-aa, aa, d);
    // Veins: midrib plus fanned side veins.
    float vein = sdSeg(p, vec2(0.0, -0.1), vec2(0.0, 0.75));
    for (int i = 0; i < 4; i++) {
        float s = float(i) - 1.5;
        vein = min(vein, sdSeg(p, vec2(0.0, -0.05 + float(i) * 0.12), vec2(s * 0.35 + sign(s) * 0.15, 0.25 + float(i) * 0.12)));
    }
    float veins = 1.0 - smoothstep(0.0, 0.035, vein);
    float edgeDark = smoothstep(-0.25, 0.0, d);
    float curl = 0.85 + 0.3 * p.x * vParams.y;
    vec3 col = vColor.rgb * curl * (1.0 - 0.35 * edgeDark) * (1.0 - 0.3 * veins);
    fragColor = vec4(col * cov, cov) * vColor.a;
}
