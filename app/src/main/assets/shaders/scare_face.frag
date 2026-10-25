#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"
#include "scare.glsl"

// JUMP SCARE: the floor is black until someone walks on. After a moment of silence a ghoul (or a
// skull) lunges up out of the floor right in front of them, screaming, with a flash; then it burns
// away to nothing. The face is drawn upright for the person it targets ([uFace].w points its top
// away from them).

in vec2 vUv;
out vec4 fragColor;

uniform vec4 uFace;      // world x, y, size, angle (radians; face +y direction)
uniform vec4 uFaceAnim;  // age (s), kind (0 ghoul, 1 skull), scream 0..1, fade 0..1
uniform float uActive;   // 0 = nothing on the floor
uniform float uFlash;    // full-screen flash 0..1
uniform float uRedPulse; // lingering red vignette 0..1

vec3 ghoul(vec2 q, float t, float scream, out float alpha) {
    // Skull-like cranium blended into a long, hanging jaw.
    float jawDrop = 0.12 * scream;
    float head = smin(sdEll(q - vec2(0.0, 0.14), vec2(0.6, 0.7)), sdEll(q - vec2(0.0, -0.46 - jawDrop * 0.5), vec2(0.38, 0.5 + jawDrop)), 0.18);
    head += 0.025 * snoise(q * 7.0 + t * 0.7);
    alpha = cover(head, 0.012);

    float depth = clamp(-head * 2.4, 0.0, 1.0);
    vec3 skin = mix(vec3(0.16, 0.2, 0.17), vec3(0.66, 0.7, 0.6), sqrt(depth));
    skin *= 0.72 + 0.4 * (fbm2(q * 5.0 + 11.0, 4) * 0.5 + 0.5);
    float vein = 1.0 - smoothstep(0.0, 0.035, abs(snoise(q * vec2(5.0, 9.0) + 3.0)));
    skin = mix(skin, vec3(0.32, 0.05, 0.12), vein * 0.5 * depth);

    // Cheek hollows and brow shadow.
    skin *= 1.0 - 0.45 * cover(sdEll(vec2(abs(q.x) - 0.33, q.y + 0.22), vec2(0.13, 0.2)), 0.12);
    skin *= 1.0 - 0.35 * cover(sdEll(q - vec2(0.0, 0.38), vec2(0.5, 0.08)), 0.1);

    // Sunken eye sockets with pin-prick glowing pupils.
    vec2 e = vec2(abs(q.x) - 0.24, q.y - 0.17);
    e = rotate2(e, 0.3);
    float sock = sdEll(e, vec2(0.19, 0.14 + 0.05 * scream));
    skin *= mix(1.0, 0.02, 1.0 - smoothstep(-0.1, 0.05, sock));
    vec2 pq = vec2(abs(q.x) - 0.24, q.y - 0.15);
    float pupil = exp(-dot(pq, pq) * 1400.0);
    skin += vec3(1.0, 0.85, 0.6) * pupil * 2.5 + vec3(1.0, 0.1, 0.05) * exp(-dot(pq, pq) * 220.0) * 0.6;

    // Nose slits.
    float nose = min(sdEll(vec2(abs(q.x) - 0.05, q.y + 0.06), vec2(0.025, 0.07)), 1.0);
    skin *= 1.0 - 0.9 * cover(nose, 0.01);

    // Screaming mouth: a tall dark oval, a red throat, and rows of jagged yellow teeth.
    vec2 mc = vec2(0.0, -0.45 - jawDrop * 0.4);
    vec2 ms = vec2(0.17 + 0.07 * scream, 0.08 + 0.3 * scream);
    vec2 m = (q - mc) / ms;
    float mouth = length(m) - 1.0;
    if (mouth < 0.0) {
        float throat = 1.0 - length(m);
        vec3 inside = mix(vec3(0.0), vec3(0.35, 0.02, 0.03), smoothstep(0.0, 0.8, throat) * 0.6);
        float upper = step(0.62 - 0.32 * tri(m.x * 3.5 + 0.5), m.y);
        float lower = step(m.y, -0.66 + 0.28 * tri(m.x * 3.5));
        vec3 tooth = vec3(0.78, 0.72, 0.5) * (0.7 + 0.3 * tri(m.x * 3.5));
        skin = mix(inside, tooth, max(upper, lower));
    }
    skin *= 1.0 - 0.8 * (1.0 - smoothstep(0.0, 0.12, abs(mouth))) * step(0.0, mouth); // dark lips

    // Lank, wet hair hanging down over the forehead (dark strands on pale skin).
    float hairZone = smoothstep(0.28, 0.6, q.y + 0.12 * snoise(vec2(q.x * 4.0, 1.3)));
    float strands = smoothstep(0.35, 0.9, abs(sin(q.x * 30.0 + snoise(q * vec2(2.5, 0.7)) * 3.0)));
    skin *= 1.0 - 0.8 * hairZone * strands;
    return skin;
}

vec3 skull(vec2 q, float t, float scream, out float alpha) {
    float jaw = 0.16 * scream;
    float cranium = sdEll(q - vec2(0.0, 0.2), vec2(0.6, 0.64));
    float cheek = sdEll(q - vec2(0.0, -0.16), vec2(0.48, 0.32));
    // The lower jaw hangs open on its hinges as it screams.
    float mandible = sdEll(q - vec2(0.0, -0.56 - jaw), vec2(0.32, 0.17));
    float rami = min(sdSegment(q, vec2(-0.38, -0.2), vec2(-0.28, -0.52 - jaw)), sdSegment(q, vec2(0.38, -0.2), vec2(0.28, -0.52 - jaw))) - 0.055;
    float d = min(smin(cranium, cheek, 0.12), smin(mandible, rami, 0.08));
    alpha = cover(d, 0.012);
    float depth = clamp(-d * 2.6, 0.0, 1.0);
    vec3 bone = mix(vec3(0.3, 0.28, 0.22), vec3(0.9, 0.86, 0.74), sqrt(depth));
    bone *= 0.72 + 0.35 * (fbm2(q * 6.0 + 4.0, 4) * 0.5 + 0.5);
    // Grime in the hollows and hairline cracks across the crown.
    bone *= 1.0 - 0.5 * cover(sdEll(vec2(abs(q.x) - 0.36, q.y + 0.2), vec2(0.12, 0.16)), 0.1);
    float crack = 1.0 - smoothstep(0.0, 0.018, abs(snoise(q * vec2(4.0, 2.0) + 7.0)));
    bone *= 1.0 - 0.7 * crack * smoothstep(0.15, 0.5, q.y);
    // Deep black sockets, each with a tiny burning pupil far inside.
    vec2 e = vec2(abs(q.x) - 0.24, q.y - 0.07);
    float sock = sdEll(rotate2(e, 0.2), vec2(0.19, 0.17));
    bone *= 1.0 - (1.0 - smoothstep(-0.06, 0.02, sock));
    vec2 pq = e - vec2(0.0, -0.02);
    bone += vec3(1.0, 0.25, 0.08) * exp(-dot(pq, pq) * 900.0) * 2.2 * (0.8 + 0.2 * sin(t * 23.0));
    bone += vec3(0.6, 0.0, 0.0) * exp(-dot(pq, pq) * 120.0) * 0.5;
    // Nasal cavity.
    vec2 n = q - vec2(0.0, -0.17);
    float nose = max(abs(n.x) * 1.7 + n.y * 0.55 - 0.07, -n.y - 0.09);
    bone *= 1.0 - cover(nose, 0.01);
    // Crooked, uneven teeth above and below a black gap that opens as it screams.
    float col = floor(q.x * 8.0);
    float h1 = 0.1 + 0.05 * fract(sin(col * 12.9) * 437.5);
    float h2 = 0.09 + 0.05 * fract(sin(col * 7.3) * 917.1);
    float top = -0.36, bot = -0.38 - jaw;
    float seam = smoothstep(0.0, 0.1, tri(q.x * 8.0 + 0.5));
    if (abs(q.x) < 0.27) {
        if (q.y < top && q.y > bot) bone = vec3(0.0);
        float upper = step(top, q.y) * step(q.y, top + h1);
        float lower = step(bot - h2, q.y) * step(q.y, bot) * step(abs(q.x), 0.24);
        bone = mix(bone, vec3(0.82, 0.76, 0.58) * (0.4 + 0.6 * seam), max(upper, lower));
    }
    return bone;
}

void main() {
    vec2 w = toWorld(vUv);
    vec3 col = vec3(0.0);

    if (uActive > 0.5) {
        vec2 q = toLocal(w, uFace.xy, uFace.w, uFace.z);
        if (dot(q, q) < 2.5) {
            float a;
            vec3 face = uFaceAnim.y < 0.5 ? ghoul(q, uTime, uFaceAnim.z, a) : skull(q, uTime, uFaceAnim.z, a);
            vec2 burn = dissolve(q, uFaceAnim.w, 3.7);
            col = face * a * burn.x + vec3(1.0, 0.35, 0.05) * burn.y * a * 2.0;
            // A dim halo of red light under the face so it reads as rising out of the floor.
            col += vec3(0.25, 0.0, 0.02) * exp(-dot(q, q) * 1.2) * (1.0 - uFaceAnim.w) * (1.0 - a);
        }
    }

    // Lingering red vignette after the scare, then the flash on top.
    vec2 v = vUv - 0.5;
    col += vec3(0.35, 0.0, 0.02) * uRedPulse * smoothstep(0.2, 0.75, length(v));
    col = mix(col, vec3(1.0, 0.92, 0.9), uFlash);
    fragColor = vec4(col, 1.0);
}
