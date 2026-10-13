#version 300 es
// Digital keystone. uHomography maps the unit canvas square to the user's 4 dragged screen
// corners. Writing the projective w into gl_Position.w makes the rasterizer interpolate vUv
// perspective-correctly, so the warp is exact across the whole quad (no tessellation needed).
uniform mat3 uHomography;
out vec2 vUv;

void main() {
    vec2 uv = vec2(float(gl_VertexID & 1), float(gl_VertexID >> 1));
    vec3 p = uHomography * vec3(uv, 1.0);
    vUv = uv;
    gl_Position = vec4(p.xy, 0.0, p.z);
}
