#version 300 es
// Instanced spider quads. One instance per boid.
layout(location = 0) in vec4 aInst0; // world x, world y, heading (rad), half-size (world)
layout(location = 1) in vec2 aInst1; // gait phase, panic (0..1)

uniform float uAspect;
uniform vec2 uShadowOffset;  // in body-size units
uniform float uShadowPass;

out vec2 vLocal;
out float vGait;
out float vPanic;

void main() {
    vec2 corner = vec2(float(gl_VertexID & 1), float(gl_VertexID >> 1)) * 2.0 - 1.0;
    vec2 local = corner * 1.15;
    float c = cos(aInst0.z);
    float s = sin(aInst0.z);
    vec2 rotated = vec2(c * local.x - s * local.y, s * local.x + c * local.y) * aInst0.w;
    vec2 world = aInst0.xy + rotated + uShadowOffset * aInst0.w * uShadowPass;
    gl_Position = vec4(vec2(world.x / uAspect, world.y) * 2.0 - 1.0, 0.0, 1.0);
    vLocal = local;
    vGait = aInst1.x;
    vPanic = aInst1.y;
}
