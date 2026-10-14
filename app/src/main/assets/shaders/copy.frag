#version 300 es
precision mediump float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uSource;

void main() {
    fragColor = vec4(texture(uSource, vUv).rgb, 1.0);
}
