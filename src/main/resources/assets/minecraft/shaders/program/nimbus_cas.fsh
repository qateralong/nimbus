#version 150

uniform sampler2D DiffuseSampler;
uniform float Sharpness;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

vec3 tap(float x, float y) {
    return texture(DiffuseSampler, texCoord + vec2(x, y) * oneTexel).rgb;
}

void main() {
    vec3 a = tap(-1.0, -1.0);
    vec3 b = tap(0.0, -1.0);
    vec3 c = tap(1.0, -1.0);
    vec3 d = tap(-1.0, 0.0);
    vec3 e = tap(0.0, 0.0);
    vec3 f = tap(1.0, 0.0);
    vec3 g = tap(-1.0, 1.0);
    vec3 h = tap(0.0, 1.0);
    vec3 i = tap(1.0, 1.0);
    vec3 mn = min(min(min(d, e), min(f, b)), h);
    mn += min(mn, min(min(a, c), min(g, i)));
    vec3 mx = max(max(max(d, e), max(f, b)), h);
    mx += max(mx, max(max(a, c), max(g, i)));
    vec3 amp = sqrt(clamp(min(mn, 2.0 - mx) / max(mx, vec3(1e-5)), 0.0, 1.0));
    float peak = -1.0 / mix(8.0, 5.0, clamp(Sharpness, 0.0, 1.0));
    vec3 w = amp * peak;
    vec3 color = ((b + d + f + h) * w + e) / (1.0 + 4.0 * w);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
