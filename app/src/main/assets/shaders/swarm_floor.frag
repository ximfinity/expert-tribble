#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// Dim, flickering lantern-light on the floor. The spiders are drawn as shadows on top of it,
// which reads far more real on a projector than bright sprites (black = no light = shadow).

in vec2 vUv;
out vec4 fragColor;

uniform float uLight;
uniform vec2 uFlash;        // beam centre, world units
uniform float uFlashRadius; // world units
uniform float uFlashPower;

// Flashlight pool: bright core, the reflector's hot ring, a dim spill halo, and a slight
// stretch away from the canvas centre as if the torch is held at an angle from there.
vec3 flashlight(vec2 p) {
    vec2 c = uFlash;
    vec2 centre = vec2(canvasAspect() * 0.5, 0.5);
    vec2 axis = c - centre;
    float len = length(axis);
    axis = len > 1e-4 ? axis / len : vec2(1.0, 0.0);
    vec2 d = p - c;
    float along = dot(d, axis) / (1.0 + len * 0.6);
    float across = dot(d, vec2(-axis.y, axis.x));
    float r = length(vec2(along, across)) / uFlashRadius;

    float core = pow(smoothstep(1.0, 0.0, r), 1.4);
    float ring = exp(-pow((r - 0.82) / 0.07, 2.0)) * 0.22;
    float spill = smoothstep(1.9, 0.9, r) * 0.1;
    float dirt = 0.9 + 0.1 * fbm2(d * 18.0 / uFlashRadius, 3);   // lens imperfections
    float flicker = 0.97 + 0.03 * snoise(vec2(uTime * 9.0, 7.7));
    float intensity = (core * dirt + ring + spill) * flicker * uFlashPower;
    return vec3(1.0, 0.93, 0.78) * intensity;
}

void main() {
    vec2 p = toWorld(vUv);
    float aspect = canvasAspect();
    float vig = smoothstep(1.15, 0.15, length((vUv - 0.5) * vec2(aspect, 1.0)));
    float grime = fbm2(p * 5.0, 4) * 0.5 + 0.5;
    float flicker = 0.86 + 0.14 * snoise(vec2(uTime * 2.7, 0.3)) + 0.05 * snoise(vec2(uTime * 13.0, 4.1));
    vec3 col = vec3(0.45, 0.40, 0.26) * (0.55 + 0.45 * grime) * vig * flicker * uLight;
    col += vec3(0.7, 0.8, 1.0) * uBeat * 0.45 * uLight;
    col += flashlight(p) * (0.75 + 0.25 * grime);
    fragColor = vec4(col, 1.0);
}
