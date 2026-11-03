#version 460 core

layout(local_size_x = 64) in;

layout(std430, binding = 0) buffer Commands {
    uint commands[];
};

layout(std430, binding = 1) readonly buffer Origins {
    ivec4 origins[];
};

uniform uint CommandBase;
uniform uint CommandCount;
uniform vec4 Planes[6];
uniform ivec3 CameraBlock;
uniform vec3 CameraFrac;

void main() {
    uint i = gl_GlobalInvocationID.x;
    if (i >= CommandCount) {
        return;
    }
    uint at = CommandBase + i * 5u;
    vec3 low = vec3(origins[commands[at + 4u]].xyz - CameraBlock) - CameraFrac;
    vec3 high = low + 16.0;
    bool inside = true;
    for (int p = 0; p < 6; p++) {
        vec4 plane = Planes[p];
        vec3 far = mix(low, high, greaterThanEqual(plane.xyz, vec3(0.0)));
        if (dot(plane.xyz, far) + plane.w < 0.0) {
            inside = false;
            break;
        }
    }
    commands[at + 1u] = inside ? 1u : 0u;
}
