#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uVelocity;
uniform vec2 uTexel;

void main() {
    float l = texture(uVelocity, vUv - vec2(uTexel.x, 0.0)).y;
    float r = texture(uVelocity, vUv + vec2(uTexel.x, 0.0)).y;
    float b = texture(uVelocity, vUv - vec2(0.0, uTexel.y)).x;
    float t = texture(uVelocity, vUv + vec2(0.0, uTexel.y)).x;
    fragColor = vec4(0.5 * ((r - l) - (t - b)), 0.0, 0.0, 1.0);
}
