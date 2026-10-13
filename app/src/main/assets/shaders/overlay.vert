#version 300 es
// Screen-aligned quad around uCenter (NDC) with half-extent uRadius (NDC).
uniform vec2 uCenter;
uniform vec2 uRadius;
out vec2 vLocal;

void main() {
    vec2 corner = vec2(float(gl_VertexID & 1), float(gl_VertexID >> 1)) * 2.0 - 1.0;
    vLocal = corner;
    gl_Position = vec4(uCenter + corner * uRadius, 0.0, 1.0);
}
