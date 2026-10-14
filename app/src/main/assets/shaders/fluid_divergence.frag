#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uVelocity;
uniform vec2 uTexel;

void main() {
    vec2 c = texture(uVelocity, vUv).xy;
    float l = texture(uVelocity, vUv - vec2(uTexel.x, 0.0)).x;
    float r = texture(uVelocity, vUv + vec2(uTexel.x, 0.0)).x;
    float b = texture(uVelocity, vUv - vec2(0.0, uTexel.y)).y;
    float t = texture(uVelocity, vUv + vec2(0.0, uTexel.y)).y;
    // Solid walls: mirror the normal velocity at the borders.
    if (vUv.x - uTexel.x < 0.0) l = -c.x;
    if (vUv.x + uTexel.x > 1.0) r = -c.x;
    if (vUv.y - uTexel.y < 0.0) b = -c.y;
    if (vUv.y + uTexel.y > 1.0) t = -c.y;
    fragColor = vec4(0.5 * (r - l + t - b), 0.0, 0.0, 1.0);
}
