#version 410 core

#ifdef LUMIN_VULKAN
#define LUMIN_BINDING(index) layout(set = 0, binding = index)
#define LUMIN_VERTEX_ID gl_VertexIndex
#else
#define LUMIN_BINDING(index)
#define LUMIN_VERTEX_ID gl_VertexID
#endif

layout(std140) LUMIN_BINDING(0) uniform LuminFrame {
    mat4 Projection;
    vec4 Viewport;
};


layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

void main() {
    gl_Position = Projection * vec4(Position, 1.0);
}
