#version 430 core

uniform float Inner;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec3 relative;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // the real world is drawn inside this square
    if (max(abs(relative.x), abs(relative.z)) < Inner) {
        discard;
    }
    vec4 color = vertexColor;
    float distance = length(relative.xz);
    if (distance > FogStart) {
        float fog = distance < FogEnd ? smoothstep(FogStart, FogEnd, distance) : 1.0;
        color.rgb = mix(color.rgb, FogColor.rgb, fog);
    }
    fragColor = vec4(color.rgb, 1.0);
}
