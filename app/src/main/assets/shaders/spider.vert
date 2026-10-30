#version 300 es
// Instanced spider quads. One instance per boid (plus the giant jump-scare spider).
layout(location = 0) in vec4 aInst0; // world x, world y, heading (rad), half-size (world)
layout(location = 1) in vec4 aInst1; // gait phase, panic (0..1), height above floor (0..1), seed

uniform float uAspect;
uniform vec2 uShadowOffset;  // fixed shadow offset in body-size units (simple shadows)
uniform float uShadowPass;   // 1 while drawing shadows
uniform float uShadow3D;     // 1 = shadows cast away from the flashlight, stretched by distance
uniform vec2 uLightPos;      // flashlight centre, world units
uniform float uLightRadius;
uniform float uQuadScale;    // grows the quad for glow passes

out vec2 vLocal;
out float vGait;
out float vPanic;
out float vHeight;
out float vSeed;
out vec2 vWorld;
out vec2 vLightDir;    // direction toward the light, in the spider's local frame
out vec2 vShadowDir;   // shadow direction in the local frame (shadow pass only)
out float vStretch;    // shadow elongation (shadow pass only)
out float vBlur;       // shadow softness (shadow pass only)

void main() {
    vec2 corner = vec2(float(gl_VertexID & 1), float(gl_VertexID >> 1)) * 2.0 - 1.0;
    float c = cos(aInst0.z);
    float s = sin(aInst0.z);
    float size = aInst0.w;
    float height = aInst1.z;

    // Direction to the light (world), used for shading and for 3D shadows.
    vec2 toLight = uLightPos - aInst0.xy;
    float dist = length(toLight);
    vec2 lightW = dist > 1e-4 ? toLight / dist : vec2(0.0, 1.0);
    // World -> local is the inverse rotation.
    vLightDir = vec2(c * lightW.x + s * lightW.y, -s * lightW.x + c * lightW.y);

    float quad = 1.15 * uQuadScale;
    vec2 offset = vec2(0.0);
    vStretch = 1.0;
    vBlur = 0.1;
    vShadowDir = vec2(1.0, 0.0);
    if (uShadowPass > 0.5) {
        if (uShadow3D > 0.5) {
            // A body raised off the floor casts its shadow away from the torch; the further
            // from the beam, the longer and softer the shadow.
            float far = clamp(dist / (uLightRadius * 2.5), 0.0, 1.0);
            float len = (0.35 + 1.6 * far) * (1.0 + height * 5.0);
            vec2 sd = -vLightDir;
            vShadowDir = sd;
            vStretch = 1.0 + 0.9 * far + height * 1.5;
            vBlur = 0.05 + 0.18 * far + height * 0.35;
            offset = -lightW * len;
            quad *= 1.0 + 0.9 * far + height * 2.0;
        } else {
            offset = uShadowOffset * (1.0 + height * 5.0);
            vBlur = 0.1 + height * 0.35;
            quad *= 1.0 + height * 1.5;
        }
    }

    vec2 local = corner * quad;
    vec2 rotated = vec2(c * local.x - s * local.y, s * local.x + c * local.y) * size;
    // The shadow quad is centred on the shadow, so the local frame is shifted to match.
    vec2 shadowShift = uShadowPass > 0.5 ? offset * size : vec2(0.0);
    vec2 world = aInst0.xy + rotated + shadowShift;
    gl_Position = vec4(vec2(world.x / uAspect, world.y) * 2.0 - 1.0, 0.0, 1.0);
    vLocal = local;
    vWorld = world;
    vGait = aInst1.x;
    vPanic = aInst1.y;
    vHeight = height;
    vSeed = aInst1.w;
}
