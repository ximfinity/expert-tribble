#version 300 es
precision highp float;
#include "noise.glsl"

// SHADOW PUPPETS (pairs with sprite.vert): side-view silhouettes cast across candle-lit floor.
// Local frame: x across (the figure faces +x), y from the feet (-1) to the top of the quad (+1).
// vColor: r = walk phase, g = reach 0..1 (arms out toward a person), b = facing (0 = left,
//         1 = right), a = opacity.
// vParams: x = type (0 witch, 1 ghost, 2 zombie, 3 clawed hand, 4 witch on a broom),
//          y = seed, z = stretch (shadow elongation), w = edge softness.

in vec2 vLocal;
in vec4 vColor;
in vec4 vParams;
out vec4 fragColor;

float sdEll(vec2 p, vec2 r) { return (length(p / r) - 1.0) * min(r.x, r.y); }
float seg(vec2 p, vec2 a, vec2 b, float r) { return sdSegment(p, a, b) - r; }
float tri(vec2 p, vec2 a, vec2 b, vec2 c) {
    // Signed distance to a triangle (exact, after Inigo Quilez).
    vec2 e0 = b - a, e1 = c - b, e2 = a - c;
    vec2 v0 = p - a, v1 = p - b, v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                     vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                     vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}

vec2 polar(float a, float r) { return vec2(cos(a), sin(a)) * r; }

float witch(vec2 p, float ph, float reach, float seed) {
    float bob = 0.02 * abs(sin(ph));
    p.y -= bob;
    // Ragged cloak: a hunched triangle whose hem flutters.
    float hem = -0.78 + 0.05 * sin(p.x * 18.0 + ph * 2.0 + seed) + 0.04 * snoise(vec2(p.x * 9.0, ph));
    float cloak = tri(p, vec2(-0.05, 0.42), vec2(-0.42 - 0.05 * sin(ph), -0.8), vec2(0.3, -0.8));
    cloak = max(cloak, hem - p.y);
    // Hunched back and head with a long hooked nose and pointed chin.
    float back = sdEll(p - vec2(-0.08, 0.3), vec2(0.2, 0.22));
    float head = sdEll(p - vec2(0.08, 0.46), vec2(0.11, 0.12));
    float nose = tri(p, vec2(0.15, 0.5), vec2(0.15, 0.43), vec2(0.33, 0.4));
    float chin = tri(p, vec2(0.1, 0.38), vec2(0.17, 0.4), vec2(0.21, 0.29));
    // The hat: a wide brim and a tall cone with a bent tip.
    float brim = sdEll(p - vec2(0.05, 0.56), vec2(0.26, 0.05));
    float cone = tri(p, vec2(-0.1, 0.57), vec2(0.18, 0.57), vec2(-0.08, 0.92));
    float tip = seg(p, vec2(-0.08, 0.9), vec2(-0.22, 0.86 - 0.03 * sin(ph * 0.5)), 0.025);
    // A bony arm reaching forward with a gnarled staff (or grasping fingers when reaching).
    vec2 sh = vec2(0.05, 0.25);
    vec2 el = sh + polar(-0.5 + reach * 0.6, 0.22);
    vec2 wr = el + polar(-0.2 + reach * 0.7, 0.2);
    float arm = min(seg(p, sh, el, 0.035), seg(p, el, wr, 0.028));
    float fingers = 1e5;
    for (int i = 0; i < 4; i++) {
        float a = -0.6 + float(i) * 0.3 + reach * 0.5 + 0.15 * sin(ph * 3.0 + float(i));
        fingers = min(fingers, seg(p, wr, wr + polar(a, 0.12), 0.012));
    }
    float staff = seg(p, wr + vec2(0.0, 0.25), wr + vec2(0.03, -0.75), 0.02) + reach * 0.5;
    staff = min(staff, seg(p, wr + vec2(0.0, 0.25), wr + vec2(-0.06, 0.32), 0.02) + reach * 0.5);
    // Legs scissoring under the cloak.
    float l1 = seg(p, vec2(-0.05, -0.7), vec2(-0.05 + 0.12 * sin(ph), -0.97), 0.03);
    float l2 = seg(p, vec2(0.05, -0.7), vec2(0.05 - 0.12 * sin(ph), -0.97), 0.03);
    float d = min(min(cloak, back), min(head, min(nose, chin)));
    d = min(d, min(min(brim, cone), tip));
    d = min(d, min(min(arm, fingers), staff));
    return min(d, min(l1, l2));
}

float ghost(vec2 p, float ph, float reach, float seed, out float holes) {
    p.y -= 0.06 * sin(ph * 0.5);
    // Drifting forward, so the sheet leans into its motion.
    p = rotate2(p, 0.12);
    // A tall sheet: domed top, sides flaring gently toward a rippling, ragged hem.
    float y = clamp((0.4 - p.y) / 1.2, 0.0, 1.0);
    float halfW = 0.3 + 0.14 * y + 0.03 * sin(p.y * 9.0 + ph);
    float sides = abs(p.x + 0.08 * y * y) - halfW;
    float top = sdEll(p - vec2(0.0, 0.4), vec2(0.3, 0.32));
    float body = min(max(sides, p.y - 0.4), top);
    float hem = -0.78 + 0.08 * sin(p.x * 13.0 - ph * 3.0 + seed) + 0.05 * sin(p.x * 29.0 + ph * 5.0);
    body = max(body, hem - p.y);
    // Both sleeves lifted toward whoever it's drifting at, drapery hanging below them.
    float d = body;
    for (int k = 0; k < 2; k++) {
        float side = k == 0 ? 1.0 : 0.55;
        vec2 sh = vec2(0.12 * side, 0.18 - 0.04 * float(k));
        float lift = 0.15 + reach * 0.55 + 0.12 * sin(ph * 1.5 + float(k));
        vec2 hand = sh + polar(lift, (0.48 + reach * 0.15) * side + 0.05);
        float sleeve = sdSegment(p, sh, hand) - mix(0.11, 0.05, clamp(length(p - sh) / 0.5, 0.0, 1.0));
        float drape = tri(p, sh + vec2(0.0, -0.05), hand, sh + vec2(0.1, -0.35 + 0.05 * sin(ph * 2.0)));
        d = smin(d, min(sleeve, drape + 0.01), 0.06);
    }
    // A wispy tail trailing behind.
    d = min(d, sdEll(p - vec2(-0.42, -0.62 + 0.05 * sin(ph)), vec2(0.22, 0.07)));
    // Eye and mouth holes let the candlelight through.
    float eyes = min(sdEll(p - vec2(0.05, 0.45), vec2(0.06, 0.09)), sdEll(p - vec2(0.2, 0.45), vec2(0.055, 0.085)));
    float mouth = sdEll(p - vec2(0.13, 0.22), vec2(0.06, 0.09 + 0.04 * sin(ph * 2.0)));
    holes = min(eyes, mouth);
    return max(d, -holes);
}

float zombie(vec2 p, float ph, float reach, float seed) {
    // A lurching shamble: the body pitches on the good leg and drags the other.
    float lurch = sin(ph);
    p = rotate2(p - vec2(0.0, -0.95), 0.1 + 0.05 * lurch) + vec2(0.0, -0.95);
    // Hunched torso in a ragged shirt.
    float torso = smin(sdEll(p - vec2(0.0, 0.08), vec2(0.17, 0.3)), sdEll(p - vec2(0.05, 0.3), vec2(0.2, 0.13)), 0.08);
    float rag = -0.24 + 0.06 * sin(p.x * 28.0 + seed) + 0.04 * snoise(vec2(p.x * 12.0, seed));
    torso = max(torso, rag - p.y);
    // Head lolling forward and to one side, with a tuft of hair.
    vec2 hc = vec2(0.2, 0.5 - 0.03 * lurch);
    float head = sdEll(rotate2(p - hc, -0.4), vec2(0.13, 0.11));
    float neck = seg(p, vec2(0.08, 0.36), hc, 0.05);
    float hair = tri(p, hc + vec2(-0.1, 0.06), hc + vec2(-0.02, 0.14), hc + vec2(0.05, 0.08));
    // Both arms stretched straight out in front; they rise as it reaches for someone.
    float armY = 0.3 + reach * 0.06;
    vec2 h1 = vec2(0.62 + reach * 0.08, armY + 0.03 * lurch);
    vec2 h2 = vec2(0.56 + reach * 0.08, armY - 0.07 - 0.03 * lurch);
    float a1 = seg(p, vec2(0.12, armY), h1, 0.055);
    float a2 = seg(p, vec2(0.06, armY - 0.07), h2, 0.05);
    float fingers = 1e5;
    for (int i = 0; i < 3; i++) {
        fingers = min(fingers, seg(p, h1, h1 + polar(-0.5 - float(i) * 0.3 + 0.2 * reach, 0.12), 0.018));
        fingers = min(fingers, seg(p, h2, h2 + polar(-0.6 - float(i) * 0.3 + 0.2 * reach, 0.11), 0.016));
    }
    // Legs: one planted with a bent knee, one dragging behind on its toes.
    float hip = -0.2;
    vec2 k1 = vec2(0.1 + 0.06 * lurch, -0.56);
    vec2 k2 = vec2(-0.14, -0.58);
    float l1 = min(seg(p, vec2(0.02, hip), k1, 0.08), seg(p, k1, vec2(0.02 + 0.08 * lurch, -0.93), 0.065));
    float l2 = min(seg(p, vec2(-0.04, hip), k2, 0.08), seg(p, k2, vec2(-0.3 - 0.04 * lurch, -0.92), 0.06));
    float feet = min(sdEll(p - vec2(0.08 + 0.08 * lurch, -0.95), vec2(0.1, 0.04)), sdEll(p - vec2(-0.32, -0.94), vec2(0.07, 0.035)));
    float d = min(torso, min(head, neck));
    d = min(d, hair);
    d = min(d, min(min(a1, a2), fingers));
    return min(d, min(min(l1, l2), feet));
}

float clawedHand(vec2 p, float ph, float reach, float seed) {
    // A long forearm rising from the bottom of the quad into a big bony hand with talons.
    float sway = 0.08 * sin(ph * 0.7 + seed);
    vec2 wrist = vec2(sway, -0.4);
    float arm = sdSegment(p, vec2(0.0, -1.3), wrist) - mix(0.07, 0.1, smoothstep(-1.3, -0.4, p.y));
    float palm = sdEll(p - (wrist + vec2(0.0, 0.18)), vec2(0.2, 0.22));
    float d = smin(arm, palm, 0.08);
    // Four long, knuckled fingers spread wide that slowly flex, curling as they reach.
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 base = wrist + vec2(-0.15 + fi * 0.1, 0.34 - abs(fi - 1.5) * 0.04);
        float flex = 0.2 + 0.18 * sin(ph * 1.3 + fi * 0.7 + seed) + reach * 0.45;
        float a0 = 1.57 + (fi - 1.5) * 0.32;
        float len = fi == 1.0 || fi == 2.0 ? 1.0 : 0.85;
        vec2 k1 = base + polar(a0, 0.3 * len);
        // Every joint bends forward (toward +x), so the hand hooks like it's about to grab.
        vec2 k2 = k1 + polar(a0 - flex * 0.6, 0.26 * len);
        vec2 k3 = k2 + polar(a0 - flex * 1.3, 0.18 * len);
        vec2 tip = k3 + polar(a0 - flex * 2.0, 0.14);
        float f = min(seg(p, base, k1, 0.045), seg(p, k1, k2, 0.038));
        f = min(f, seg(p, k2, k3, 0.03));
        // Claw: tapers to a point.
        f = min(f, sdSegment(p, k3, tip) - 0.026 * (1.0 - clamp(dot(p - k3, tip - k3) / dot(tip - k3, tip - k3), 0.0, 1.0)));
        f = min(f, min(length(p - k1) - 0.055, length(p - k2) - 0.045));
        d = min(d, f);
    }
    // Thumb.
    vec2 tb = wrist + vec2(0.18, 0.12);
    vec2 tk = tb + polar(0.5 - reach * 0.4, 0.2);
    d = min(d, min(seg(p, tb, tk, 0.045), sdSegment(p, tk, tk + polar(1.2 - reach * 0.6, 0.18)) - 0.03 * (1.0 - clamp(length(p - tk) / 0.18, 0.0, 1.0))));
    return d;
}

float broomWitch(vec2 p, float ph, float reach, float seed) {
    // Riding side-saddle on a broom, bobbing through the air; cloak streaming behind.
    p.y -= 0.08 * sin(ph * 0.6);
    float stick = seg(p, vec2(-0.75, -0.25), vec2(0.8, -0.05), 0.025);
    float bristles = tri(p, vec2(-0.7, -0.24), vec2(-1.05, -0.05 + 0.05 * sin(ph * 4.0)), vec2(-1.05, -0.45));
    float body = sdEll(p - vec2(0.0, 0.1), vec2(0.16, 0.24));
    float cape = tri(p, vec2(-0.05, 0.3), vec2(-0.75, 0.05 + 0.1 * sin(ph * 2.0 + seed)), vec2(-0.1, -0.15));
    float head = sdEll(p - vec2(0.08, 0.42), vec2(0.1, 0.11));
    float nose = tri(p, vec2(0.15, 0.45), vec2(0.15, 0.39), vec2(0.3, 0.36));
    float brim = sdEll(p - vec2(0.04, 0.52), vec2(0.23, 0.045));
    float cone = tri(p, vec2(-0.1, 0.53), vec2(0.15, 0.53), vec2(-0.2, 0.85));
    float leg = seg(p, vec2(0.0, -0.08), vec2(0.18, -0.35), 0.04);
    float arm = seg(p, vec2(0.05, 0.15), vec2(0.28, -0.08), 0.03);
    float d = min(min(stick, bristles), min(body, cape));
    return min(d, min(min(head, nose), min(min(brim, cone), min(leg, arm))));
}

void main() {
    float stretch = vParams.z;
    // Elongate along the shadow: the figure fills the quad's height but only 1/stretch of its width.
    vec2 p = vec2(vLocal.x * stretch, vLocal.y);
    float facing = vColor.b > 0.5 ? 1.0 : -1.0;
    p.x *= facing;
    float ph = vColor.r;
    float reach = vColor.g;
    float seed = vParams.y;
    int type = int(vParams.x + 0.5);
    float holes = 1.0;
    float d;
    if (type == 0) d = witch(p, ph, reach, seed);
    else if (type == 1) d = ghost(p, ph, reach, seed, holes);
    else if (type == 2) d = zombie(p, ph, reach, seed);
    else if (type == 3) d = clawedHand(p, ph, reach, seed);
    else d = broomWitch(p, ph, reach, seed);
    // Soft penumbra; shadows get softer toward their far end, as real ones do.
    float soft = vParams.w * (1.0 + 0.8 * (vLocal.y * 0.5 + 0.5));
    float a = 1.0 - smoothstep(-soft, soft, d);
    fragColor = vec4(0.0, 0.0, 0.0, a * vColor.a * 0.92);
}
