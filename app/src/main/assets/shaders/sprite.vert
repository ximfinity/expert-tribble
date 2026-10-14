#version 300 es
// Generic instanced sprite (see SpriteBatch.kt).
layout(location = 0) in vec4 aA; // world x, world y, angle, half-size (world)
layout(location = 1) in vec4 aB; // rgba
layout(location = 2) in vec4 aC; // shape parameters

uniform float uAspect;
uniform vec2 uOffset; // world-space offset (used for drop shadows), scaled by sprite size

out vec2 vLocal;
out vec4 vColor;
out vec4 vParams;

void main() {
    vec2 corner = vec2(float(gl_VertexID & 1), float(gl_VertexID >> 1)) * 2.0 - 1.0;
    float c = cos(aA.z);
    float s = sin(aA.z);
    vec2 rotated = vec2(c * corner.x - s * corner.y, s * corner.x + c * corner.y) * aA.w;
    vec2 world = aA.xy + rotated + uOffset * aA.w;
    gl_Position = vec4(vec2(world.x / uAspect, world.y) * 2.0 - 1.0, 0.0, 1.0);
    vLocal = corner;
    vColor = aB;
    vParams = aC;
}
