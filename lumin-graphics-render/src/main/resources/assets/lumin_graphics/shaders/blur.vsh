#version 410 core

#ifdef LUMIN_VULKAN
#define LUMIN_BINDING(index) layout(set = 0, binding = index)
#define LUMIN_VERTEX_ID gl_VertexIndex
#else
#define LUMIN_BINDING(index)
#define LUMIN_VERTEX_ID gl_VertexID
#endif

layout(location = 0) out vec2 vUv;

void main() {
    vec2 pos;
    if (LUMIN_VERTEX_ID == 0) pos = vec2(-1.0, -1.0);
    else if (LUMIN_VERTEX_ID == 1) pos = vec2(3.0, -1.0);
    else pos = vec2(-1.0, 3.0);
    vUv = pos * 0.5 + 0.5;
    gl_Position = vec4(pos, 0.0, 1.0);
}
