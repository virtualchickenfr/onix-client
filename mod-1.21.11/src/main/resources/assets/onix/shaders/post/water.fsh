#version 330

// The sunken scene's water (the same as the launcher's, launcher/src/js/fall.js): seen from below, the surface ripples
// and glows far above, light falls in through it in soft shafts flickering with the waves, caustics (the moving net
// of light) play over everything, specks drift at three depths, and it all fades into the dark below.

layout(std140) uniform WaterConfig {
    vec2 Size;   // pixels
    float Time;  // seconds
};

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}
float fbm(vec2 p) { float s = 0.0, a = 0.5; for (int i = 0; i < 4; i++) { s += a * noise(p); p = p * 2.03 + 7.1; a *= 0.5; } return s; }

float caustic(vec2 p, float t) {
    vec2 w = vec2(fbm(p * 0.7 + vec2(t * 0.10, -t * 0.07)), fbm(p * 0.7 + vec2(-t * 0.08, t * 0.11) + 5.2));
    p += (w - 0.5) * 2.4;
    float a = sin(p.x * 1.7 + t * 0.55) + sin(p.y * 1.9 - t * 0.45) + sin((p.x + p.y) * 1.3 + t * 0.35);
    float b = sin(p.x * 2.3 - t * 0.40) + sin(p.y * 2.1 + t * 0.50) + sin((p.x - p.y) * 1.6 - t * 0.30);
    float l1 = pow(clamp(1.0 - abs(a) * 0.9, 0.0, 1.0), 6.0);
    float l2 = pow(clamp(1.0 - abs(b) * 0.9, 0.0, 1.0), 6.0);
    return l1 + l2 * 0.7;
}

float specks(vec2 uv, float scale, float speed, float size, float blur, float seed, float t) {
    vec2 g = uv * scale + vec2(sin(t * 0.13 + seed) * 0.6, -t * speed);
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    float h = hash(id + seed);
    if (h < 0.55) return 0.0;
    vec2 o = vec2(hash(id + 3.1 + seed), hash(id + 7.7 + seed)) - 0.5;
    o += 0.15 * vec2(sin(t * 0.7 + h * 20.0), cos(t * 0.6 + h * 13.0));
    float d = length(f - o * 0.7);
    return smoothstep(size + blur, size * 0.2, d) * (0.4 + 0.6 * hash(id + 11.0 + seed));
}

void main() {
    vec2 uv = vec2(texCoord.x, 1.0 - texCoord.y);   // y down, like the launcher's
    float aspect = Size.x / Size.y;
    vec2 q = vec2((uv.x - 0.5) * aspect, uv.y);
    float t = Time;

    vec3 top = vec3(0.11, 0.42, 0.47), mid = vec3(0.03, 0.17, 0.21), deep = vec3(0.005, 0.03, 0.045);
    vec3 col = mix(top, mid, smoothstep(0.0, 0.45, uv.y));
    col = mix(col, deep, smoothstep(0.35, 1.05, uv.y));

    float surf = smoothstep(0.2, 0.0, uv.y);
    float ripple = fbm(vec2(q.x * 5.0 + t * 0.15, uv.y * 24.0 - t * 0.6));
    float sparkle = caustic(vec2(q.x * 7.0, uv.y * 28.0) + vec2(t * 0.2, 0.0), t * 1.4);
    col += vec3(0.45, 0.82, 0.86) * surf * surf * (0.35 + 0.65 * ripple);
    col += vec3(0.7, 0.96, 1.0) * surf * sparkle * 0.35;

    vec2 o = vec2(sin(t * 0.07) * 0.15, -0.35);
    vec2 d = q - o;
    float ang = atan(d.x, d.y);
    float shafts = fbm(vec2(ang * 7.0, t * 0.12)) * 0.6 + fbm(vec2(ang * 19.0 + 4.0, t * 0.22)) * 0.4;
    shafts = smoothstep(0.38, 0.78, shafts);
    shafts *= 0.75 + 0.25 * sin(t * 1.7 + ang * 30.0);
    float reach = exp(-length(d) * 1.45) * smoothstep(1.15, 0.05, uv.y);
    col += vec3(0.42, 0.85, 0.9) * shafts * reach * 0.55;

    float cs = caustic(q * 3.2, t * 0.9);
    float cs2 = caustic(q * 5.3 + 11.0, t * 1.15);
    float cw = (0.35 + 0.65 * shafts) * smoothstep(1.0, 0.0, uv.y);
    col += vec3(0.5, 0.92, 0.95) * (cs * 0.10 + cs2 * 0.05) * cw;

    float lit = 0.35 + 0.65 * (shafts * reach * 2.0 + surf);
    float sp = specks(q, 34.0, 0.010, 0.05, 0.02, 1.0, t) * 0.35
             + specks(q, 18.0, 0.016, 0.06, 0.05, 7.0, t) * 0.5
             + specks(q, 8.0, 0.026, 0.05, 0.16, 13.0, t) * 0.45;
    col += vec3(0.75, 0.95, 1.0) * sp * lit * smoothstep(1.05, 0.2, uv.y);

    float vig = smoothstep(1.35, 0.35, length((uv - vec2(0.5, 0.42)) * vec2(aspect * 0.8, 1.0)));
    col *= mix(0.45, 1.0, vig);
    col += (hash(uv * Size + fract(t) * 91.0) - 0.5) * 0.012;
    fragColor = vec4(col, 1.0);
}
