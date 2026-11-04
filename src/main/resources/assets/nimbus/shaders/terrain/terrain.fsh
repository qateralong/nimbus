#version 430 core

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float AlphaCutoff;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
#ifdef ALPHA_CUTOFF
    if (color.a < AlphaCutoff) {
        discard;
    }
#endif
    if (vertexDistance > FogStart) {
        float fog = vertexDistance < FogEnd ? smoothstep(FogStart, FogEnd, vertexDistance) : 1.0;
        color.rgb = mix(color.rgb, FogColor.rgb, fog * FogColor.a);
    }
    fragColor = color;
}
