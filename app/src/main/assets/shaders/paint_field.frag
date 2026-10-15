#version 300 es
precision highp float;
// Coloring-game canvas: RGB = paint colour, A = coverage. Brushes are round stamps with a soft
// edge; a stamp with B.w < 0 erases.
in vec2 vUv;
out vec4 fragColor;
#define MAX_STAMPS 16
uniform sampler2D uState;
uniform float uAspect;
uniform vec4 uStamps[MAX_STAMPS];  // x, y (uv), radius (world), strength
uniform vec4 uStampsB[MAX_STAMPS]; // rgb colour, w < 0 = eraser
uniform int uStampCount;

void main() {
    vec4 s = texture(uState, vUv);
    vec2 p = vec2(vUv.x * uAspect, vUv.y);
    for (int i = 0; i < MAX_STAMPS; i++) {
        if (i >= uStampCount) break;
        float d = distance(p, vec2(uStamps[i].x * uAspect, uStamps[i].y));
        float r = uStamps[i].z;
        float g = (1.0 - smoothstep(r * 0.7, r, d)) * uStamps[i].w;
        if (uStampsB[i].w < 0.0) {
            s.a *= 1.0 - g;
        } else {
            s.rgb = mix(s.rgb, uStampsB[i].rgb, g);
            s.a = max(s.a, g);
        }
    }
    fragColor = s;
}
