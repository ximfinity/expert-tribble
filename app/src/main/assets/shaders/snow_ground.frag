#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// WINTER floor: lit snow cover with footprints and glitter. Christmas adds twinkling string
// lights around the edge; Hanukkah adds blue-and-white lights and a menorah lit for the night.

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uCover;
uniform vec2 uCoverTexel;
uniform float uTheme;   // 0 snow, 1 christmas, 2 hanukkah
uniform float uLights;  // light brightness
uniform float uNight;   // hanukkah night 1..8

vec3 bulbColor(float idx, float theme) {
    float k = mod(idx, 5.0);
    if (theme > 1.5) return mod(idx, 2.0) < 0.5 ? vec3(0.25, 0.45, 1.0) : vec3(0.85, 0.9, 1.0);
    if (k < 0.5) return vec3(1.0, 0.12, 0.08);
    if (k < 1.5) return vec3(0.1, 0.95, 0.25);
    if (k < 2.5) return vec3(1.0, 0.7, 0.15);
    if (k < 3.5) return vec3(0.2, 0.4, 1.0);
    return vec3(1.0, 0.95, 0.85);
}

// String lights along the border. Returns light in rgb, wire darkness in a.
vec4 borderLights(vec2 p, float aspect, float theme) {
    const float inset = 0.035;
    const float spacing = 0.07;
    vec3 light = vec3(0.0);
    float wire = 0.0;
    for (int e = 0; e < 4; e++) {
        float along, perp, len;
        if (e == 0) { along = p.x; perp = p.y - inset; len = aspect; }
        else if (e == 1) { along = p.x; perp = (1.0 - inset) - p.y; len = aspect; }
        else if (e == 2) { along = p.y; perp = p.x - inset; len = 1.0; }
        else { along = p.y; perp = (aspect - inset) - p.x; len = 1.0; }
        // The wire scallops inward between bulbs.
        float sag = 0.012 * abs(sin(along / spacing * 3.14159));
        wire = max(wire, exp(-pow((perp - sag) / 0.0025, 2.0)) * step(inset, along) * step(along, len - inset));
        float idx = floor(along / spacing + 0.5);
        float a = clamp(idx * spacing, inset, len - inset);
        vec2 bulb = e == 0 ? vec2(a, inset) : e == 1 ? vec2(a, 1.0 - inset) : e == 2 ? vec2(inset, a) : vec2(aspect - inset, a);
        float d = length(p - bulb);
        float h = hash12(vec2(idx, float(e)));
        float twinkle = 0.55 + 0.45 * sin(uTime * (1.2 + h * 2.0) + h * 30.0);
        vec3 c = bulbColor(idx + float(e) * 3.0, theme);
        light += c * (exp(-d * d / 0.00004) * 2.0 + exp(-d * d / 0.0012) * 0.35) * twinkle;
    }
    return vec4(light, wire);
}

// Nine-branched menorah near the top edge; the shamash (centre) is always lit.
vec3 menorah(vec2 p, float aspect, float night, inout float shade) {
    vec2 c = vec2(aspect * 0.5, 0.79);
    vec3 gold = vec3(1.0, 0.75, 0.3);
    vec3 col = vec3(0.0);
    float body = 1e5;
    body = min(body, sdSegment(p, c + vec2(-0.29, 0.0), c + vec2(0.29, 0.0)));
    body = min(body, sdSegment(p, c, c + vec2(0.0, -0.11)));
    body = min(body, sdSegment(p, c + vec2(-0.06, -0.11), c + vec2(0.06, -0.11)));
    float rank[9] = float[9](7.0, 6.0, 5.0, 4.0, -1.0, 3.0, 2.0, 1.0, 0.0);
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        bool shamash = i == 4;
        vec2 base = c + vec2((fi - 4.0) * 0.072, 0.0);
        float top = shamash ? 0.075 : 0.045;
        body = min(body, sdSegment(p, base, base + vec2(0.0, 0.012)));
        // Candle
        float candle = sdSegment(p, base + vec2(0.0, 0.012), base + vec2(0.0, top));
        col += vec3(0.75, 0.82, 1.0) * (1.0 - smoothstep(0.004, 0.007, candle)) * 0.55;
        bool lit = shamash || rank[i] < night;
        if (lit) {
            vec2 tip = base + vec2(0.0, top + 0.018);
            float flick = 1.0 + 0.15 * snoise(vec2(uTime * 6.0, fi * 3.1));
            vec2 q = (p - tip) / vec2(0.008, 0.016 * flick);
            q.y += 0.3 * q.x * q.x;
            float flame = 1.0 - smoothstep(0.6, 1.0, length(q));
            col += mix(vec3(1.0, 0.45, 0.05), vec3(1.0, 0.95, 0.7), flame) * flame * 1.6;
            float d = length(p - tip);
            col += vec3(1.0, 0.7, 0.3) * exp(-d * d / 0.0025) * 0.35 * flick;
        }
    }
    float bodyMask = 1.0 - smoothstep(0.004, 0.007, body);
    col += gold * bodyMask * 0.8;
    shade = max(shade, bodyMask * 0.6);
    return col;
}

void main() {
    float aspect = canvasAspect();
    vec2 p = toWorld(vUv);
    float d = texture(uCover, vUv).r;
    vec2 o = uCoverTexel * 1.5;
    float dx = texture(uCover, vUv + vec2(o.x, 0.0)).r - texture(uCover, vUv - vec2(o.x, 0.0)).r;
    float dy = texture(uCover, vUv + vec2(0.0, o.y)).r - texture(uCover, vUv - vec2(0.0, o.y)).r;
    vec3 n = normalize(vec3(-dx * 6.0, -dy * 6.0, 1.0));
    float diff = clamp(dot(n, normalize(vec3(-0.5, 0.6, 0.65))), 0.0, 1.0);

    float drift = fbm2(p * 3.0, 4) * 0.5 + 0.5;
    vec3 snowCol = mix(vec3(0.55, 0.62, 0.78), vec3(0.95, 0.97, 1.0), diff) * (0.85 + 0.15 * drift);
    if (uTheme > 1.5) snowCol *= vec3(0.88, 0.94, 1.08);
    vec3 ground = vec3(0.04, 0.045, 0.06) * (0.7 + 0.6 * fbm2(p * 12.0, 3));
    float cover = smoothstep(0.04, 0.4, d);
    vec3 col = mix(ground, snowCol * (0.45 + 0.4 * d), cover);

    // Glitter: a few ice crystals catch the light and twinkle.
    vec2 g = floor(p * 220.0);
    float h = hash12(g);
    float glint = step(0.986, h) * pow(0.5 + 0.5 * sin(uTime * 2.5 + h * 60.0), 16.0) * cover;
    vec3 glitter = uTheme > 1.5 ? vec3(0.7, 0.85, 1.0) : uTheme > 0.5 ? bulbColor(floor(h * 50.0), uTheme) : vec3(1.0);
    col += glitter * glint * 0.9;

    if (uTheme > 0.5) {
        vec4 bl = borderLights(p, aspect, uTheme);
        col *= 1.0 - bl.a * 0.7;
        col += bl.rgb * uLights;
        // Lights reflect softly in nearby snow.
        col += bl.rgb * uLights * 0.15 * cover;
    }
    if (uTheme > 1.5) {
        float shade = 0.0;
        vec3 m = menorah(p, aspect, uNight, shade);
        col = col * (1.0 - shade) + m * uLights;
    }

    col *= 0.85 + uBeat * 0.3;
    vec2 e = smoothstep(0.0, 0.02, vUv) * smoothstep(0.0, 0.02, 1.0 - vUv);
    fragColor = vec4(col * e.x * e.y, 1.0);
}
