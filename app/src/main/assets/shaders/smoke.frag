#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// GROUND SMOKE: shades the simulated smoke density as low-lying fog-machine smoke. Fine
// noise, drifting with the flow, breaks the simulation grid into wisps; the density gradient
// gives soft top-lighting. Moving feet stir coloured dye into it (carried by the flow), and
// each step leaves a glowing footprint that shows through thin smoke.
// Without float render targets it falls back to procedural smoke.

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uDensity;
uniform sampler2D uVelocity;
uniform float uSimulated;
uniform vec2 uSimTexel;
uniform vec3 uTint;
uniform float uBrightness;
uniform float uStir;       // how strongly stirred colour shows
uniform sampler2D uPrints; // footprint glow: R = glow, GBA = colour
uniform float uPrintGlow;

void main() {
    vec2 p = toWorld(vUv);
    float d;
    vec2 vel = vec2(0.0);
    vec3 stir = vec3(0.0);
    if (uSimulated > 0.5) {
        vec4 dye = texture(uDensity, vUv);
        d = dye.x;
        stir = max(dye.yzw, vec3(0.0));
        vel = texture(uVelocity, vUv).xy * uSimTexel;
    } else {
        d = smoothstep(0.1, 0.8, fbm3(vec3(p * 1.8, uTime * 0.05), 4) * 0.5 + 0.55);
    }

    // Wispy detail that streaks along the flow.
    vec2 flowOffset = vel * 2.0;
    float detail = fbm3(vec3(p * 7.0 - flowOffset * 3.0, uTime * 0.12), 4) * 0.5 + 0.5;
    float fine = fbm3(vec3(p * 18.0, uTime * 0.2), 3) * 0.5 + 0.5;
    float dens = clamp(d, 0.0, 1.6) * (0.55 + 0.6 * detail) * (0.85 + 0.3 * fine);
    dens = 1.0 - exp(-dens * 4.0); // optical depth -> coverage

    // Soft top light from the density slope.
    float g = 0.0;
    if (uSimulated > 0.5) {
        float dx = texture(uDensity, vUv + vec2(uSimTexel.x * 2.0, 0.0)).x - texture(uDensity, vUv - vec2(uSimTexel.x * 2.0, 0.0)).x;
        float dy = texture(uDensity, vUv + vec2(0.0, uSimTexel.y * 2.0)).x - texture(uDensity, vUv - vec2(0.0, uSimTexel.y * 2.0)).x;
        g = clamp(-dx * 0.6 + dy * 0.8, -0.5, 0.5);
    }
    // Stirred colour: hue from the dye, strength from how much of this smoke has been stirred.
    float sm = max(max(stir.r, stir.g), stir.b);
    vec3 stirCol = stir / max(sm, 1e-4);
    float stirAmt = clamp(sm / max(d, 1e-3), 0.0, 1.0) * uStir;
    vec3 base = mix(uTint, stirCol * 1.15, stirAmt);
    vec3 col = base * dens * (0.8 + g + 0.25 * detail);
    col *= uBrightness * (1.0 + uBass * 0.4) + uBeat * 0.4;

    vec4 fp = texture(uPrints, vUv);
    col += fp.gba * fp.r * uPrintGlow * (0.5 + 0.5 * (1.0 - dens));

    vec2 e = smoothstep(0.0, 0.04, vUv) * smoothstep(0.0, 0.04, 1.0 - vUv);
    fragColor = vec4(col * e.x * e.y, 1.0);
}
