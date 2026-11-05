#version 430 core

layout(location = 0) in vec3 Center;
layout(location = 1) in vec4 Rotation;
layout(location = 2) in float Size;
layout(location = 3) in vec4 Uv;
layout(location = 4) in vec4 Color;
layout(location = 5) in ivec2 Light;

uniform sampler2D Sampler2;
uniform mat4 MvpMat;
uniform int FogShape;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

// strip order of vanilla's corners (1,-1) (1,1) (-1,-1) (-1,1)
const vec2 CORNERS[4] = vec2[](vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, -1.0), vec2(-1.0, 1.0));

void main() {
    vec2 corner = CORNERS[gl_VertexID];
    vec3 local = vec3(corner, 0.0);
    vec3 rotated = local + 2.0 * cross(Rotation.xyz, cross(Rotation.xyz, local) + Rotation.w * local);
    vec3 position = Center + rotated * Size;
    gl_Position = MvpMat * vec4(position, 1.0);
    vertexDistance = FogShape == 0 ? length(position) : max(length(position.xz), abs(position.y));
    texCoord0 = vec2(corner.x > 0.0 ? Uv.z : Uv.x, corner.y > 0.0 ? Uv.y : Uv.w);
    vertexColor = Color * texelFetch(Sampler2, Light / 16, 0);
}
