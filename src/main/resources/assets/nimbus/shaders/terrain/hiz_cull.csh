#version 460 core

// Mode: 0 select last visible, 1 test opaque (draw newly visible), 2 test translucent, 3 no pyramid.

layout(local_size_x = 64) in;

layout(std430, binding = 0) buffer Commands {
    uint commands[];
};

layout(std430, binding = 1) readonly buffer Origins {
    ivec4 origins[];
};

layout(std430, binding = 2) buffer Stats {
    uint stats[];
};

layout(std430, binding = 3) readonly buffer VisiblePrev {
    uint visiblePrev[];
};

layout(std430, binding = 4) writeonly buffer VisibleNext {
    uint visibleNext[];
};

uniform uint CommandBase;
uniform uint CommandCount;
uniform uint StatsSlot;
uniform int Mode;
uniform mat4 Mvp;
uniform ivec3 CameraBlock;
uniform vec3 CameraFrac;
uniform sampler2D Pyramid;
uniform ivec2 PyramidSize;
uniform int PyramidLevels;

bool occluded(ivec3 origin) {
    vec3 low = vec3(origin - CameraBlock) - CameraFrac;
    vec3 high = low + 16.0;
    if (all(lessThan(low, vec3(2.0))) && all(greaterThan(high, vec3(-2.0)))) {
        return false;
    }
    vec2 lo = vec2(1.0e9);
    vec2 hi = vec2(-1.0e9);
    float nearest = 1.0;
    for (int c = 0; c < 8; c++) {
        vec3 corner = vec3((c & 1) != 0 ? high.x : low.x, (c & 2) != 0 ? high.y : low.y, (c & 4) != 0 ? high.z : low.z);
        vec4 clip = Mvp * vec4(corner, 1.0);
        if (clip.w <= 0.05) {
            return false;
        }
        vec3 ndc = clip.xyz / clip.w;
        vec2 uv = ndc.xy * 0.5 + 0.5;
        lo = min(lo, uv);
        hi = max(hi, uv);
        nearest = min(nearest, ndc.z * 0.5 + 0.5);
    }
    if (lo.x < 0.0 || lo.y < 0.0 || hi.x > 1.0 || hi.y > 1.0 || nearest <= 0.0) {
        return false;
    }
    vec2 extent = (hi - lo) * vec2(PyramidSize);
    int level = clamp(int(ceil(log2(max(max(extent.x, extent.y), 1.0)))), 0, PyramidLevels - 1);
    ivec2 size = max(PyramidSize >> level, ivec2(1));
    ivec2 a = clamp(ivec2(lo * vec2(size)), ivec2(0), size - 1);
    ivec2 b = clamp(ivec2(hi * vec2(size)), ivec2(0), size - 1);
    float farthest = 0.0;
    for (int y = a.y; y <= b.y; y++) {
        for (int x = a.x; x <= b.x; x++) {
            farthest = max(farthest, texelFetch(Pyramid, ivec2(x, y), level).r);
        }
    }
    return nearest > farthest;
}

void main() {
    uint i = gl_GlobalInvocationID.x;
    if (i >= CommandCount) {
        return;
    }
    uint at = CommandBase + i * 5u;
    uint id = commands[at + 4u];
    bool before = visiblePrev[id] != 0u;
    if (Mode == 0) {
        if (!before) {
            commands[at + 1u] = 0u;
        }
        return;
    }
    if (Mode == 3) {
        visibleNext[id] = 1u;
        commands[at + 1u] = before ? 0u : 1u;
        return;
    }
    bool hidden = occluded(origins[id].xyz);
    visibleNext[id] = hidden ? 0u : 1u;
    bool draw = Mode == 2 ? !hidden : (!hidden && !before);
    commands[at + 1u] = draw ? 1u : 0u;
    if (hidden) {
        atomicAdd(stats[StatsSlot], 1u);
        atomicAdd(stats[StatsSlot + 1u], commands[at] / 6u);
    }
}
