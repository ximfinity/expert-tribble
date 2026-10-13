#version 300 es
precision mediump float;

in vec2 vLocal;
out vec4 fragColor;

uniform vec4 uColor; // straight alpha
uniform float uRing; // 0 = filled disc, otherwise ring thickness (fraction of radius)

void main() {
    float d = length(vLocal);
    float aa = fwidth(d) * 1.5;
    float a = 1.0 - smoothstep(1.0 - aa, 1.0, d);
    if (uRing > 0.0) a *= smoothstep(1.0 - uRing - aa, 1.0 - uRing, d);
    a *= uColor.a;
    fragColor = vec4(uColor.rgb * a, a); // premultiplied
}
