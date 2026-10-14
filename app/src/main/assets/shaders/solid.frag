#version 300 es
precision mediump float;
// Constant colour, used with blend modes to fade or tint a target.
out vec4 fragColor;
uniform vec4 uColor;

void main() {
    fragColor = uColor;
}
