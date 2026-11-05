#version 430 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec3 Normal;
layout(location = 3) in vec4 Pose0;
layout(location = 4) in vec4 Pose1;
layout(location = 5) in vec4 Pose2;
layout(location = 6) in vec3 Normal0;
layout(location = 7) in vec3 Normal1;
layout(location = 8) in vec3 Normal2;
layout(location = 9) in ivec2 UV2;
layout(location = 10) in ivec2 UV1;
layout(location = 11) in vec4 Color;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform mat4 MvpMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;

void main() {
    vec4 local = vec4(Position, 1.0);
    vec3 position = vec3(dot(Pose0, local), dot(Pose1, local), dot(Pose2, local));
    vec3 normal = normalize(vec3(dot(Normal0, Normal), dot(Normal1, Normal), dot(Normal2, Normal)));
    gl_Position = MvpMat * vec4(position, 1.0);
    vertexDistance = FogShape == 0 ? length(position) : max(length(position.xz), abs(position.y));
    float light0 = max(0.0, dot(Light0_Direction, normal));
    float light1 = max(0.0, dot(Light1_Direction, normal));
    float lightAccum = min(1.0, (light0 + light1) * 0.6 + 0.4);
    vertexColor = vec4(Color.rgb * lightAccum, Color.a);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
