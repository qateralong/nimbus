#version 430 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

uniform sampler2D Sampler2;
uniform mat4 MvpMat;
uniform vec3 Offset;

out vec3 relative;
out vec4 vertexColor;

void main() {
    relative = Position + Offset;
    gl_Position = MvpMat * vec4(relative, 1.0);
    // full sky light, no block light: the lightmap's day and night colour
    vertexColor = Color * texelFetch(Sampler2, ivec2(0, 15), 0);
}
