#version 300 es
precision highp float;
// One Jacobi iteration of the pressure Poisson equation.
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uPressure;
uniform sampler2D uDivergence;
uniform vec2 uTexel;

void main() {
    float l = texture(uPressure, vUv - vec2(uTexel.x, 0.0)).x;
    float r = texture(uPressure, vUv + vec2(uTexel.x, 0.0)).x;
    float b = texture(uPressure, vUv - vec2(0.0, uTexel.y)).x;
    float t = texture(uPressure, vUv + vec2(0.0, uTexel.y)).x;
    float div = texture(uDivergence, vUv).x;
    fragColor = vec4((l + r + b + t - div) * 0.25, 0.0, 0.0, 1.0);
}
