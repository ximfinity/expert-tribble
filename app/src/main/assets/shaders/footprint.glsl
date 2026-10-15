// Shoe-print shape. q is in foot-local coordinates scaled by the print length:
// +y points to the toe, x across the foot. side = +1 right foot, -1 left (mirrored).
// Returns coverage 0..1; tread gives the sole's ridged texture (0..1).
float footprint(vec2 q, float side, out float tread) {
    q.x *= side;
    // Sole: wider at the ball, curving slightly inward; heel: a smaller oval.
    float sole = length((q - vec2(0.03, 0.17)) / vec2(0.19, 0.31)) - 1.0;
    float heel = length((q - vec2(-0.005, -0.29)) / vec2(0.145, 0.17)) - 1.0;
    // Narrow waist joining them (the arch of a shoe).
    float waist = length((q - vec2(0.02, -0.06)) / vec2(0.11, 0.2)) - 1.0;
    float d = min(min(sole, heel), waist);
    tread = step(0.5, fract(q.y * 13.0 + 0.25 * sin(q.x * 18.0)));
    return 1.0 - smoothstep(-0.12, 0.04, d);
}
