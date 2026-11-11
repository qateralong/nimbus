#version 430 core

//PAGES

layout(location = 0) in ivec4 Origin;

uniform mat4 MvpMat;
uniform int FogShape;
uniform ivec3 CameraBlock;
uniform vec3 CameraFrac;
uniform sampler2D Sampler2;
uniform int FadeNow;
uniform vec4 DynLights[16];
uniform int DynCount;
uniform float FadeTime;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float vertexFade;

void main() {
    uvec4 v = fetchVertex(gl_VertexID);
    vec3 local = vec3(float(v.x & 0xFFFFu), float(v.x >> 16u), float(v.y & 0xFFFFu)) * (1.0 / 1024.0) - 16.0;
    vec3 pos = local + vec3(Origin.xyz - CameraBlock) - CameraFrac;
    gl_Position = MvpMat * vec4(pos, 1.0);
    vertexDistance = FogShape == 0 ? length(pos) : max(length(pos.xz), abs(pos.y));
    vec2 light = vec2(float((v.z >> 16u) & 0xFFu), float(v.z >> 24u));
    // dynamic lights (camera-relative xyz, level in w): block light = level - distance, like a placed torch
    for (int i = 0; i < DynCount; i++) {
        light.x = max(light.x, clamp(DynLights[i].w - distance(pos, DynLights[i].xyz), 0.0, 15.0) * 16.0);
    }
    vertexColor = unpackUnorm4x8(v.w) * texture(Sampler2, clamp(light / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    // chunk fade-in: 0 = just appeared (fog colour), 1 = fully shown; eased
    float fade = FadeTime > 0.0 ? clamp(float(FadeNow - Origin.w) / FadeTime, 0.0, 1.0) : 1.0;
    vertexFade = fade * fade * (3.0 - 2.0 * fade);
    texCoord0 = vec2(float(v.y >> 16u), float(v.z & 0xFFFFu)) * (1.0 / 65536.0);
}
