#version 300 es
precision highp float;
// Adds gaussian splats to a field. In radial mode (velocity) .w of the value is an outward push;
// otherwise all four channels are added (dye: density + RGB colour).
in vec2 vUv;
out vec4 fragColor;
#define MAX_SPLATS 16
uniform sampler2D uTarget;
uniform float uAspect;
uniform vec4 uSplatPos[MAX_SPLATS]; // x, y (uv), radius (world), unused
uniform vec4 uSplatVal[MAX_SPLATS]; // value xyz, radial push w
uniform int uSplatCount;
uniform float uClampMin;
uniform float uRadialMode;

void main() {
    vec4 base = texture(uTarget, vUv);
    vec2 p = vec2(vUv.x * uAspect, vUv.y);
    for (int i = 0; i < MAX_SPLATS; i++) {
        if (i >= uSplatCount) break;
        vec2 c = vec2(uSplatPos[i].x * uAspect, uSplatPos[i].y);
        vec2 d = p - c;
        float r = uSplatPos[i].z;
        float g = exp(-dot(d, d) / (r * r));
        vec2 dir = d / max(length(d), 1e-4);
        if (uRadialMode > 0.5) {
            base.xyz += g * uSplatVal[i].xyz;
            base.xy += g * dir * uSplatVal[i].w;
        } else {
            base += g * uSplatVal[i];
        }
    }
    base = max(base, vec4(uClampMin));
    fragColor = base;
}
