#version 430 core

layout(local_size_x = 8, local_size_y = 8) in;

uniform sampler2D Source;
uniform int SourceLevel;
uniform ivec2 SourceSize;

layout(r32f, binding = 0) writeonly uniform image2D Target;

void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(Target);
    if (p.x >= size.x || p.y >= size.y) {
        return;
    }
    ivec2 s = p * 2;
    float d = 0.0;
    for (int y = 0; y < 3; y++) {
        for (int x = 0; x < 3; x++) {
            ivec2 q = min(s + ivec2(x, y), SourceSize - 1);
            d = max(d, texelFetch(Source, q, SourceLevel).r);
        }
    }
    imageStore(Target, p, vec4(d));
}
