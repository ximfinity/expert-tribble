#version 300 es
precision highp float;
// Semi-Lagrangian advection: trace each cell back along the velocity field.
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uVelocity;
uniform sampler2D uSource;
uniform vec2 uTexel;
uniform float uDt;
uniform float uDissipation; // fraction kept per second

void main() {
    vec2 vel = texture(uVelocity, vUv).xy;
    vec2 coord = vUv - uDt * vel * uTexel;
    fragColor = texture(uSource, coord) * pow(uDissipation, uDt);
}
