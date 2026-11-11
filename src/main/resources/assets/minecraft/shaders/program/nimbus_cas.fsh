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
    vec3 b = tap(0.0, -1.0);
    vec3 d = tap(-1.0, 0.0);
    vec3 e = tap(0.0, 0.0);
    vec3 f = tap(1.0, 0.0);
    vec3 h = tap(0.0, 1.0);
    vec3 mn = min(min(min(d, e), min(f, b)), h);
    vec3 mx = max(max(max(d, e), max(f, b)), h);
    vec3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, vec3(1e-5)), 0.0, 1.0));
    vec3 w = amp * (-1.0 / mix(8.0, 5.0, clamp(Sharpness, 0.0, 1.0)));
    fragColor = vec4(clamp(((b + d + f + h) * w + e) / (1.0 + 4.0 * w), 0.0, 1.0), 1.0);
}
