#version 300 es
precision highp float;
#include "noise.glsl"

// An orb web being spun (pairs with sprite.vert). Radial spokes go up first, then the sticky
// spiral winds outward as [progress] goes 0 -> 1. Dew drops glint; [tear] rips it apart.
// vColor.rgb = thread light (brighter in the torch beam), vColor.a = opacity.
// vParams: x = progress 0..1, y = seed, z = tear 0..1.

in vec2 vLocal;
in vec4 vColor;
in vec4 vParams;
out vec4 fragColor;

float hash1(float n) { return fract(sin(n * 127.1) * 43758.5453); }

void main() {
    float progress = vParams.x;
    float seed = vParams.y;
    float tear = vParams.z;
    vec2 p = vLocal;
    // Torn webs sag and drift apart.
    p += tear * 0.1 * vec2(snoise(p * 3.0 + seed), snoise(p * 3.0 - seed));
    float r = length(p);
    if (r > 1.0) discard;
    float a = atan(p.y, p.x);
    float lw = fwidth(r) * 0.9;

    // Spokes: 12-17 with jittered angles, revealed one by one and growing outward.
    float n = 12.0 + floor(hash1(seed) * 6.0);
    float slot = a / 6.2831853 * n;
    float k = floor(slot + 0.5);
    float ak = (k + (hash1(k + seed * 7.0) - 0.5) * 0.35) / n * 6.2831853;
    float spokeD = r * abs(sin(a - ak));
    float spokeReveal = clamp(progress / 0.3 * n - mod(k + n, n), 0.0, 1.0);
    float spokeLen = 0.95 * spokeReveal;
    float spokes = (1.0 - smoothstep(0.0, lw, spokeD)) * step(r, spokeLen);

    // Frame thread around the outside.
    float frame = 1.0 - smoothstep(0.0, lw, abs(r - 0.93)) ;
    frame *= step(0.3, progress);

    // Sticky spiral: rings spaced 0.07 apart, wound outward as progress goes 0.3 -> 1.
    // Measured to the straight chord between neighbouring spokes, so each turn of the spiral
    // is a polygon of straight threads, with a slight sag.
    float sector = (floor(slot) + 0.5) / n * 6.2831853;
    float rp = r * cos(a - sector) / cos(3.14159265 / n);
    rp *= 1.0 + 0.04 * sin(fract(slot) * 3.14159265);
    float spacing = 0.11;
    float s = (rp - 0.12) / spacing - a / 6.2831853;
    float ringD = abs(fract(s + 0.5) - 0.5) * spacing;
    float spiralReach = 0.12 + 0.8 * clamp((progress - 0.3) / 0.7, 0.0, 1.0);
    float spiral = (1.0 - smoothstep(0.0, lw * 0.8, ringD)) * step(0.12, r) * step(r, spiralReach);

    // Hub: a dense little tangle in the middle.
    float hub = (1.0 - smoothstep(0.03, 0.06, r)) * 0.6 * step(0.05, progress);

    float thread = max(max(spokes, frame * 0.8), max(spiral * 0.85, hub));
    // Dew drops strung along the spiral catch the light.
    float drop = step(0.85, hash12(floor(vec2(s * 6.0, a * 9.0)))) * spiral;
    // Tearing: threads snap in patches.
    float keep = tear > 0.0 ? smoothstep(tear * 1.2 - 0.1, tear * 1.2 + 0.05, hash12(floor(p * 7.0 + seed))) : 1.0;
    thread *= keep;
    float alpha = thread * vColor.a * (1.0 - smoothstep(0.85, 1.0, r) * 0.3);
    vec3 col = vColor.rgb * (thread * 0.45 + drop * keep * 1.2) * vColor.a;
    fragColor = vec4(col, alpha * 0.15);
}
