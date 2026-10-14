#version 300 es
precision highp float;
// Multiplies a field (used to relax last frame's pressure as the solver's starting guess).
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uSource;
uniform float uScale;

void main() {
    fragColor = texture(uSource, vUv) * uScale;
}
