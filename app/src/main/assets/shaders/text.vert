#version 300 es
// Text quad centred at uCenter (world units) with half-size uHalf.
uniform vec2 uCenter;
uniform vec2 uHalf;
uniform float uAspect;
out vec2 vUv;

void main() {
    vec2 c = vec2(float(gl_VertexID & 1), float(gl_VertexID >> 1)) * 2.0 - 1.0;
    vec2 world = uCenter + c * uHalf;
    vUv = vec2(c.x * 0.5 + 0.5, 0.5 - c.y * 0.5);
    gl_Position = vec4(vec2(world.x / uAspect, world.y) * 2.0 - 1.0, 0.0, 1.0);
}
