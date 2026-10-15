#version 300 es
precision mediump float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTex;  // premultiplied white text with a dark outline
uniform vec4 uColor;

void main() {
    vec4 t = texture(uTex, vUv);
    fragColor = vec4(t.rgb * uColor.rgb, t.a) * uColor.a;
}
