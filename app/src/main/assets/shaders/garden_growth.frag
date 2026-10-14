#version 300 es
precision highp float;
// Garden state. R = how much has been "planted" here (footsteps raise it, it slowly wilts).
// G = how grown the plants actually are; it chases R at a limited speed so growth animates.
in vec2 vUv;
out vec4 fragColor;
#define MAX_STAMPS 16
uniform sampler2D uState;
uniform float uAspect;
uniform vec4 uStamps[MAX_STAMPS];
uniform int uStampCount;
uniform float uWilt;   // amount R drops this step
uniform float uGrow;   // max amount G rises this step
uniform float uShrink; // max amount G falls this step

void main() {
    vec2 s = texture(uState, vUv).rg;
    float planted = max(s.r - uWilt, 0.0);
    vec2 p = vec2(vUv.x * uAspect, vUv.y);
    for (int i = 0; i < MAX_STAMPS; i++) {
        if (i >= uStampCount) break;
        vec2 c = vec2(uStamps[i].x * uAspect, uStamps[i].y);
        vec2 q = p - c;
        float r = uStamps[i].z;
        planted = min(1.0, planted + uStamps[i].w * exp(-dot(q, q) / (r * r)));
    }
    float grown = s.g + clamp(planted - s.g, -uShrink, uGrow);
    fragColor = vec4(planted, grown, 0.0, 1.0);
}
