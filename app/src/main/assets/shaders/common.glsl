// Uniforms shared by every scene shader (set by Scene.setCommon()).
#define MAX_PEOPLE 8
uniform vec2 uResolution;
uniform float uTime;
uniform vec4 uPeople[MAX_PEOPLE]; // xy = canvas uv, zw = velocity (uv / s)
uniform int uPeopleCount;
uniform float uLevel;
uniform float uBass;
uniform float uMid;
uniform float uTreble;
uniform float uBeat;

float canvasAspect() { return uResolution.x / uResolution.y; }

// Canvas uv -> isotropic "world" coordinates: x in [0, aspect], y in [0, 1].
vec2 toWorld(vec2 uv) { return vec2(uv.x * canvasAspect(), uv.y); }

// Ripple simulation textures store signed height; 8-bit fallback stores it biased.
float decodeRipple(float v, float encoded) { return encoded > 0.5 ? v * 2.0 - 1.0 : v; }
