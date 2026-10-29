#version 460 core

layout(std430, binding = 0) readonly buffer Vertices {
    uvec4 vertices[];
};

layout(location = 0) in ivec3 Origin;

uniform mat4 MvpMat;
uniform int FogShape;
uniform ivec3 CameraBlock;
uniform vec3 CameraFrac;
uniform sampler2D Sampler2;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    uvec4 v = vertices[gl_VertexID];
    vec3 local = vec3(float(v.x & 0xFFFFu), float(v.x >> 16u), float(v.y & 0xFFFFu)) * (1.0 / 1024.0) - 16.0;
    vec3 pos = local + vec3(Origin - CameraBlock) - CameraFrac;
    gl_Position = MvpMat * vec4(pos, 1.0);
    vertexDistance = FogShape == 0 ? length(pos) : max(length(pos.xz), abs(pos.y));
    vec2 light = vec2(float((v.z >> 16u) & 0xFFu), float(v.z >> 24u));
    vertexColor = unpackUnorm4x8(v.w) * texture(Sampler2, clamp(light / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    texCoord0 = vec2(float(v.y >> 16u), float(v.z & 0xFFFFu)) * (1.0 / 65536.0);
}
