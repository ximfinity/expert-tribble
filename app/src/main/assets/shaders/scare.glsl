// Shared helpers for the Scary scenes. Everything outside the creatures stays pure black, so on a
// projector the floor looks empty until something appears.

float sdEll(vec2 p, vec2 r) { return (length(p / r) - 1.0) * min(r.x, r.y); }

// Coverage of a signed distance with a fixed soft edge (scene shaders work in world units).
float cover(float d, float w) { return 1.0 - smoothstep(-w, w, d); }

// Triangle wave in [0, 1]: rows of pointed teeth along an edge.
float tri(float x) { return abs(fract(x) - 0.5) * 2.0; }

// World position of this pixel, with the face/hand frame's +y pointing along [angle].
vec2 toLocal(vec2 w, vec2 centre, float angle, float size) {
    vec2 d = w - centre;
    float c = cos(-angle), s = sin(-angle);
    return vec2(c * d.x - s * d.y, s * d.x + c * d.y) / size;
}

// Burns the creature away: returns coverage multiplier and a glowing ember edge.
vec2 dissolve(vec2 q, float fade, float seed) {
    if (fade <= 0.0) return vec2(1.0, 0.0);
    float n = fbm2(q * 3.0 + seed, 4) * 0.5 + 0.5;
    float t = fade * 1.25 - 0.1;
    float keep = smoothstep(t, t + 0.06, n);
    float edge = smoothstep(t - 0.05, t, n) * (1.0 - keep);
    return vec2(keep, edge);
}
