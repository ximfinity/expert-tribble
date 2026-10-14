#version 300 es
precision mediump float;
// Falling snow seen from above: small flakes are soft dots, large ones show six-fold crystals.
// p0 = crystal amount (0..1), p1 = blur (0 sharp .. 1 very soft). uShadow = 1 for the shadow pass.
in vec2 vLocal;
in vec4 vColor;
in vec4 vParams;
out vec4 fragColor;
uniform float uShadow;

void main() {
    float r = length(vLocal);
    if (r > 1.0) discard;
    float a = atan(vLocal.y, vLocal.x);
    // Six arms with little side branches.
    float sector = mod(a + 3.14159 / 6.0, 3.14159 / 3.0) - 3.14159 / 6.0;
    vec2 q = vec2(cos(sector), sin(sector)) * r;
    float arm = abs(q.y) - 0.05 * (1.0 - q.x);
    float branch = abs(abs(q.y) - (q.x - 0.45) * 0.6) - 0.03;
    branch = max(branch, abs(q.x - 0.6) - 0.2);
    float crystal = min(arm, branch);
    crystal = max(crystal, r - 0.9);
    float blur = mix(0.03, 0.25, vParams.y);
    float shape = 1.0 - smoothstep(-blur, blur, crystal);
    float dot = exp(-r * r * mix(9.0, 3.0, vParams.y));
    float cov = mix(dot, max(shape, dot * 0.5), vParams.x);
    if (uShadow > 0.5) {
        fragColor = vec4(0.0, 0.0, 0.0, cov * 0.3 * vColor.a);
        return;
    }
    float i = cov * vColor.a;
    fragColor = vec4(vColor.rgb * i, i * 0.6);
}
