#version 300 es
precision highp float;
// Subtract the pressure gradient: leaves a divergence-free (incompressible) velocity field.
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uPressure;
uniform sampler2D uVelocity;
uniform vec2 uTexel;

void main() {
    float l = texture(uPressure, vUv - vec2(uTexel.x, 0.0)).x;
    float r = texture(uPressure, vUv + vec2(uTexel.x, 0.0)).x;
    float b = texture(uPressure, vUv - vec2(0.0, uTexel.y)).x;
    float t = texture(uPressure, vUv + vec2(0.0, uTexel.y)).x;
    vec2 vel = texture(uVelocity, vUv).xy - vec2(r - l, t - b);
    fragColor = vec4(vel, 0.0, 1.0);
}
