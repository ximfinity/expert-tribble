#version 300 es
precision highp float;
#include "noise.glsl"
#include "common.glsl"

// Coloring game display: the painted canvas (with a little brush texture) plus an optional
// outline picture to colour in. Output is premultiplied for blending over the game background.
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uPaint;
uniform float uTemplate; // 0 none, 1 pumpkin, 2 star, 3 heart, 4 house, 5 fish

float sdBox(vec2 p, vec2 b) {
    vec2 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
}
float sdEll(vec2 p, vec2 r) { return (length(p / r) - 1.0) * min(r.x, r.y); }

float outline(vec2 q, int t) {
    if (t == 1) { // pumpkin: three lobes and a stem
        float d = min(sdEll(q - vec2(-0.32, 0.0), vec2(0.42, 0.55)), sdEll(q - vec2(0.32, 0.0), vec2(0.42, 0.55)));
        d = min(d, sdEll(q, vec2(0.45, 0.6)));
        d = min(d, sdBox(q - vec2(0.0, 0.68), vec2(0.07, 0.12)));
        float face = min(min(sdEll(q - vec2(-0.25, 0.12), vec2(0.1, 0.09)), sdEll(q - vec2(0.25, 0.12), vec2(0.1, 0.09))),
                         sdEll(q - vec2(0.0, -0.22), vec2(0.32, 0.08)));
        return min(abs(d), abs(face));
    }
    if (t == 2) { // star
        float an = atan(q.y, q.x) - 1.5708;
        float rs = mix(0.38, 0.85, pow(0.5 + 0.5 * cos(5.0 * an), 3.0));
        return abs(length(q) - rs);
    }
    if (t == 3) { // heart (exact distance; tip at the bottom)
        vec2 h = vec2(abs(q.x), q.y + 0.7) / 1.3;
        float d;
        if (h.y + h.x > 1.0) {
            d = length(h - vec2(0.25, 0.75)) - 0.35355;
        } else {
            vec2 a = h - vec2(0.0, 1.0);
            vec2 b = h - 0.5 * max(h.x + h.y, 0.0);
            d = sqrt(min(dot(a, a), dot(b, b))) * sign(h.x - h.y);
        }
        return abs(d * 1.3);
    }
    if (t == 4) { // house
        float body = sdBox(q - vec2(0.0, -0.2), vec2(0.5, 0.4));
        vec2 r = q - vec2(0.0, 0.2);
        float roof = max(abs(r.x) * 0.8 + r.y - 0.55, -r.y);
        float door = sdBox(q - vec2(0.0, -0.4), vec2(0.12, 0.2));
        float win = sdBox(q - vec2(0.28, -0.05), vec2(0.1, 0.1));
        return min(min(abs(body), abs(roof)), min(abs(door), abs(win)));
    }
    if (t == 5) { // fish
        float body = sdEll(q - vec2(0.12, 0.0), vec2(0.6, 0.35));
        vec2 tq = q - vec2(-0.55, 0.0);
        float tail = max(abs(tq.y) - (0.05 - tq.x) * 0.8, max(tq.x - 0.05, -tq.x - 0.3));
        float eye = length(q - vec2(0.45, 0.1)) - 0.05;
        return min(min(abs(body), abs(tail)), abs(eye));
    }
    return 1e5;
}

void main() {
    vec4 paint = texture(uPaint, vUv);
    vec2 p = toWorld(vUv);
    float grain = 0.88 + 0.12 * fbm2(p * 60.0, 2);
    vec3 col = paint.rgb * grain;
    float a = paint.a;
    int t = int(uTemplate + 0.5);
    if (t > 0) {
        vec2 q = (p - vec2(canvasAspect() * 0.5, 0.56)) / 0.34;
        float d = outline(q, t) * 0.34;
        float line = 1.0 - smoothstep(0.004, 0.007, d);
        col = mix(col * a, vec3(0.95), line);
        a = max(a, line);
        fragColor = vec4(col, a);
        return;
    }
    fragColor = vec4(col * a, a);
}
