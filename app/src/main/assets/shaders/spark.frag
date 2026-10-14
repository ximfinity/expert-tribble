#version 300 es
precision mediump float;
// Glowing point light: hot core + soft halo. Additive (premultiplied) output.
// p0 = core sharpness (0 soft .. 1 pinpoint), p1 = twinkle phase (< 0 disables), p2 = halo strength.
in vec2 vLocal;
in vec4 vColor;
in vec4 vParams;
out vec4 fragColor;
uniform float uTime;

void main() {
    float r = length(vLocal);
    if (r > 1.0) discard;
    float sharp = mix(6.0, 60.0, vParams.x);
    float core = exp(-r * r * sharp);
    float halo = (1.0 - smoothstep(0.0, 1.0, r)) * (1.0 - smoothstep(0.0, 1.0, r)) * vParams.z;
    float tw = vParams.y < 0.0 ? 1.0 : 0.55 + 0.45 * sin(uTime * 23.0 + vParams.y * 6.283);
    float i = (core + halo) * vColor.a * tw;
    vec3 col = mix(vColor.rgb, vec3(1.0), core * 0.6);
    fragColor = vec4(col * i, 0.0);
}
